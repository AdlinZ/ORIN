<template>
  <div class="page-container">
    <PageHeader 
      title="API密钥管理" 
      description="管理API访问密钥,控制访问权限和配额"
      icon="Key"
    >
      <template #actions>
        <el-button @click="showCreateDialog" type="success" :icon="Plus">创建密钥</el-button>
      </template>
    </PageHeader>

    <!-- API密钥列表 -->
    <el-card shadow="never" class="table-card premium-card">
      <el-table :data="apiKeys" style="width: 100%" v-loading="loading" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-descriptions title="密钥详情" :column="2" border>
                <el-descriptions-item label="密钥ID">{{ row.id }}</el-descriptions-item>
                <el-descriptions-item label="密钥前缀">{{ row.keyPrefix }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDateTime(row.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="最后使用">{{ formatDateTime(row.lastUsedAt) || '从未使用' }}</el-descriptions-item>
                <el-descriptions-item label="过期时间">{{ formatDateTime(row.expiresAt) || '永不过期' }}</el-descriptions-item>
                <el-descriptions-item label="状态">
                  <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                    {{ row.enabled ? '启用' : '禁用' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="速率限制">
                  {{ row.rateLimitPerMinute }}/分钟, {{ row.rateLimitPerDay }}/天
                </el-descriptions-item>
                <el-descriptions-item label="Token配额">
                  {{ formatNumber(row.usedTokens) }} / {{ formatNumber(row.monthlyTokenQuota) }}
                  ({{ row.quotaPercentage.toFixed(1) }}%)
                </el-descriptions-item>
                <el-descriptions-item label="描述" :span="2">
                  {{ row.description || '无描述' }}
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="名称" width="140" show-overflow-tooltip />

        <el-table-column prop="keyPrefix" label="密钥前缀" width="160">
          <template #default="{ row }">
            <code class="key-prefix">{{ row.keyPrefix }}...</code>
          </template>
        </el-table-column>

        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="配额使用" min-width="180">
          <template #default="{ row }">
            <div class="quota-bar">
              <el-progress 
                :percentage="row.quotaPercentage" 
                :color="getQuotaColor(row.quotaPercentage)"
                :stroke-width="8"
              />
              <div class="quota-text">
                {{ formatNumber(row.usedTokens) }} / {{ formatNumber(row.monthlyTokenQuota) }}
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="rateLimitPerMinute" label="限流" width="90" align="center">
          <template #default="{ row }">
            {{ row.rateLimitPerMinute }}/分
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button 
              size="small" 
              :type="row.enabled ? 'warning' : 'success'" 
              @click="toggleApiKey(row)"
              link
            >
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" @click="handleResetQuota(row)" link>
              重置配额
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)" link>删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建密钥对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      title="创建API密钥"
      width="600px"
    >
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="140px">
        <el-form-item label="密钥名称" prop="name">
          <el-input v-model="formData.name" placeholder="为密钥取一个名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="密钥用途描述" />
        </el-form-item>
        <el-form-item label="速率限制(分钟)">
          <el-input-number v-model="formData.rateLimitPerMinute" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="速率限制(天)">
          <el-input-number v-model="formData.rateLimitPerDay" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item label="月度Token配额">
          <el-input-number v-model="formData.monthlyTokenQuota" :min="1000" :max="100000000" :step="10000" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker
            v-model="formData.expiresAt"
            type="datetime"
            placeholder="选择过期时间(可选)"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreate" :loading="submitting">创建</el-button>
      </template>
    </el-dialog>

    <!-- 密钥创建成功对话框 -->
    <el-dialog 
      v-model="secretDialogVisible" 
      title="密钥创建成功"
      width="600px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <el-alert
        title="重要提示"
        type="warning"
        description="请妥善保存此密钥,它只会显示一次!关闭此对话框后将无法再次查看。"
        :closable="false"
        show-icon
      />
      <div class="secret-key-container">
        <div class="secret-key-label">API密钥:</div>
        <div class="secret-key-value">
          <code>{{ createdSecretKey }}</code>
          <el-button 
            type="primary" 
            size="small" 
            @click="copyToClipboard(createdSecretKey)"
            :icon="CopyDocument"
          >
            复制
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretDialogVisible = false">我已保存密钥</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { Plus, CopyDocument } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  getAllApiKeys,
  createApiKey,
  enableApiKey,
  disableApiKey,
  deleteApiKey,
  resetQuota
} from '@/api/apiKey';

const loading = ref(false);
const apiKeys = ref([]);
const dialogVisible = ref(false);
const secretDialogVisible = ref(false);
const submitting = ref(false);
const formRef = ref(null);
const createdSecretKey = ref('');

const formData = ref({
  name: '',
  description: '',
  rateLimitPerMinute: 100,
  rateLimitPerDay: 10000,
  monthlyTokenQuota: 1000000,
  expiresAt: null
});

const rules = {
  name: [{ required: true, message: '请输入密钥名称', trigger: 'blur' }]
};

const fetchApiKeys = async () => {
  loading.value = true;
  try {
    const res = await getAllApiKeys();
    apiKeys.value = res;
  } catch (error) {
    ElMessage.error('获取API密钥列表失败');
  } finally {
    loading.value = false;
  }
};

const showCreateDialog = () => {
  formData.value = {
    name: '',
    description: '',
    rateLimitPerMinute: 100,
    rateLimitPerDay: 10000,
    monthlyTokenQuota: 1000000,
    expiresAt: null
  };
  dialogVisible.value = true;
};

const handleCreate = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  submitting.value = true;
  try {
    const res = await createApiKey(formData.value);
    createdSecretKey.value = res.secretKey;
    ElMessage.success('创建成功');
    dialogVisible.value = false;
    secretDialogVisible.value = true;
    fetchApiKeys();
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '创建失败');
  } finally {
    submitting.value = false;
  }
};

