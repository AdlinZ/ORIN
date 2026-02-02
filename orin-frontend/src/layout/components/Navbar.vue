<template>
  <div class="header-container">
    <div class="left-panel">
      <el-breadcrumb separator="/" class="dynamic-breadcrumb">
        <el-breadcrumb-item :to="{ path: '/dashboard/monitor' }">智能看板</el-breadcrumb-item>
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index" :to="item.path">
          {{ item.meta.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="right-panel">
      <!-- Teleport target for page-specific actions -->
      <div id="navbar-actions" class="navbar-actions"></div>

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

        <el-tooltip content="刷新当前页面" placement="bottom">
          <el-icon class="action-icon" @click="handleRefresh">
            <Refresh />
          </el-icon>
        </el-tooltip>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAppStore } from '@/stores/app';
import { useDark, useFullscreen } from '@vueuse/core';
import { 
  Refresh, Moon, Sunny, FullScreen
} from '@element-plus/icons-vue';

const router = useRouter();
const route = useRoute();

const appStore = useAppStore();

const breadcrumbs = computed(() => {
  return route.matched.filter(item => 
    item.meta && 
    item.meta.title && 
    item.path !== '/dashboard' && 
    item.path !== '/dashboard/monitor'
  );
});

// Dark mode logic
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

// 刷新当前页面
const handleRefresh = () => {
  // 触发当前页面的刷新事件
  window.dispatchEvent(new Event('page-refresh'));
  
  ElMessage({
    message: '正在刷新页面数据...',
    type: 'info',
    duration: 1500
  });
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

.dynamic-breadcrumb {
  flex-shrink: 0;
}

.right-panel {
  display: flex;
  align-items: center;
  gap: 16px;
}

.navbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-items {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 16px;
  border-left: 1px solid var(--neutral-gray-100);
}

.action-icon {
  font-size: 20px;
  cursor: pointer;
  color: var(--neutral-gray-600);
  transition: all 0.3s;
}

.action-icon:hover {
  color: var(--primary-color);
  transform: translateY(-1px);
}

.refresh-icon.is-refreshing {
  color: var(--error-color);
  animation: pulse 1s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.7;
    transform: scale(1.1);
  }
}

/* Dark mode support */
html.dark .header-container {
  /* No specific override needed, base class uses var(--neutral-white) which flips */
}

html.dark .action-items {
  /* var(--neutral-gray-100) flips to dark */
}

html.dark .action-icon {
  /* var(--neutral-gray-600) flips to light-ish */
}

html.dark .action-icon:hover {
  color: var(--orin-primary);
}
</style>
