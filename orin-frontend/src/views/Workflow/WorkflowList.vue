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
        <el-button type="success" :icon="Connection" @click="handleCreateVisual">
          可视化编辑器
        </el-button>
        <el-button type="warning" :icon="Upload" @click="handleImportClick">
          导入 Dify DSL
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
              <span>{{ row.workflowName }}</span>
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

    <!-- Import Dialog -->
    <el-dialog
      v-model="importDialogVisible"
      title="导入 Dify 工作流"
      width="500px"
    >
      <el-form label-width="100px">
        <el-form-item label="工作流名称" required>
          <el-input v-model="importForm.name" placeholder="请输入工作流名称" />
        </el-form-item>
        <el-form-item label="DSL 文件" required>
          <el-upload
            class="upload-demo"
            action="#"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            accept=".yml,.yaml"
          >
            <template #trigger>
              <el-button type="primary">选择文件</el-button>
            </template>
            <template #tip>
              <div class="el-upload__tip">
                只能上传 yml/yaml 文件
              </div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="importing" @click="handleImportSubmit">
            导入
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { Plus, Search, VideoPlay, DataLine, Timer, Connection, Upload } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import dayjs from 'dayjs';
import { getWorkflows, importWorkflow, deleteWorkflow } from '@/api/workflow';

const router = useRouter();
const searchQuery = ref('');
const loading = ref(false);
const workflows = ref([]);

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getWorkflows();
    workflows.value = res || [];
  } catch (error) {
    console.error(error);
    ElMessage.error('加载工作流列表失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  fetchData();
});

const handleCreate = () => {
  router.push(ROUTES.APPLICATIONS.WORKFLOW_VISUAL);
};

const handleCreateVisual = () => {
  router.push(ROUTES.APPLICATIONS.WORKFLOW_VISUAL);
};

const handleEdit = (row) => {
  router.push(`${ROUTES.APPLICATIONS.WORKFLOW_VISUAL}/${row.id}`);
};

const handleRun = (row) => {
  ElMessage.success(`已触发测试运行: ${row.workflowName}`); // Updated property name
};

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除工作流 "${row.workflowName}" 吗？此操作不可撤销。`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    try {
      await deleteWorkflow(row.id);
      ElMessage.success('删除成功');
      fetchData(); // Refresh the list
    } catch (error) {
      console.error(error);
      ElMessage.error('删除失败');
    }
  }).catch(() => {
    // Cancelled
  });
};

// Import Logic
const importDialogVisible = ref(false);
const importing = ref(false);
const importForm = reactive({
  name: '',
  file: null
});

const handleImportClick = () => {
  importForm.name = '';
  importForm.file = null;
  importDialogVisible.value = true;
};

const handleFileChange = (file) => {
  importForm.file = file.raw;
  // Auto fill name if empty
  if (!importForm.name && file.name) {
    importForm.name = file.name.replace(/\.(yml|yaml)$/i, '');
  }
};

const handleFileRemove = () => {
  importForm.file = null;
};

const handleImportSubmit = async () => {
  if (!importForm.name) {
    ElMessage.warning('请输入工作流名称');
    return;
  }
  if (!importForm.file) {
    ElMessage.warning('请选择 DSL 文件');
    return;
  }

  importing.value = true;
  try {
    const formData = new FormData();
    formData.append('file', importForm.file);
    formData.append('name', importForm.name);
    
    await importWorkflow(formData);
    ElMessage.success('导入成功');
    importDialogVisible.value = false;
    fetchData(); // Refresh list
  } catch (error) {
    console.error(error);
    ElMessage.error('导入失败');
  } finally {
    importing.value = false;
  }
};

const formatTime = (time) => {
  if (!time) return '-';
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
