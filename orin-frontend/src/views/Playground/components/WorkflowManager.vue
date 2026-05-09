<script setup>
import { computed, inject, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Search } from "@element-plus/icons-vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  CheckCircle2,
  GitBranch,
  Plus,
  Route,
} from "lucide-vue-next";
import { ROUTES } from "@/router/routes";
import OrinDataTable from "@/components/orin/OrinDataTable.vue";
import OrinFilterBar from "@/components/orin/OrinFilterBar.vue";
import OrinPageShell from "@/components/orin/OrinPageShell.vue";
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
const route = useRoute();
const router = useRouter();
const i18n = inject(I18N_KEY, null);
const workflowTypeLabel = i18n?.workflowTypeLabel || ((type) => type);
const workflowTypeDesc = i18n?.workflowTypeDesc || ((_type, fallback) => fallback || _type);

const managedWorkflowTypes = [
  "single_agent_chat",
  "router_specialists",
  "planner_executor",
  "supervisor_dynamic",
  "peer_handoff",
];

const defaultRouterPrompt =
  "You are an orchestration router. Select the best specialist based on user intent.";

const colorTokens = [
  "agent-theme-blue",
  "agent-theme-violet",
  "agent-theme-green",
  "agent-theme-orange",
  "agent-theme-rose",
  "agent-theme-indigo",
];

const modeToneMap = {
  single_agent_chat: "tone-blue",
  router_specialists: "tone-violet",
  planner_executor: "tone-emerald",
  supervisor_dynamic: "tone-amber",
  peer_handoff: "tone-rose",
};

const searchQuery = ref("");
const typeFilter = ref("");
const agentFilter = ref("");
const drawerVisible = ref(false);
const editingWorkflowId = ref("");
const formError = ref("");

const form = reactive({
  name: "",
  type: "router_specialists",
  description: "",
  specialist_agent_ids: [],
  finalizer_enabled: true,
  router_prompt: defaultRouterPrompt,
  agent_max_tokens: 2400,
});

const workflowTypeOptions = computed(() =>
  props.templates.filter((template) => managedWorkflowTypes.includes(template.type)),
);

const workflowModeCards = computed(() =>
  workflowTypeOptions.value.map((template) => ({
    type: template.type,
    title: workflowTypeLabel(template.type),
    desc: workflowTypeDesc(template.type, template.description || ""),
    count: template.required_agent_count || 2,
    tone: workflowTone(template.type),
  })),
);

const visibleWorkflows = computed(() =>
  props.workflows.filter((workflow) => managedWorkflowTypes.includes(workflow.type)),
);

const filteredWorkflows = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();

  return visibleWorkflows.value.filter((workflow) => {
    const description = workflowDescription(workflow);
    const matchQuery =
      !query ||
      String(workflow.name || "").toLowerCase().includes(query) ||
      String(workflow.id || "").toLowerCase().includes(query) ||
      String(description || "").toLowerCase().includes(query) ||
      String(workflowTypeLabel(workflow.type) || "").toLowerCase().includes(query);
    const matchType = !typeFilter.value || workflow.type === typeFilter.value;
    const matchAgent =
      !agentFilter.value ||
      (workflow.specialist_agent_ids || []).includes(agentFilter.value);

    return matchQuery && matchType && matchAgent;
  });
});

const selectedWorkflow = computed(() =>
  visibleWorkflows.value.find((workflow) => workflow.id === props.selectedWorkflowId) || null,
);

const selectedAgents = computed(() =>
  (selectedWorkflow.value?.specialist_agent_ids || []).map((agentId) => resolveAgent(agentId)),
);

const drawerTitle = computed(() =>
  editingWorkflowId.value ? "编辑协同方案" : "新建协同方案",
);

const requiredAgentCount = computed(() => requiredCountForType(form.type));
const isSingleAgentType = computed(() => form.type === "single_agent_chat");

const canSubmit = computed(() =>
  Boolean(form.name.trim()) &&
  Number(form.agent_max_tokens || 0) >= 256 &&
  Number(form.agent_max_tokens || 0) <= 16000 &&
  (isSingleAgentType.value
    ? form.specialist_agent_ids.length === 1
    : form.specialist_agent_ids.length >= requiredAgentCount.value),
);

