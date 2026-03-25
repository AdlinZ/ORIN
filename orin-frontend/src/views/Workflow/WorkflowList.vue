<template>
  <div class="workflow-container">
    <PageHeader
      title="工作流管理"
      description="管理 Dify 工作流的导入导出与同步"
      icon="Connection"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          新建工作流
        </el-button>
        <el-button type="success" :icon="Connection" @click="handleCreateVisual">
          可视化编辑器
        </el-button>
        <el-button type="warning" :icon="Upload" @click="handleImportClick">
          导入 Dify DSL
        </el-button>
        <el-button type="info" :icon="Refresh" @click="handleSyncDify">
          同步 Dify
        </el-button>
      </template>

      <template #filters>
        <el-input
          v-model="searchQuery"
          placeholder="搜索工作流..."
          :prefix-icon="Search"
          clearable
          class="search-input"
        />
      </template>
    </PageHeader>

    <!-- Stats Cards -->
    <div class="stats-row">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">总工作流</span>
            <span class="value">{{ stats.total }}</span>
          </div>
          <el-icon class="icon primary"><Connection /></el-icon>
        </div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">已发布</span>
            <span class="value">{{ stats.published }}</span>
          </div>
          <el-icon class="icon success"><VideoPlay /></el-icon>
        </div>
      </el-card>

      <el-card shadow="hover" class="stat-card">
        <div class="stat-content">
          <div class="stat-info">
            <span class="label">草稿</span>
            <span class="value">{{ stats.draft }}</span>
          </div>
          <el-icon class="icon warning"><Edit /></el-icon>
        </div>
      </el-card>
    </div>

    <!-- Workflow List -->
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>工作流列表</span>
        </div>
      </template>

      <el-table border :data="filteredWorkflows" style="width: 100%" v-loading="loading">
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

        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编排</el-button>
            <el-button link type="primary" @click="handleRun(row)">测试</el-button>
            <el-button link type="success" @click="handleExport(row)">导出</el-button>
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

    <!-- Dify Sync Dialog -->
    <el-dialog
      v-model="syncDialogVisible"
      title="从 Dify 同步工作流"
      width="700px"
    >
      <el-form label-width="100px" class="sync-form">
        <el-form-item label="Dify 地址">
          <el-input v-model="syncForm.endpoint" placeholder="http://localhost:3000/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="syncForm.apiKey" placeholder="请输入 Dify API Key" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testingConnection" @click="handleTestConnection">
            测试连接
          </el-button>
          <el-button type="success" :loading="loadingDifyWorkflows" @click="handleFetchDifyWorkflows">
            获取工作流列表
          </el-button>
        </el-form-item>
      </el-form>

      <el-divider v-if="difyWorkflows.length > 0" />

      <div v-if="difyWorkflows.length > 0" class="dify-workflows">
        <h4>Dify 工作流列表</h4>
        <el-table :data="difyWorkflows" border style="width: 100%" max-height="300">
          <el-table-column prop="name" label="名称" min-width="150" />
          <el-table-column prop="mode" label="模式" width="100">
            <template #default="{ row }">
              <el-tag :type="row.mode === 'workflow' ? 'success' : 'info'" size="small">
                {{ row.mode }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'published' ? 'success' : 'warning'" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="handleImportDifyWorkflow(row)">
                导入
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="syncDialogVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { Plus, Search, VideoPlay, DataLine, Timer, Connection, Upload, Refresh, Edit } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import dayjs from 'dayjs';
import { getWorkflows, importWorkflow, deleteWorkflow, exportWorkflow, listDifyWorkflows, importFromDify, testDifyConnection } from '@/api/workflow';
import PageHeader from '@/components/PageHeader.vue';

const router = useRouter();
const searchQuery = ref('');
const loading = ref(false);
const workflows = ref([]);

// 计算统计数据
const stats = computed(() => {
  const list = workflows.value;
  const published = list.filter(w => w.status === 'PUBLISHED').length;
  const draft = list.filter(w => w.status !== 'PUBLISHED').length;
  return {
    total: list.length,
    published,
    draft
  };
});

const filteredWorkflows = computed(() => {
  if (!searchQuery.value) return workflows.value;

  const q = searchQuery.value.toLowerCase().trim();
  return workflows.value.filter((workflow) => {
    const name = (workflow.workflowName || '').toLowerCase();
    const desc = (workflow.description || '').toLowerCase();
    return name.includes(q) || desc.includes(q);
  });
});

// Dify Sync
const syncDialogVisible = ref(false);
const syncForm = reactive({
  endpoint: '',
  apiKey: ''
});
const difyWorkflows = ref([]);
const loadingDifyWorkflows = ref(false);
const testingConnection = ref(false);

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
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

onMounted(() => {
  fetchData();
  window.addEventListener('page-refresh', fetchData);
});

onUnmounted(() => {
  window.removeEventListener('page-refresh', fetchData);
});

const handleCreate = () => {
    router.push(ROUTES.AGENTS.WORKFLOWS);
};

const handleCreateVisual = () => {
  router.push('/dashboard/agents/workflows/visual');
};

const handleEdit = (row) => {
    router.push(`${ROUTES.AGENTS.WORKFLOWS}/${row.id}`);
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

  // Handle array format [2024, 10, 27, 10, 0, 0] often returned by Spring/Jackson
  if (Array.isArray(time)) {
    // dayjs can't handle [2024, 10, 27...] directly
    const [year, month, day, hour, minute] = time;
    return dayjs(new Date(year, month - 1, day, hour || 0, minute || 0)).format('YYYY-MM-DD HH:mm');
  }

  const d = dayjs(time);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm') : '-';
};

// Dify Sync Handlers
const handleSyncDify = () => {
  syncForm.endpoint = '';
  syncForm.apiKey = '';
  difyWorkflows.value = [];
  syncDialogVisible.value = true;
};

const handleTestConnection = async () => {
  if (!syncForm.apiKey) {
    ElMessage.warning('请输入 API Key');
    return;
  }

  testingConnection.value = true;
  try {
    const res = await testDifyConnection(syncForm.endpoint, syncForm.apiKey);
    if (res.success) {
      ElMessage.success('连接成功');
    } else {
      ElMessage.error(res.message || '连接失败');
    }
  } catch (error) {
    ElMessage.error('连接失败: ' + (error.message || '未知错误'));
  } finally {
    testingConnection.value = false;
  }
};

const handleFetchDifyWorkflows = async () => {
  if (!syncForm.apiKey) {
    ElMessage.warning('请输入 API Key');
    return;
  }

  loadingDifyWorkflows.value = true;
  try {
    const res = await listDifyWorkflows(syncForm.endpoint, syncForm.apiKey);
    if (res.data) {
      difyWorkflows.value = res.data;
      ElMessage.success(`获取到 ${res.data.length} 个工作流`);
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('获取工作流列表失败');
  } finally {
    loadingDifyWorkflows.value = false;
  }
};

const handleImportDifyWorkflow = async (difyWorkflow) => {
  try {
    const res = await importFromDify({
      endpoint: syncForm.endpoint,
      apiKey: syncForm.apiKey,
      workflowId: difyWorkflow.id,
      name: difyWorkflow.name
    });

    if (res.success || res.workflow) {
      ElMessage.success('导入成功');
      fetchData();
      syncDialogVisible.value = false;
    } else {
      ElMessage.error(res.error || '导入失败');
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('导入失败: ' + (error.message || '未知错误'));
  }
};

const handleExport = async (row) => {
  try {
    const res = await exportWorkflow(row.id);
    const blob = new Blob([res], { type: 'application/x-yaml' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${row.workflowName}.yaml`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
    ElMessage.success('导出成功');
  } catch (error) {
    console.error(error);
    ElMessage.error('导出失败');
  }
};
</script>

<style scoped>
.workflow-container {
  padding: 0 0 20px 0;
}

.search-input {
  width: 260px;
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

.icon.success { color: var(--success-color); background: var(--success-50); }
.icon.primary { color: var(--primary-color); background: var(--primary-50); }
.icon.warning { color: var(--warning-color); background: var(--warning-50); }

.table-card {
  margin-top: 5px;
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: flex-start;
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

.sync-form {
  margin-bottom: 20px;
}

.dify-workflows h4 {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: var(--neutral-gray-700);
}
</style>
