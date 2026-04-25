<script setup>
import "./playground.css"
import { computed, onMounted, provide, ref, watch } from "vue"

import {
  createAgent,
  createConversation,
  createWorkflow,
  deleteAgent,
  deleteWorkflow,
  fetchAppSettings,
  fetchAgents,
  fetchConversation,
  fetchSkills,
  fetchTemplates,
  fetchWorkflowGraph,
  fetchWorkflows,
  updateAgent,
  updateAppSettings,
  updateWorkflow,
  runWorkflowStream,
  runWorkflow,
} from "./api"
import { I18N_KEY, createUiI18n } from "./i18n"
import AgentsPage from "./pages/AgentsPage.vue"
import OverviewPage from "./pages/OverviewPage.vue"
import PlaygroundPage from "./pages/PlaygroundPage.vue"
import SettingsPage from "./pages/SettingsPage.vue"
import WorkflowsPage from "./pages/WorkflowsPage.vue"

const props = defineProps({
  initialPage: {
    type: String,
    default: "overview",
  },
  themeStyle: {
    type: String,
    default: "legacy",
  },
  standalone: {
    type: Boolean,
    default: false,
  },
})

const templates = ref([])
const skills = ref([])
const agents = ref([])
const workflows = ref([])
const selectedWorkflowId = ref("")
const selectedGraph = ref(null)
const lastRun = ref(null)
const loading = ref(false)
const errorMessage = ref("")
const currentPage = ref(props.initialPage)
const chatMessages = ref([])
const currentConversationId = ref("")
const displayedTrace = ref([])
const replayNodeId = ref("")
const replayingTrace = ref(false)
const replayToken = ref(0)
const activeRunController = ref(null)
const skillSyncStatus = ref("")
const appSettings = ref(null)
const savingSettings = ref(false)
const conversationStorageKey = "agent-playground:workflow-conversations"
const selectedWorkflowStorageKey = "agent-playground:selected-workflow"

const i18n = createUiI18n()
provide(I18N_KEY, i18n)
const t = i18n?.t || ((key) => key)

const activeNodeId = computed(() => {
  if (replayingTrace.value) return replayNodeId.value
  const trace = lastRun.value?.trace || []
  for (let index = trace.length - 1; index >= 0; index -= 1) {
    if (trace[index].payload?.node_id) return trace[index].payload.node_id
  }
  return ""
})

const traceForView = computed(() =>
  replayingTrace.value ? displayedTrace.value : (lastRun.value?.trace || [])
)

const selectedWorkflow = computed(() =>
  workflows.value.find((w) => w.id === selectedWorkflowId.value) || null
)