const workflowMetrics = computed(() => {
  const items = visibleWorkflows.value;
  const selected = selectedWorkflow.value;
  const agentTotal = items.reduce(
    (total, item) => total + (item.specialist_agent_ids?.length || 0),
    0,
  );

  return [
    {
      key: "total",
      label: "协同方案",
      value: items.length,
      meta: `${filteredWorkflows.value.length} 个当前结果`,
      tone: "teal",
    },
    {
      key: "selected",
      label: "当前选中",
      value: selected ? 1 : 0,
      meta: selected ? selected.name : "点击列表行即可设为运行方案",
      tone: "blue",
    },
    {
      key: "agents",
      label: "参与智能体",
      value: agentTotal,
      meta: `${props.agents.length} 个可用智能体`,
      tone: "violet",
    },
    {
      key: "modes",
      label: "协作模式",
      value: new Set(items.map((item) => item.type)).size,
      meta: `${workflowModeCards.value.length} 种模板可选`,
      tone: "amber",
    },
  ];
});

watch(
  () => form.type,
  (nextType) => {
    if (nextType === "single_agent_chat" && form.specialist_agent_ids.length > 1) {
      form.specialist_agent_ids = [form.specialist_agent_ids[0]];
    }
  },
);

watch(
  [() => route.query.workflowId, visibleWorkflows],
  ([workflowId]) => {
    const id = String(workflowId || "");
    if (!id || id === props.selectedWorkflowId) return;
    if (visibleWorkflows.value.some((workflow) => workflow.id === id)) {
      emit("select", id);
    }
  },
  { immediate: true },
);

function requiredCountForType(type) {
  const found = props.templates.find((template) => template.type === type);
  return found?.required_agent_count || 2;
}

function workflowDescription(workflow) {
  return workflow.description || workflowTypeDesc(workflow.type, "");
}

function resolveAgent(agentId) {
  const index = props.agents.findIndex((agent) => agent.id === agentId);
  const agent = props.agents.find((item) => item.id === agentId);

  return {
    id: agentId,
    name: agent?.name || agentId,
    model: agent?.model || agent?.model_name || agent?.modelName || "",
    theme: colorTokens[(index >= 0 ? index : 0) % colorTokens.length],
  };
}

function selectedAgentNames(workflow) {
  return (workflow.specialist_agent_ids || [])
    .map((agentId) => resolveAgent(agentId).name)
    .join("、");
}

function workflowTone(type) {
  return modeToneMap[type] || "tone-teal";
}

function resetForm() {
  form.name = "";
  form.type = "router_specialists";
  form.description = "";
  form.specialist_agent_ids = [];
  form.finalizer_enabled = true;
  form.router_prompt = defaultRouterPrompt;
  form.agent_max_tokens = 2400;
  formError.value = "";
}

function beginCreate() {
  editingWorkflowId.value = "";
  resetForm();
  drawerVisible.value = true;
}

function beginEdit(workflow) {
  if (!managedWorkflowTypes.includes(workflow.type)) return;

  editingWorkflowId.value = workflow.id;
  form.name = workflow.name || "";
  form.type = workflow.type || "router_specialists";
  form.description = workflowDescription(workflow);
  form.specialist_agent_ids = [...(workflow.specialist_agent_ids || [])];
  form.finalizer_enabled = Boolean(workflow.finalizer_enabled);
  form.router_prompt = workflow.router_prompt || defaultRouterPrompt;
  form.agent_max_tokens = Number(workflow.agent_max_tokens || 2400);
  formError.value = "";
  drawerVisible.value = true;
}

function cancelForm() {
  drawerVisible.value = false;
  editingWorkflowId.value = "";
  resetForm();
}

function selectWorkflow(workflow) {
  emit("select", workflow.id);
}

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

async function confirmDelete(workflow) {
  try {
    await ElMessageBox.confirm(
      `确认删除协同方案「${workflow.name || workflow.id}」？删除后不可恢复。`,
      "删除协同方案",
      {
        type: "warning",
        confirmButtonText: "删除",
        cancelButtonText: "取消",
      },
    );
    emit("delete", workflow.id);
    if (editingWorkflowId.value === workflow.id) {
      cancelForm();
    }
  } catch {
    // user cancelled
  }
}

