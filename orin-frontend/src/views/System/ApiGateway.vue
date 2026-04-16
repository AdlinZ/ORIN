<template>
  <div class="api-gateway-container">
    <PageHeader
      title="统一网关"
      description="管理 API 网关配置、路由规则和访问控制"
      icon="Router"
    />

    <el-tabs v-model="activeTab" class="gateway-tabs">
      <el-tab-pane label="网关概览" name="overview" :lazy="true">
        <GatewayOverviewTab />
      </el-tab-pane>
      <el-tab-pane label="路由管理" name="routes" :lazy="true">
        <GatewayRoutesTab />
      </el-tab-pane>
      <el-tab-pane label="服务管理" name="services" :lazy="true">
        <GatewayServicesTab />
      </el-tab-pane>
      <el-tab-pane label="访问控制" name="acl" :lazy="true">
        <GatewayAclTab />
      </el-tab-pane>
      <el-tab-pane label="策略管理" name="policies" :lazy="true">
        <GatewayPoliciesTab />
      </el-tab-pane>
      <el-tab-pane label="全局限流" name="rate-limit" :lazy="true">
        <GatewayRateLimitTab />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import GatewayOverviewTab from './components/gateway/GatewayOverviewTab.vue'
import GatewayRoutesTab from './components/gateway/GatewayRoutesTab.vue'
import GatewayServicesTab from './components/gateway/GatewayServicesTab.vue'
import GatewayAclTab from './components/gateway/GatewayAclTab.vue'
import GatewayPoliciesTab from './components/gateway/GatewayPoliciesTab.vue'
import GatewayRateLimitTab from './components/gateway/GatewayRateLimitTab.vue'

const activeTab = ref('overview')
</script>

<style scoped>
.api-gateway-container {
  padding: 20px;
}

.gateway-tabs {
  margin-top: 20px;
}
</style>
