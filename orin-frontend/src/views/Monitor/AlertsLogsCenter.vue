<template>
  <div class="alerts-logs-center">
    <PageHeader
      title="告警与日志"
      description="统一查看告警规则、告警历史和审计日志"
      icon="Bell"
    />

    <el-tabs v-model="activeTab" class="center-tabs">
      <el-tab-pane label="告警规则" name="rules" lazy>
        <AlertManagement mode="rules" :show-header="false" initial-tab="rules" />
      </el-tab-pane>
      <el-tab-pane label="告警历史" name="history" lazy>
        <AlertManagement mode="history" :show-header="false" initial-tab="history" />
      </el-tab-pane>
      <el-tab-pane label="审计日志" name="audit" lazy>
        <AuditCenterV2 mode="logs" :show-header="false" :show-header-actions="false" initial-tab="logs" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import AlertManagement from '@/views/System/AlertManagement.vue'
import AuditCenterV2 from '@/views/revamp/system/AuditCenterV2.vue'

const activeTab = ref('rules')
</script>

<style scoped>
.alerts-logs-center {
  padding: 20px;
}
</style>
