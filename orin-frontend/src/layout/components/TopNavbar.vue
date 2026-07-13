<template>
  <div class="top-navbar">
    <!-- Logo 区域 -->
    <div class="navbar-logo" @click="goHome">
      <BrandingLogo :height="44" />
      <span v-if="surfaceLabel" class="navbar-surface-label" :title="surfaceLabel">
        {{ surfaceLabel }}
      </span>
    </div>

    <!-- 中间菜单区域 -->
    <nav class="navbar-menu">
      <div 
        v-for="menu in visibleMenus" 
        :key="menu.id"
        class="menu-item"
        :class="{ active: activeMenuId === menu.id }"
        @mouseenter="handleMenuHover(menu.id)"
        @mouseleave="handleMenuLeave"
        @click="handleMenuClick(menu)"
      >
        <el-icon>
          <component :is="getIconComponent(menu.icon)" />
        </el-icon>
        <span class="menu-title">{{ menu.title }}</span>
        
        <!-- 下拉二级菜单 -->
        <transition name="dropdown">
          <div
            v-show="activeDropdown === menu.id || menu.id === activeMenuId"
            class="dropdown-menu"
            @mouseenter="keepDropdownOpen(menu.id)"
            @mouseleave="handleMenuLeave"
          >
            <template v-for="child in menu.children" :key="child.path || child.title">
              <div v-if="isMenuSection(child)" class="dropdown-section">
                {{ child.title }}
              </div>
              <router-link
                v-else
                :to="child.path"
                class="dropdown-item"
                @click="closeDropdown"
              >
                <el-icon v-if="child.icon">
                  <component :is="getIconComponent(child.icon)" />
                </el-icon>
                <span>{{ child.title }}</span>
              </router-link>
            </template>
          </div>
        </transition>
      </div>
    </nav>

    <!-- 右侧操作区 -->
    <div class="navbar-actions">
      <!-- 动态插槽容器 (Teleport 目标) -->
      <div id="navbar-actions" class="navbar-page-actions" />
      <!-- 刷新按钮 -->
      <div class="action-item optional-action">
        <el-tooltip content="刷新页面" placement="bottom">
          <el-button
            text
            :icon="Refresh"
            class="action-btn"
            @click="handleRefresh"
          />
        </el-tooltip>
      </div>

      <!-- 主题切换按钮 -->
      <div class="action-item optional-action">
        <el-tooltip :content="isDarkMode ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
          <el-button 
            text 
            :icon="isDarkMode ? Sunny : Moon" 
            class="action-btn" 
            @click="toggleTheme" 
          />
        </el-tooltip>
      </div>

      <!-- 通知图标 -->
      <div class="action-item optional-action">
        <el-tooltip content="通知中心" placement="bottom">
          <el-button
            text
            :icon="Bell"
            class="action-btn"
            @click="showNotifications"
          />
        </el-tooltip>
      </div>

      <!-- 切换到侧边栏模式 -->
      <div class="action-item">
        <el-tooltip content="切换到侧边栏模式" placement="bottom">
          <el-button
            text
            :icon="Fold"
            class="action-btn"
            @click="appStore.toggleMenuMode()"
          />
        </el-tooltip>
      </div>

      <!-- 用户下拉菜单 -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-info">
          <el-avatar :src="userInfo.avatar" :size="36">
            {{ userInfo.name?.charAt(0) }}
          </el-avatar>
          <span class="user-name">{{ userInfo.name }}</span>
          <el-icon class="dropdown-icon">
            <ArrowDown />
          </el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>
              <span>账号设置</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 移动端汉堡菜单 -->
    <div class="mobile-menu-toggle" @click="toggleMobileMenu">
      <el-icon><Menu /></el-icon>
    </div>
  </div>

  <!-- 通知中心抽屉 -->
  <NotificationCenter 
    v-model="showNotificationCenter"
  />

  <!-- 移动端侧边抽屉 -->
  <el-drawer
    v-model="showMobileMenu"
    direction="ltr"
    size="280px"
    :show-close="false"
  >
    <template #header>
      <div class="mobile-drawer-header">
        <BrandingLogo :height="32" class="drawer-logo" />
      </div>
    </template>

    <div class="mobile-menu-content">
      <div v-for="menu in visibleMenus" :key="menu.id" class="mobile-menu-group">
        <div class="mobile-menu-title" :style="{ color: menu.color }">
          <el-icon><component :is="getIconComponent(menu.icon)" /></el-icon>
          <span>{{ menu.title }}</span>
        </div>
        <template v-for="child in menu.children" :key="child.path || child.title">
          <div v-if="isMenuSection(child)" class="mobile-menu-section">
            {{ child.title }}
          </div>
          <router-link
            v-else
            :to="child.path"
            class="mobile-menu-item"
            @click="closeMobileMenu"
          >
            {{ child.title }}
          </router-link>
        </template>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import {
  getActiveMenuIdForMenu,
  isMenuSection as detectSection,
} from '@/router/menuConfig/shared'
import NotificationCenter from './NotificationCenter.vue'
import {
  Bell, ArrowDown, User, Sunny, Moon, SwitchButton, Menu, Refresh,
  Fold,
} from '@element-plus/icons-vue'
import BrandingLogo from '@/components/BrandingLogo.vue'
import { ElMessage } from 'element-plus'
import { useTheme } from '@/composables/useTheme'
import { useUser } from '@/composables/useUser'
import { getIconComponent } from '@/utils/iconMap'

