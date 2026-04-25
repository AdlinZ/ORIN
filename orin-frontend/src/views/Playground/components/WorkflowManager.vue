<script setup>
import {
  CheckCircle2,
  GitBranch,
  Layers3,
  Pencil,
  Plus,
  Route,
  Trash2,
  Users,
  Workflow as WorkflowIcon,
} from "lucide-vue-next";
import { computed, inject, reactive, ref, watch } from "vue";
import { I18N_KEY } from "../i18n";

const props = defineProps({
  templates: {
    type: Array,
    required: true,
  },
  agents: {
    type: Array,
    required: true,
  },
  workflows: {
    type: Array,
    required: true,
  },
  selectedWorkflowId: {
    type: String,
    default: "",
  },
});

const emit = defineEmits(["create", "update", "delete", "select"]);
const i18n = inject(I18N_KEY, null);
const t = i18n?.t || ((key) => key);
const workflowTypeLabel = i18n?.workflowTypeLabel || ((type) => type);
const workflowTypeDesc = i18n?.workflowTypeDesc || ((_type, fallback) => fallback || _type);
const managedWorkflowTypes = [
  "single_agent_chat",
  "router_specialists",
  "planner_executor",
  "supervisor_dynamic",
  "peer_handoff",
];

const isAdding = ref(false);
const editingWorkflowId = ref("");
const form = reactive({
  name: "",
  type: "router_specialists",
  description: "",
  specialist_agent_ids: [],
  finalizer_enabled: true,
  router_prompt: "You are an orchestration router. Select the best specialist based on user intent.",
  agent_max_tokens: 2400,
});
const formError = ref("");

const colorTokens = [
  "agent-theme-blue",
  "agent-theme-violet",
  "agent-theme-green",
  "agent-theme-orange",
  "agent-theme-rose",
  "agent-theme-indigo",
];

const requiredAgentCount = computed(() => {
  const found = props.templates.find((template) => template.type === form.type);
  return found?.required_agent_count || 2;
});

const isSingleAgentType = computed(() => form.type === "single_agent_chat");

const workflowTypeOptions = computed(() =>
  props.templates.filter((template) => managedWorkflowTypes.includes(template.type)),
);

const workflowModeCards = computed(() =>
  workflowTypeOptions.value.map((template) => ({
    type: template.type,
    title: workflowTypeLabel(template.type),
    desc: workflowTypeDesc(template.type, template.description || ""),
    count: template.required_agent_count || 2,
  })),
);

const visibleWorkflows = computed(() =>
  props.workflows.filter((workflow) => managedWorkflowTypes.includes(workflow.type)),
);

const workflowStats = computed(() => {
  const items = visibleWorkflows.value;
  return [
    {
      key: "total",
      label: "编排方案",
      value: items.length,
      icon: GitBranch,
      tone: "primary",
    },
    {
      key: "selected",
      label: "当前选中",
      value: props.selectedWorkflowId ? 1 : 0,
      icon: CheckCircle2,
      tone: "success",
    },
    {
      key: "agents",
      label: "绑定 Agent",
      value: items.reduce((total, item) => total + (item.specialist_agent_ids?.length || 0), 0),
      icon: Users,
      tone: "warning",
    },
    {
      key: "modes",
      label: "协作模式",
      value: new Set(items.map((item) => item.type)).size,
      icon: Layers3,
      tone: "info",
    },
  ];
});

const canSubmit = computed(() =>
  Boolean(form.name.trim()) &&
  Number(form.agent_max_tokens || 0) >= 256 &&
  Number(form.agent_max_tokens || 0) <= 16000 &&
  (isSingleAgentType.value
    ? form.specialist_agent_ids.length === 1
    : form.specialist_agent_ids.length >= requiredAgentCount.value),
);

function toggleAgent(agentId) {
  if (isSingleAgentType.value) {
    form.specialist_agent_ids = form.specialist_agent_ids.includes(agentId) ? [] : [agentId];
    return;
  }
  if (form.specialist_agent_ids.includes(agentId)) {
    form.specialist_agent_ids = form.specialist_agent_ids.filter((id) => id !== agentId);
  } else {
    form.specialist_agent_ids = [...form.specialist_agent_ids, agentId];
  }
}

watch(
  () => form.type,
  (nextType) => {
    if (nextType === "single_agent_chat" && form.specialist_agent_ids.length > 1) {
      form.specialist_agent_ids = [form.specialist_agent_ids[0]];
    }
  },
);

function resolveAgent(agentId) {
  const index = props.agents.findIndex((agent) => agent.id === agentId);
  const agent = props.agents.find((item) => item.id === agentId);
  return {
    name: agent?.name || agentId,
    theme: colorTokens[(index >= 0 ? index : 0) % colorTokens.length],
  };
}

function workflowDescription(workflow) {
  return workflowTypeDesc(workflow.type, "");
}

