<template>
  <div class="page-container">
    <PageHeader 
      title="API端点管理" 
      description="管理对外开放的API端点,配置访问权限和速率限制"
      icon="Connection"
    >
      <template #actions>
        <el-button @click="fetchEndpoints" type="primary" :icon="Refresh">刷新</el-button>
        <el-button @click="initializeDefaults" :icon="MagicStick">初始化默认端点</el-button>
        <el-button @click="showCreateDialog" type="success" :icon="Plus">创建端点</el-button>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalEndpoints || 0 }}</div>
              <div class="stat-label">总端点数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
              <el-icon><Checked /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.enabledEndpoints || 0 }}</div>
              <div class="stat-label">已启用</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
              <el-icon><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ formatNumber(stats.totalCalls) }}</div>
              <div class="stat-label">总调用次数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: linear-gradient(135deg, #43e97b 0%, #38f9d7 100%);">
              <el-icon><TrendCharts /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.successRate ? stats.successRate.toFixed(1) : 0 }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- API端点列表 -->
    <el-card shadow="never" class="table-card premium-card">
      <el-table :data="endpoints" style="width: 100%" v-loading="loading" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-descriptions title="端点详情" :column="2" border>
                <el-descriptions-item label="端点ID">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDateTime(row.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="更新时间">{{ formatDateTime(row.updatedAt) }}</el-descriptions-item>
                <el-descriptions-item label="需要认证">
                  <el-tag :type="row.requireAuth ? 'success' : 'info'" size="small">
                    {{ row.requireAuth ? '是' : '否' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="权限要求">
                  {{ row.permissionRequired || '无' }}
                </el-descriptions-item>
                <el-descriptions-item label="速率限制">
                  {{ row.rateLimitPerMinute }}/分钟, {{ row.rateLimitPerHour }}/小时, {{ row.rateLimitPerDay }}/天
                </el-descriptions-item>
                <el-descriptions-item label="调用统计" :span="2">
                  总计: {{ row.totalCalls }}, 成功: {{ row.successCalls }}, 失败: {{ row.failedCalls }}, 
                  成功率: {{ row.successRate.toFixed(2) }}%, 平均响应: {{ row.avgResponseTimeMs }}ms
                </el-descriptions-item>
                <el-descriptions-item label="描述" :span="2">
                  {{ row.description || '无描述' }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="method" label="方法" width="80">
          <template #default="{ row }">
            <el-tag :type="getMethodType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="path" label="路径" min-width="180" show-overflow-tooltip />

        <el-table-column prop="name" label="名称" width="120" show-overflow-tooltip />

        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="totalCalls" label="调用" width="100" align="center" sortable>
          <template #default="{ row }">
            <span class="font-bold">{{ formatNumber(row.totalCalls) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="successRate" label="成功率" width="90" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="getSuccessRateType(row.successRate)" size="small">
              {{ row.successRate.toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button 
              size="small" 
              :type="row.enabled ? 'warning' : 'success'" 
              @click="toggleEndpoint(row)"
              link
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" @click="showEditDialog(row)" link>编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" link>删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="dialogMode === 'create' ? '创建API端点' : '编辑API端点'"
      width="600px"
    >
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="路径" prop="path">
          <el-input v-model="formData.path" placeholder="/v1/your-endpoint" />
        </el-form-item>
        <el-form-item label="HTTP方法" prop="method">
          <el-select v-model="formData.method" placeholder="选择HTTP方法">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="端点名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="端点描述" />
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="formData.enabled" />
        </el-form-item>
        <el-form-item label="需要认证">
          <el-switch v-model="formData.requireAuth" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="formData.permissionRequired" placeholder="如: chat, embedding" />
        </el-form-item>
        <el-form-item label="速率限制(分钟)">
          <el-input-number v-model="formData.rateLimitPerMinute" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="速率限制(小时)">
          <el-input-number v-model="formData.rateLimitPerHour" :min="1" :max="100000" />
        </el-form-item>
        <el-form-item label="速率限制(天)">
          <el-input-number v-model="formData.rateLimitPerDay" :min="1" :max="1000000" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { 
  Refresh, Plus, Connection, Checked, DataLine, TrendCharts, MagicStick 
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getAllEndpoints,
  createEndpoint,
  updateEndpoint,
  deleteEndpoint,
  toggleEndpoint as toggleEndpointApi,
  getEndpointStats,
  initializeDefaultEndpoints
} from '@/api/apiEndpoint';

const loading = ref(false);
const endpoints = ref([]);
const stats = ref({});
const dialogVisible = ref(false);
const dialogMode = ref('create');
const submitting = ref(false);
const formRef = ref(null);

const formData = ref({
  path: '',
  method: 'GET',
  name: '',
  description: '',
  enabled: true,
  requireAuth: true,
  permissionRequired: '',
  rateLimitPerMinute: 100,
  rateLimitPerHour: 5000,
  rateLimitPerDay: 100000
});

const rules = {
  path: [{ required: true, message: '请输入路径', trigger: 'blur' }],
  method: [{ required: true, message: '请选择HTTP方法', trigger: 'change' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
};

const fetchEndpoints = async () => {
  loading.value = true;
  try {
    const res = await getAllEndpoints();
    endpoints.value = res.data;
  } catch (error) {
    ElMessage.error('获取API端点列表失败');
  } finally {
    loading.value = false;
  }
};

const fetchStats = async () => {
  try {
    const res = await getEndpointStats();
    stats.value = res.data;
  } catch (error) {
    console.error('获取统计信息失败', error);
  }
};

const showCreateDialog = () => {
  dialogMode.value = 'create';
  formData.value = {
    path: '',
    method: 'GET',
    name: '',
    description: '',
    enabled: true,
    requireAuth: true,
    permissionRequired: '',
    rateLimitPerMinute: 100,
    rateLimitPerHour: 5000,
    rateLimitPerDay: 100000
  };
  dialogVisible.value = true;
};

const showEditDialog = (row) => {
  dialogMode.value = 'edit';
  formData.value = { ...row };
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  submitting.value = true;
  try {
    if (dialogMode.value === 'create') {
      await createEndpoint(formData.value);
      ElMessage.success('创建成功');
    } else {
      await updateEndpoint(formData.value.id, formData.value);
      ElMessage.success('更新成功');
    }
    dialogVisible.value = false;
    fetchEndpoints();
    fetchStats();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '操作失败');
  } finally {
    submitting.value = false;
  }
};

const toggleEndpoint = async (row) => {
  try {
    await toggleEndpointApi(row.id, !row.enabled);
    ElMessage.success(row.enabled ? '已禁用' : '已启用');
    fetchEndpoints();
    fetchStats();
  } catch (error) {
    ElMessage.error('操作失败');
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此API端点吗?', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await deleteEndpoint(row.id);
    ElMessage.success('删除成功');
    fetchEndpoints();
    fetchStats();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const initializeDefaults = async () => {
  try {
    await ElMessageBox.confirm('确定要初始化默认API端点吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    });
    await initializeDefaultEndpoints();
    ElMessage.success('初始化成功');
    fetchEndpoints();
    fetchStats();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('初始化失败');
    }
  }
};

const formatDateTime = (val) => {
  if (!val) return '-';
  return new Date(val).toLocaleString();
};

const formatNumber = (num) => {
  return num ? num.toLocaleString() : 0;
};

const getMethodType = (method) => {
  const types = {
    GET: 'success',
    POST: 'primary',
    PUT: 'warning',
    DELETE: 'danger',
    PATCH: 'info'
  };
  return types[method] || '';
};

const getSuccessRateType = (rate) => {
  if (rate >= 95) return 'success';
  if (rate >= 80) return 'warning';
  return 'danger';
};

const getLatencyType = (ms) => {
  if (ms > 1000) return 'danger';
  if (ms > 500) return 'warning';
  return 'success';
};

onMounted(() => {
  fetchEndpoints();
  fetchStats();
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: var(--radius-lg);
  border: none;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.expand-content {
  padding: 20px;
  background: var(--neutral-gray-50);
}

.font-bold {
  font-weight: 600;
}
</style>