const props = defineProps({
  menus: {
    type: Array,
    default: () => [],
  },
  surfaceLabel: {
    type: String,
    default: '',
  },
  profileRoute: {
    type: String,
    default: '/chat/profile',
  },
  homeRoute: {
    type: String,
    default: '/',
  },
})

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

// 共享 composable
const { isDarkMode, toggleTheme } = useTheme()
const { handleLogout: _doLogout } = useUser()

// State
const activeDropdown = ref(null)
const showMobileMenu = ref(false)
const showNotificationCenter = ref(false)

// Computed
const userInfo = computed(() => {
  if (!userStore.userInfo) userStore.restoreFromCookies()
  return {
    name: userStore.userInfo?.nickname || userStore.userInfo?.username || '用户',
    avatar: userStore.userInfo?.avatar || ''
  }
})

const visibleMenus = computed(() => props.menus || [])
const activeMenuId = computed(() => getActiveMenuIdForMenu(visibleMenus.value, route.path))
const isMenuSection = (item = {}) => detectSection(item)

// Methods
const goHome = () => router.push(props.homeRoute)

const handleRefresh = () => {
  // 触发页面刷新事件
  window.dispatchEvent(new Event('page-refresh'))

  ElMessage({
    message: '正在刷新页面数据...',
    type: 'info',
    duration: 1500
  })

  // 监听页面刷新完成事件
  const handleRefreshDone = () => {
    ElMessage({
      message: '页面数据已刷新',
      type: 'success',
      duration: 1500
    })
    window.removeEventListener('page-refresh-done', handleRefreshDone)
  }
  window.addEventListener('page-refresh-done', handleRefreshDone)

  // 5秒后自动移除监听，防止意外触发
  setTimeout(() => {
    window.removeEventListener('page-refresh-done', handleRefreshDone)
  }, 5000)
}

const toggleMobileMenu = () => { showMobileMenu.value = !showMobileMenu.value }
const closeMobileMenu = () => { showMobileMenu.value = false }

const handleMenuHover = (menuId) => {
  activeDropdown.value = menuId
}

const handleMenuLeave = () => {
  // 延迟关闭，给用户时间移动到下拉菜单
  setTimeout(() => {
    if (activeDropdown.value) {
      activeDropdown.value = null
    }
  }, 200)
}

const keepDropdownOpen = (menuId) => {
  activeDropdown.value = menuId
}

const handleMenuClick = (menu) => {
  // 只切换下拉菜单，不导航到父路由
  if (activeDropdown.value === menu.id) {
    activeDropdown.value = null
  } else {
    activeDropdown.value = menu.id
  }
}

