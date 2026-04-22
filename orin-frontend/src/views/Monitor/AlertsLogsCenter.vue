<template>
  <div class="alerts-logs-center">
    <el-card shadow="never" class="page-nav-card">
      <PageHeader
        title="告警与日志"
        description="统一管理告警规则、告警历史与审计日志"
        icon="Bell"
        flat
      />
      <div class="tab-nav-bar">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          class="tab-nav-item"
          :class="{ active: activeTab === tab.name }"
          @click="activeTab = tab.name"
        >
          {{ tab.label }}
        </button>
      </div>
    </el-card>

    <AlertManagement v-if="activeTab === 'rules'" mode="rules" :show-header="false" initial-tab="rules" />
    <AlertManagement v-else-if="activeTab === 'history'" mode="history" :show-header="false" initial-tab="history" />
    <AuditCenterV2 v-else-if="activeTab === 'audit'" mode="logs" :show-header="false" :show-header-actions="false" initial-tab="logs" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AlertManagement from '@/views/System/AlertManagement.vue'
import AuditCenterV2 from '@/views/revamp/system/AuditCenterV2.vue'

const activeTab = ref('rules')

const tabs = [
  { name: 'rules', label: '告警规则' },
  { name: 'history', label: '告警历史' },
  { name: 'audit', label: '审计日志' },
]
</script>

<style scoped>
.alerts-logs-center {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-nav-card :deep(.el-card__body) {
  padding: 0;
}

.tab-nav-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 20px 0;
  border-top: 1px solid var(--border-color, #e2e8f0);
}

.tab-nav-item {
  position: relative;
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary, #64748b);
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color 0.15s;
  outline: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}

.tab-nav-item:hover {
  color: var(--orin-primary, #0d9488);
}

.tab-nav-item.active {
  color: var(--orin-primary, #0d9488);
  font-weight: 600;
  border-bottom-color: var(--orin-primary, #0d9488);
}

html.dark .tab-nav-bar {
  border-top-color: var(--neutral-gray-200, #1a2e2e);
}
</style>