function beginCreate() {
  editingWorkflowId.value = "";
  isAdding.value = true;
  form.name = "";
  form.type = "router_specialists";
  form.description = "";
  form.specialist_agent_ids = [];
  form.finalizer_enabled = true;
  form.router_prompt = "You are an orchestration router. Select the best specialist based on user intent.";
  form.agent_max_tokens = 2400;
  formError.value = "";
}

function beginEdit(workflow) {
  if (!managedWorkflowTypes.includes(workflow.type)) return;
  isAdding.value = false;
  editingWorkflowId.value = workflow.id;
  form.name = workflow.name || "";
  form.type = workflow.type || "router_specialists";
  form.description = workflowDescription(workflow);
  form.specialist_agent_ids = [...(workflow.specialist_agent_ids || [])];
  form.finalizer_enabled = Boolean(workflow.finalizer_enabled);
  form.router_prompt = workflow.router_prompt || "You are an orchestration router. Select the best specialist based on user intent.";
  form.agent_max_tokens = Number(workflow.agent_max_tokens || 2400);
  formError.value = "";
}

function cancelForm() {
  isAdding.value = false;
  editingWorkflowId.value = "";
  form.name = "";
  form.type = "router_specialists";
  form.description = "";
  form.specialist_agent_ids = [];
  form.finalizer_enabled = true;
  form.router_prompt = "You are an orchestration router. Select the best specialist based on user intent.";
  form.agent_max_tokens = 2400;
  formError.value = "";
}

function removeWorkflow(workflowId) {
  emit("delete", workflowId);
  if (editingWorkflowId.value === workflowId) {
    cancelForm();
  }
}

async function submit() {
  if (!canSubmit.value) return;
  formError.value = "";
  const data = {
    name: form.name,
    type: form.type,
    specialist_agent_ids: form.specialist_agent_ids,
    finalizer_enabled: form.finalizer_enabled,
    router_prompt: form.router_prompt,
    execution_mode: "DYNAMIC",
    dag_subtasks: [],
    agent_max_tokens: Number(form.agent_max_tokens || 2400),
  };
  if (editingWorkflowId.value) {
    await emit("update", {
      id: editingWorkflowId.value,
      data,
    });
  } else {
    await emit("create", data);
  }
  cancelForm();
}
</script>

