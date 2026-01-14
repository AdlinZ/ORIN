<template>
  <div class="page-container">
    <PageHeader 
      title="训练检查点 (Checkpoints)" 
      description="管理并部署模型训练过程中的历史权重与适配器"
      icon="Collection"
    />


    <el-card shadow="never" class="table-card">
      <el-table :data="checkpointList" style="width: 100%" v-loading="loading">
        <el-table-column prop="version" label="版本标识" width="180">
          <template #default="{ row }">
            <span style="font-family: var(--font-heading); font-weight: 600;">{{ row.version }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="baseModel" label="基座模型" min-width="150" />
        <el-table-column prop="loss" label="训练 Loss" width="120" align="center">
          <template #default="{ row }">
            <el-tag type="success" effect="light">{{ row.loss }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="step" label="Steps" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'READY' ? 'success' : 'info'" effect="plain">
              {{ row.status === 'READY' ? '可用' : '损坏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Position">发布为应用</el-button>
            <el-button link type="primary" :icon="Download">下载</el-button>
            <el-button link type="danger" :icon="Delete">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { Position, Download, Delete, Collection } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';

const loading = ref(false);

const checkpointList = ref([
  { version: 'v1.0-lora-epoch1', baseModel: 'Llama3-8b', loss: '1.452', step: '850', status: 'READY' },
  { version: 'v1.0-lora-epoch2', baseModel: 'Llama3-8b', loss: '1.210', step: '1700', status: 'READY' },
  { version: 'v1.1-full-epoch3', baseModel: 'Qwen1.5-7b', loss: '0.892', step: '3200', status: 'READY' }
]);
</script>

<style scoped>
.page-container {
  padding: 0;
}
.table-card {
  border-radius: var(--border-radius-lg) !important;
}
</style>