function readConversationStorage() {
  try {
    const raw = window.localStorage.getItem(conversationStorageKey)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function getStoredSelectedWorkflowId() {
  try {
    return String(window.localStorage.getItem(selectedWorkflowStorageKey) || "").trim()
  } catch {
    return ""
  }
}

function setStoredSelectedWorkflowId(workflowId) {
  try {
    if (workflowId) {
      window.localStorage.setItem(selectedWorkflowStorageKey, workflowId)
    } else {
      window.localStorage.removeItem(selectedWorkflowStorageKey)
    }
  } catch {}
}

function writeConversationStorage(payload) {
  try {
    window.localStorage.setItem(conversationStorageKey, JSON.stringify(payload))
  } catch {}
}

function getStoredConversationId(workflowId) {
  if (!workflowId) return ""
  const store = readConversationStorage()
  const found = store.find((item) => String(item?.workflow_id || "") === workflowId)
  return String(found?.conversation_id || "").trim()
}

function setStoredConversationId(workflowId, conversationId) {
  if (!workflowId) return
  const store = readConversationStorage().filter(
    (item) => String(item?.workflow_id || "") !== workflowId
  )
  if (conversationId) store.push({ workflow_id: workflowId, conversation_id: conversationId })
  writeConversationStorage(store)
}

async function restoreConversation(workflowId) {
  const conversationId = getStoredConversationId(workflowId)
  if (!conversationId) return
  try {
    const conversation = await fetchConversation(conversationId)
    currentConversationId.value = conversation.id
    chatMessages.value = (conversation.messages || []).map((message) => ({
      id: message.id,
      role: message.role,
      content: message.content,
      agentName: message.agent_name || "",
    }))
  } catch {
    currentConversationId.value = ""
    chatMessages.value = []
    setStoredConversationId(workflowId, "")
  }
}

async function loadInitialData() {
  ;[templates.value, skills.value, agents.value, workflows.value, appSettings.value] =
    await Promise.all([
      fetchTemplates(),
      fetchSkills(),
      fetchAgents(),
      fetchWorkflows(),
      fetchAppSettings(),
    ])

  if (!selectedWorkflowId.value && workflows.value.length) {
    const storedWorkflowId = getStoredSelectedWorkflowId()
    const restoredWorkflow = workflows.value.find((w) => w.id === storedWorkflowId)
    selectedWorkflowId.value = restoredWorkflow?.id || workflows.value[0].id
  }
}

async function loadGraph(workflowId) {
  if (!workflowId) {
    selectedGraph.value = null
    return
  }
  selectedGraph.value = await fetchWorkflowGraph(workflowId)
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function coalesceRuntimeWaitTrace(traceEvents) {
  if (!Array.isArray(traceEvents) || !traceEvents.length) return []
  const merged = []
  for (const event of traceEvents) {
    const nodeId = String(event?.payload?.node_id || "")
    if (nodeId === "runtime_wait") {
      const last = merged[merged.length - 1]
      if (String(last?.payload?.node_id || "") === "runtime_wait") {
        merged[merged.length - 1] = event
        continue
      }
    }
    merged.push(event)
  }
  return merged
}

async function replayTrace(traceEvents) {
  const normalizedEvents = coalesceRuntimeWaitTrace(traceEvents)
  const token = replayToken.value + 1
  replayToken.value = token
  displayedTrace.value = []
  replayNodeId.value = "start"
  replayingTrace.value = true

  if (!normalizedEvents?.length) {
    replayingTrace.value = false
    return true
  }

  const stepDelay = normalizedEvents.length > 24 ? 85 : normalizedEvents.length > 14 ? 120 : 160
  for (const event of normalizedEvents) {
    if (token !== replayToken.value) return false
    displayedTrace.value = [...displayedTrace.value, event]
    const nextNode = event?.payload?.node_id || event?.payload?.next_node_id || ""
    if (nextNode) replayNodeId.value = nextNode
    await sleep(stepDelay)
  }

  if (token === replayToken.value) {
    replayingTrace.value = false
    return true
  }
  return false
}

async function handleCreateAgent(payload) {
  errorMessage.value = ""
  try {
    const createdAgent = await createAgent(payload)
    const latestAgents = await fetchAgents()
    agents.value = [createdAgent, ...latestAgents.filter((a) => a.id !== createdAgent.id)]
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleUpdateAgent(payload) {
  errorMessage.value = ""
  try {
    await updateAgent(payload.id, payload.data)
    agents.value = await fetchAgents()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleDeleteAgent(agentId) {
  errorMessage.value = ""
  try {
    await deleteAgent(agentId)
    agents.value = await fetchAgents()
    workflows.value = await fetchWorkflows()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

function findSingleAgentChatWorkflow(agentId) {
  return (
    workflows.value.find(
      (w) =>
        w.type === "single_agent_chat" &&
        Array.isArray(w.specialist_agent_ids) &&
        w.specialist_agent_ids.length === 1 &&
        w.specialist_agent_ids[0] === agentId
    ) || null
  )
}

async function handleQuickChatAgent(agent) {
  if (!agent?.id) return
  errorMessage.value = ""
  try {
    let targetWorkflow = findSingleAgentChatWorkflow(agent.id)
    if (!targetWorkflow) {
      targetWorkflow = await createWorkflow({
        name: `${agent.name || "Agent"} Chat`,
        type: "single_agent_chat",
        specialist_agent_ids: [agent.id],
        finalizer_enabled: false,
        router_prompt: "Direct single-agent chat workflow.",
      })
      workflows.value = await fetchWorkflows()
      targetWorkflow =
        workflows.value.find((w) => w.id === targetWorkflow.id) ||
        findSingleAgentChatWorkflow(agent.id) ||
        targetWorkflow
    }
    currentPage.value = "playground"
    selectedWorkflowId.value = targetWorkflow.id
    await loadGraph(targetWorkflow.id)
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleCreateWorkflow(payload) {
  errorMessage.value = ""
  try {
    const workflow = await createWorkflow(payload)
    workflows.value = await fetchWorkflows()
    selectedWorkflowId.value = workflow.id
    currentPage.value = "playground"
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleUpdateWorkflow(payload) {
  errorMessage.value = ""
  try {
    const updated = await updateWorkflow(payload.id, payload.data)
    workflows.value = await fetchWorkflows()
    if (selectedWorkflowId.value === updated.id) await loadGraph(updated.id)
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleDeleteWorkflow(workflowId) {
  errorMessage.value = ""
  try {
    await deleteWorkflow(workflowId)
    workflows.value = await fetchWorkflows()
    if (selectedWorkflowId.value === workflowId) {
      selectedWorkflowId.value = workflows.value[0]?.id || ""
      if (selectedWorkflowId.value) {
        await loadGraph(selectedWorkflowId.value)
      } else {
        selectedGraph.value = null
      }
    }
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleSaveSettings(payload) {
  errorMessage.value = ""
  if (savingSettings.value) return
  savingSettings.value = true
  try {
    appSettings.value = await updateAppSettings(payload)
    skills.value = await fetchSkills()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  } finally {
    savingSettings.value = false
  }
}

async function handleRun(payload) {
  errorMessage.value = ""
  loading.value = true
  if (activeRunController.value) {
    activeRunController.value.abort()
    activeRunController.value = null
  }
  const token = replayToken.value + 1
  replayToken.value = token
  displayedTrace.value = []
  replayNodeId.value = "start"
  replayingTrace.value = true
  const controller = new AbortController()
  activeRunController.value = controller

  const userMessage = {
    id: `user_${Date.now()}`,
    role: "user",
    content: payload.user_input,
  }
  chatMessages.value = [...chatMessages.value, userMessage]
  const streamingAssistantId = `assistant_stream_${Date.now()}`
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
  ]

  const runPayload = {
    ...payload,
    conversation_id: currentConversationId.value || undefined,
  }

  try {
    let streamResult = null
    let streamError = ""
    let streamTransportFailed = false

    try {
      await runWorkflowStream(runPayload, {
        signal: controller.signal,
        onTrace: (event) => {
          if (token !== replayToken.value) return
          const nodeId = String(event?.payload?.node_id || "")
          const isRuntimeWait = nodeId === "runtime_wait"
          if (isRuntimeWait) {
            const current = [...displayedTrace.value]
            const last = current[current.length - 1]
            if (String(last?.payload?.node_id || "") === "runtime_wait") {
              current[current.length - 1] = event
            } else {
              current.push(event)
            }
            displayedTrace.value = current
          } else {
            displayedTrace.value = [...displayedTrace.value, event]
          }
          const nextNode = event?.payload?.node_id || event?.payload?.next_node_id || ""
          if (nextNode) replayNodeId.value = nextNode
          const detail = String(event?.detail || "").trim()
          if (detail) {
            chatMessages.value = chatMessages.value.map((msg) =>
              msg.id === streamingAssistantId
                ? {
                    ...msg,
                    content: (() => {
                      const current = String(msg.content || "")
                      if (!isRuntimeWait) return `${current}- ${detail}\n`
                      const waitPattern = /- Workflow is still running \(\d+s\)\.\n?$/m
                      if (waitPattern.test(current)) {
                        return current.replace(waitPattern, `- ${detail}\n`)
                      }
                      return `${current}- ${detail}\n`
                    })(),
                  }
                : msg,
            )
          }
        },
        onFinal: (result) => {
          if (token !== replayToken.value) return
          streamResult = result
        },
        onError: (error) => {
          if (token !== replayToken.value) return
          streamError = error?.message || String(error || "")
        },
      })
    } catch (error) {
      if (error?.name === "AbortError") return
      streamResult = null
      streamTransportFailed = true
    }

    if (token !== replayToken.value) return

    if (!streamResult) {
      if (streamError && !streamTransportFailed) {
        errorMessage.value = streamError
        return
      }
      const runResult = await runWorkflow(runPayload)
      if (token !== replayToken.value) return
      lastRun.value = runResult
      selectedGraph.value = runResult.graph
      if (runResult.conversation_id) {
        currentConversationId.value = runResult.conversation_id
        setStoredConversationId(payload.workflow_id, runResult.conversation_id)
      }
      const finished = await replayTrace(runResult.trace || [])
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
        ]
      }
      return
    }

    if (streamError) errorMessage.value = streamError

    lastRun.value = streamResult
    selectedGraph.value = streamResult.graph
    displayedTrace.value = coalesceRuntimeWaitTrace(streamResult.trace || displayedTrace.value)
    if (streamResult.conversation_id) {
      currentConversationId.value = streamResult.conversation_id
      setStoredConversationId(payload.workflow_id, streamResult.conversation_id)
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
    ]
  } catch (error) {
    if (token === replayToken.value) {
      errorMessage.value = String(error.message || error)
      chatMessages.value = chatMessages.value.filter((msg) => msg.id !== streamingAssistantId)
    }
  } finally {
    if (activeRunController.value === controller) activeRunController.value = null
    if (token === replayToken.value) replayingTrace.value = false
    loading.value = false
  }
}

function handleClearRun() {
  if (activeRunController.value) {
    activeRunController.value.abort()
    activeRunController.value = null
  }
  lastRun.value = null
  chatMessages.value = []
  if (selectedWorkflowId.value) setStoredConversationId(selectedWorkflowId.value, "")
  currentConversationId.value = ""
  displayedTrace.value = []
  replayNodeId.value = ""
  replayingTrace.value = false
  replayToken.value += 1
}

function handleStopRun() {
  if (activeRunController.value) {
    activeRunController.value.abort()
    activeRunController.value = null
  }
  replayingTrace.value = false
  loading.value = false
}

watch(selectedWorkflowId, async (workflowId) => {
  setStoredSelectedWorkflowId(workflowId)
  if (activeRunController.value) {
    activeRunController.value.abort()
    activeRunController.value = null
  }
  chatMessages.value = []
  currentConversationId.value = ""
  lastRun.value = null
  displayedTrace.value = []
  replayNodeId.value = ""
  replayingTrace.value = false
  replayToken.value += 1
  await loadGraph(workflowId)
  await restoreConversation(workflowId)
})

onMounted(async () => {
  try {
    await loadInitialData()
    await loadGraph(selectedWorkflowId.value)
    await restoreConversation(selectedWorkflowId.value)
  } catch {
    templates.value = []
    skills.value = []
    agents.value = []
    workflows.value = []
    appSettings.value = null
  }
})
</script>

<template>
  <div
    class="playground-scope app-frame"
    :class="{
      'playground-mode': currentPage === 'playground',
      'standalone-run': props.standalone,
      'workflows-page': currentPage === 'workflows',
    }"
    :data-theme="props.themeStyle"
  >
    <main class="shell page-shell">
      <div v-if="errorMessage" class="error-banner">
        {{ errorMessage }}
      </div>

      <Transition name="page-fade" mode="out-in">
        <div
          :key="currentPage"
          class="page-stage"
          :class="{ 'playground-stage': currentPage === 'playground' }"
        >
          <OverviewPage
            v-if="!props.standalone && currentPage === 'overview'"
            :agents="agents"
            :workflows="workflows"
            :templates="templates"
            @navigate="currentPage = $event"
          />

          <AgentsPage
            v-else-if="!props.standalone && currentPage === 'agents'"
            :agents="agents"
            :skills="skills"
            :skill-sync-status="skillSyncStatus"
            @create="handleCreateAgent"
            @update="handleUpdateAgent"
            @delete="handleDeleteAgent"
            @quick-chat="handleQuickChatAgent"
          />

          <WorkflowsPage
            v-else-if="!props.standalone && currentPage === 'workflows'"
            :templates="templates"
            :agents="agents"
            :workflows="workflows"
            :selected-workflow-id="selectedWorkflowId"
            @create="handleCreateWorkflow"
            @update="handleUpdateWorkflow"
            @delete="handleDeleteWorkflow"
            @select="selectedWorkflowId = $event"
          />

          <PlaygroundPage
            v-else-if="currentPage === 'playground'"
            :workflows="workflows"
            :agents="agents"
            :selected-workflow-id="selectedWorkflowId"
            :selected-workflow="selectedWorkflow"
            :selected-graph="selectedGraph"
            :active-node-id="activeNodeId"
            :loading="loading"
            :trace="traceForView"
            :trace-playing="replayingTrace"
            :chat-messages="chatMessages"
            @run="handleRun"
            @clear="handleClearRun"
            @stop="handleStopRun"
            @select-workflow="selectedWorkflowId = $event"
          />

          <SettingsPage
            v-else-if="!props.standalone"
            :settings="appSettings"
            :saving="savingSettings"
            @save="handleSaveSettings"
          />
        </div>
      </Transition>
    </main>
  </div>
</template>

<style scoped>
/* playground-scope provides all base styles via playground.css import */
/* Only add layout overrides needed for ORIN embedding */
.app-frame {
  height: 100%;
  min-height: 0;
}

.app-frame.workflows-page .page-shell {
  width: min(1280px, 100%);
}

.app-frame.workflows-page .page-stage {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #e11d48;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