<template>
  <section class="page-stack workflow-admin-page">
    <div class="workflow-admin-header">
      <div class="workflow-admin-title">
        <span class="workflow-admin-icon">
          <WorkflowIcon :size="20" />
        </span>
        <div>
          <h2>多智能体编排</h2>
          <p>{{ t("page.workflowsDesc") }}</p>
        </div>
      </div>
      <button class="primary-button workflow-create-button" @click="beginCreate">
        <Plus :size="16" />
        {{ t("workflow.new") }}
      </button>
    </div>

    <div class="workflow-stat-grid">
      <article
        v-for="stat in workflowStats"
        :key="stat.key"
        class="workflow-stat-card"
        :class="`workflow-stat-${stat.tone}`"
      >
        <span class="workflow-stat-icon">
          <component :is="stat.icon" :size="20" />
        </span>
        <span class="workflow-stat-main">
          <strong>{{ stat.value }}</strong>
          <small>{{ stat.label }}</small>
        </span>
      </article>
    </div>

    <div class="workflow-admin-list">
      <article v-if="isAdding || editingWorkflowId" class="workflow-admin-card workflow-form">
        <div class="workflow-card-header">
          <div>
            <h3>{{ editingWorkflowId ? "编辑编排方案" : "创建编排方案" }}</h3>
            <p>配置协作模式、执行策略和参与 Agent。</p>
          </div>
          <button class="ghost-button workflow-close-form" type="button" @click="cancelForm">
            {{ t("workflow.cancel") }}
          </button>
        </div>
        <div class="workflow-form-grid">
          <div class="workflow-form-section">
            <h4>基础配置</h4>
            <label class="workflow-field">
              <span>方案名称</span>
              <input v-model="form.name" :placeholder="t('workflow.name')" />
            </label>

            <div class="workflow-field">
              <span>编排模式</span>
              <div class="workflow-mode-grid">
                <button
                  v-for="mode in workflowModeCards"
                  :key="mode.type"
                  type="button"
                  class="workflow-mode-card"
                  :class="{ selected: form.type === mode.type }"
                  @click="form.type = mode.type"
                >
                  <span class="workflow-mode-card-head">
                    <strong>{{ mode.title }}</strong>
                    <CheckCircle2 v-if="form.type === mode.type" :size="15" />
                  </span>
                  <small>{{ mode.desc }}</small>
                  <em>{{ mode.count }}+ Agents · 内置动态调度</em>
                </button>
              </div>
            </div>

            <label class="workflow-field">
              <span>协作说明</span>
              <textarea
                v-model="form.description"
                rows="4"
                placeholder="Collaboration logic description..."
              />
            </label>

            <label class="check-row workflow-finalizer-row">
              <input v-model="form.finalizer_enabled" type="checkbox" />
              <span>
                <strong>{{ t("workflow.enableFinalizer") }}</strong>
                <small>运行结束后统一收口、压缩冲突并生成最终答复。</small>
              </span>
            </label>

            <label class="workflow-field workflow-token-field">
              <span>Agent 单次输出上限</span>
              <input
                v-model.number="form.agent_max_tokens"
                type="number"
                min="256"
                max="16000"
                step="100"
              />
              <small>控制每个 Agent 子任务单次回复的最大 tokens，避免过长任务拖慢运行。</small>
            </label>
            <p v-if="formError" class="helper-text" style="color:#dc2626">{{ formError }}</p>
          </div>

          <div class="workflow-form-section">
            <h4>
              {{ t("workflow.bindAgents") }} ({{ form.specialist_agent_ids.length }})
            </h4>
            <p class="helper-text">
              {{
                isSingleAgentType
                  ? "Single Agent Chat 需且仅需 1 个 Agent。"
                  : t("workflow.requiresAtLeast", { count: requiredAgentCount })
              }}
            </p>
            <div class="selection-list workflow-agent-selection">
              <label
                v-for="agent in props.agents"
                :key="agent.id"
                class="selection-item"
                :class="{ selected: form.specialist_agent_ids.includes(agent.id) }"
              >
                <div class="selection-main">
                  <span class="mini-dot" :class="resolveAgent(agent.id).theme"></span>
                  <span>{{ agent.name }}</span>
                </div>
                <input
                  type="checkbox"
                  :checked="form.specialist_agent_ids.includes(agent.id)"
                  @change="toggleAgent(agent.id)"
                />
                <CheckCircle2
                  v-if="form.specialist_agent_ids.includes(agent.id)"
                  :size="16"
                  class="workflow-agent-check"
                />
              </label>
            </div>
            <div class="inline-actions">
              <button class="primary-button workflow-save-button" :disabled="!canSubmit" @click="submit">
                {{ editingWorkflowId ? "Save Changes" : t("workflow.save") }}
              </button>
              <button class="ghost-button" @click="cancelForm">{{ t("workflow.cancel") }}</button>
            </div>
          </div>
        </div>
      </article>

      <article
        v-for="workflow in visibleWorkflows"
        :key="workflow.id"
        class="workflow-admin-card workflow-card workflow-card-rich"
        :class="{ selected: workflow.id === props.selectedWorkflowId }"
        @click="emit('select', workflow.id)"
      >
        <div class="workflow-row-main">
          <div class="workflow-row-leading">
            <span class="workflow-row-icon" :class="{ selected: workflow.id === props.selectedWorkflowId }">
              <Route :size="20" />
            </span>
            <div class="workflow-rich-main">
              <div class="workflow-title-row">
                <h4>{{ workflow.name }}</h4>
                <span v-if="workflow.id === props.selectedWorkflowId" class="workflow-selected-inline">
                  <CheckCircle2 :size="12" />
                  {{ t("workflow.selected") }}
                </span>
              </div>
              <p class="workflow-id">workflow_{{ workflow.id }}</p>
              <p class="workflow-rich-desc">{{ workflowDescription(workflow) }}</p>
            </div>
          </div>

          <div class="workflow-row-meta">
            <span class="workflow-meta-pill">{{ workflowTypeLabel(workflow.type) }}</span>
            <span class="workflow-meta-pill">单次 {{ workflow.agent_max_tokens || 2400 }} tokens</span>
            <span class="workflow-agent-stack">
              <span class="avatar-stack">
                <span
                  v-for="(agentId, index) in workflow.specialist_agent_ids"
                  :key="agentId"
                  class="stack-avatar"
                  :class="resolveAgent(agentId).theme"
                  :style="{ zIndex: 10 - index }"
                >
                  {{ resolveAgent(agentId).name.charAt(0) }}
                </span>
              </span>
              <span class="workflow-agent-count">
                {{ workflow.specialist_agent_ids.length }} Agents
              </span>
            </span>
          </div>

          <div class="inline-actions compact-workflow-actions">
            <button
              class="workflow-icon-action"
              type="button"
              title="Edit Workflow"
              @click.stop="beginEdit(workflow)"
            >
              <Pencil :size="14" />
            </button>
            <button
              class="workflow-icon-action workflow-icon-delete"
              type="button"
              title="Delete Workflow"
              @click.stop="removeWorkflow(workflow.id)"
            >
              <Trash2 :size="14" />
            </button>
          </div>
        </div>
      </article>

      <article v-if="!visibleWorkflows.length && !isAdding" class="workflow-admin-card workflow-empty-card">
        <span class="workflow-row-icon">
          <WorkflowIcon :size="20" />
        </span>
        <div>
          <h4>暂无编排方案</h4>
          <p>创建一个方案后，可在 Playground 中选择并运行。</p>
        </div>
        <button class="primary-button workflow-create-button" @click="beginCreate">
          <Plus :size="16" />
          {{ t("workflow.new") }}
        </button>
      </article>
    </div>
  </section>
</template>