function validateForm() {
  if (!form.name.trim()) {
    formError.value = "请填写方案名称。";
    return false;
  }

  if (Number(form.agent_max_tokens || 0) < 256 || Number(form.agent_max_tokens || 0) > 16000) {
    formError.value = "Agent 单次输出上限需在 256 到 16000 tokens 之间。";
    return false;
  }

  if (isSingleAgentType.value && form.specialist_agent_ids.length !== 1) {
    formError.value = "单智能体对话需且仅需 1 个智能体。";
    return false;
  }

  if (!isSingleAgentType.value && form.specialist_agent_ids.length < requiredAgentCount.value) {
    formError.value = `当前模式至少需要 ${requiredAgentCount.value} 个智能体。`;
    return false;
  }

  formError.value = "";
  return true;
}

function submit() {
  if (!validateForm()) {
    ElMessage.warning(formError.value);
    return;
  }

  const data = {
    name: form.name.trim(),
    type: form.type,
    description: form.description.trim(),
    specialist_agent_ids: [...form.specialist_agent_ids],
    finalizer_enabled: form.finalizer_enabled,
    router_prompt: form.router_prompt,
    execution_mode: "DYNAMIC",
    dag_subtasks: [],
    agent_max_tokens: Number(form.agent_max_tokens || 2400),
  };

  if (editingWorkflowId.value) {
    emit("update", {
      id: editingWorkflowId.value,
      data,
    });
  } else {
    emit("create", data);
  }

  cancelForm();
}

function rowClassName({ row }) {
  return row.id === props.selectedWorkflowId ? "is-selected-workflow" : "";
}

function goWorkspace() {
  router.push(ROUTES.AGENTS.WORKSPACE);
}
</script>

