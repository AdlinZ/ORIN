<script setup>
import { computed, onMounted, ref } from "vue"
import {
  Activity,
  ArrowRight,
  BrainCircuit,
  ChevronRight,
  GitBranch,
  Settings2,
  Users,
  Workflow as WorkflowIcon,
} from "lucide-vue-next"
import { fetchAgents, fetchWorkflows, fetchTemplates } from "./api"

const agents = ref([])
const workflows = ref([])
const templates = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    ;[agents.value, workflows.value, templates.value] = await Promise.all([
      fetchAgents(),
      fetchWorkflows(),
      fetchTemplates(),
    ])
  } catch {
    // backend not running – show zeros
  } finally {
    loading.value = false
  }
})

const defaultWorkflow = computed(() => workflows.value[0] || null)

const defaultWorkflowAgents = computed(() => {
  if (!defaultWorkflow.value) return []
  return (defaultWorkflow.value.specialist_agent_ids || [])
    .map((id) => agents.value.find((a) => a.id === id))
    .filter(Boolean)
})

const workflowTypeLabels = {
  router_specialists: "路由专家",
  planner_executor: "规划执行",
  supervisor_dynamic: "动态监督",
  single_agent_chat: "单 Agent 对话",
  peer_handoff: "同伴交接",
}

function typeLabel(type) {
  return workflowTypeLabels[type] || type
}

const steps = [
  { id: "01", title: "创建 Agents", detail: "定义可复用专家角色。" },
  { id: "02", title: "定义模式", detail: "选择协作模式并创建编排方案。" },
  { id: "03", title: "运行 Playground", detail: "发送消息并查看图与追踪。" },
]
</script>

<template>
  <div class="po-root">
    <!-- Header -->
    <header class="po-topbar">
      <div class="po-shell po-topbar-inner">
        <div class="po-brand">
          <div class="po-brand-mark">
            <BrainCircuit :size="22" />
          </div>
          <div>
            <h1>多智能体控制台</h1>
            <p>多智能体编排与执行追踪系统</p>
          </div>
        </div>
        <div class="po-topbar-right">
          <div class="po-topbar-status">
            <span class="po-chip po-chip-dark">MVP</span>
            <Activity :size="14" class="po-status-icon" />
            <span>系统就绪</span>
          </div>
        </div>
      </div>
    </header>

    <!-- Page content -->
    <main class="po-shell po-main">
      <div class="po-page-stack">

        <!-- Hero + Stats row -->
        <section class="po-overview-grid">
          <div class="po-glass po-hero-card">
            <span class="po-chip po-chip-blue">编排优先演示</span>
            <h2>
              先定义模式。<br />
              再观察智能体协作。
            </h2>
            <p>该界面用于演示：在一个视图中查看 Agents、编排路由和运行 Trace。</p>
            <div class="po-hero-orb"></div>
          </div>

          <div class="po-stat-stack">
            <article class="po-glass po-stat-tile">
              <div class="po-stat-icon po-stat-blue"><Users :size="22" /></div>
              <div>
                <div class="po-stat-label">Agents</div>
                <div class="po-stat-value">{{ agents.length }}</div>
              </div>
              <ChevronRight :size="18" class="po-stat-chevron" />
            </article>

            <article class="po-glass po-stat-tile">
              <div class="po-stat-icon po-stat-violet"><GitBranch :size="22" /></div>
              <div>
                <div class="po-stat-label">方案</div>
                <div class="po-stat-value">{{ workflows.length }}</div>
              </div>
              <ChevronRight :size="18" class="po-stat-chevron" />
            </article>

            <article class="po-glass po-stat-tile">
              <div class="po-stat-icon po-stat-green"><WorkflowIcon :size="22" /></div>
              <div>
                <div class="po-stat-label">模式</div>
                <div class="po-stat-value">{{ templates.length }}</div>
              </div>
              <ChevronRight :size="18" class="po-stat-chevron" />
            </article>
          </div>
        </section>

        <!-- Steps -->
        <section class="po-glass po-section-card">
          <div class="po-section-header">
            <div>
              <h3><Settings2 :size="18" class="po-icon-muted" /> 流程</h3>
              <p>按下面顺序体验最清晰。</p>
            </div>
          </div>
          <div class="po-step-grid">
            <div v-for="step in steps" :key="step.id" class="po-step-card">
              <div class="po-step-line">
                <span class="po-step-index">{{ step.id }}</span>
                <span class="po-step-divider"></span>
              </div>
              <h4>
                {{ step.title }}
                <ArrowRight :size="15" class="po-step-arrow" />
              </h4>
              <p>{{ step.detail }}</p>
            </div>
          </div>
        </section>

        <!-- Default Workflow -->
        <section class="po-glass po-section-card">
          <div class="po-section-header">
            <div>
              <h3><Activity :size="18" class="po-icon-green" /> Default Workflow</h3>
              <p>This setup is loaded first in Run.</p>
            </div>
            <button class="po-text-btn">
              Go to Run <ArrowRight :size="14" />
            </button>
          </div>

          <div v-if="defaultWorkflow" class="po-workflow-highlight">
            <div>
              <div class="po-workflow-title">{{ defaultWorkflow.name }}</div>
              <div class="po-workflow-id">workflow_{{ defaultWorkflow.id }}</div>
              <div class="po-workflow-badges">
                <span class="po-chip">{{ typeLabel(defaultWorkflow.type) }}</span>
                <span
                  v-for="agent in defaultWorkflowAgents"
                  :key="agent.id"
                  class="po-chip po-chip-blue"
                >{{ agent.name }}</span>
              </div>
            </div>
            <div class="po-workflow-mark"><WorkflowIcon :size="24" /></div>
          </div>

          <div v-else-if="loading" class="po-empty">加载中...</div>
          <div v-else class="po-empty">暂无方案，请先创建 Workflow。</div>
        </section>

      </div>
    </main>
  </div>
