<template>
  <div class="main-layout">
    <!-- 顶部导航栏 -->
    <TopNavbar v-if="appStore.menuMode === 'topbar'" />

    <!-- 侧边栏模式 -->
    <Sidebar v-if="appStore.menuMode === 'sidebar'" />

    <!-- 主内容区域 -->
    <div
      class="content-area"
      :class="{
        'with-sidebar': appStore.menuMode === 'sidebar',
        'collapsed': appStore.menuMode === 'sidebar' && appStore.isCollapse,
        'is-workspace-page': isWorkspaceRoute
      }"
    >
      <router-view v-slot="{ Component }">
        <transition name="fade-transform">
          <component :is="Component" :key="$route.fullPath" />
        </transition>
      </router-view>
    </div>

    <!-- 侧边栏外部切换按钮 -->
    <div
      v-if="appStore.menuMode === 'sidebar' && !isWorkspaceRoute"
      class="sidebar-external-toggle"
      :class="{ 'is-collapsed': appStore.isCollapse }"
      @click="appStore.toggleSidebar"
    >
      <el-icon>
        <DArrowLeft v-if="!appStore.isCollapse" />
        <DArrowRight v-else />
      </el-icon>
    </div>
  </div>
</template>

<script setup>
import TopNavbar from './components/TopNavbar.vue'
import Sidebar from './components/Sidebar.vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { computed } from 'vue'
import { DArrowLeft, DArrowRight } from '@element-plus/icons-vue'

const $route = useRoute()
const appStore = useAppStore()
const isWorkspaceRoute = computed(() => $route.name === 'ApplicationWorkspace')
</script>

<style scoped>
.main-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.content-area {
  padding: 20px;
  min-height: calc(100vh - 64px);
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1), width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.content-area.with-sidebar {
  margin-left: var(--sidebar-width);
  width: calc(100% - var(--sidebar-width));
}

.content-area.collapsed {
  margin-left: var(--sidebar-width-collapsed);
  width: calc(100% - var(--sidebar-width-collapsed));
}

.content-area.is-workspace-page {
  padding: 0;
  height: calc(100vh - 64px);
  overflow: hidden;
}

/* 页面切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}

/* 深色模式适配 */
html.dark .main-layout {
  background: #0f0f0f;
}

html.dark .content-area {
  background: #0f0f0f;
}

/* 侧边栏外部切换按钮 */
.sidebar-external-toggle {
  position: fixed;
  left: var(--sidebar-width);
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  background: linear-gradient(135deg, rgba(255,255,255,0.95) 0%, rgba(248,250,252,0.9) 100%);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
  border: 1px solid var(--neutral-gray-200);
  border-left: none;
  border-radius: 0 8px 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 2px 0 12px rgba(0, 0, 0, 0.1);
  z-index: 100;
  opacity: 0;
}

.sidebar-external-toggle:hover {
  background: var(--orin-primary-soft);
  border-color: var(--orin-primary);
}

.sidebar-external-toggle:hover .el-icon {
  color: var(--orin-primary);
}

.sidebar-external-toggle .el-icon {
  font-size: 14px;
  color: var(--neutral-gray-600);
  transition: all 0.3s;
}

.sidebar-external-toggle.is-collapsed {
  left: var(--sidebar-width-collapsed);
}

/* 鼠标悬停时显示 */
.main-layout:hover .sidebar-external-toggle {
  opacity: 1;
}

/* 淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
