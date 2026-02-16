<template>
  <div class="top-navbar">
    <!-- Logo 区域 -->
    <div class="navbar-logo" @click="goHome">
      <img src="/logo.png" alt="ORIN Logo" />
      <span class="logo-text">ORIN</span>
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
            v-show="activeDropdown === menu.id" 
            class="dropdown-menu"
            @mouseenter="keepDropdownOpen(menu.id)"
            @mouseleave="handleMenuLeave"
          >
            <router-link 
              v-for="child in menu.children"
              :key="child.path"
              :to="child.path"
              class="dropdown-item"
              @click="closeDropdown"
            >
              <el-icon v-if="child.icon">
                <component :is="getIconComponent(child.icon)" />
              </el-icon>
              <span>{{ child.title }}</span>
            </router-link>
          </div>
        </transition>
      </div>
    </nav>

    <!-- 右侧操作区 -->
    <div class="navbar-actions">
      <!-- 刷新按钮 -->
      <el-tooltip content="刷新页面" placement="bottom">
        <el-button text :icon="Refresh" @click="handleRefresh" class="action-btn" />
      </el-tooltip>

      <!-- 主题切换按钮 -->
      <el-tooltip :content="isDarkMode ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
        <el-button 
          text 
          :icon="isDarkMode ? Sunny : Moon" 
          @click="toggleTheme" 
          class="action-btn" 
        />
      </el-tooltip>

      <!-- 通知图标 -->
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
        <el-tooltip content="通知中心" placement="bottom">
          <el-button text :icon="Bell" @click="showNotifications" class="action-btn" />
        </el-tooltip>
      </el-badge>

      <!-- 分隔线 -->
      <div class="action-divider"></div>

      <!-- 系统 AI 按钮 -->
      <el-tooltip content="系统 AI 助手" placement="bottom">
        <div class="system-ai-btn" @click="showSystemAI">
          <el-icon><DataAnalysis /></el-icon>
          <span>AI</span>
        </div>
      </el-tooltip>

      <!-- 用户下拉菜单 -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-info">
          <el-avatar :src="userInfo.avatar" :size="36">
            {{ userInfo.name?.charAt(0) }}
          </el-avatar>
          <span class="user-name">{{ userInfo.name }}</span>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
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
    @update:unreadCount="handleUnreadCountUpdate"
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
        <img src="/logo.png" alt="ORIN Logo" class="drawer-logo" />
        <span class="drawer-title">ORIN</span>
      </div>
    </template>
    
    <div class="mobile-menu-content">
      <div v-for="menu in visibleMenus" :key="menu.id" class="mobile-menu-group">
        <div class="mobile-menu-title" :style="{ color: menu.color }">
          <el-icon><component :is="getIconComponent(menu.icon)" /></el-icon>
          <span>{{ menu.title }}</span>
        </div>
        <router-link
          v-for="child in menu.children"
          :key="child.path"
          :to="child.path"
          class="mobile-menu-item"
          @click="closeMobileMenu"
        >
          {{ child.title }}
        </router-link>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDark } from '@vueuse/core'
