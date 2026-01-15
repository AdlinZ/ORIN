<template>
  <div class="page-container">
    <PageHeader 
      title="训练文件管理" 
      description="管理用于微调模型的本地数据集与语料库"
      icon="Files"
    >
      <template #actions>
        <el-button type="primary" :icon="Upload" @click="handleUpload">上传训练集</el-button>
      </template>
    </PageHeader>


    <el-row :gutter="24" style="margin-bottom: 24px;">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="label">{{ stat.label }}</div>
            <div class="value">{{ stat.value }}</div>
          </div>
          <el-icon class="stat-icon" :style="{ color: stat.color }"><component :is="stat.icon" /></el-icon>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-table :data="fileList" style="width: 100%" v-loading="loading">
        <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div style="display: flex; align-items: center; gap: 10px;">
              <el-icon style="font-size: 18px; color: var(--primary-color)"><Document /></el-icon>
              <span style="font-weight: 500;">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="size" label="大小" width="120" align="center" />
        <el-table-column prop="records" label="记录条数" width="120" align="center" />
        <el-table-column prop="createTime" label="上传时间" width="180" align="center" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary">预览</el-button>
            <el-button link type="primary">校验</el-button>
            <el-button link type="danger">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { Upload, Document, Files, DataLine, PieChart } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage } from 'element-plus';

const loading = ref(false);

const stats = [
  { label: '文件总数', value: '12', icon: Files, color: '#6366f1' },
  { label: '总记录数', value: '15.4k', icon: DataLine, color: '#10b981' },
  { label: '总存储占用', value: '256 MB', icon: PieChart, color: '#f59e0b' }
];

const fileList = ref([
  { name: 'customer_support_qa_v1.jsonl', type: 'JSONL', size: '4.2 MB', records: '2,500', createTime: '2024-03-20 14:20' },
  { name: 'legal_docs_corpus.txt', type: 'TXT', size: '128 MB', records: '-', createTime: '2024-03-18 09:15' },
  { name: 'code_assistant_v2.json', type: 'JSON', size: '15 MB', records: '8,400', createTime: '2024-03-15 11:45' }
]);

const handleUpload = () => {
  ElMessage.info('上传功能集成中...');
};
</script>

<style scoped>
.page-container {
  padding: 0;
}
.table-card {
  border-radius: var(--radius-lg) !important;
}

.stat-card {
  border-radius: var(--radius-lg) !important;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px;
}

.stat-content .label {
  font-size: 13px;
  color: var(--neutral-gray-4);
  margin-bottom: 4px;
}

.stat-content .value {
  font-size: 22px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  font-family: var(--font-heading);
}

.stat-icon {
  font-size: 32px;
  opacity: 0.8;
}
</style>