const closeDropdown = () => {
  activeDropdown.value = null
}

const showNotifications = () => {
  showNotificationCenter.value = true
}

const handleUserCommand = (command) => {
  switch (command) {
    case 'profile':
      router.push(props.profileRoute)
      break
    case 'settings':
      router.push(props.profileRoute)
      break
    case 'logout':
      handleLogout()
      break
  }
}

const handleLogout = () => _doLogout(true)


onMounted(() => {
})
</script>

<style scoped>
.top-navbar {
  height: 72px;
  background: rgba(255, 255, 255, 0.86);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.04);
  display: grid;
  grid-template-columns: minmax(150px, 220px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding: 0 28px;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.navbar-surface-label {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--orin-primary);
  padding: 4px 10px;
  background: var(--orin-primary-soft);
  border-radius: 8px;
  white-space: nowrap;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Logo 区域 */
.navbar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  min-width: 0;
}

.navbar-logo:hover {
  opacity: 0.8;
}

.navbar-logo img {
  height: 48px;
  width: auto;
  display: block;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

/* 一级菜单 - 始终居中 */
.navbar-menu {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: visible;
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  height: 44px;
  padding: 0 13px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.22s ease, box-shadow 0.22s ease, color 0.22s ease;
  user-select: none;
  white-space: nowrap;
  flex: 0 0 auto;
}

.menu-item .el-icon {
  color: var(--neutral-gray-600);
  font-size: 17px;
  flex: 0 0 auto;
}

.menu-item:hover {
  background: var(--neutral-gray-50);
}

.menu-item:hover .el-icon,
.menu-item:hover .menu-title {
  color: var(--orin-primary);
}

.menu-item.active {
  background: var(--orin-primary-soft);
  box-shadow: inset 0 0 0 1px rgba(0, 191, 165, 0.14);
}

.menu-item.active .el-icon,
.menu-item.active .menu-title {
  color: var(--orin-primary);
  font-weight: 600;
}

.menu-title {
  font-size: 14px;
  line-height: 1;
  color: var(--neutral-gray-700);
  transition: all 0.3s;
  white-space: nowrap;
}

/* 下拉菜单 - 轻毛玻璃 */
.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  background: rgba(255, 255, 255, 0.92);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
  min-width: 180px;
  padding: 8px;
  z-index: 1001;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 6px;
  color: var(--neutral-gray-700);
  text-decoration: none;
  transition: all 0.2s;
  font-size: 14px;
}

.dropdown-section {
  padding: 8px 12px 4px;
  color: var(--neutral-gray-500);
  font-size: 12px;
  font-weight: 700;
  line-height: 1.2;
}

.dropdown-section:not(:first-child) {
  margin-top: 6px;
  border-top: 1px solid var(--neutral-gray-100);
}

.dropdown-item:hover {
  background: var(--neutral-gray-50);
  color: var(--orin-primary);
}

.dropdown-item .el-icon {
  font-size: 16px;
}

/* 下拉动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.3s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 右侧操作区域 */
.navbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
}

.navbar-page-actions {
  display: flex;
  align-items: center;
}

.navbar-page-actions:empty {
  display: none;
}

.action-item {
  display: flex;
  align-items: center;
}

.action-btn {
  width: 34px;
  height: 34px;
  color: var(--neutral-gray-600);
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
}

.action-btn:hover {
  color: var(--orin-primary);
  background: var(--neutral-gray-50);
}

.action-divider {
  width: 1px;
  height: 20px;
  background: var(--neutral-gray-300);
  margin: 0 4px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px 0 8px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
  background: rgba(15, 23, 42, 0.04);
}

.user-info:hover {
  background: var(--neutral-gray-50);
}

.user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-icon {
  font-size: 12px;
  color: var(--neutral-gray-500);
  transition: transform 0.3s;
}

.user-info:hover .dropdown-icon {
  transform: rotate(180deg);
}

