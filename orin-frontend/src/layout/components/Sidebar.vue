<template>
  <div class="sidebar-container" :class="{ 'collapsed': appStore.isCollapse }">
    <!-- Logo Section -->
    <div class="logo-container">
      <div class="logo-box">
        <BrandingLogo :height="appStore.isCollapse ? 28 : 32" class="logo" />
      </div>
      <span v-if="!appStore.isCollapse" class="title"><span class="highlight">Monitor</span></span>
    </div>

    <!-- Menu Section (scrollable) -->
    <div class="menu-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.isCollapse"
        router
        unique-opened
        class="el-menu-vertical"
      >
        <!-- 首页 -->
        <el-menu-item index="/dashboard/home" class="home-menu-item">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </el-menu-item>

        <!-- 动态渲染四大模块 -->
        <template v-for="menu in visibleMenus" :key="menu.id">
          <el-sub-menu :index="getSubMenuIndex('menu', menu)" :class="`menu-${menu.id}`">
            <template #title>
              <el-icon :style="{ color: menu.color }">
                <component :is="getIconComponent(menu.icon)" />
              </el-icon>
              <span>{{ menu.title }}</span>
            </template>

            <!-- 二级菜单 -->
            <template v-for="child in menu.children.filter(c => !c.divider)" :key="child.path">
              <!-- 有三级菜单的二级菜单 -->
              <el-sub-menu v-if="child.children && child.children.length > 0" :index="getSubMenuIndex('child', child, menu.id)">
                <template #title>
                  <el-icon v-if="child.icon">
                    <component :is="getIconComponent(child.icon)" />
                  </el-icon>
                  <span>{{ child.title }}</span>
                </template>
                <!-- 三级菜单 -->
                <el-menu-item
                  v-for="subChild in child.children.filter(c => !c.divider)"
                  :key="subChild.path"
                  :index="subChild.path"
                >
                  <el-icon v-if="subChild.icon">
                    <component :is="getIconComponent(subChild.icon)" />
                  </el-icon>
                  <span>{{ subChild.title }}</span>
                  <el-tag
                    v-if="subChild.status"
                    size="small"
                    :type="getMaturityTagType(subChild.status)"
                    effect="plain"
                    class="menu-status-tag"
                  >
                    {{ getMaturityText(subChild.status) }}
                  </el-tag>
                </el-menu-item>
              </el-sub-menu>
              <!-- 无三级菜单的二级菜单 -->
              <el-menu-item v-else :index="child.path">
                <el-icon v-if="child.icon">
                  <component :is="getIconComponent(child.icon)" />
                </el-icon>
                <span>{{ child.title }}</span>
                <el-tag
                  v-if="child.status"
                  size="small"
                  :type="getMaturityTagType(child.status)"
                  effect="plain"
                  class="menu-status-tag"
                >
                  {{ getMaturityText(child.status) }}
                </el-tag>
              </el-menu-item>
            </template>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>

    <!-- User Section (fixed at bottom) -->
    <div class="user-section">
      <el-dropdown trigger="click" placement="top-start" @command="handleCommand">
        <div class="user-wrapper">
          <el-avatar
            :size="appStore.isCollapse ? 36 : 40"
            :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'"
            class="user-avatar"
          />
          <div v-if="!appStore.isCollapse" class="user-info">
            <span class="user-name">{{ userInfo.name || '游客' }}</span>
            <span v-if="userInfo.role" class="user-role">{{ userInfo.role }}</span>
          </div>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown">
            <el-dropdown-item v-if="userInfo.name" command="profile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item divided command="toggle_menu_mode">
              <el-icon><Expand /></el-icon>
              <span>切换到顶栏模式</span>
            </el-dropdown-item>
            <el-dropdown-item v-if="!userInfo.name" command="login">
              <el-icon><User /></el-icon>登录
            </el-dropdown-item>
            <el-dropdown-item
              v-if="userInfo.name"
              divided
              command="logout"
              class="logout-item"
            >
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- Bottom Actions -->
      <div v-if="!appStore.isCollapse" class="bottom-actions">
        <el-tooltip content="刷新页面" placement="right">
          <el-button
            text
            :icon="Refresh"
            class="action-btn"
            @click="handleRefresh"
          />
        </el-tooltip>
        <el-tooltip :content="isDarkMode ? '浅色模式' : '深色模式'" placement="right">
          <el-button
            text
            :icon="isDarkMode ? Sunny : Moon"
            class="action-btn"
            @click="toggleTheme"
          />
        </el-tooltip>
        <el-tooltip content="通知中心" placement="right">
          <el-button
            text
            :icon="Bell"
            class="action-btn"
            @click="showNotifications"
          />
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import BrandingLogo from '@/components/BrandingLogo.vue'
import { ElMessage } from 'element-plus'
import { ROUTES } from '@/router/routes'
import { getVisibleMenus } from '@/router/topMenuConfig'
import { DArrowLeft, DArrowRight, Refresh, Moon, Sunny, Bell, Expand, User, SwitchButton } from '@element-plus/icons-vue' // eslint-disable-line no-unused-vars
import { useUser } from '@/composables/useUser'
import { useTheme } from '@/composables/useTheme'
import { getIconComponent } from '@/utils/iconMap'
import { getMaturityTagType, getMaturityText } from '@/utils/maturity'