</template>

<style scoped>
/* ── CSS variables ── */
.po-root {
  --text: #0f172a;
  --muted: #64748b;
  --muted-soft: #94a3b8;
  --panel: rgba(255, 255, 255, 0.82);
  --border: rgba(148, 163, 184, 0.22);
  --shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
  --dark: #0f172a;

  display: flex;
  flex-direction: column;
  min-height: 100%;
  background:
    radial-gradient(circle at top center, rgba(148, 163, 184, 0.08), transparent 28%),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif;
  color: var(--text);
  box-sizing: border-box;
}

*, *::before, *::after { box-sizing: inherit; }

/* ── Shell ── */
.po-shell {
  width: min(1360px, calc(100vw - 32px));
  margin: 0 auto;
}

/* ── Topbar ── */
.po-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 16px 0;
  backdrop-filter: blur(16px);
  background: rgba(248, 250, 252, 0.78);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.po-topbar-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.po-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}

.po-brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--dark);
  color: #fff;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.15);
}

.po-brand h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.02em;
}

.po-brand p {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 12px;
  font-weight: 600;
}

.po-topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.po-topbar-status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 12px;
  font-weight: 700;
}

.po-status-icon { color: #22c55e; animation: pulse-soft 1.6s infinite; }

@keyframes pulse-soft {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.55; }
}

/* ── Chips ── */
.po-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.78);
  color: #475569;
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.po-chip-dark { background: var(--dark); color: #fff; }

.po-chip-blue {
  background: rgba(219, 234, 254, 0.95);
  color: #1d4ed8;
  text-transform: none;
  letter-spacing: 0;
}

/* ── Main ── */
.po-main { padding: 28px 0 36px; flex: 1; }

/* ── Page stack ── */
.po-page-stack { display: grid; gap: 24px; }

/* ── Glass panel ── */
.po-glass {
  border: 1px solid var(--border);
  border-radius: 26px;
  background: var(--panel);
  backdrop-filter: blur(16px);
  box-shadow: var(--shadow);
}

/* ── Overview grid ── */
.po-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 24px;
}

/* ── Hero ── */
.po-hero-card {
  position: relative;
  overflow: hidden;
  padding: 32px;
  min-height: 310px;
}

.po-hero-card h2 {
  position: relative;
  z-index: 1;
  margin: 18px 0 16px;
  font-size: clamp(32px, 5vw, 52px);
  line-height: 1.02;
  letter-spacing: -0.05em;
}

.po-hero-card p {
  position: relative;
  z-index: 1;
  max-width: 720px;
  margin: 0;
  color: var(--muted);
  font-size: 17px;
}

.po-hero-orb {
  position: absolute;
  right: -80px;
  bottom: -120px;
  width: 320px;
  height: 320px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(191, 219, 254, 0.72), rgba(191, 219, 254, 0));
  filter: blur(20px);
}

/* ── Stats ── */
.po-stat-stack { display: grid; gap: 16px; }

.po-stat-tile {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.po-stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.po-stat-blue   { background: linear-gradient(135deg, #dbeafe, #bfdbfe); }
.po-stat-violet { background: linear-gradient(135deg, #ede9fe, #ddd6fe); }
.po-stat-green  { background: linear-gradient(135deg, #dcfce7, #bbf7d0); }

.po-stat-label { color: var(--muted); font-size: 13px; font-weight: 700; }
.po-stat-value { font-size: 30px; font-weight: 800; letter-spacing: -0.03em; }
.po-stat-chevron { margin-left: auto; color: var(--muted-soft); }

/* ── Section card ── */
.po-section-card { padding: 28px; }

.po-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.po-section-header h3 {
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.02em;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.po-section-header p {
  margin: 6px 0 0;
  color: var(--muted);
}

.po-icon-muted  { color: #94a3b8; }
.po-icon-green  { color: #16a34a; }

/* ── Steps ── */
.po-step-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px;
}

.po-step-card {
  padding: 0;
  text-align: left;
  background: transparent;
  border: none;
  cursor: pointer;
}

.po-step-line {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 12px;
}

.po-step-index {
  font-size: 42px;
  font-weight: 900;
  color: #e2e8f0;
  line-height: 1;
}

.po-step-divider {
  height: 1px;
  flex: 1;
  background: #e2e8f0;
}

.po-step-card h4 {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 20px;
  letter-spacing: -0.02em;
  font-weight: 700;
}

.po-step-card p { margin: 0; color: var(--muted); }

.po-step-arrow {
  opacity: 0;
  transform: translateX(-5px);
  transition: 0.2s ease;
}

.po-step-card:hover .po-step-arrow {
  opacity: 1;
  transform: translateX(0);
}

/* ── Text button ── */
.po-text-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: transparent;
  border: none;
  color: var(--muted);
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: color 0.15s;
}

.po-text-btn:hover { color: var(--text); }

/* ── Workflow highlight ── */
.po-workflow-highlight {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 24px;
  border-radius: 20px;
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(226, 232, 240, 0.92);
}

.po-workflow-title {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.po-workflow-id {
  margin-top: 6px;
  color: var(--muted-soft);
  font-size: 12px;
  font-family: "JetBrains Mono", Consolas, monospace;
}

.po-workflow-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.po-workflow-mark {
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  background: rgba(15, 23, 42, 0.05);
  color: #cbd5e1;
  flex-shrink: 0;
}

.po-empty {
  color: var(--muted);
  font-size: 14px;
  padding: 8px 0;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .po-overview-grid { grid-template-columns: 1fr; }
  .po-step-grid { grid-template-columns: 1fr; }
}
</style>
