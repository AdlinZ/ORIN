<template>
  <div class="header-container">
    <div class="left-panel">
      <el-icon class="fold-btn" @click="appStore.toggleSidebar">
        <Fold v-if="!appStore.isCollapse" />
        <Expand v-else />
      </el-icon>
      
      <el-breadcrumb separator="/" class="dynamic-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index" :to="item.path">
          {{ item.meta.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="right-panel">
      <div class="action-items">
        <el-tooltip :content="isDark ? '切换亮色模式' : '切换暗黑模式'" placement="bottom">
          <el-icon class="action-icon" @click="toggleDarkMode">
            <Moon v-if="!isDark" />
            <Sunny v-else />
          </el-icon>
        </el-tooltip>

        <el-tooltip content="全屏切换" placement="bottom">
          <el-icon class="action-icon" @click="toggleFullScreen"><FullScreen /></el-icon>
        </el-tooltip>

        <el-tooltip content="刷新数据" placement="bottom">
          <el-icon class="action-icon" @click="handleRefresh"><Refresh /></el-icon>
        </el-tooltip>
      </div>

      <div class="user-control">
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="avatar-wrapper">
            <div class="user-info-text">
              <span class="user-name">{{ userInfo.name || '游客' }}</span>
              <span class="user-tag" v-if="userInfo.role">{{ userInfo.role }}</span>
            </div>
            <el-avatar 
              :size="36" 
              :src="userInfo.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" 
              class="user-avatar"
            />
            <el-icon class="caret-icon"><CaretBottom /></el-icon>
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
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import Cookies from 'js-cookie';
import { ElMessage } from 'element-plus';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';
import { useRoute } from 'vue-router';
import { useDark, useToggle, useFullscreen } from '@vueuse/core';
import { 
  Fold, Expand, Refresh, CaretBottom, Moon, 
  Sunny, FullScreen, User, Setting, SwitchButton 
} from '@element-plus/icons-vue';

const router = useRouter();

const appStore = useAppStore();
const userStore = useUserStore();
const route = useRoute();

// 用户信息状态
const userInfo = reactive({
  name: '',
  role: '',
  avatar: ''
});

// 角色代码到显示名称的映射
const roleNameMap = {
  'ROLE_ADMIN': '管理员',
  'ROLE_USER': '用户'
};

// 检查登录状态并更新用户信息
const checkLoginStatus = () => {
  const token = Cookies.get('orin_token');
  if (token) {
    // 优先从 userStore 获取用户信息
    if (userStore.userInfo) {
      userInfo.name = userStore.userInfo.username || userStore.userInfo.nickname || '用户';
      userInfo.avatar = userStore.userInfo.avatar || '';
      // 显示第一个角色的友好名称
      if (userStore.roles && userStore.roles.length > 0) {
        userInfo.role = roleNameMap[userStore.roles[0]] || userStore.roles[0];
      } else {
        userInfo.role = '用户';
      }
    } else {
      // 如果 userStore 没有数据，从 localStorage 获取
      const storedUser = localStorage.getItem('orin_user');
      if (storedUser) {
        try {
          const user = JSON.parse(storedUser);
          userInfo.name = user.username || user.nickname || '用户';
          userInfo.avatar = user.avatar || '';
          
          // 从 cookie 获取角色
          const storedRoles = Cookies.get('orin_roles');
          if (storedRoles) {
            const roles = JSON.parse(storedRoles);
            userInfo.role = roleNameMap[roles[0]] || roles[0] || '用户';
          } else {
            userInfo.role = '用户';
          }
        } catch (e) {
          console.error('解析用户信息失败:', e);
          userInfo.name = '用户';
          userInfo.role = '用户';
        }
      } else {
        userInfo.name = '用户';
        userInfo.role = '用户';
      }
    }
  } else {
    // 未登录状态
    userInfo.name = '';
    userInfo.role = '';
    userInfo.avatar = '';
  }
};

// 初始化时检查登录状态
onMounted(() => {
  checkLoginStatus();
});

// 监听路由变化，更新用户信息
router.afterEach(() => {
  checkLoginStatus();
});

const breadcrumbs = computed(() => {
  return route.matched.filter(item => item.meta && item.meta.title && item.path !== '/dashboard');
});

// Dark mode logic - explicit control
const isDark = useDark({
  onChanged(dark) {
    if (dark) {
      document.documentElement.classList.add('dark');
    } else {
      document.documentElement.classList.remove('dark');
    }
  }
});
const toggleDarkMode = () => {
  isDark.value = !isDark.value;
};

// Fullscreen logic
const { isFullscreen, toggle: toggleFullScreen } = useFullscreen();

const handleRefresh = () => {
  window.location.reload();
};

const handleLogout = () => {
  // 使用 userStore 的 logout 方法清除所有数据
  userStore.logout();
  // 清除 localStorage
  localStorage.removeItem('orin_user');
  ElMessage.success('已安全退出登录');
  router.push('/login');
};

const handleCommand = (command) => {
  switch (command) {
    case 'logout':
      handleLogout();
      break;
    case 'profile':
      router.push('/dashboard/profile');
      break;
    case 'settings':
      ElMessage.info('系统设置模块开发中');
      break;
    case 'login':
      router.push('/login');
      break;
  }
};
</script>

<style scoped>
.header-container {
  height: var(--header-height);
  background: var(--neutral-white);
  border-bottom: 1px solid var(--neutral-gray-2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.left-panel {
  display: flex;
  align-items: center;
}

.fold-btn {
  font-size: 20px;
  cursor: pointer;
  margin-right: 20px;
  color: var(--neutral-gray-5);
}

.right-panel {
  display: flex;
  align-items: center;
  gap: 24px;
}

.action-items {
  display: flex;
  align-items: center;
  border-right: 1px solid var(--neutral-gray-100);
  padding-right: 20px;
}

.action-icon {
  font-size: 20px;
  cursor: pointer;
  margin-left: 16px;
  color: var(--neutral-gray-600);
  transition: all 0.2s;
}

.action-icon:hover {
  color: var(--primary-color);
  transform: translateY(-1px);
}

.user-control {
  cursor: pointer;
}

.avatar-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.avatar-wrapper:hover {
  background: var(--neutral-gray-50);
}

.user-info-text {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.user-name {
  font-size: 14px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  line-height: 1.2;
}

.user-tag {
  font-size: 10px;
  font-weight: 800;
  color: var(--primary-color);
  background: var(--primary-light);
  padding: 0 4px;
  border-radius: 4px;
  margin-top: 2px;
}

.user-avatar {
  border: 2px solid white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

.caret-icon {
  font-size: 12px;
  color: var(--neutral-gray-400);
}

/* Dropdown Menu Styling */
.user-dropdown {
  padding: 8px !important;
  border-radius: 12px !important;
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
</style>