const appStore = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

// 共享 composable
const { userInfo, checkLoginStatus, handleLogout } = useUser()
const { isDarkMode, toggleTheme } = useTheme()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.isAdmin)

// 可见菜单（根据权限过滤）
const visibleMenus = computed(() => getVisibleMenus(isAdmin.value))

const getSubMenuIndex = (level, item, parentId = '') => {
  const identity = item.id || item.path || item.title || 'menu'
  return `${level}:${parentId}:${identity}`
}

const handleRefresh = () => {
  window.dispatchEvent(new Event('page-refresh'))
  ElMessage({ message: '正在刷新页面数据...', type: 'info', duration: 1500 })
}

const showNotifications = () => {
  ElMessage.info('通知中心功能开发中')
}

const handleCommand = (command) => {
  switch (command) {
    case 'logout':
      handleLogout(false)
      break
    case 'profile':
      router.push(ROUTES.PROFILE)
      break
    case 'toggle_menu_mode':
      appStore.toggleMenuMode()
      break
    case 'login':
      router.push('/login')
      break
  }
}

onMounted(() => {
  checkLoginStatus()
})

router.afterEach(() => {
  checkLoginStatus()
})
</script>

<style scoped>
.sidebar-container {
  width: var(--sidebar-width);
  height: 100vh;
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.85) 0%, rgba(248,250,252,0.75) 100%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);
  backdrop-filter: blur(20px) saturate(180%);
  border-right: 1px solid var(--neutral-gray-200);
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.04);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1001;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-container.collapsed {
  width: var(--sidebar-width-collapsed);
}

.action-btn {
  width: 32px;
  height: 32px;
  color: var(--neutral-gray-600);
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}

.action-btn:hover {
  color: var(--orin-primary);
  background: var(--neutral-gray-50);
}

.logo-container {
  height: var(--header-height);
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--neutral-gray-100);
  flex-shrink: 0;
  transition: all 0.3s;
}

.sidebar-container.collapsed .logo-container {
  padding: 0;
  justify-content: center;
  align-items: center;
  gap: 0;
}

.logo-box {
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  transition: all 0.3s;
}

.sidebar-container.collapsed .logo-box {
  width: 100%;
  display: grid;
  place-items: center;
  justify-content: center;
}

.logo {
  height: 32px;
  width: auto;
  filter: drop-shadow(0 0 8px var(--primary-glow));
  transition: all 0.3s;
}

.sidebar-container.collapsed .logo {
  height: 28px;
  margin: 0 auto;
}

.title {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 18px;
  color: var(--neutral-gray-900);
  letter-spacing: -0.5px;
  white-space: nowrap;
  transition: opacity 0.3s;
}

.title .highlight {
  color: var(--orin-primary);
}

