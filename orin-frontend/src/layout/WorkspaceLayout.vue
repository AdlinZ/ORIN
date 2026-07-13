<template>
  <div class="workspace-layout main-layout">
    <!-- 顶部导航栏 -->
    <TopNavbar
      v-if="appStore.menuMode === 'topbar'"
      :menus="visibleMenus"
      surface-label="ORIN 工作台"
      :profile-route="profileRoute"
      :home-route="ROUTES.WORKSPACE"
    />

    <!-- 侧边栏模式 -->
    <Sidebar
      v-if="appStore.menuMode === 'sidebar'"
      :menus="visibleMenus"
      surface-label="ORIN 工作台"
      :profile-route="profileRoute"
    />

    <!-- 主内容区域 -->
    <div
      class="content-area"
      :class="{
        'with-sidebar': appStore.menuMode === 'sidebar',
        'collapsed': appStore.menuMode === 'sidebar' && appStore.isCollapse,
        'is-workspace-page': isWorkspaceRoute,
        'has-topbar': appStore.menuMode === 'topbar'
      }"
    >
      <div class="content-inner">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform">
            <component :is="Component" :key="routeViewKey" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import TopNavbar from './components/TopNavbar.vue'
import Sidebar from './components/Sidebar.vue'
import { WORKSPACE_MENU } from '@/router/menuConfig/workspaceMenu'
import { buildVisibleMenus } from '@/router/menuConfig/shared'
import { ROUTES } from '@/router/routes'

const $route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const routeViewKey = computed(() => $route.path)

const profileRoute = computed(() => ROUTES.WORKSPACE_PATHS.PROFILE)

const visibleMenus = computed(() => buildVisibleMenus(WORKSPACE_MENU, userStore.roles || []))

const WORKSPACE_ROUTE_NAMES = new Set([
  'ApplicationWorkspace',
  'AgentConsole',
  'AgentConsoleEntry',
  'PlaygroundRun',
])
const isWorkspaceRoute = computed(() => WORKSPACE_ROUTE_NAMES.has(String($route.name || '')))
</script>

<style scoped>
.workspace-layout {
  min-height: 100vh;
  background: #ffffff;
}

.content-area {
  padding: var(--orin-page-gap, 20px);
  min-height: calc(100vh - var(--header-height, 64px));
  transition: margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1), width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.content-inner {
  width: 100%;
  margin: 0 auto;
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
  height: 100vh;
  overflow: hidden;
}

.content-area.is-workspace-page .content-inner {
  max-width: none;
  height: 100%;
}

.content-area.is-workspace-page.has-topbar {
  height: calc(100vh - var(--header-height, 64px));
}

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

html.dark .workspace-layout {
  background: var(--bg-color);
}

html.dark .content-area {
  background: transparent;
}
</style>
