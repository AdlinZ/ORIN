<template>
  <div class="sidebar-container" :class="{ 'collapsed': appStore.isCollapse }">
    <!-- Logo Section -->
    <div class="logo-container">
      <div class="logo-box">
        <img src="/logo.png" alt="Logo" class="logo" />
      </div>
      <span class="title" v-show="!appStore.isCollapse">ORIN <span class="highlight">Monitor</span></span>
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
        <el-menu-item index="/dashboard/home">
          <el-icon><HomeFilled /></el-icon>
          <template #title>首页</template>
        </el-menu-item>

        <!-- 动态渲染四大模块 -->
        <template v-for="menu in visibleMenus" :key="menu.id">
          <el-sub-menu :index="menu.path" :class="`menu-${menu.id}`">
            <template #title>
              <el-icon :style="{ color: menu.color }">
                <component :is="getIconComponent(menu.icon)" />
              </el-icon>
              <span>{{ menu.title }}</span>
            </template>
            
            <!-- 二级菜单 -->
            <el-menu-item 
              v-for="child in menu.children" 
              :key="child.path" 
              :index="child.path"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </div>

    <!-- User Section (fixed at bottom) -->
    <div class="user-section">
      <el-dropdown trigger="click" @command="handleCommand" placement="top-start">
        <div class="user-wrapper">
          <el-avatar 
            :size="appStore.isCollapse ? 36 : 40" 
            :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
            class="user-avatar"
          />
          <div class="user-info" v-show="!appStore.isCollapse">
            <span class="user-name">{{ userInfo.name || '游客' }}</span>
            <span class="user-role" v-if="userInfo.role">{{ userInfo.role }}</span>
          </div>
        </div>
        <template #dropdown>
          <el-dropdown-menu class="user-dropdown">
            <el-dropdown-item command="profile" v-if="userInfo.name">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item command="settings" v-if="userInfo.name">
              <el-icon><Setting /></el-icon>账号设置
            </el-dropdown-item>
            <el-dropdown-item command="login" v-if="!userInfo.name">
              <el-icon><User /></el-icon>登录
            </el-dropdown-item>
            <el-dropdown-item divided command="logout" class="logout-item" v-if="userInfo.name">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- Floating Toggle Button -->
    <el-tooltip 
      :content="appStore.isCollapse ? '展开侧边栏' : '收起侧边栏'" 
      placement="right"
    >
      <div class="sidebar-toggle-btn" @click="appStore.toggleSidebar">
        <el-icon>
          <DArrowLeft v-if="!appStore.isCollapse" />
          <DArrowRight v-else />
        </el-icon>
      </div>
    </el-tooltip>
  </div>
</template>

