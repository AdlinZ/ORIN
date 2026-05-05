<template>
  <div class="alerts-logs-center">
    <section class="alerts-hero">
      <div class="hero-copy">
        <span class="hero-kicker">Runtime Operations</span>
        <h1>告警与日志</h1>
        <p>把告警规则、告警历史与审计日志放在一个工作台里，先判断风险，再下钻处理。</p>
      </div>

      <div class="hero-tabs" role="tablist" aria-label="告警与日志视图">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          type="button"
          class="hero-tab"
          :class="{ active: activeTab === tab.name }"
          @click="activeTab = tab.name"
        >
          <span>{{ tab.label }}</span>
          <small>{{ tab.desc }}</small>
        </button>
      </div>
    </section>

    <section class="alerts-content-shell">
      <AlertManagement
        v-if="activeTab === 'rules'"
        mode="rules"
        :show-header="false"
        initial-tab="rules"
      />
      <AlertManagement
        v-else-if="activeTab === 'history'"
        mode="history"
        :show-header="false"
        initial-tab="history"
      />
      <AuditCenterV2
        v-else-if="activeTab === 'audit'"
        mode="logs"
        :show-header="false"
        :show-header-actions="false"
        initial-tab="logs"
      />
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import AlertManagement from '@/views/System/AlertManagement.vue'
import AuditCenterV2 from '@/views/revamp/system/AuditCenterV2.vue'

const activeTab = ref('rules')

const tabs = [
  { name: 'rules', label: '告警规则', desc: '阈值与通知' },
  { name: 'history', label: '告警历史', desc: '处理与追踪' },
  { name: 'audit', label: '审计日志', desc: '操作留痕' }
]
</script>

<style scoped>
.alerts-logs-center {
  min-height: 100%;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.56), transparent 260px);
}

.alerts-hero {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(420px, 0.92fr);
  gap: 20px;
  align-items: stretch;
  padding: 18px;
  border-radius: 16px;
  border: 1px solid rgba(203, 213, 225, 0.78);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(248, 250, 252, 0.88)),
    linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(59, 130, 246, 0.06));
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.hero-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.hero-kicker {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--orin-primary, #0d9488);
}

.hero-copy h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.1;
  letter-spacing: 0;
  color: var(--text-primary, #0f172a);
}

.hero-copy p {
  max-width: 620px;
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--text-secondary, #64748b);
}

.hero-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.hero-tab {
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  padding: 14px;
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.78);
  background: rgba(255, 255, 255, 0.68);
  color: var(--text-secondary, #64748b);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    transform 0.16s ease;
  outline: none;
}

.hero-tab:hover {
  transform: translateY(-1px);
  border-color: rgba(15, 118, 110, 0.32);
  background: rgba(255, 255, 255, 0.86);
}

.hero-tab span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 800;
}

.hero-tab small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--text-tertiary, #94a3b8);
}

.hero-tab.active {
  border-color: rgba(15, 118, 110, 0.38);
  background: rgba(15, 118, 110, 0.1);
  color: var(--orin-primary, #0d9488);
}

.alerts-content-shell {
  min-width: 0;
}

html.dark .alerts-logs-center {
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.1), transparent 32%),
    #0f172a;
}

html.dark .alerts-hero {
  border-color: rgba(100, 116, 139, 0.46);
  background:
    linear-gradient(135deg, rgba(30, 41, 59, 0.92), rgba(15, 23, 42, 0.88)),
    linear-gradient(135deg, rgba(20, 184, 166, 0.08), rgba(59, 130, 246, 0.08));
  box-shadow: 0 14px 32px rgba(2, 6, 23, 0.32);
}

html.dark .hero-copy h1 {
  color: #f1f5f9;
}

html.dark .hero-copy p {
  color: #cbd5e1;
}

html.dark .hero-tab {
  border-color: rgba(100, 116, 139, 0.46);
  background: rgba(15, 23, 42, 0.38);
}

html.dark .hero-tab:hover,
html.dark .hero-tab.active {
  background: rgba(20, 184, 166, 0.1);
}

@media (max-width: 960px) {
  .alerts-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .alerts-logs-center {
    padding: 12px;
  }

  .hero-tabs {
    grid-template-columns: 1fr;
  }
}
</style>
