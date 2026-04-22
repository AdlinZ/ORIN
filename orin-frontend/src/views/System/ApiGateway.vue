<template>
  <div class="api-gateway-container">
    <div class="gateway-header">
      <div class="gateway-header-title">
        <el-icon class="gateway-header-icon"><Connection /></el-icon>
        <div>
          <h2 class="gateway-title">统一网关</h2>
          <p class="gateway-desc">{{ pageDesc }}</p>
        </div>
      </div>
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
    </div>

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
import { Connection, DataAnalysis, Share, SetUp, Lock, Operation, Lightning, Key } from '@element-plus/icons-vue'
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
  padding: 24px 24px 0;
}

/* Header: title + tabs in one block, no card */
.gateway-header {
  margin-bottom: 24px;
}

.gateway-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.gateway-header-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9, #eff6ff);
  color: var(--el-color-primary, #2563eb);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.gateway-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900, #111827);
  margin: 0 0 3px;
  line-height: 1.2;
}

.gateway-desc {
  font-size: 13px;
  color: var(--neutral-gray-500, #6b7280);
  margin: 0;
}

/* Underline tab nav */
.gateway-nav {
  display: flex;
  align-items: center;
  gap: 0;
  border-bottom: 1px solid var(--neutral-gray-200, #e5e7eb);
}

.gateway-nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 16px 10px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  font-size: 13px;
  font-weight: 500;
  color: var(--neutral-gray-500, #6b7280);
  cursor: pointer;
  transition: color 0.15s, border-color 0.15s;
  white-space: nowrap;
  margin-bottom: -1px;
}

.gateway-nav-item .el-icon {
  font-size: 14px;
}

.gateway-nav-item:hover:not(.active) {
  color: var(--neutral-gray-800, #1f2937);
}

.gateway-nav-item.active {
  color: var(--el-color-primary, #2563eb);
  border-bottom-color: var(--el-color-primary, #2563eb);
  font-weight: 600;
}

.gateway-content {
  padding-top: 20px;
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
</style>