import { ROUTES } from '@/router/routes'
import { TOP_MENU_CONFIG, getVisibleMenus, getActiveMenuId } from '@/router/topMenuConfig'
import NotificationCenter from './NotificationCenter.vue'
import {
  Bell, ArrowDown, User, Setting, Sunny, Moon, SwitchButton, Menu, Refresh, DataAnalysis,
  Box, Monitor, Collection, Setting as SettingIcon,
  List, ChatDotRound, Cpu, MagicStick, Connection,
  DataLine, TrendCharts, Share, Warning,
  Document, Picture, Histogram, Search, View, Grid,
  Notebook, Link, Coin
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Cookies from 'js-cookie'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// Icon mapping
const iconMap = {
  Box, Monitor, Collection, Setting: SettingIcon,
  List, ChatDotRound, Cpu, MagicStick, Connection,
  DataLine, TrendCharts, Share, Warning,
  Document, Picture, Histogram, Search, View, Grid,
  User, Notebook, Link, Coin
}

// State
const activeDropdown = ref(null)
const showMobileMenu = ref(false)
const showNotificationCenter = ref(false)
const unreadCount = ref(3) // 初始未读数量

// Dark mode logic (from original Navbar)
const isDarkMode = useDark({
  onChanged(dark) {
    if (dark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }
})

// Computed
const userInfo = computed(() => ({
  name: userStore.userInfo?.username || 'Admin',
  avatar: userStore.userInfo?.avatar || ''
}))

const isAdmin = computed(() => {
  // TODO: 从 userStore 获取真实权限
  return userStore.userInfo?.role === 'admin' || true
})

const visibleMenus = computed(() => {
  return getVisibleMenus(isAdmin.value)
})

const activeMenuId = computed(() => {
  return getActiveMenuId(route.path)
})

// Methods
const getIconComponent = (iconName) => {
  return iconMap[iconName] || Box
}

const goHome = () => {
  router.push(ROUTES.HOME)
}

const handleRefresh = () => {
  // 触发页面刷新事件
  window.dispatchEvent(new Event('page-refresh'))
  
  ElMessage({
    message: '正在刷新页面数据...',
    type: 'info',
    duration: 1500
  })
}

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value
}

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

const showSystemAI = () => {
  ElMessage.info('系统 AI 助手功能开发中')
}

const handleUnreadCountUpdate = (count) => {
  unreadCount.value = count
}

const handleUserCommand = (command) => {
  switch (command) {
    case 'profile':
      router.push(ROUTES.PROFILE)
      break
    case 'settings':
      // TODO: 创建设置页面后添加路由
      ElMessage.info('账号设置功能开发中')
      break
    case 'logout':
      handleLogout()
      break
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    Cookies.remove('token')
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

const toggleMobileMenu = () => {
  showMobileMenu.value = !showMobileMenu.value
}

const closeMobileMenu = () => {
  showMobileMenu.value = false
}

onMounted(() => {
  // TODO: 加载未读通知数量
})
</script>

<style scoped>
.top-navbar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  z-index: 1000;
  display: flex;
  align-items: center;
  padding: 0 24px;
}

/* Logo 区域 */
.navbar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  margin-right: 48px;
  transition: opacity 0.3s;
}

.navbar-logo:hover {
  opacity: 0.8;
}

.navbar-logo img {
  width: 32px;
  height: 32px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

/* 一级菜单 - 居中 */
.navbar-menu {
  display: flex;
  gap: 8px;
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  user-select: none;
}

.menu-item .el-icon {
  color: var(--neutral-gray-600);
  font-size: 18px;
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
}

.menu-item.active .el-icon,
.menu-item.active .menu-title {
  color: var(--orin-primary);
  font-weight: 600;
}

.menu-title {
  font-size: 14px;
  color: var(--neutral-gray-700);
  transition: all 0.3s;
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
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
  gap: 8px;
  margin-left: auto;
}

.action-btn {
  width: 36px;
  height: 36px;
  color: var(--neutral-gray-600);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.action-btn:hover {
  color: var(--orin-primary);
  background: var(--neutral-gray-50);
}

.notification-badge {
  display: inline-flex;
  align-items: center;
}

.notification-badge :deep(.el-badge__content) {
  transform: translateY(-50%) translateX(50%);
  right: 8px;
  top: 8px;
}

.action-divider {
  width: 1px;
  height: 20px;
  background: var(--neutral-gray-300);
  margin: 0 4px;
}

.system-ai-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  height: 36px;
  border-radius: 8px;
  background: var(--neutral-gray-50);
  border: 1px solid var(--neutral-gray-200);
  color: var(--orin-primary);
  cursor: pointer;
  transition: all 0.3s;
  font-size: 13px;
  font-weight: 600;
}

.system-ai-btn:hover {
  background: var(--orin-primary);
  color: white;
  border-color: var(--orin-primary);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(21, 94, 239, 0.2);
}

.system-ai-btn .el-icon {
  font-size: 16px;
}

.system-ai-btn span {
  font-size: 12px;
  letter-spacing: 0.5px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.user-info:hover {
  background: var(--neutral-gray-50);
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--neutral-gray-900);
  max-width: 120px;
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

.mobile-menu-item:hover {
  background: var(--neutral-gray-50);
  color: var(--orin-primary);
}

/* 响应式 */
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

/* 深色模式适配 */
html.dark .top-navbar {
  background: #1a1a1a;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

html.dark .logo-text {
  color: #ffffff;
}

html.dark .menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .menu-item.active {
  background: rgba(21, 94, 239, 0.2);
}

html.dark .menu-title {
  color: #e0e0e0;
}

html.dark .dropdown-menu {
  background: #2a2a2a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
}

html.dark .dropdown-item {
  color: #e0e0e0;
}

html.dark .dropdown-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

html.dark .user-info {
  background: rgba(255, 255, 255, 0.05);
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

html.dark .mobile-menu-item {
  color: #b0b0b0;
}

html.dark .action-divider {
  background: rgba(255, 255, 255, 0.2);
}

html.dark .system-ai-btn {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  color: #667eea;
}

html.dark .system-ai-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
}
</style>
