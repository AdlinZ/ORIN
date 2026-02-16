<template>
  <div class="quick-access-widget">
    <div class="widget-header">
      <h3>快捷入口</h3>
    </div>
    
    <div class="shortcuts-grid">
      <router-link 
        v-for="shortcut in shortcuts" 
        :key="shortcut.path" 
        :to="shortcut.path"
        class="shortcut-card"
      >
        <div class="shortcut-icon" :style="{ background: shortcut.color + '15', color: shortcut.color }">
          <el-icon><component :is="shortcut.icon" /></el-icon>
        </div>
        <div class="shortcut-title">{{ shortcut.title }}</div>
        <div class="shortcut-desc">{{ shortcut.description }}</div>
      </router-link>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Box, Monitor, Collection, Setting } from '@element-plus/icons-vue'
import { ROUTES } from '@/router/routes'

const shortcuts = ref([
  {
    title: '应用',
    description: '管理智能体应用',
    path: ROUTES.APPLICATIONS.AGENTS,
    icon: Box,
    color: '#155eef'
  },
  {
    title: '运行',
    description: '监控运行状态',
    path: ROUTES.RUNTIME.OVERVIEW,
    icon: Monitor,
    color: '#10b981'
  },
  {
    title: '资源',
    description: '知识库与素材',
    path: ROUTES.RESOURCES.KNOWLEDGE,
    icon: Collection,
    color: '#8b5cf6'
  },
  {
    title: '控制',
    description: '系统设置管理',
    path: ROUTES.CONTROL.USERS,
    icon: Setting,
    color: '#64748b'
  }
])
</script>

<style scoped>
.quick-access-widget {
  padding: 24px;
  height: 100%;
}

.widget-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin: 0 0 20px 0;
}

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.shortcut-card {
  padding: 20px;
  background: var(--neutral-gray-50);
  border-radius: 12px;
  text-decoration: none;
  transition: all 0.3s;
  cursor: pointer;
}

.shortcut-card:hover {
  background: var(--neutral-gray-100);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.shortcut-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 12px;
}

.shortcut-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin-bottom: 4px;
}

.shortcut-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}
</style>