const toggleApiKey = async (row) => {
  try {
    if (row.enabled) {
      await disableApiKey(row.id);
      ElMessage.success('已禁用');
    } else {
      await enableApiKey(row.id);
      ElMessage.success('已启用');
    }
    fetchApiKeys();
  } catch (error) {
    ElMessage.error('操作失败');
  }
};

const handleResetQuota = async (row) => {
  try {
    await ElMessageBox.confirm('确定要重置此密钥的月度配额吗?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await resetQuota(row.id);
    ElMessage.success('配额已重置');
    fetchApiKeys();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重置失败');
    }
  }
};

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此API密钥吗?此操作不可恢复!', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
    await deleteApiKey(row.id);
    ElMessage.success('删除成功');
    fetchApiKeys();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板');
  }).catch(() => {
    ElMessage.error('复制失败');
  });
};

const formatDateTime = (val) => {
  if (!val) return '';
  return new Date(val).toLocaleString();
};

const formatNumber = (num) => {
  return num ? num.toLocaleString() : 0;
};

const getQuotaColor = (percentage) => {
  if (percentage >= 90) return '#f56c6c';
  if (percentage >= 70) return '#e6a23c';
  return '#67c23a';
};

onMounted(() => {
  fetchApiKeys();
  
  // 监听全局刷新事件
  window.addEventListener('global-refresh', fetchApiKeys);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', fetchApiKeys);
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.expand-content {
  padding: 20px;
  background: var(--neutral-gray-50);
}

.key-prefix {
  font-family: 'Monaco', 'Courier New', monospace;
  background: var(--neutral-gray-100);
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.quota-bar {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quota-text {
  font-size: 12px;
  color: var(--neutral-gray-600);
  text-align: center;
}

.secret-key-container {
  margin: 20px 0;
  padding: 20px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
}

.secret-key-label {
  font-weight: 600;
  margin-bottom: 10px;
  color: var(--neutral-gray-700);
}

.secret-key-value {
  display: flex;
  align-items: center;
  gap: 12px;
}

.secret-key-value code {
  flex: 1;
  padding: 12px;
  background: white;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 6px;
  font-family: 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  word-break: break-all;
}
</style>
