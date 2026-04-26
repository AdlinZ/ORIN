import { computed, ref, watch } from "vue";

function readJsonStorage(key, fallback) {
  try {
    const raw = window.localStorage.getItem(key);
    if (!raw) return fallback;
    const parsed = JSON.parse(raw);
    return parsed ?? fallback;
  } catch {
    return fallback;
  }
}

function writeJsonStorage(key, payload) {
  try {
    window.localStorage.setItem(key, JSON.stringify(payload));
  } catch {
    // localStorage may be unavailable in private windows.
  }
}

export function useWorkflowRunner({
  workflows,
  fetchWorkflowGraph,
  fetchConversation,
  runWorkflow,
  runWorkflowStream,
  t = (key) => key,
  conversationStorageKey = "agent-playground:workflow-conversations",
  selectedWorkflowStorageKey = "agent-playground:selected-workflow",
} = {}) {
  const selectedWorkflowId = ref("");
  const selectedGraph = ref(null);
  const lastRun = ref(null);
  const loading = ref(false);
  const errorMessage = ref("");
  const currentConversationId = ref("");
  const chatMessages = ref([]);
  const displayedTrace = ref([]);
  const replayNodeId = ref("");
  const replayingTrace = ref(false);
  const replayToken = ref(0);
  const activeRunController = ref(null);

  const selectedWorkflow = computed(() =>
    (workflows?.value || []).find((workflow) => workflow.id === selectedWorkflowId.value) || null
  );

  const activeNodeId = computed(() => {
    if (replayingTrace.value) return replayNodeId.value;
    const trace = lastRun.value?.trace || [];
    for (let index = trace.length - 1; index >= 0; index -= 1) {
      if (trace[index].payload?.node_id) return trace[index].payload.node_id;
    }
    return "";
  });

  const traceForView = computed(() =>
    replayingTrace.value ? displayedTrace.value : (lastRun.value?.trace || [])
  );

  function readConversationStorage() {
    const parsed = readJsonStorage(conversationStorageKey, []);
    return Array.isArray(parsed) ? parsed : [];
  }

  function getStoredSelectedWorkflowId() {
    try {
      return String(window.localStorage.getItem(selectedWorkflowStorageKey) || "").trim();
    } catch {
      return "";
    }
  }

  function setStoredSelectedWorkflowId(workflowId) {
    try {
      if (workflowId) {
        window.localStorage.setItem(selectedWorkflowStorageKey, workflowId);
      } else {
        window.localStorage.removeItem(selectedWorkflowStorageKey);
      }
    } catch {
      // noop
    }
  }

  function getStoredConversationId(workflowId) {
    if (!workflowId) return "";
    const store = readConversationStorage();
    const found = store.find((item) => String(item?.workflow_id || "") === workflowId);
    return String(found?.conversation_id || "").trim();
  }

  function setStoredConversationId(workflowId, conversationId) {
    if (!workflowId) return;
    const store = readConversationStorage().filter(
      (item) => String(item?.workflow_id || "") !== workflowId
    );
    if (conversationId) store.push({ workflow_id: workflowId, conversation_id: conversationId });
    writeJsonStorage(conversationStorageKey, store);
  }

  function coalesceRuntimeWaitTrace(traceEvents) {
    if (!Array.isArray(traceEvents) || !traceEvents.length) return [];
    const merged = [];
    for (const event of traceEvents) {
      const nodeId = String(event?.payload?.node_id || "");
      if (nodeId === "runtime_wait") {
        const last = merged[merged.length - 1];
        if (String(last?.payload?.node_id || "") === "runtime_wait") {
          merged[merged.length - 1] = event;
          continue;
        }
      }
      merged.push(event);
    }
    return merged;
  }

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function replayTrace(traceEvents) {
    const normalizedEvents = coalesceRuntimeWaitTrace(traceEvents);
    const token = replayToken.value + 1;
    replayToken.value = token;
    displayedTrace.value = [];
    replayNodeId.value = "start";
    replayingTrace.value = true;

    if (!normalizedEvents?.length) {
      replayingTrace.value = false;
      return true;
    }

    const stepDelay = normalizedEvents.length > 24 ? 85 : normalizedEvents.length > 14 ? 120 : 160;
    for (const event of normalizedEvents) {
      if (token !== replayToken.value) return false;
      displayedTrace.value = [...displayedTrace.value, event];
      const nextNode = event?.payload?.node_id || event?.payload?.next_node_id || "";
      if (nextNode) replayNodeId.value = nextNode;
      await sleep(stepDelay);
    }

    if (token === replayToken.value) {
      replayingTrace.value = false;
      return true;
    }
    return false;
  }

  async function loadGraph(workflowId) {
    if (!workflowId) {
      selectedGraph.value = null;
      return;
    }
    selectedGraph.value = await fetchWorkflowGraph(workflowId);
  }

  async function restoreConversation(workflowId) {
    const conversationId = getStoredConversationId(workflowId);
    if (!conversationId) return;
    try {
      const conversation = await fetchConversation(conversationId);
      currentConversationId.value = conversation.id;
      chatMessages.value = (conversation.messages || []).map((message) => ({
        id: message.id,
        role: message.role,
        content: message.content,
        agentName: message.agent_name || "",
      }));
    } catch {
      currentConversationId.value = "";
      chatMessages.value = [];
      setStoredConversationId(workflowId, "");
    }
  }

  function chooseInitialWorkflow() {
    if (selectedWorkflowId.value || !(workflows?.value || []).length) return;
    const storedWorkflowId = getStoredSelectedWorkflowId();
    const restoredWorkflow = (workflows.value || []).find((workflow) => workflow.id === storedWorkflowId);
    selectedWorkflowId.value = restoredWorkflow?.id || workflows.value[0].id;
  }

  async function run(payload) {
    errorMessage.value = "";
    loading.value = true;
    if (activeRunController.value) {
      activeRunController.value.abort();
      activeRunController.value = null;
    }
    const token = replayToken.value + 1;
    replayToken.value = token;
    displayedTrace.value = [];
    replayNodeId.value = "start";
    replayingTrace.value = true;
    const controller = new AbortController();
    activeRunController.value = controller;

    const userMessage = {
      id: `user_${Date.now()}`,
      role: "user",
      content: payload.user_input,
    };
    chatMessages.value = [...chatMessages.value, userMessage];
    const streamingAssistantId = `assistant_stream_${Date.now()}`;
    chatMessages.value = [
      ...chatMessages.value,
      {
        id: streamingAssistantId,
        role: "assistant",
        agentName: t("chat.assistant"),
        content: "正在编排并执行任务...\n",
        taskReports: [],
        planner: null,
      },
    ];

    const runPayload = {
      ...payload,
      conversation_id: currentConversationId.value || undefined,
    };

    try {
      let streamResult = null;
      let streamError = "";
      let streamTransportFailed = false;

      try {
        await runWorkflowStream(runPayload, {
          signal: controller.signal,
          onTrace: (event) => {
            if (token !== replayToken.value) return;
            const nodeId = String(event?.payload?.node_id || "");
            const isRuntimeWait = nodeId === "runtime_wait";
            if (isRuntimeWait) {
              const current = [...displayedTrace.value];
              const last = current[current.length - 1];
              if (String(last?.payload?.node_id || "") === "runtime_wait") {
                current[current.length - 1] = event;
              } else {
                current.push(event);
              }
              displayedTrace.value = current;
            } else {
              displayedTrace.value = [...displayedTrace.value, event];
            }
            const nextNode = event?.payload?.node_id || event?.payload?.next_node_id || "";
            if (nextNode) replayNodeId.value = nextNode;
            const detail = String(event?.detail || "").trim();
            if (detail) {
              chatMessages.value = chatMessages.value.map((msg) =>
                msg.id === streamingAssistantId
                  ? {
                      ...msg,
                      content: (() => {
                        const current = String(msg.content || "");
                        if (!isRuntimeWait) return `${current}- ${detail}\n`;
                        const waitPattern = /- Workflow is still running \(\d+s\)\.\n?$/m;
                        if (waitPattern.test(current)) {
                          return current.replace(waitPattern, `- ${detail}\n`);
                        }
                        return `${current}- ${detail}\n`;
                      })(),
                    }
                  : msg,
              );
            }
          },
          onFinal: (result) => {
            if (token !== replayToken.value) return;
            streamResult = result;
          },
          onError: (error) => {
            if (token !== replayToken.value) return;
            streamError = error?.message || String(error || "");
          },
        });
      } catch (error) {
        if (error?.name === "AbortError") return;
        streamResult = null;
        streamTransportFailed = true;
      }

      if (token !== replayToken.value) return;

      if (!streamResult) {
        if (streamError && !streamTransportFailed) {
          errorMessage.value = streamError;
          return;
        }
        const runResult = await runWorkflow(runPayload);
        if (token !== replayToken.value) return;
        lastRun.value = runResult;
        selectedGraph.value = runResult.graph;
        if (runResult.conversation_id) {
          currentConversationId.value = runResult.conversation_id;
          setStoredConversationId(payload.workflow_id, runResult.conversation_id);
        }
        const finished = await replayTrace(runResult.trace || []);
        if (finished && token === replayToken.value) {
          chatMessages.value = [
            ...chatMessages.value.filter((msg) => msg.id !== streamingAssistantId),
            {
              id: streamingAssistantId,
              role: "assistant",
              agentName: runResult.artifacts?.route_agent_name || t("chat.assistant"),
              content: runResult.assistant_message,
              taskReports: Array.isArray(runResult.artifacts?.task_reports) ? runResult.artifacts.task_reports : [],
              planner: runResult.artifacts?.planner || null,
            },
          ];
        }
        return;
      }

      if (streamError) errorMessage.value = streamError;

      lastRun.value = streamResult;
      selectedGraph.value = streamResult.graph;
      displayedTrace.value = coalesceRuntimeWaitTrace(streamResult.trace || displayedTrace.value);
      if (streamResult.conversation_id) {
        currentConversationId.value = streamResult.conversation_id;
        setStoredConversationId(payload.workflow_id, streamResult.conversation_id);
      }
      chatMessages.value = [
        ...chatMessages.value.filter((msg) => msg.id !== streamingAssistantId),
        {
          id: streamingAssistantId,
          role: "assistant",
          agentName: streamResult.artifacts?.route_agent_name || t("chat.assistant"),
          content: streamResult.assistant_message,
          taskReports: Array.isArray(streamResult.artifacts?.task_reports) ? streamResult.artifacts.task_reports : [],
          planner: streamResult.artifacts?.planner || null,
        },
      ];
    } catch (error) {
      if (token === replayToken.value) {
        errorMessage.value = String(error.message || error);
        chatMessages.value = chatMessages.value.filter((msg) => msg.id !== streamingAssistantId);
      }
    } finally {
      if (activeRunController.value === controller) activeRunController.value = null;
      if (token === replayToken.value) replayingTrace.value = false;
      loading.value = false;
    }
  }

  function clear() {
    if (activeRunController.value) {
      activeRunController.value.abort();
      activeRunController.value = null;
    }
    lastRun.value = null;
    chatMessages.value = [];
    if (selectedWorkflowId.value) setStoredConversationId(selectedWorkflowId.value, "");
    currentConversationId.value = "";
    displayedTrace.value = [];
    replayNodeId.value = "";
    replayingTrace.value = false;
    replayToken.value += 1;
  }

  function stop() {
    if (activeRunController.value) {
      activeRunController.value.abort();
      activeRunController.value = null;
    }
    replayingTrace.value = false;
    loading.value = false;
  }

  watch(selectedWorkflowId, async (workflowId) => {
    setStoredSelectedWorkflowId(workflowId);
    if (activeRunController.value) {
      activeRunController.value.abort();
      activeRunController.value = null;
    }
    chatMessages.value = [];
    currentConversationId.value = "";
    lastRun.value = null;
    displayedTrace.value = [];
    replayNodeId.value = "";
    replayingTrace.value = false;
    replayToken.value += 1;
    await loadGraph(workflowId);
    await restoreConversation(workflowId);
  });

  return {
    selectedWorkflowId,
    selectedWorkflow,
    selectedGraph,
    lastRun,
    loading,
    errorMessage,
    currentConversationId,
    chatMessages,
    displayedTrace,
    replayNodeId,
    replayingTrace,
    activeNodeId,
    traceForView,
    chooseInitialWorkflow,
    loadGraph,
    restoreConversation,
    run,
    clear,
    stop,
  };
}
