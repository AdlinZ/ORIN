<template>
  <div class="workflow-container">
    <div class="header-section">
      <div class="title-area">
        <h2 class="page-title">Working Flow 管理</h2>
        <p class="subtitle">可视化的智能体工作流编排</p>
      </div>
      <div class="actions">
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          新建工作流
        </el-button>
      </div>
    </div>

    <!-- Stats Cards -->
    <div class="stats-row">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">活跃工作流</span>
            <span class="value">12</span>
          </div>
          <el-icon class="icon success"><VideoPlay /></el-icon>
        </div>
      </el-card>
      
      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">总执行次数</span>
            <span class="value">1,284</span>
          </div>
          <el-icon class="icon primary"><DataLine /></el-icon>
        </div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">平均耗时</span>
            <span class="value">2.4s</span>
          </div>
          <el-icon class="icon warning"><Timer /></el-icon>
        </div>
      </el-card>
    </div>

    <!-- Workflow List -->
    <el-card class="list-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>工作流列表</span>
          <div class="filter-actions">
            <el-input
              v-model="searchQuery"
              placeholder="搜索工作流..."
              prefix-icon="Search"
              class="search-input"
            />
          </div>
        </div>
      </template>

      <el-table :data="workflows" style="width: 100%" v-loading="loading">
        <el-table-column prop="name" label="工作流名称" min-width="180">
          <template #default="{ row }">
            <div class="workflow-name">
              <el-icon class="flow-icon"><Connection /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="light" round>
              {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="updatedAt" label="最后更新" width="180">
           <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>

        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编排</el-button>
            <el-button link type="primary" @click="handleRun(row)">测试</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { Plus, Search, VideoPlay, DataLine, Timer, Connection } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import dayjs from 'dayjs';

const searchQuery = ref('');
const loading = ref(false);
const workflows = ref([
  // Mock data for demo
  {
    id: '1',
    name: '客服自动回复流',
    description: '自动分析用户意图并调用知识库回复，若未知则转人工',
    status: 'PUBLISHED',
    updatedAt: '2026-01-18T10:00:00',
  },
  {
    id: '2',
    name: '每日财报分析',
    description: '定时抓取财经新闻，生成摘要并发送邮件',
    status: 'DRAFT',
    updatedAt: '2026-01-19T09:30:00',
  }
]);

const handleCreate = () => {
  ElMessage.info('创建功能开发中...');
};

const handleEdit = (row) => {
  ElMessage.info(`正在打开 ${row.name} 的编排画布...`);
};

const handleRun = (row) => {
  ElMessage.success(`已触发测试运行: ${row.name}`);
};

const handleDelete = (row) => {
  ElMessage.warning('删除功能暂未开放');
};

const formatTime = (time) => {
  return dayjs(time).format('YYYY-MM-DD HH:mm');
};
</script>

<style scoped>
.workflow-container {
  padding: 0 0 20px 0;
}

.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin: 0 0 8px 0;
}

.subtitle {
  color: var(--neutral-gray-500);
  margin: 0;
  font-size: 14px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid var(--neutral-gray-200);
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-info .label {
  font-size: 14px;
  color: var(--neutral-gray-500);
  margin-bottom: 8px;
}

.stat-info .value {
  font-size: 24px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.icon {
  font-size: 40px;
  padding: 10px;
  border-radius: 10px;
  background: var(--neutral-gray-50);
}

.icon.success { color: var(--success); background: var(--success-bg); }
.icon.primary { color: var(--primary-color); background: var(--primary-light-1); }
.icon.warning { color: var(--warning); background: var(--warning-bg); }

.list-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.workflow-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.flow-icon {
  font-size: 18px;
  color: var(--primary-color);
}
</style>