/* Menu wrapper - scrollable area */
.menu-wrapper {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.menu-wrapper::-webkit-scrollbar {
  width: 4px;
}

.menu-wrapper::-webkit-scrollbar-thumb {
  background: var(--neutral-gray-300);
  border-radius: 2px;
}

.el-menu-vertical {
  width: 100%;
  border-right: none;
  background: transparent;
}

.el-menu-vertical:not(.el-menu--collapse) {
  width: var(--sidebar-width);
}

.el-menu-vertical.el-menu--collapse {
  width: var(--sidebar-width-collapsed);
}

:deep(.el-menu-item), :deep(.el-sub-menu__title) {
  height: 54px !important;
  line-height: 54px !important;
  color: var(--neutral-gray-600);
  display: flex;
  align-items: center;
  transition: all 0.3s;
}

:deep(.el-menu-item > .el-icon),
:deep(.el-sub-menu__title > .el-icon) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 核心修复：收起状态下的图标居中 */
:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  padding: 0 !important;
  display: flex !important;
  justify-content: center !important;
  align-items: center !important;
  width: var(--sidebar-width-collapsed) !important;
  position: relative;
}

:deep(.el-menu--collapse .el-menu-item > .el-icon),
:deep(.el-menu--collapse .el-sub-menu__title > .el-icon) {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

:deep(.el-menu--collapse .el-icon) {
  margin: 0 !important;
  font-size: 22px;
  width: 22px !important;
  height: 22px !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

/* 隐藏收起状态下的子菜单箭头和文字 */
:deep(.el-menu--collapse span),
:deep(.el-menu--collapse .el-sub-menu__icon-arrow) {
  display: none !important;
}

/* 统一 Active 状态指示条，使用绝对定位防止挤压图标 */
:deep(.el-menu-item.is-active) {
  color: var(--orin-primary) !important;
  background-color: var(--orin-primary-soft) !important;
  font-weight: 600;
  border-left: none !important;
}

:deep(.el-menu-item.is-active)::after {
  content: '';
  position: absolute;
  left: 0;
  top: 15%;
  bottom: 15%;
  width: 3px;
  background-color: var(--orin-primary);
  border-radius: 0 4px 4px 0;
}

.sidebar-container.collapsed :deep(.el-menu-item.is-active)::after {
  top: 0;
  bottom: 0;
  width: 4px;
}

:deep(.el-menu-item:hover), :deep(.el-sub-menu__title:hover) {
  background-color: var(--neutral-gray-50) !important;
}

/* User Section */
.user-section {
  flex-shrink: 0;
  padding: 16px;
  border-top: 1px solid var(--neutral-gray-100);
  background: linear-gradient(180deg, rgba(255,255,255,0.6) 0%, rgba(248,250,252,0.5) 100%);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  transition: all 0.3s;
}

.bottom-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--neutral-gray-200);
}

.user-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.sidebar-container.collapsed .user-wrapper {
  justify-content: center;
  padding: 8px 0;
}

.user-wrapper:hover {
  background: var(--neutral-gray-50);
}

.user-avatar {
  flex-shrink: 0;
  border: 2px solid var(--neutral-gray-100);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-role {
  font-size: 11px;
  font-weight: 700;
  color: var(--orin-primary);
  background: var(--orin-primary-soft);
  padding: 2px 6px;
  border-radius: 4px;
  display: inline-block;
  align-self: flex-start;
}

.user-dropdown {
  padding: 8px !important;
  border-radius: 12px !important;
  min-width: 180px;
}

:deep(.el-dropdown-menu__item) {
  padding: 10px 16px !important;
  border-radius: 8px !important;
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 10px;
}

.logout-item {
  color: var(--error-color) !important;
}

.logout-item:hover {
  background-color: #fff1f0 !important;
}

/* Dark mode */
html.dark .sidebar-container {
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.95) 0%, rgba(4, 10, 18, 0.9) 100%);
  border-color: var(--orin-border);
}

html.dark .action-btn {
  color: var(--neutral-gray-400);
}

html.dark .action-btn:hover {
  color: var(--orin-primary);
  background: rgba(255, 255, 255, 0.1);
}

html.dark .logo-container {
  border-color: var(--orin-border);
}

html.dark .menu-wrapper {
  border-color: var(--orin-border);
}

html.dark .user-section {
  background: rgba(15, 23, 42, 0.8);
  border-color: var(--orin-border);
}

html.dark .bottom-actions {
  border-color: var(--orin-border);
}

html.dark .user-name {
  color: #fff;
}

/* Menu status tag for placeholder items */
.menu-status-tag {
  margin-left: 8px;
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 16px;
  opacity: 0.7;
}
</style>
