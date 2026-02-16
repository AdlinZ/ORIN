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
        <el-button text :icon="Refresh" @click="handleRefresh" circle class="action-btn" />
      </el-tooltip>

      <!-- 主题切换按钮 -->
      <el-tooltip :content="isDarkMode ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
        <el-button 
          text 
          :icon="isDarkMode ? Sunny : Moon" 
          @click="toggleTheme" 
          circle 
          class="action-btn" 
        />
      </el-tooltip>

      <!-- 通知图标 -->
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
        <el-button text :icon="Bell" @click="showNotifications" circle class="action-btn" />
      </el-badge>

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
  Bell, ArrowDown, User, Setting, Sunny, Moon, SwitchButton, Menu, Refresh,
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
  color: var(--neutral-gray-600);
  font-size: 18px;
}

.action-btn:hover {
  color: var(--orin-primary);
  background: var(--neutral-gray-50);
}

.notification-badge {
  cursor: pointer;
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
</style>