<script setup>
import { computed, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import Cookies from 'js-cookie'
import { ElMessage } from 'element-plus'
import { ROUTES, SIDEBAR_MENU_CONFIG } from '@/router/routes'
import { 
  HomeFilled, User, SwitchButton, DArrowLeft, DArrowRight, Setting,
  Box, Monitor, Collection, Setting as SettingIcon
} from '@element-plus/icons-vue'

const appStore = useAppStore()
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const isAdmin = computed(() => userStore.isAdmin)

// 用户信息状态
const userInfo = reactive({
  name: '',
  role: '',
  avatar: ''
})

// 角色代码到显示名称的映射
const roleNameMap = {
  'ROLE_ADMIN': '管理员',
  'ROLE_USER': '用户'
}

// 可见菜单（根据权限过滤）
const visibleMenus = computed(() => {
  return SIDEBAR_MENU_CONFIG.filter(menu => {
    // 如果菜单需要管理员权限，检查用户是否是管理员
    if (menu.requiresAdmin) {
      return isAdmin.value
    }
    return true
  })
})

// 获取图标组件
const getIconComponent = (iconName) => {
  const iconMap = {
    'Box': Box,
    'Monitor': Monitor,
    'Collection': Collection,
    'Setting': SettingIcon
  }
  return iconMap[iconName] || Box
}

// 检查登录状态并更新用户信息
const checkLoginStatus = () => {
  const token = Cookies.get('orin_token')
  if (token) {
    if (userStore.userInfo) {
      userInfo.name = userStore.userInfo.username || userStore.userInfo.nickname || '用户'
      userInfo.avatar = userStore.userInfo.avatar || ''
      if (userStore.roles && userStore.roles.length > 0) {
        userInfo.role = roleNameMap[userStore.roles[0]] || userStore.roles[0]
      } else {
        userInfo.role = '用户'
      }
    } else {
      const storedUser = localStorage.getItem('orin_user')
      if (storedUser) {
        try {
          const user = JSON.parse(storedUser)
          userInfo.name = user.username || user.nickname || '用户'
          userInfo.avatar = user.avatar || ''
          const storedRoles = Cookies.get('orin_roles')
          if (storedRoles) {
            const roles = JSON.parse(storedRoles)
            userInfo.role = roleNameMap[roles[0]] || roles[0] || '用户'
          } else {
            userInfo.role = '用户'
          }
        } catch (e) {
          console.error('解析用户信息失败:', e)
          userInfo.name = '用户'
          userInfo.role = '用户'
        }
      } else {
        userInfo.name = '用户'
        userInfo.role = '用户'
      }
    }
  } else {
    userInfo.name = ''
    userInfo.role = ''
    userInfo.avatar = ''
  }
}

const handleLogout = () => {
  userStore.logout()
  localStorage.removeItem('orin_user')
  ElMessage.success('已安全退出登录')
  router.push('/login')
}

const handleCommand = (command) => {
  switch (command) {
    case 'logout':
      handleLogout()
      break
    case 'profile':
      router.push(ROUTES.PROFILE)
      break
    case 'settings':
      ElMessage.info('系统设置模块开发中')
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
  background-color: var(--neutral-white);
  border-right: 1px solid var(--neutral-gray-200);
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 1001;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.sidebar-container.collapsed {
  width: var(--sidebar-width-collapsed);
}

.logo-container {
  height: var(--header-height);
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--neutral-gray-100);
  flex-shrink: 0;
}

.logo-box {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo {
  width: 28px;
  height: 28px;
  filter: drop-shadow(0 0 8px var(--primary-glow));
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

.menu-wrapper::-webkit-scrollbar-thumb:hover {
  background: var(--neutral-gray-400);
}

.el-menu-vertical:not(.el-menu--collapse) {
  width: var(--sidebar-width);
}

.el-menu {
  border-right: none;
}

:deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin: 4px 0;
  color: var(--neutral-gray-5);
  transition: all 0.3s;
}

:deep(.el-menu-item.is-active) {
  color: var(--orin-primary) !important;
  background-color: var(--orin-primary-soft) !important;
  font-weight: 600;
  border-left: 3px solid var(--orin-primary);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: var(--orin-primary) !important;
}

:deep(.el-menu-item:hover) {
  background-color: var(--neutral-gray-1);
}

:deep(.el-sub-menu__title:hover) {
  background-color: var(--neutral-gray-1);
}

/* 一级菜单主题色 */
:deep(.menu-applications .el-sub-menu__title:hover) {
  background-color: rgba(21, 94, 239, 0.05);
}

:deep(.menu-runtime .el-sub-menu__title:hover) {
  background-color: rgba(16, 185, 129, 0.05);
}

:deep(.menu-resources .el-sub-menu__title:hover) {
  background-color: rgba(139, 92, 246, 0.05);
}

:deep(.menu-control .el-sub-menu__title:hover) {
  background-color: rgba(100, 116, 139, 0.05);
}

/* User Section - fixed at bottom */
.user-section {
  flex-shrink: 0;
  padding: 16px;
  border-top: 1px solid var(--neutral-gray-200);
  background-color: var(--neutral-white);
  transition: all 0.3s;
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

.user-avatar:hover {
  border-color: var(--orin-primary);
  box-shadow: 0 0 0 3px var(--orin-primary-soft);
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
  opacity: 1;
  transition: opacity 0.3s;
}

.sidebar-container.collapsed .user-info {
  opacity: 0;
  width: 0;
  overflow: hidden;
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
  white-space: nowrap;
}

/* Dropdown Menu Styling */
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
  transition: all 0.2s;
}

:deep(.el-dropdown-menu__item:hover) {
  background-color: var(--neutral-gray-50) !important;
}

.logout-item {
  color: var(--error-color) !important;
}

.logout-item:hover {
  background-color: #fff1f0 !important;
}

/* Floating Toggle Button */
.sidebar-toggle-btn {
  position: absolute;
  right: -12px;
  top: 50%;
  transform: translateY(-50%);
  width: 24px;
  height: 48px;
  background: var(--neutral-white);
  border: 1px solid var(--neutral-gray-200);
  border-radius: 0 8px 8px 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 0;
  transition: all 0.3s;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
  z-index: 10;
}

.sidebar-container:hover .sidebar-toggle-btn {
  opacity: 1;
}

.sidebar-toggle-btn:hover {
  background: var(--orin-primary-soft);
  border-color: var(--orin-primary);
}

.sidebar-toggle-btn .el-icon {
  font-size: 14px;
  color: var(--neutral-gray-600);
  transition: all 0.3s;
}

.sidebar-toggle-btn:hover .el-icon {
  color: var(--orin-primary);
}
</style>