<template>
  <section class="page-container collab-workflow-page">
    <OrinPageShell
      title="多智能体协同"
      description="配置跨智能体协同方案，维护路由、参与智能体与结果收口策略"
      icon="Connection"
      domain="智能体管理"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" @click="beginCreate">
          <Plus :size="16" />
          新建协同方案
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="searchQuery"
            :prefix-icon="Search"
            clearable
            placeholder="搜索方案、ID、模式"
            class="filter-search"
          />
          <el-select v-model="typeFilter" clearable placeholder="协作模式" class="filter-select">
            <el-option
              v-for="mode in workflowModeCards"
              :key="mode.type"
              :label="mode.title"
              :value="mode.type"
            />
          </el-select>
          <el-select
            v-model="agentFilter"
            clearable
            placeholder="按智能体筛选"
            class="filter-select agent-filter-select"
          >
            <el-option
              v-for="agent in props.agents"
              :key="agent.id"
              :label="agent.name"
              :value="agent.id"
            />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <section class="collab-metrics" aria-label="协同方案概览">
      <article
        v-for="metric in workflowMetrics"
        :key="metric.key"
        class="collab-metric-card"
        :class="`metric-tone-${metric.tone}`"
      >
        <span class="metric-signal" aria-hidden="true" />
        <div>
          <span class="metric-label">{{ metric.label }}</span>
          <strong class="metric-value">{{ metric.value }}</strong>
          <span class="metric-meta">{{ metric.meta }}</span>
        </div>
      </article>
    </section>

    <section class="collab-main-grid">
      <OrinDataTable class="workflow-table-panel">
        <template #header>
          <div class="table-heading">
            <div>
              <strong>协同方案目录</strong>
              <span>选择一个方案作为工作台当前运行方案，或进入抽屉维护配置。</span>
            </div>
            <span>{{ filteredWorkflows.length }} / {{ visibleWorkflows.length }}</span>
          </div>
        </template>

        <el-table
          :data="filteredWorkflows"
          border
          stripe
          table-layout="auto"
          :row-class-name="rowClassName"
          @row-click="selectWorkflow"
        >
          <el-table-column label="方案" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="workflow-name-cell">
                <span
                  class="workflow-row-icon"
                  :class="[workflowTone(row.type), { selected: row.id === props.selectedWorkflowId }]"
                >
                  <Route :size="18" />
                </span>
                <div class="workflow-name-main">
                  <div class="workflow-title-line">
                    <strong>{{ row.name }}</strong>
                    <el-tag v-if="row.id === props.selectedWorkflowId" size="small" type="success" effect="light">
                      当前
                    </el-tag>
                  </div>
                  <span>workflow_{{ row.id }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="协作模式" width="170">
            <template #default="{ row }">
              <span class="mode-pill" :class="workflowTone(row.type)">
                {{ workflowTypeLabel(row.type) }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="参与智能体" min-width="240" show-overflow-tooltip>
            <template #default="{ row }">
              <div class="agent-stack-cell" :title="selectedAgentNames(row)">
                <span class="avatar-stack">
                  <span
                    v-for="(agentId, index) in row.specialist_agent_ids"
                    :key="agentId"
                    class="stack-avatar"
                    :class="resolveAgent(agentId).theme"
                    :style="{ zIndex: 10 - index }"
                  >
                    {{ resolveAgent(agentId).name.charAt(0) }}
                  </span>
                </span>
                <span>{{ row.specialist_agent_ids?.length || 0 }} 个智能体</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="策略" width="190">
            <template #default="{ row }">
              <div class="policy-cell">
                <span>{{ row.agent_max_tokens || 2400 }} tokens</span>
                <el-tag size="small" :type="row.finalizer_enabled ? 'success' : 'info'" effect="light">
                  {{ row.finalizer_enabled ? "统一收口" : "直接返回" }}
                </el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="190" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="selectWorkflow(row)">
                选择
              </el-button>
              <el-button link type="primary" @click.stop="beginEdit(row)">
                编辑
              </el-button>
              <el-button link type="danger" @click.stop="confirmDelete(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!filteredWorkflows.length" class="table-empty-state">
          <el-empty
            :image-size="64"
            :description="visibleWorkflows.length ? '没有匹配筛选条件的协同方案' : '暂无协同方案'"
          >
            <el-button type="primary" @click="beginCreate">新建协同方案</el-button>
          </el-empty>
        </div>
      </OrinDataTable>

      <aside class="selected-panel" :class="selectedWorkflow ? workflowTone(selectedWorkflow.type) : 'tone-neutral'">
        <div class="selected-panel-head">
          <span class="panel-icon">
            <GitBranch :size="18" />
          </span>
          <div>
            <strong>当前运行方案</strong>
            <p>工作台会优先使用这里选中的协同方案。</p>
          </div>
        </div>

        <template v-if="selectedWorkflow">
          <div class="selected-summary">
            <h3>{{ selectedWorkflow.name }}</h3>
            <span class="mode-pill" :class="workflowTone(selectedWorkflow.type)">
              {{ workflowTypeLabel(selectedWorkflow.type) }}
            </span>
          </div>
          <p class="selected-description">
            {{ workflowDescription(selectedWorkflow) || "暂无协作说明" }}
          </p>
          <div class="selected-meta-grid">
            <div>
              <span>参与智能体</span>
              <strong>{{ selectedWorkflow.specialist_agent_ids?.length || 0 }}</strong>
            </div>
            <div>
              <span>输出上限</span>
              <strong>{{ selectedWorkflow.agent_max_tokens || 2400 }}</strong>
            </div>
            <div>
              <span>结果收口</span>
              <strong>{{ selectedWorkflow.finalizer_enabled ? "启用" : "关闭" }}</strong>
            </div>
          </div>
          <div class="selected-agent-list">
            <div
              v-for="agent in selectedAgents"
              :key="agent.id"
              class="selected-agent"
            >
              <span class="stack-avatar" :class="agent.theme">{{ agent.name.charAt(0) }}</span>
              <div>
                <strong>{{ agent.name }}</strong>
                <small>{{ agent.model || "未配置模型信息" }}</small>
              </div>
            </div>
          </div>
          <div class="selected-actions">
            <el-button type="primary" @click="goWorkspace">去工作台运行</el-button>
            <el-button @click="beginEdit(selectedWorkflow)">编辑方案</el-button>
          </div>
        </template>

        <el-empty v-else :image-size="64" description="尚未选择协同方案">
          <el-button @click="beginCreate">新建方案</el-button>
        </el-empty>
      </aside>
    </section>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="620px"
      class="collab-workflow-drawer"
      destroy-on-close
    >
      <el-form label-position="top" class="workflow-form">
        <el-form-item label="方案名称" required>
          <el-input v-model="form.name" placeholder="例如：需求分析协同组" />
        </el-form-item>

        <el-form-item label="协作模式" required>
          <div class="mode-grid">
            <button
              v-for="mode in workflowModeCards"
              :key="mode.type"
              type="button"
              class="mode-card"
              :class="[mode.tone, { selected: form.type === mode.type }]"
              @click="form.type = mode.type"
            >
              <span>
                <strong>{{ mode.title }}</strong>
                <CheckCircle2 v-if="form.type === mode.type" :size="15" />
              </span>
              <small>{{ mode.desc }}</small>
              <em>{{ mode.count }}+ 智能体</em>
            </button>
          </div>
        </el-form-item>

        <el-form-item label="协作说明">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="说明这个方案适合处理什么任务"
          />
        </el-form-item>

        <el-form-item label="路由提示词">
          <el-input
            v-model="form.router_prompt"
            type="textarea"
            :rows="4"
            placeholder="用于决定任务如何分配给参与智能体"
          />
        </el-form-item>

        <div class="form-inline-grid">
          <el-form-item label="Agent 单次输出上限" required>
            <el-input-number
              v-model="form.agent_max_tokens"
              :min="256"
              :max="16000"
              :step="100"
              controls-position="right"
              class="token-input"
            />
          </el-form-item>
          <el-form-item label="最终收口">
            <el-switch
              v-model="form.finalizer_enabled"
              active-text="启用"
              inactive-text="关闭"
            />
          </el-form-item>
        </div>

        <el-form-item required>
          <template #label>
            <span>参与智能体（已选 {{ form.specialist_agent_ids.length }}）</span>
          </template>
          <p class="agent-pick-hint">
            {{
              isSingleAgentType
                ? "单智能体对话需且仅需 1 个智能体。"
                : `当前模式至少需要 ${requiredAgentCount} 个智能体。`
            }}
          </p>
          <div class="agent-picker">
            <button
              v-for="agent in props.agents"
              :key="agent.id"
              type="button"
              class="agent-pick-card"
              :class="{ selected: form.specialist_agent_ids.includes(agent.id) }"
              @click="toggleAgent(agent.id)"
            >
              <span class="stack-avatar" :class="resolveAgent(agent.id).theme">
                {{ agent.name.charAt(0) }}
              </span>
              <span>{{ agent.name }}</span>
              <CheckCircle2 v-if="form.specialist_agent_ids.includes(agent.id)" :size="16" />
            </button>
          </div>
        </el-form-item>

        <el-alert
          v-if="formError"
          :title="formError"
          type="warning"
          show-icon
          :closable="false"
        />
      </el-form>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="cancelForm">取消</el-button>
          <el-button type="primary" :disabled="!canSubmit" @click="submit">
            {{ editingWorkflowId ? "保存修改" : "创建方案" }}
          </el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<style scoped>
.collab-workflow-page {
  display: grid;
  gap: var(--orin-page-gap, 20px);
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.3), rgba(255, 255, 255, 0) 260px);
}

