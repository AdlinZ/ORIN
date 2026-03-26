<template>
  <div class="page-container">
    <PageHeader
      title="端侧知识库同步"
      description="管理端侧客户端知识库同步，包括变更记录、检查点和 Webhook 配置"
      icon="Upload"
    />

    <el-tabs v-model="activeTab" class="sync-tabs">
      <!-- 变更记录 Tab -->
      <el-tab-pane label="变更记录" name="changes">
        <el-card class="sync-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>变更记录查询</span>
              <el-button :icon="Refresh" @click="loadChanges" :loading="changesLoading">刷新</el-button>
            </div>
          </template>

          <el-form :inline="true" class="filter-form">
            <el-form-item label="Agent ID">
              <el-input v-model="agentIdInput" placeholder="输入 Agent ID" clearable />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadChanges">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="changes" v-loading="changesLoading" stripe>
            <el-table-column prop="documentId" label="文档ID" width="200" show-overflow-tooltip />
            <el-table-column prop="knowledgeBaseId" label="知识库ID" width="150" show-overflow-tooltip />
            <el-table-column prop="changeType" label="变更类型" width="100">
              <template #default="{ row }">
                <el-tag :type="getChangeTypeTag(row.changeType)" size="small">
                  {{ row.changeType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="版本" width="80" />
            <el-table-column prop="contentHash" label="Hash" width="150" show-overflow-tooltip />
            <el-table-column prop="changedAt" label="变更时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.changedAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="synced" label="已同步" width="80">
              <template #default="{ row }">
                <el-tag :type="row.synced ? 'success' : 'warning'" size="small">
                  {{ row.synced ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="changesPage"
              v-model:page-size="changesSize"
              :total="changesTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadChanges"
              @current-change="loadChanges"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- 同步记录 Tab -->
      <el-tab-pane label="同步记录" name="records">
        <el-card class="sync-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>最近同步记录</span>
              <div>
                <el-button type="primary" size="small" @click="handleFullSync" :loading="syncing">
                  全量同步
                </el-button>
                <el-button type="success" size="small" @click="handleIncrementalSync" :loading="syncing">
                  增量同步
                </el-button>
              </div>
            </div>
          </template>

          <el-descriptions :column="3" border v-if="checkpointData">
            <el-descriptions-item label="Agent ID">{{ checkpointData.agentId }}</el-descriptions-item>
            <el-descriptions-item label="最新检查点">
              <el-tag type="success">{{ checkpointData.checkpoint || '暂无' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="待同步变更">
              <el-tag type="warning">{{ pendingCount }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <el-divider />

          <el-row :gutter="20" class="sync-stats">
            <el-col :span="8">
              <div class="stat-item">
                <span class="stat-label">新增</span>
                <span class="stat-value success">{{ stats.added || 0 }}</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <span class="stat-label">更新</span>
                <span class="stat-value warning">{{ stats.updated || 0 }}</span>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-item">
                <span class="stat-label">删除</span>
                <span class="stat-value danger">{{ stats.deleted || 0 }}</span>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-tab-pane>

      <!-- Webhook 配置 Tab -->
      <el-tab-pane label="Webhook 配置" name="webhooks">
        <el-card class="sync-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Webhook 配置</span>
              <el-button type="primary" size="small" @click="showWebhookDialog = true">添加</el-button>
            </div>
          </template>

          <el-table :data="webhooks" v-loading="webhooksLoading">
            <el-table-column prop="webhookUrl" label="URL" show-overflow-tooltip />
            <el-table-column prop="eventTypes" label="事件类型" width="200">
              <template #default="{ row }">
                <el-tag v-for="event in (row.eventTypes || '').split(',')" :key="event" size="small" class="event-tag">
                  {{ event }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.disabled ? 'danger' : (row.enabled ? 'success' : 'info')" size="small">
                  {{ row.disabled ? '已失效' : (row.enabled ? '启用' : '禁用') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="failureCount" label="失败次数" width="100">
              <template #default="{ row }">
                <span :class="{ 'text-danger': row.failureCount > 0 }">{{ row.failureCount || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="lastFailureTime" label="最后失败" width="150">
              <template #default="{ row }">
                {{ row.lastFailureTime ? formatDateTime(row.lastFailureTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button v-if="row.disabled" type="primary" size="small" text @click="handleReenableWebhook(row.id)">
                  重新启用
                </el-button>
                <el-button type="danger" size="small" text @click="handleDeleteWebhook(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- Webhook 添加对话框 -->
    <el-dialog v-model="showWebhookDialog" title="添加 Webhook" width="500px">
      <el-form :model="webhookForm" label-position="top">
        <el-form-item label="Webhook URL">
          <el-input v-model="webhookForm.webhookUrl" placeholder="https://example.com/webhook" />
        </el-form-item>
        <el-form-item label="密钥 (可选)">
          <el-input v-model="webhookForm.webhookSecret" type="password" placeholder="用于签名验证" />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-checkbox-group v-model="webhookForm.eventTypes">
            <el-checkbox label="document_added">文档新增</el-checkbox>
            <el-checkbox label="document_updated">文档更新</el-checkbox>
            <el-checkbox label="document_deleted">文档删除</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWebhookDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveWebhook">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import {
  getClientChanges,
  getClientCheckpoint,
  getPendingChangeCount,
  getClientWebhooks,
  saveClientWebhook,
  deleteClientWebhook,
  reenableClientWebhook,
  triggerFullSync,
  triggerIncrementalSync
} from '@/api/knowledge';

const activeTab = ref('changes');

// Changes state
const agentIdInput = ref('');
const changes = ref([]);
const changesLoading = ref(false);
const changesPage = ref(0);
const changesSize = ref(10);
const changesTotal = ref(0);

// Records state
const checkpointData = ref(null);
const pendingCount = ref(0);
const stats = ref({ added: 0, updated: 0, deleted: 0 });

// Webhooks state
const webhooks = ref([]);
const webhooksLoading = ref(false);
const showWebhookDialog = ref(false);
const webhookForm = ref({
  webhookUrl: '',
  webhookSecret: '',
  eventTypes: []
});

// Sync state
const syncing = ref(false);
const selectedAgentId = ref('');

const getChangeTypeTag = (type) => {
  const map = {
    'ADDED': 'success',
    'UPDATED': 'warning',
    'DELETED': 'danger'
  };
  return map[type] || 'info';
};

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return date.toLocaleString('zh-CN');
};

const loadChanges = async () => {
  if (!agentIdInput.value) {
    ElMessage.warning('请输入 Agent ID');
    return;
  }

  changesLoading.value = true;
  try {
    const res = await getClientChanges(agentIdInput.value, {
      page: changesPage.value,
      size: changesSize.value
    });
    const data = res.data || res;
    changes.value = data.content || [];
    changesTotal.value = data.totalElements || data.total || 0;
  } catch (e) {
    ElMessage.error('加载变更记录失败: ' + e.message);
  } finally {
    changesLoading.value = false;
  }
};

const loadCheckpoint = async () => {
  if (!agentIdInput.value) return;

  try {
    const res = await getClientCheckpoint(agentIdInput.value);
    checkpointData.value = res.data || res;

    const countRes = await getPendingChangeCount(agentIdInput.value);
    pendingCount.value = (countRes.data || countRes).pendingCount || 0;
  } catch (e) {
    console.error('加载检查点失败:', e);
  }
};

// 手动触发全量同步
const handleFullSync = async () => {
  if (!agentIdInput.value) {
    ElMessage.warning('请先输入 Agent ID');
    return;
  }
  syncing.value = true;
  try {
    const res = await triggerFullSync(agentIdInput.value);
    if (res.success || res.data?.success) {
      ElMessage.success('全量同步已触发');
    } else {
      ElMessage.error(res.message || '全量同步失败');
    }
  } catch (e) {
    ElMessage.error('全量同步失败: ' + e.message);
  } finally {
    syncing.value = false;
  }
};

// 手动触发增量同步
const handleIncrementalSync = async () => {
  if (!agentIdInput.value) {
    ElMessage.warning('请先输入 Agent ID');
    return;
  }
  syncing.value = true;
  try {
    const res = await triggerIncrementalSync(agentIdInput.value);
    if (res.success || res.data?.success) {
      ElMessage.success('增量同步已触发');
    } else {
      ElMessage.error(res.message || '增量同步失败');
    }
  } catch (e) {
    ElMessage.error('增量同步失败: ' + e.message);
  } finally {
    syncing.value = false;
  }
};

const loadWebhooks = async () => {
  if (!agentIdInput.value) return;

  webhooksLoading.value = true;
  try {
    const res = await getClientWebhooks(agentIdInput.value);
    webhooks.value = res.data || res || [];
  } catch (e) {
    ElMessage.error('加载 Webhooks 失败: ' + e.message);
  } finally {
    webhooksLoading.value = false;
  }
};

const handleSaveWebhook = async () => {
  if (!agentIdInput.value) {
    ElMessage.warning('请先输入 Agent ID');
    return;
  }

  if (!webhookForm.value.webhookUrl) {
    ElMessage.warning('请输入 Webhook URL');
    return;
  }

  try {
    await saveClientWebhook(agentIdInput.value, {
      webhookUrl: webhookForm.value.webhookUrl,
      webhookSecret: webhookForm.value.webhookSecret,
      eventTypes: webhookForm.value.eventTypes.join(','),
      enabled: true
    });
    ElMessage.success('Webhook 保存成功');
    showWebhookDialog.value = false;
    webhookForm.value = { webhookUrl: '', webhookSecret: '', eventTypes: [] };
    loadWebhooks();
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message);
  }
};

const handleDeleteWebhook = async (webhookId) => {
  try {
    await ElMessageBox.confirm('确定要删除此 Webhook 吗?', '确认删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    await deleteClientWebhook(webhookId);
    ElMessage.success('删除成功');
    loadWebhooks();
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + e.message);
    }
  }
};

const handleReenableWebhook = async (webhookId) => {
  try {
    await reenableClientWebhook(webhookId);
    ElMessage.success('Webhook 已重新启用');
    loadWebhooks();
  } catch (e) {
    ElMessage.error('启用失败: ' + e.message);
  }
};

// Watch tab changes
const handleTabChange = () => {
  if (activeTab.value === 'changes') {
    loadChanges();
  } else if (activeTab.value === 'records') {
    loadCheckpoint();
  } else if (activeTab.value === 'webhooks') {
    loadWebhooks();
  }
};

onMounted(() => {
  // Initialize with empty data
});
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.sync-tabs {
  padding: 0;
}

.sync-card {
  border-radius: var(--radius-xl) !important;
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.sync-stats {
  margin-top: 20px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.stat-label {
  display: block;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
}

.stat-value.success { color: var(--el-color-success); }
.stat-value.warning { color: var(--el-color-warning); }
.stat-value.danger { color: var(--el-color-danger); }

.event-tag {
  margin-right: 4px;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: 500;
}
</style>
