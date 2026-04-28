<template>
  <div class="api-gateway-container">
    <OrinEntityHeader
      domain="系统配置"
      title="统一网关"
      :description="pageDesc"
      :summary="gatewayHeaderSummary"
    >
      <template #filters>
      <div class="gateway-nav">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          class="gateway-nav-item"
          :class="{ active: activeTab === tab.name }"
          @click="activeTab = tab.name"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </button>
      </div>
      </template>
    </OrinEntityHeader>

    <div class="gateway-content">
      <GatewayOverviewTab v-show="activeTab === 'overview'" />
      <GatewayRoutesTab v-show="activeTab === 'routes'" />
      <GatewayServicesTab v-show="activeTab === 'services'" />
      <GatewayAclTab v-show="activeTab === 'acl'" />
      <GatewayPoliciesTab v-show="activeTab === 'policies'" />
      <GatewayRateLimitTab v-show="activeTab === 'rate-limit'" />
      <ApiKeyManagement v-show="activeTab === 'secrets'" />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Share, SetUp, Lock, Operation, Lightning, Key } from '@element-plus/icons-vue'
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue'
import GatewayOverviewTab from './components/gateway/GatewayOverviewTab.vue'
import GatewayRoutesTab from './components/gateway/GatewayRoutesTab.vue'
import GatewayServicesTab from './components/gateway/GatewayServicesTab.vue'
import GatewayAclTab from './components/gateway/GatewayAclTab.vue'
import GatewayPoliciesTab from './components/gateway/GatewayPoliciesTab.vue'
import GatewayRateLimitTab from './components/gateway/GatewayRateLimitTab.vue'
import ApiKeyManagement from './ApiKeyManagement.vue'

const route = useRoute()
const activeTab = ref('overview')
const pageDesc = computed(() => activeTab.value === 'secrets'
  ? '统一管理访问密钥、供应商凭据、轮换与受控回显'
  : '管理 API 网关配置、路由规则和访问控制')

const gatewayHeaderSummary = computed(() => [
  { label: '当前模块', value: tabs.find(tab => tab.name === activeTab.value)?.label || '-' },
  { label: '治理范围', value: '7 项' },
  { label: '密钥中心', value: activeTab.value === 'secrets' ? '当前' : '可切换' }
])

if (route.query.tab === 'secrets') {
  activeTab.value = 'secrets'
}
if (route.path.endsWith('/api-keys')) {
  activeTab.value = 'secrets'
}

const tabs = [
  { name: 'overview',    label: '网关概览', icon: DataAnalysis },
  { name: 'routes',      label: '路由管理', icon: Share },
  { name: 'services',    label: '服务管理', icon: SetUp },
  { name: 'acl',         label: '访问控制', icon: Lock },
  { name: 'policies',    label: '策略管理', icon: Operation },
  { name: 'rate-limit',  label: '全局限流', icon: Lightning },
  { name: 'secrets',     label: '密钥中心', icon: Key },
]
</script>

<style scoped>
.api-gateway-container {
  padding: 0;
  color: #243244;
}

.gateway-nav {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
}

.gateway-nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid #dbe4ee;
  border-radius: 6px;
  background: #ffffff;
  font-size: 13px;
  font-weight: 500;
  color: var(--neutral-gray-500, #6b7280);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s, background 0.15s;
  white-space: nowrap;
}

.gateway-nav-item .el-icon {
  font-size: 14px;
}

.gateway-nav-item:hover:not(.active) {
  color: var(--neutral-gray-800, #1f2937);
  border-color: #9edbd4;
}

.gateway-nav-item.active {
  color: #0d9488;
  background: #ecfdf9;
  border-color: #0d9488;
  font-weight: 600;
}

.gateway-content {
  padding-top: 0;
  min-height: 400px;
}

html.dark .gateway-title {
  color: #e2e8f0;
}

html.dark .gateway-desc {
  color: #94a3b8;
}

html.dark .gateway-nav {
  border-bottom-color: rgba(71, 85, 105, 0.5);
}

html.dark .gateway-nav-item {
  color: #94a3b8;
}

html.dark .gateway-nav-item:hover:not(.active) {
  color: #e2e8f0;
}

html.dark .gateway-nav-item.active {
  color: #5eead4;
  border-bottom-color: #2dd4bf;
}

/* Unify dark mode surface for all gateway sub tabs */
html.dark .api-gateway-container :deep(.section-card),
html.dark .api-gateway-container :deep(.stat-card),
html.dark .api-gateway-container :deep(.stats-grid-item) {
  background: #111c2f;
  border-color: #2b3d59;
}

html.dark .api-gateway-container :deep(.section-header) {
  border-bottom-color: #2b3d59;
}

html.dark .api-gateway-container :deep(.section-title),
html.dark .api-gateway-container :deep(.rule-text),
html.dark .api-gateway-container :deep(.stats-grid-value),
html.dark .api-gateway-container :deep(.stat-value) {
  color: #e2e8f0;
}

html.dark .api-gateway-container :deep(.rule-burst),
html.dark .api-gateway-container :deep(.stats-grid-label),
html.dark .api-gateway-container :deep(.stat-label),
html.dark .api-gateway-container :deep(.empty-text),
html.dark .api-gateway-container :deep(.target-text),
html.dark .api-gateway-container :deep(.text-muted) {
  color: #94a3b8;
}

html.dark .api-gateway-container :deep(.stat-card:hover) {
  box-shadow: 0 8px 20px rgba(2, 8, 23, 0.45);
}

html.dark .api-gateway-container :deep(.stat-icon-wrap) {
  background: rgba(148, 163, 184, 0.18) !important;
}

html.dark .api-gateway-container :deep(.stats-grid) {
  background: #2b3d59;
}

html.dark .api-gateway-container :deep(.policy-nav-item) {
  background: #111c2f;
  border-color: #2b3d59;
  color: #94a3b8;
}

html.dark .api-gateway-container :deep(.policy-nav-item:hover:not(.active)) {
  border-color: #22d3ee;
  color: #e2e8f0;
}

html.dark .api-gateway-container :deep(.policy-nav-item.active) {
  background: #16314f;
  border-color: #22d3ee;
  color: #67e8f9;
}

html.dark .api-gateway-container :deep(.policy-nav-item:not(.active) .policy-badge) {
  background: #334155;
  color: #cbd5e1;
}
</style>