.collab-metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 14px;
}

.collab-metric-card {
  position: relative;
  display: flex;
  min-width: 0;
  gap: 13px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--metric-border);
  border-radius: 8px;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(255, 255, 255, 0.78)),
    linear-gradient(135deg, var(--metric-soft), rgba(255, 255, 255, 0));
  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.05);
}

.collab-metric-card::after {
  position: absolute;
  inset: auto 0 0 0;
  height: 3px;
  content: "";
  background: linear-gradient(90deg, var(--metric-color), transparent);
}

.metric-signal {
  width: 38px;
  height: 38px;
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--metric-border);
  border-radius: 10px;
  background: var(--metric-soft);
}

.metric-signal::before {
  width: 14px;
  height: 14px;
  border-radius: 999px;
  content: "";
  background: var(--metric-color);
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--metric-color) 14%, transparent);
}

.metric-label,
.metric-meta {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.metric-label {
  font-weight: 700;
}

.metric-value {
  display: block;
  margin: 5px 0 4px;
  color: #0f172a;
  font-size: 25px;
  line-height: 1;
}

.metric-tone-teal {
  --metric-color: #0d9488;
  --metric-soft: rgba(20, 184, 166, 0.13);
  --metric-border: rgba(13, 148, 136, 0.18);
}

.metric-tone-blue {
  --metric-color: #2563eb;
  --metric-soft: rgba(59, 130, 246, 0.13);
  --metric-border: rgba(37, 99, 235, 0.18);
}

.metric-tone-violet {
  --metric-color: #7c3aed;
  --metric-soft: rgba(124, 58, 237, 0.13);
  --metric-border: rgba(124, 58, 237, 0.18);
}

.metric-tone-amber {
  --metric-color: #d97706;
  --metric-soft: rgba(245, 158, 11, 0.15);
  --metric-border: rgba(217, 119, 6, 0.2);
}

.tone-blue {
  --tone-color: #2563eb;
  --tone-strong: #1d4ed8;
  --tone-soft: rgba(59, 130, 246, 0.12);
  --tone-panel: rgba(239, 246, 255, 0.86);
  --tone-border: rgba(37, 99, 235, 0.22);
}

.tone-violet {
  --tone-color: #7c3aed;
  --tone-strong: #6d28d9;
  --tone-soft: rgba(124, 58, 237, 0.12);
  --tone-panel: rgba(245, 243, 255, 0.88);
  --tone-border: rgba(124, 58, 237, 0.22);
}

.tone-emerald {
  --tone-color: #059669;
  --tone-strong: #047857;
  --tone-soft: rgba(16, 185, 129, 0.13);
  --tone-panel: rgba(236, 253, 245, 0.86);
  --tone-border: rgba(5, 150, 105, 0.22);
}

.tone-amber {
  --tone-color: #d97706;
  --tone-strong: #b45309;
  --tone-soft: rgba(245, 158, 11, 0.15);
  --tone-panel: rgba(255, 251, 235, 0.9);
  --tone-border: rgba(217, 119, 6, 0.24);
}

.tone-rose {
  --tone-color: #e11d48;
  --tone-strong: #be123c;
  --tone-soft: rgba(244, 63, 94, 0.12);
  --tone-panel: rgba(255, 241, 242, 0.9);
  --tone-border: rgba(225, 29, 72, 0.2);
}

.tone-teal,
.tone-neutral {
  --tone-color: #0d9488;
  --tone-strong: #0f766e;
  --tone-soft: rgba(20, 184, 166, 0.12);
  --tone-panel: rgba(240, 253, 250, 0.86);
  --tone-border: rgba(13, 148, 136, 0.2);
}

.filter-search {
  width: 280px;
}

.filter-select {
  width: 180px;
}

.agent-filter-select {
  width: 220px;
}

.collab-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.table-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.table-heading strong,
.selected-panel-head strong {
  display: block;
  color: #0f172a;
  font-size: 15px;
}

.table-heading span,
.selected-panel-head p {
  margin: 3px 0 0;
  color: #64748b;
  font-size: 12px;
}

.workflow-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.workflow-row-icon,
.panel-icon {
  width: 36px;
  height: 36px;
  display: inline-grid;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  color: var(--tone-strong);
  background: var(--tone-soft);
}

.workflow-row-icon.selected {
  color: #fff;
  border-color: var(--tone-color);
  background: linear-gradient(135deg, var(--tone-color), var(--tone-strong));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--tone-color) 22%, transparent);
}

