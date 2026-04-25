<script setup>
import { MessageSquare, PanelLeftClose, PanelLeftOpen, PanelRightClose, PanelRightOpen, Send, Square } from "lucide-vue-next";
import { inject, nextTick, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { marked } from "marked";
import { I18N_KEY } from "../i18n";

const props = defineProps({
  selectedWorkflowId: {
    type: String,
    default: "",
  },
  selectedWorkflow: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  leftVisible: {
    type: Boolean,
    default: true,
  },
  rightVisible: {
    type: Boolean,
    default: true,
  },
  showRightToggle: {
    type: Boolean,
    default: true,
  },
  messages: {
    type: Array,
    default: () => [],
  },
});

const emit = defineEmits(["run", "clear", "stop", "toggle-left", "toggle-right"]);
const i18n = inject(I18N_KEY, null);
const t = i18n?.t || ((key) => key);
const router = useRouter();

const form = reactive({
  user_input: "",
});
const reportExpanded = ref({});
const inputRef = ref(null);
const scrollRef = ref(null);

marked.setOptions({
  breaks: true,
  gfm: true,
});

function resizeInput() {
  const el = inputRef.value;
  if (!el) return;
  el.style.height = "auto";
  const maxHeight = 180;
  const nextHeight = Math.min(el.scrollHeight, maxHeight);
  el.style.height = `${nextHeight}px`;
  el.style.overflowY = el.scrollHeight > maxHeight ? "auto" : "hidden";
}

function handleInput() {
  nextTick(resizeInput);
}

function renderMarkdown(content) {
  return marked.parse(String(content || ""));
}

function hasTaskReports(message) {
  return Array.isArray(message?.taskReports) && message.taskReports.length > 0;
}

function hasPlanner(message) {
  return Boolean(message?.planner && typeof message.planner === "object");
}

function reportKey(messageId, taskId) {
  return `${messageId}:${taskId}`;
}

function reportCollapsed(messageId, taskId) {
  return Boolean(reportExpanded.value[reportKey(messageId, taskId)]);
}

function shouldEnableCollapse(report) {
  return String(report?.result || "").length > 260;
}

function toggleReport(messageId, taskId) {
  const key = reportKey(messageId, taskId);
  reportExpanded.value = {
    ...reportExpanded.value,
    [key]: !Boolean(reportExpanded.value[key]),
  };
}

function scrollToBottom() {
  const el = scrollRef.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

function handleKeydown(event) {
  if (event.isComposing) return;
  if (event.key === "Enter" && !event.shiftKey) {
    event.preventDefault();
    submit();
  }
}

async function submit() {
  if (props.loading || !props.selectedWorkflowId || !form.user_input.trim()) return;
  const nextInput = form.user_input;
  form.user_input = "";
  await nextTick();
  resizeInput();
  await emit("run", {
    workflow_id: props.selectedWorkflowId,
    user_input: nextInput,
  });
}

function editCurrentWorkflow() {
  if (!props.selectedWorkflowId) return;
  router.push({
    path: "/dashboard/applications/playground/workflows",
    query: { workflowId: props.selectedWorkflowId },
  });
}

onMounted(() => {
  resizeInput();
  nextTick(scrollToBottom);
});

watch(
  () => [props.messages.length, props.loading],
  () => {
    nextTick(scrollToBottom);
  },
);
</script>

<template>
  <section class="glass-panel chat-shell">
    <header class="run-panel-header">
      <div class="chat-head-main">
        <button class="chat-panel-button" type="button" @click="$emit('toggle-left')">
          <component :is="props.leftVisible ? PanelLeftClose : PanelLeftOpen" :size="15" />
        </button>
        <div class="chat-icon">
          <MessageSquare :size="16" />
        </div>
        <div>
          <h3 class="run-panel-title">{{ t("chat.title") }}</h3>
          <p class="chat-active-text">{{ t("chat.active") }}: {{ selectedWorkflow?.name || t("chat.noneSelected") }}</p>
        </div>
      </div>
      <div class="chat-header-actions">
        <button
          v-if="selectedWorkflowId"
          class="text-button text-xs"
          @click="editCurrentWorkflow"
        >
          {{ t("chat.editWorkflow") }}
        </button>
        <button class="text-button text-xs" @click="$emit('clear')">
          {{ t("chat.clear") }}
        </button>
        <div v-if="props.showRightToggle" class="chat-header-divider"></div>
        <button
          v-if="props.showRightToggle"
          class="chat-panel-button"
          type="button"
          @click="$emit('toggle-right')"
        >
          <component :is="props.rightVisible ? PanelRightClose : PanelRightOpen" :size="15" />
        </button>
      </div>
    </header>

    <div ref="scrollRef" class="chat-scroll">
      <div v-if="!messages.length && !loading" class="chat-empty-state">
        <div class="chat-empty-icon">
          <Send :size="26" />
        </div>
        <div>
          <h4>{{ t("chat.startRun") }}</h4>
          <p>{{ t("chat.startRunDesc") }}</p>
        </div>
      </div>

      <template v-else>
        <div
          v-for="message in messages"
          :key="message.id"
          class="chat-row"
          :class="{ user: message.role === 'user' }"
        >
          <div class="chat-row-inner">
            <span v-if="message.agentName" class="chat-agent-name">{{ message.agentName }}</span>
            <div v-if="message.role === 'user'" class="chat-bubble markdown-body user">
              <div>{{ message.content }}</div>
            </div>
            <template v-else>
              <section v-if="hasPlanner(message)" class="chat-bubble markdown-body planner-brief">
                <p class="assistant-section-title">Planner</p>
                <section class="planner-brief-inner">
                  <header class="planner-brief-head">
                    <strong>Plan IR</strong>
                    <span class="planner-brief-chip">{{ message.planner.workflow_type || "planner_executor" }}</span>
                  </header>
                  <p class="planner-brief-meta">
                    package: <code>{{ message.planner.package_id || "-" }}</code>
                    · subtasks: {{ message.planner.subtask_count ?? 0 }}
                    · mode: <code>{{ message.planner.execution_mode || "DYNAMIC" }}</code>
                  </p>
                  <ul
                    v-if="Array.isArray(message.planner.subtasks) && message.planner.subtasks.length"
                    class="planner-subtask-list"
                  >
                    <li v-for="plannerTask in message.planner.subtasks" :key="plannerTask.id" class="planner-subtask-item">
                      <p class="planner-subtask-title">
                        <strong>{{ plannerTask.id }}</strong>
                        <span>{{ plannerTask.logical_role || plannerTask.expected_role || "SPECIALIST" }}</span>
                      </p>
                      <p class="planner-subtask-desc">{{ plannerTask.description }}</p>
                      <p class="planner-subtask-meta">
                        depends_on:
                        <code>{{
                          Array.isArray(plannerTask.depends_on) && plannerTask.depends_on.length
                            ? plannerTask.depends_on.join(", ")
                            : "-"
                        }}</code>
                        · preferred_agent:
                        <code>{{ plannerTask.preferred_agent_name || "-" }}</code>
                        · preferred_agent_id:
                        <code>{{ plannerTask.preferred_agent_id || "-" }}</code>
                      </p>
                    </li>
                  </ul>
                </section>
              </section>
              <section v-if="hasTaskReports(message)" class="chat-bubble markdown-body task-report-panel">
                <p class="assistant-section-title">Task Reports</p>
                <div class="task-report-list">
                  <p class="task-report-summary">
                    已完成 {{ message.taskReports.length }} 个子任务，以下为分任务结果：
                  </p>
                  <article
                    v-for="report in message.taskReports"
                    :key="report.task_id"
                    class="task-report-card"
                  >
                    <header class="task-report-head">
                      <strong>{{ report.task_id }}</strong>
                      <span class="task-report-status">{{ report.status || 'COMPLETED' }}</span>
                    </header>
                    <p v-if="report.description" class="task-report-desc">{{ report.description }}</p>
                    <div
                      class="task-report-result"
                      :class="{ collapsed: shouldEnableCollapse(report) && reportCollapsed(message.id, report.task_id) }"
                      v-html="renderMarkdown(report.result || '')"
                    ></div>
                    <button
                      v-if="shouldEnableCollapse(report)"
                      type="button"
                      class="task-report-toggle"
                      @click="toggleReport(message.id, report.task_id)"
                    >
                      {{ reportCollapsed(message.id, report.task_id) ? '展开详情' : '收起详情' }}
                    </button>
                  </article>
                </div>
              </section>
              <section
                v-if="hasTaskReports(message) && message.content"
                class="chat-bubble markdown-body merge-summary-panel"
              >
                <p class="assistant-section-title">Merge Final</p>
                <div class="merge-summary markdown-body" v-html="renderMarkdown(message.content)"></div>
              </section>
              <div
                v-if="!hasTaskReports(message) && message.content"
                class="chat-bubble markdown-body"
                v-html="renderMarkdown(message.content)"
              ></div>
            </template>
          </div>
        </div>

        <div v-if="loading" class="chat-row">
          <div class="typing-indicator">
            <span></span>
            <span></span>
            <span></span>
            <strong>{{ t("chat.thinking") }}</strong>
          </div>
        </div>
      </template>
    </div>

    <footer class="chat-input-wrap">
      <div class="chat-input-shell">
        <textarea
          ref="inputRef"
          v-model="form.user_input"
          rows="1"
          :placeholder="t('chat.inputPlaceholder')"
          @input="handleInput"
          @keydown="handleKeydown"
        />
        <button
          v-if="loading"
          class="stop-mini-button"
          type="button"
          @click="$emit('stop')"
        >
          <Square :size="12" />
        </button>
        <button class="send-mini-button" :disabled="!selectedWorkflowId || loading" @click="submit">
          <Send :size="14" />
        </button>
      </div>
    </footer>
  </section>
</template>
