<script setup>
import { computed, nextTick, ref, watch } from "vue";
import { marked } from "marked";
import { Send } from "lucide-vue-next";

const props = defineProps({
  messages: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  emptyTitle: {
    type: String,
    default: "开始协作对话",
  },
  emptyDescription: {
    type: String,
    default: "选择一个工作流后，输入任务即可运行多智能体协作。",
  },
  thinkingText: {
    type: String,
    default: "正在编排并执行任务...",
  },
});

const scrollRef = ref(null);
const reportExpanded = ref({});

marked.setOptions({
  breaks: true,
  gfm: true,
});

const hasMessages = computed(() => props.messages.length > 0);

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
    [key]: !reportExpanded.value[key],
  };
}

function scrollToBottom() {
  const el = scrollRef.value;
  if (!el) return;
  el.scrollTop = el.scrollHeight;
}

watch(
  () => [props.messages.length, props.loading],
  () => nextTick(scrollToBottom),
  { immediate: true },
);
</script>

<template>
  <div ref="scrollRef" class="collab-message-list">
    <div v-if="!hasMessages && !loading" class="collab-empty-state">
      <div class="collab-empty-icon">
        <Send :size="26" />
      </div>
      <div>
        <h4>{{ emptyTitle }}</h4>
        <p>{{ emptyDescription }}</p>
      </div>
    </div>

    <template v-else>
      <div
        v-for="message in messages"
        :key="message.id"
        class="collab-chat-row"
        :class="{ user: message.role === 'user' }"
      >
        <div class="collab-chat-row-inner">
          <span v-if="message.agentName" class="collab-agent-name">{{ message.agentName }}</span>
          <div v-if="message.role === 'user'" class="collab-chat-bubble user">
            <div>{{ message.content }}</div>
          </div>
          <template v-else>
            <section v-if="hasPlanner(message)" class="collab-chat-bubble planner-brief">
              <p class="collab-section-title">Planner</p>
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

            <section v-if="hasTaskReports(message)" class="collab-chat-bubble task-report-panel">
              <p class="collab-section-title">Task Reports</p>
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
                    <span class="task-report-status">{{ report.status || "COMPLETED" }}</span>
                  </header>
                  <p v-if="report.description" class="task-report-desc">{{ report.description }}</p>
                  <div
                    class="task-report-result"
                    :class="{ collapsed: shouldEnableCollapse(report) && reportCollapsed(message.id, report.task_id) }"
                    v-html="renderMarkdown(report.result || '')"
                  />
                  <button
                    v-if="shouldEnableCollapse(report)"
                    type="button"
                    class="task-report-toggle"
                    @click="toggleReport(message.id, report.task_id)"
                  >
                    {{ reportCollapsed(message.id, report.task_id) ? "展开详情" : "收起详情" }}
                  </button>
                </article>
              </div>
            </section>

            <section
              v-if="hasTaskReports(message) && message.content"
              class="collab-chat-bubble merge-summary-panel"
            >
              <p class="collab-section-title">Merge Final</p>
              <div class="merge-summary" v-html="renderMarkdown(message.content)" />
            </section>

            <div
              v-if="!hasTaskReports(message) && message.content"
              class="collab-chat-bubble"
              v-html="renderMarkdown(message.content)"
            />
          </template>
        </div>
      </div>

      <div v-if="loading" class="collab-chat-row">
        <div class="collab-typing-indicator">
          <span />
          <span />
          <span />
          <strong>{{ thinkingText }}</strong>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.collab-message-list {
  height: 100%;
  overflow-y: auto;
  padding: 24px clamp(16px, 4vw, 40px) 144px;
}

.collab-empty-state {
  width: min(520px, 100%);
  margin: 92px auto 0;
  display: flex;
  align-items: center;
  gap: 16px;
  color: #64748b;
}

.collab-empty-icon {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  color: #0f766e;
  background: #ecfdf5;
  border: 1px solid #ccfbf1;
}

.collab-empty-state h4 {
  margin: 0 0 6px;
  color: #0f172a;
  font-size: 18px;
}

.collab-empty-state p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}

.collab-chat-row {
  display: flex;
  margin-bottom: 16px;
}

.collab-chat-row.user {
  justify-content: flex-end;
}

.collab-chat-row-inner {
  width: min(860px, 92%);
}

.collab-chat-row.user .collab-chat-row-inner {
  display: flex;
  justify-content: flex-end;
}

.collab-agent-name {
  display: block;
  margin: 0 0 6px 4px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.collab-chat-bubble {
  color: #0f172a;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.05);
  line-height: 1.7;
}

.collab-chat-bubble.user {
  max-width: min(680px, 100%);
  background: #eff6ff;
  border-color: #bfdbfe;
  white-space: pre-wrap;
}

.collab-section-title {
  margin: 0 0 10px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
  text-transform: uppercase;
}

.planner-brief,
.task-report-panel,
.merge-summary-panel {
  margin-bottom: 10px;
}

.planner-brief-inner {
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 12px;
}

.planner-brief-head,
.task-report-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.planner-brief-chip,
.task-report-status {
  border-radius: 999px;
  padding: 3px 8px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 11px;
  font-weight: 700;
}

.planner-brief-meta,
.planner-subtask-meta,
.task-report-desc,
.task-report-summary {
  color: #64748b;
  font-size: 12px;
}

.planner-subtask-list {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
  display: grid;
  gap: 10px;
}

.planner-subtask-item,
.task-report-card {
  border-radius: 10px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  padding: 10px;
}

.planner-subtask-title {
  margin: 0;
  display: flex;
  gap: 8px;
  align-items: center;
}

.planner-subtask-title span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.planner-subtask-desc {
  margin: 6px 0;
  color: #0f172a;
}

.task-report-list {
  display: grid;
  gap: 10px;
}

.task-report-result {
  max-height: none;
  overflow: hidden;
}

.task-report-result.collapsed {
  max-height: 140px;
}

.task-report-toggle {
  margin-top: 8px;
  border: 0;
  background: transparent;
  color: #0f766e;
  cursor: pointer;
  font-weight: 700;
}

.collab-typing-indicator {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #ffffff;
  padding: 9px 13px;
  color: #64748b;
  font-size: 13px;
}

.collab-typing-indicator span {
  width: 7px;
  height: 7px;
  border-radius: 999px;
  background: #94a3b8;
  animation: collabPulse 1s infinite ease-in-out;
}

.collab-typing-indicator span:nth-child(2) {
  animation-delay: 0.15s;
}

.collab-typing-indicator span:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes collabPulse {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

@media (max-width: 768px) {
  .collab-message-list {
    padding: 16px 12px 132px;
  }

  .collab-empty-state {
    margin-top: 48px;
    align-items: flex-start;
  }

  .collab-chat-row-inner {
    width: 100%;
  }
}
</style>