.workflow-name-main {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.workflow-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.workflow-title-line strong {
  overflow: hidden;
  color: #0f172a;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workflow-name-main > span {
  color: #94a3b8;
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
}

.agent-stack-cell,
.policy-cell {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #475569;
  font-size: 13px;
}

.mode-pill {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  min-height: 26px;
  padding: 0 9px;
  border: 1px solid var(--tone-border);
  border-radius: 999px;
  color: var(--tone-strong);
  background: var(--tone-soft);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.avatar-stack {
  display: inline-flex;
  align-items: center;
}

.stack-avatar {
  width: 26px;
  height: 26px;
  display: inline-grid;
  margin-left: -7px;
  place-items: center;
  border: 2px solid #fff;
  border-radius: 999px;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.stack-avatar:first-child {
  margin-left: 0;
}

.agent-theme-blue {
  background: #2563eb;
}

.agent-theme-violet {
  background: #7c3aed;
}

.agent-theme-green {
  background: #059669;
}

.agent-theme-orange {
  background: #ea580c;
}

.agent-theme-rose {
  background: #e11d48;
}

.agent-theme-indigo {
  background: #4f46e5;
}

.selected-panel {
  position: relative;
  display: grid;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.82)),
    linear-gradient(135deg, var(--tone-panel), rgba(255, 255, 255, 0));
  box-shadow: 0 14px 30px rgba(15, 23, 42, 0.06);
}

.selected-panel::before {
  position: absolute;
  inset: 0 0 auto 0;
  height: 4px;
  content: "";
  background: linear-gradient(90deg, var(--tone-color), transparent);
}

.selected-panel-head {
  position: relative;
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.selected-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.selected-summary h3 {
  margin: 0;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.3;
}

.selected-description {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.selected-meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.selected-meta-grid > div {
  padding: 10px;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.68);
}

.selected-meta-grid span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.selected-meta-grid strong {
  display: block;
  margin-top: 4px;
  color: #0f172a;
  font-size: 15px;
}

.selected-agent-list {
  display: grid;
  gap: 8px;
}

.selected-agent {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.selected-agent .stack-avatar {
  margin-left: 0;
}

.selected-agent strong,
.selected-agent small {
  display: block;
}

.selected-agent strong {
  color: #0f172a;
  font-size: 13px;
}

.selected-agent small {
  color: #94a3b8;
  font-size: 12px;
}

.selected-actions {
  display: flex;
  gap: 8px;
}

.table-empty-state {
  padding: 28px;
}

.workflow-form {
  display: grid;
  gap: 2px;
}

.mode-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.mode-card {
  min-height: 118px;
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 13px;
  border: 1px solid var(--tone-border);
  border-radius: 8px;
  background: #fff;
  color: #334155;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.mode-card:hover,
.mode-card.selected {
  border-color: var(--tone-color);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.88), rgba(255, 255, 255, 0.72)),
    var(--tone-panel);
  box-shadow: inset 3px 0 0 var(--tone-color);
}

.agent-pick-card:hover,
.agent-pick-card.selected {
  border-color: rgba(13, 148, 136, 0.42);
  background: var(--orin-primary-soft, #f0fdfa);
}

.mode-card > span {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  color: #0f172a;
}

.mode-card small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.mode-card em {
  color: var(--tone-strong);
  font-size: 11px;
  font-style: normal;
  font-weight: 700;
}

.form-inline-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 16px;
}

.token-input {
  width: 100%;
}

.agent-pick-hint {
  margin: -4px 0 10px;
  color: #64748b;
  font-size: 12px;
}

.agent-picker {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  max-height: 240px;
  overflow: auto;
}

.agent-pick-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 9px;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  color: #0f172a;
  text-align: left;
}

.agent-pick-card .stack-avatar {
  margin-left: 0;
}

.agent-pick-card > span:nth-child(2) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.collab-workflow-page :deep(.is-selected-workflow td.el-table__cell) {
  background: rgba(13, 148, 136, 0.06) !important;
}

.collab-workflow-page :deep(.el-input__inner),
.collab-workflow-page :deep(.el-input-number .el-input__inner) {
  min-height: auto !important;
  padding: 0 !important;
  border: 0 !important;
  box-shadow: none !important;
}

.collab-workflow-page :deep(.el-textarea__inner) {
  font-family: inherit;
}

.collab-workflow-page :deep(.el-select__placeholder) {
  color: #94a3b8;
}

@media (max-width: 1180px) {
  .collab-main-grid {
    grid-template-columns: 1fr;
  }

  .selected-panel {
    order: -1;
  }
}

@media (max-width: 760px) {
  .filter-search,
  .filter-select {
    width: 100%;
  }

  .table-heading,
  .selected-summary,
  .selected-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .mode-grid,
  .form-inline-grid,
  .agent-picker,
  .selected-meta-grid {
    grid-template-columns: 1fr;
  }
}
</style>
