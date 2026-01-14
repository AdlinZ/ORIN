<template>
  <div class="sidebar-container" :class="{ 'collapsed': appStore.isCollapse }">
    <!-- Logo Section -->
    <div class="logo-container">
      <div class="logo-box">
        <img src="/vite.svg" alt="Logo" class="logo" />
      </div>
      <span class="title" v-show="!appStore.isCollapse">ORIN <span class="highlight">Monitor</span></span>
    </div>


    <!-- Menu -->
    <el-menu
      :default-active="activeMenu"
      :collapse="appStore.isCollapse"
      router
      unique-opened
      class="el-menu-vertical"
    >
      <el-menu-item index="/">
        <el-icon><HomeFilled /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <el-menu-item index="/dashboard/monitor">
        <el-icon><Monitor /></el-icon>
        <template #title>监控大屏</template>
      </el-menu-item>

      <el-sub-menu index="/dashboard/agent">
        <template #title>
          <el-icon><Cpu /></el-icon>
          <span>智能体管理</span>
        </template>
        <el-menu-item index="/dashboard/agent/list">智能体列表</el-menu-item>
        <el-menu-item index="/dashboard/agent/onboard">接入新 Agent</el-menu-item>
        <el-menu-item index="/dashboard/agent/logs">会话记录</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="/dashboard/knowledge">
        <template #title>
          <el-icon><Collection /></el-icon>
          <span>知识库管理</span>
        </template>
        <el-menu-item index="/dashboard/knowledge/list">知识库列表</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="/dashboard/model">
        <template #title>
          <el-icon><Operation /></el-icon>
          <span>模型管理</span>
        </template>
        <el-menu-item index="/dashboard/model/config">系统配置</el-menu-item>
        <el-menu-item index="/dashboard/model/list">模型列表</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="/dashboard/training">
        <template #title>
          <el-icon><Aim /></el-icon>
          <span>模型训练</span>
        </template>
        <el-menu-item index="/dashboard/training/files">训练文件管理</el-menu-item>
        <el-menu-item index="/dashboard/training/train">训练模型</el-menu-item>
        <el-menu-item index="/dashboard/training/checkpoints">检查点</el-menu-item>
      </el-sub-menu>

      <el-sub-menu index="/dashboard/system" v-if="isAdmin">
        <template #title>
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </template>
        <el-menu-item index="/dashboard/system/log-config">日志配置</el-menu-item>
        <el-menu-item index="/dashboard/system/audit-logs">审计日志</el-menu-item>
        <el-menu-item index="/dashboard/system/alerts">告警管理</el-menu-item>
        <el-menu-item index="/dashboard/system/api-management">API端点管理</el-menu-item>
        <el-menu-item index="/dashboard/system/api-keys">API密钥管理</el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { useAppStore } from '@/stores/app';
import { useUserStore } from '@/stores/user';
import { 
  Monitor, Cpu, Collection, Operation, HomeFilled, 
  Aim, FolderOpened, LocationInformation, ChatLineSquare, Setting
} from '@element-plus/icons-vue';

const appStore = useAppStore();
const userStore = useUserStore();
const route = useRoute();

const activeMenu = computed(() => route.path);
const isAdmin = computed(() => userStore.isAdmin);
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
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
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
}

.title .highlight {
  color: var(--primary-color);
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
}

:deep(.el-menu-item.is-active) {
  color: var(--primary-color) !important;
  background-color: var(--primary-light-1) !important;
  font-weight: 600;
  border-left: 3px solid var(--primary-color);
}

:deep(.el-menu-item:hover) {
  background-color: var(--neutral-gray-1);
}

:deep(.el-sub-menu__title:hover) {
  background-color: var(--neutral-gray-1);
}
</style>