/* 移动端汉堡菜单 */
.mobile-menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.mobile-menu-toggle:hover {
  background: var(--neutral-gray-50);
}

.mobile-menu-toggle .el-icon {
  font-size: 24px;
}

/* 移动端抽屉 */
.mobile-drawer-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-logo {
  width: 32px;
  height: 32px;
}

.drawer-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.mobile-menu-content {
  padding: 16px 0;
}

.mobile-menu-group {
  margin-bottom: 24px;
}

.mobile-menu-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.mobile-menu-item {
  display: block;
  padding: 10px 16px 10px 48px;
  color: var(--neutral-gray-700);
  text-decoration: none;
  font-size: 14px;
  border-radius: 6px;
  margin: 4px 8px;
  transition: all 0.2s;
}

.mobile-menu-section {
  padding: 10px 16px 4px 48px;
  color: var(--neutral-gray-500);
  font-size: 12px;
  font-weight: 700;
}

.mobile-menu-item:hover {
  background: var(--neutral-gray-50);
  color: var(--orin-primary);
}

/* 响应式 */
@media (max-width: 1280px) {
  .top-navbar {
    grid-template-columns: minmax(140px, 210px) minmax(0, 1fr) auto;
    gap: 10px;
    padding: 0 20px;
  }

  .navbar-logo img {
    height: 42px;
  }

  .navbar-menu {
    gap: 3px;
  }

  .menu-item {
    height: 40px;
    gap: 5px;
    padding: 0 9px;
    border-radius: 10px;
  }

  .menu-title {
    font-size: 13px;
  }

  .menu-item .el-icon {
    font-size: 16px;
  }

  .navbar-actions {
    gap: 3px;
  }

  .action-btn {
    width: 32px;
    height: 32px;
  }

  .user-info {
    height: 44px;
    padding-right: 10px;
  }

  .user-name {
    max-width: 116px;
  }
}

@media (max-width: 1180px) {
  .optional-action {
    display: none;
  }
}

@media (max-width: 1024px) {
  .navbar-menu {
    display: none;
  }
  
  .mobile-menu-toggle {
    display: flex;
  }
  
  .user-name {
    display: none;
  }
}

@media (max-width: 768px) {
  .top-navbar {
    padding: 0 16px;
  }
  
  .navbar-logo {
    margin-right: 16px;
  }
  
  .logo-text {
    font-size: 18px;
  }
}

/* 深色模式适配 - Glassmorphism */
html.dark .top-navbar {
  background: rgba(15, 23, 42, 0.86);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  box-shadow: 0 4px 18px rgba(0, 0, 0, 0.24);
  border-bottom: 1px solid rgba(148, 163, 184, 0.22);
}

html.dark .logo-text {
  color: #ffffff;
}

html.dark .menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .menu-item.active {
  background: rgba(0, 191, 165, 0.14);
  box-shadow: inset 0 0 0 1px rgba(0, 191, 165, 0.28), 0 10px 28px rgba(0, 191, 165, 0.08);
}

html.dark .menu-title {
  color: #e0e0e0;
}

html.dark .dropdown-menu {
  background: rgba(15, 23, 42, 0.94);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(148, 163, 184, 0.22);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.28);
}

html.dark .dropdown-item {
  color: #e0e0e0;
}

html.dark .dropdown-section {
  color: #94a3b8;
  border-top-color: rgba(148, 163, 184, 0.18);
}

html.dark .dropdown-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

html.dark .user-info {
  background: rgba(255, 255, 255, 0.07);
}

html.dark .user-info:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .user-name {
  color: #ffffff;
}

html.dark .mobile-menu-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .mobile-menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .drawer-title {
  color: #ffffff;
}

html.dark .mobile-menu-title {
  color: #e0e0e0;
}

html.dark .mobile-menu-section {
  color: #94a3b8;
}

html.dark .mobile-menu-item {
  color: #b0b0b0;
}

html.dark .action-divider {
  background: rgba(255, 255, 255, 0.2);
}

</style>
