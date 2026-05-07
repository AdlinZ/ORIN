<template>
  <div class="data-assets-page page-container fade-in">
    <section class="data-assets-shell">
      <header class="data-assets-header">
        <div>
          <span class="header-eyebrow">系统设置</span>
          <h1>数据资产</h1>
          <p>统一管理多模态文件资产、端侧数据同步和外部平台同步入口。</p>
        </div>
      </header>

      <el-tabs v-model="activeWorkspace" class="workspace-tabs" @tab-change="handleWorkspaceChange">
        <el-tab-pane label="文件资产" name="files">
          <FileManagement embedded />
        </el-tab-pane>
        <el-tab-pane label="同步管理" name="sync">
          <ClientSync embedded />
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FileManagement from '@/views/System/FileManagement.vue'
import ClientSync from '@/views/System/ClientSync.vue'

const route = useRoute()
const router = useRouter()

const activeWorkspace = computed({
  get() {
    if (route.query.assetTab === 'sync') return 'sync'
    if (['changes', 'webhooks', 'dify'].includes(route.query.tab)) return 'sync'
    return 'files'
  },
  set(value) {
    const query = value === 'sync'
      ? { ...route.query, assetTab: 'sync', tab: route.query.tab || 'changes' }
      : { assetTab: 'files' }

    router.replace({ path: '/dashboard/control/data-assets', query }).catch(() => {})
  }
})

const handleWorkspaceChange = (name) => {
  activeWorkspace.value = name
}
</script>

<style scoped>
.data-assets-page {
  min-height: 100vh;
  padding: 32px;
  color: var(--el-text-color-primary);
}

.fade-in {
  animation: fadeIn 0.35s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.data-assets-shell {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1600px;
  margin: 0 auto;
}

.data-assets-header {
  display: flex;
  justify-content: space-between;
  gap: 24px;
}

.header-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.data-assets-header h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-weight: 760;
  line-height: 1.2;
}

.data-assets-header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.workspace-tabs {
  min-width: 0;
}

:deep(.workspace-tabs > .el-tabs__header) {
  margin-bottom: 16px;
}

:deep(.workspace-tabs > .el-tabs__content) {
  overflow: visible;
}

@media (max-width: 760px) {
  .data-assets-page {
    padding: 20px;
  }
}
</style>
