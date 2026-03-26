<template>
  <div class="settings-layout">
    <PageHeader
      title="系统设置"
      description="统一管理系统各项配置参数"
      icon="Tools"
    />

    <div class="settings-container">
      <!-- 右侧内容区 -->
      <main class="settings-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style scoped>
.settings-layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.settings-container {
  display: flex;
  flex: 1;
  padding: 0 24px 24px;
  gap: 24px;
  overflow: hidden;
}

.settings-content {
  flex: 1;
  overflow-y: auto;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  padding: 24px;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
