<template>
  <div class="page-container">
    <div class="tab-wrapper-card">
      <OrinEntityHeader
        v-if="!embedded"
        :title="selfService ? 'API Key 自助' : 'API 密钥'"
        :description="selfService ? '创建和管理你自己的平台访问密钥，用于调用 ORIN MCP 与开放网关能力' : '管理平台访问密钥、供应商凭据、调用额度与限流策略'"
        :domain="selfService ? '个人访问' : '组织权限'"
      >
        <template #actions>
          <el-button
            v-if="activeTab === 'platform'"
            type="success"
            :icon="Plus"
            @click="showCreateDialog"
          >
            创建平台密钥
          </el-button>
          <el-button
            v-else-if="!selfService"
            type="primary"
            :icon="Plus"
            @click="showExternalCreate"
          >
            添加供应商密钥
          </el-button>
        </template>
      </OrinEntityHeader>

      <div v-else class="embedded-access-toolbar">
        <div>
          <span class="command-eyebrow">访问凭据</span>
          <h3>{{ selfService ? 'API Key 自助' : 'API Key 与供应商凭据' }}</h3>
          <p>{{ selfService ? '管理你的个人平台访问密钥和调用历史。' : '管理调用方访问密钥、上游供应商凭据和配额状态。' }}</p>
        </div>
        <div class="embedded-access-actions">
          <el-button
            v-if="activeTab === 'platform'"
            type="success"
            :icon="Plus"
            @click="showCreateDialog"
          >
            创建平台密钥
          </el-button>
          <el-button
            v-else-if="!selfService"
            type="primary"
            :icon="Plus"
            @click="showExternalCreate"
          >
            添加供应商密钥
          </el-button>
        </div>
      </div>

      <OrinStatusSummary :items="apiKeyStatusItems" class="governance-summary" />

      <el-tabs v-model="activeTab" class="api-key-tabs">
        <el-tab-pane label="平台访问密钥" name="platform">
          <OrinDataTable class="table-card">
            <el-table
              v-loading="loading"
              border
              :data="apiKeys"
              style="width: 100%"
              stripe
            >
              <!-- ... existing platform table columns (truncated for brevity in ReplacementContent but will be kept in full file) ... -->
              <el-table-column type="expand">
                <template #default="{ row }">
                  <div class="expand-content">
                    <el-descriptions title="密钥详情" :column="2" border>
                      <el-descriptions-item label="密钥ID">
                        {{ row.id }}
                      </el-descriptions-item>
                      <el-descriptions-item label="密钥前缀">
                        {{ row.keyPrefix }}
                      </el-descriptions-item>
                      <el-descriptions-item label="创建时间">
                        {{ formatDateTime(row.createdAt) }}
                      </el-descriptions-item>
                      <el-descriptions-item label="最后使用">
                        {{ formatDateTime(row.lastUsedAt) || '从未使用' }}
                      </el-descriptions-item>
                      <el-descriptions-item label="过期时间">
                        {{ formatDateTime(row.expiresAt) || '永不过期' }}
                      </el-descriptions-item>
                      <el-descriptions-item label="状态">
                        <el-tag :type="getKeyStatusType(row)" size="small">
                          {{ getKeyStatusLabel(row) }}
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

              <el-table-column
                prop="name"
                label="名称"
                width="140"
                show-overflow-tooltip
              />

              <el-table-column prop="keyPrefix" label="密钥前缀" width="160">
                <template #default="{ row }">
                  <code class="key-prefix">{{ row.keyPrefix }}...</code>
                </template>
              </el-table-column>

              <el-table-column
                prop="enabled"
                label="状态"
                width="80"
                align="center"
              >
                <template #default="{ row }">
                  <el-tag :type="getKeyStatusType(row)" size="small">
                    {{ getKeyStatusLabel(row) }}
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

              <el-table-column
                prop="rateLimitPerMinute"
                label="限流"
                width="90"
                align="center"
              >
                <template #default="{ row }">
                  {{ row.rateLimitPerMinute }}/分
                </template>
              </el-table-column>

              <el-table-column label="操作" width="380" fixed="right">
                <template #default="{ row }">
                  <el-button 
                    size="small" 
                    :type="row.enabled ? 'warning' : 'success'" 
                    link
                    @click="toggleApiKey(row)"
                  >
                    {{ row.enabled ? '禁用' : '启用' }}
                  </el-button>
                  <el-button
                    v-if="!selfService && row.canRevealSecret"
                    size="small"
                    type="info"
                    link
                    :loading="revealLoadingKeys.has(row.id)"
                    @click="handleRevealSecret(row)"
                  >
                    查看明文
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    link
                    @click="handleRotate(row)"
                  >
                    轮换
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    link
                    @click="handleShowUsage(row)"
                  >
                    历史
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    link
                    @click="copyMcpConfig(row)"
                  >
                    配置
                  </el-button>
                  <el-button
                    v-if="!selfService"
                    size="small"
                    type="primary"
                    link
                    @click="handleResetQuota(row)"
                  >
                    重置配额
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    link
                    @click="handleDelete(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <OrinEmptyState
                  description="暂无平台访问密钥，请先创建受控调用凭据"
                  action-label="创建平台密钥"
                  @action="showCreateDialog"
                />
              </template>
            </el-table>
          </OrinDataTable>
        </el-tab-pane>

        <el-tab-pane v-if="!selfService" label="外部供应商密钥 (Credentials)" name="provider">
          <OrinDataTable class="table-card">
            <el-table
              v-loading="loading"
              border
              :data="externalKeys"
              style="width: 100%"
              stripe
            >
              <el-table-column prop="name" label="密钥名称" min-width="150" />
              <el-table-column prop="provider" label="供应商" width="150">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">
                    {{ row.provider }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="API密钥" min-width="200">
                <template #default="{ row }">
                  <code class="key-prefix">
                    {{ isKeyVisible(row.id) ? row.apiKey : maskKey(row.apiKey) }}
                  </code>
                  <el-button link :icon="isKeyVisible(row.id) ? Hide : View" @click="toggleKeyVisibility(row.id)" />
                </template>
              </el-table-column>
              <el-table-column
                prop="baseUrl"
                label="端点地址"
                min-width="180"
                show-overflow-tooltip
              />
              <el-table-column
                prop="enabled"
                label="状态"
                width="100"
                align="center"
              >
                <template #default="{ row }">
                  <el-switch v-model="row.enabled" @change="handleToggleExternal(row)" />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="150" fixed="right">
                <template #default="{ row }">
                  <el-button
                    size="small"
                    type="primary"
                    link
                    @click="handleEditExternal(row)"
                  >
                    编辑
                  </el-button>
                  <el-button
                    size="small"
                    type="danger"
                    link
                    @click="handleDeleteExternal(row)"
                  >
                    删除
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <OrinEmptyState
                  description="暂无供应商密钥，请添加上游模型服务凭据"
                  action-label="添加供应商密钥"
                  @action="showExternalCreate"
                />
              </template>
            </el-table>
          </OrinDataTable>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 已存在的创建密钥对话框 (平台) - Truncated for diff but preserved in file -->

    <!-- 新增外部供应商密钥对话框 -->
    <el-dialog 
      v-model="externalDialogVisible" 
      :title="externalFormData.id ? '编辑供应商密钥' : '添加供应商密钥'"
      width="550px"
    >
      <el-form
        ref="externalFormRef"
        :model="externalFormData"
        :rules="externalRules"
        label-position="top"
      >
        <el-form-item label="密钥名称" prop="name">
          <el-input v-model="externalFormData.name" placeholder="例如: 我的 OpenAI 主密钥" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应商" prop="provider">
              <el-select v-model="externalFormData.provider" style="width: 100%">
                <el-option
                  v-for="provider in providerList"
                  :key="provider.providerKey"
                  :label="provider.providerName"
                  :value="provider.providerKey"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Base URL (可选)">
              <el-input v-model="externalFormData.baseUrl" placeholder="默认官方地址" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Key" prop="apiKey">
          <el-input
            v-model="externalFormData.apiKey"
            type="password"
            show-password
            placeholder="sk-..."
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="externalFormData.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="externalDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="submitting" @click="handleSaveExternal">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 创建密钥对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      title="创建API密钥"
      width="600px"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="140px"
      >
        <el-form-item label="密钥名称" prop="name">
          <el-input v-model="formData.name" placeholder="为密钥取一个名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="密钥用途描述"
          />
        </el-form-item>
        <el-form-item v-if="!selfService" label="速率限制(分钟)">
          <el-input-number v-model="formData.rateLimitPerMinute" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item v-if="!selfService" label="速率限制(天)">
          <el-input-number v-model="formData.rateLimitPerDay" :min="1" :max="1000000" />
        </el-form-item>
        <el-form-item v-if="!selfService" label="月度Token配额">
          <el-input-number
            v-model="formData.monthlyTokenQuota"
            :min="1000"
            :max="100000000"
            :step="10000"
          />
        </el-form-item>
        <el-form-item v-else label="配额与限流">
          <span class="readonly-form-note">按平台默认策略生效</span>
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
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="submitting" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- 密钥创建成功对话框 -->
    <el-dialog 
      v-model="secretDialogVisible" 
      :title="secretDialogTitle"
      width="600px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      @closed="handleSecretDialogClosed"
    >
      <el-alert
        title="重要提示"
        type="warning"
        :description="secretDialogDescription"
        :closable="false"
        show-icon
      />
      <div class="secret-key-container">
        <div class="secret-key-label">
          API密钥:
        </div>
        <div class="secret-key-value">
          <code>{{ createdSecretKey }}</code>
          <el-button 
            type="primary" 
            size="small" 
            :icon="CopyDocument"
            @click="copyToClipboard(createdSecretKey)"
          >
            复制
          </el-button>
          <el-button
            size="small"
            :icon="CopyDocument"
            @click="copyMcpConfig({ secretKey: createdSecretKey })"
          >
            复制 MCP 配置
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretDialogVisible = false">
          我已处理
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="usageDialogVisible"
      :title="usageDialogTitle"
      width="860px"
    >
      <div v-loading="usageLoading" class="usage-dialog">
        <el-descriptions v-if="usageData" :column="3" border>
          <el-descriptions-item label="30天调用">
            {{ formatNumber(usageData.totalCalls) }}
          </el-descriptions-item>
          <el-descriptions-item label="失败率">
            {{ formatPercent(usageData.failureRate) }}
          </el-descriptions-item>
          <el-descriptions-item label="平均耗时">
            {{ formatLatency(usageData.averageLatencyMs) }}
          </el-descriptions-item>
          <el-descriptions-item label="成功">
            {{ formatNumber(usageData.successCalls) }}
          </el-descriptions-item>
          <el-descriptions-item label="失败">
            {{ formatNumber(usageData.failedCalls) }}
          </el-descriptions-item>
          <el-descriptions-item label="窗口 Token">
            {{ formatNumber(usageData.tokensInWindow) }}
          </el-descriptions-item>
        </el-descriptions>

        <el-table
          v-if="usageData"
          class="usage-events-table"
          :data="usageData.recentEvents || []"
          border
          stripe
          max-height="320"
        >
          <el-table-column prop="createdAt" label="时间" width="170">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="source" label="来源" width="90" />
          <el-table-column prop="method" label="方法" width="80" />
          <el-table-column prop="path" label="路径" min-width="190" show-overflow-tooltip />
          <el-table-column prop="statusCode" label="状态码" width="90" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">
              {{ formatLatency(row.latencyMs) }}
            </template>
          </el-table-column>
          <el-table-column prop="traceId" label="Trace ID" min-width="180" show-overflow-tooltip />
          <el-table-column prop="errorSummary" label="错误摘要" min-width="180" show-overflow-tooltip />
          <template #empty>
            <OrinEmptyState description="暂无调用历史" />
          </template>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="usageDialogVisible = false">
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue';
import { Plus, CopyDocument } from '@element-plus/icons-vue';
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue';
import OrinStatusSummary from '@/components/orin/OrinStatusSummary.vue';
import OrinDataTable from '@/components/orin/OrinDataTable.vue';
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getProviderList } from '@/api/system';
import {
  getAllApiKeys,
  createApiKey,
  enableApiKey,
  disableApiKey,
  deleteApiKey,
  resetQuota,
  rotateApiKey,
  getApiKeySecret,
  getApiKeyUsage,
  getExternalKeys,
  saveExternalKey,
  deleteExternalKey,
  toggleExternalKeyStatus
} from '@/api/apiKey';
import { View, Hide } from '@element-plus/icons-vue';

const props = defineProps({
  embedded: {
    type: Boolean,
    default: false
  },
  selfService: {
    type: Boolean,
    default: false
  }
});

const selfService = computed(() => props.selfService);

const activeTab = ref('platform');
const externalKeys = ref([]);
const providerList = ref([]);
const externalDialogVisible = ref(false);
const visibleKeys = ref(new Set());
const externalFormRef = ref(null);

const loading = ref(false);
const apiKeys = ref([]);
const dialogVisible = ref(false);
const secretDialogVisible = ref(false);
const secretDialogTitle = ref('密钥创建成功');
const secretDialogDescription = ref('请妥善保存此密钥,它只会显示一次!关闭此对话框后将无法再次查看。');
const submitting = ref(false);
const formRef = ref(null);
const createdSecretKey = ref('');
const revealLoadingKeys = ref(new Set());
const usageDialogVisible = ref(false);
const usageLoading = ref(false);
const usageData = ref(null);
const usageKey = ref(null);
let secretAutoHideTimer = null;

const usageDialogTitle = computed(() => {
  if (!usageKey.value) return 'API Key 调用历史';
  return `调用历史 - ${usageKey.value.name || usageKey.value.id}`;
});

const apiKeyStatusItems = computed(() => {
  const items = [
    {
      label: '平台密钥',
      value: apiKeys.value.length,
      meta: selfService.value ? '你创建的访问凭据' : '面向业务系统的访问凭据'
    },
    {
      label: '启用中',
      value: apiKeys.value.filter(key => key.enabled).length,
      meta: '当前可调用平台网关',
      intent: 'success'
    }
  ];

  if (!selfService.value) {
    items.push({
      label: '供应商凭据',
      value: externalKeys.value.length,
      meta: '上游模型服务访问凭据'
    });
  }

  items.push({
    label: '停用凭据',
    value: apiKeys.value.filter(key => !key.enabled).length
      + (selfService.value ? 0 : externalKeys.value.filter(key => !key.enabled).length),
    meta: '已从调用链路移除',
    intent: 'warning'
  });

  return items;
});

const formData = ref({
  name: '',
  description: '',
  rateLimitPerMinute: 100,
  rateLimitPerDay: 10000,
  monthlyTokenQuota: 1000000,
  expiresAt: null
});

const externalFormData = ref({
  id: null,
  name: '',
  provider: 'OpenAI',
  apiKey: '',
  baseUrl: '',
  description: '',
  enabled: true
});

const externalRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  apiKey: [{ required: true, message: '请输入 API Key', trigger: 'blur' }]
};

const rules = {
  name: [{ required: true, message: '请输入密钥名称', trigger: 'blur' }]
};

const fetchApiKeys = async () => {
  loading.value = true;
  try {
    const [platformRes, externalRes] = await Promise.all([
      getAllApiKeys(),
      selfService.value ? Promise.resolve([]) : getExternalKeys()
    ]);
    apiKeys.value = platformRes;
    externalKeys.value = externalRes;
  } catch (error) {
    ElMessage.error('获取密钥列表失败');
  } finally {
    loading.value = false;
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

// 获取供应商列表
const fetchProviders = async () => {
  if (selfService.value) return;
  try {
    const res = await getProviderList();
    providerList.value = res || [];
  } catch (e) {
    console.error('Failed to fetch providers:', e);
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

const buildCreatePayload = () => {
  if (selfService.value) {
    return {
      name: formData.value.name,
      description: formData.value.description,
      expiresAt: formData.value.expiresAt
    };
  }

  return { ...formData.value };
};

const handleCreate = async () => {
  const valid = await formRef.value.validate();
  if (!valid) return;

  submitting.value = true;
  try {
    const res = await createApiKey(buildCreatePayload());
    createdSecretKey.value = res.secretKey;
    secretDialogTitle.value = '密钥创建成功';
    secretDialogDescription.value = '请妥善保存此密钥,它只会显示一次!关闭此对话框后将无法再次查看。';
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

const handleRotate = async (row) => {
  try {
    await ElMessageBox.confirm(
      `轮换后旧密钥会立即失效。请确认已准备更新所有使用 ${row.name || row.id} 的客户端配置。`,
      '轮换平台密钥',
      {
        confirmButtonText: '确认轮换',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    const res = await rotateApiKey(row.id);
    createdSecretKey.value = res.secretKey;
    secretDialogTitle.value = '密钥轮换成功';
    secretDialogDescription.value = '旧密钥已失效。请立即更新客户端配置；新密钥关闭后不再显示。';
    secretDialogVisible.value = true;
    ElMessage.success('轮换成功');
    fetchApiKeys();
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('轮换失败');
    }
  }
};

const handleRevealSecret = async (row) => {
  try {
    await ElMessageBox.confirm(
      '该操作会短暂展示完整平台访问密钥，并写入审计日志。请确认当前环境安全，且仅用于受控运维。',
      '敏感操作二次确认',
      {
        confirmButtonText: '继续',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    const passwordPrompt = await ElMessageBox.prompt(
      '请输入当前登录密码以继续查看明文（本次操作会被审计记录）',
      '管理员密钥回显确认',
      {
        confirmButtonText: '确认查看',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPattern: /^.{1,}$/,
        inputErrorMessage: '请输入密码'
      }
    );
    revealLoadingKeys.value.add(row.id);
    const res = await getApiKeySecret(row.id, {
      currentPassword: passwordPrompt.value,
      confirmReveal: 'REVEAL_API_KEY'
    });
    createdSecretKey.value = res.secretKey;
    secretDialogTitle.value = '管理员密钥回显';
    secretDialogDescription.value = '该明文仅用于受控运维场景，30 秒后将自动隐藏。请勿截图传播或粘贴到不安全环境。';
    secretDialogVisible.value = true;
    if (secretAutoHideTimer) {
      clearTimeout(secretAutoHideTimer);
    }
    secretAutoHideTimer = setTimeout(() => {
      secretDialogVisible.value = false;
      ElMessage.info('密钥明文已自动隐藏');
    }, 30000);
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(
        error?.response?.status === 403
          ? '当前密码错误'
          : error?.response?.status === 404
            ? '该密钥为历史数据，不支持回显'
            : '回显失败'
      );
    }
  } finally {
    revealLoadingKeys.value.delete(row.id);
  }
};

const handleShowUsage = async (row) => {
  usageKey.value = row;
  usageData.value = null;
  usageDialogVisible.value = true;
  usageLoading.value = true;
  try {
    usageData.value = await getApiKeyUsage(row.id, { limit: 20 });
  } catch (error) {
    ElMessage.error('获取调用历史失败');
  } finally {
    usageLoading.value = false;
  }
};

const handleSecretDialogClosed = () => {
  if (secretAutoHideTimer) {
    clearTimeout(secretAutoHideTimer);
    secretAutoHideTimer = null;
  }
  createdSecretKey.value = '';
};

const copyToClipboard = (text) => {
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板');
  }).catch(() => {
    ElMessage.error('复制失败');
  });
};

const buildMcpConfig = (secretKey) => JSON.stringify({
  mcpServers: {
    orin: {
      url: `${window.location.origin.replace(/\/$/, '')}/v1/mcp`,
      headers: {
        Authorization: `Bearer ${secretKey}`
      }
    }
  }
}, null, 2);

const copyMcpConfig = (row) => {
  copyToClipboard(buildMcpConfig(row?.secretKey || '<paste-sk-orin-key-here>'));
};

const formatDateTime = (val) => {
  if (!val) return '';
  return new Date(val).toLocaleString();
};

const formatNumber = (num) => {
  return num ? num.toLocaleString() : 0;
};

const formatPercent = (num) => {
  const value = Number(num || 0);
  return `${value.toFixed(1)}%`;
};

const formatLatency = (num) => {
  if (num === null || num === undefined || Number.isNaN(Number(num))) return '-';
  return `${Math.round(Number(num))} ms`;
};

const getQuotaColor = (percentage) => {
  if (percentage >= 90) return 'var(--error-500)';
  if (percentage >= 70) return 'var(--warning-500)';
  return 'var(--success-500)';
};

const getKeyStatusLabel = (row) => {
  if (row.status === 'EXPIRED') return '过期';
  if (row.status === 'DISABLED' || !row.enabled) return '禁用';
  return '启用';
};

const getKeyStatusType = (row) => {
  if (row.status === 'EXPIRED') return 'danger';
  if (row.status === 'DISABLED' || !row.enabled) return 'info';
  return 'success';
};

// --- External Key Handlers ---

const showExternalCreate = () => {
  externalFormData.value = {
    id: null,
    name: '',
    provider: 'OpenAI',
    apiKey: '',
    baseUrl: '',
    description: '',
    enabled: true
  };
  externalDialogVisible.value = true;
};

const handleSaveExternal = async () => {
  const valid = await externalFormRef.value.validate();
  if (!valid) return;

  submitting.value = true;
  try {
    await saveExternalKey(externalFormData.value);
    ElMessage.success('保存成功');
    externalDialogVisible.value = false;
    fetchApiKeys();
  } catch (error) {
    ElMessage.error('保存失败');
  } finally {
    submitting.value = false;
  }
};

const handleEditExternal = (row) => {
  externalFormData.value = { ...row };
  externalDialogVisible.value = true;
};

const handleDeleteExternal = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此供应商密钥吗?', '警告', { type: 'warning' });
    await deleteExternalKey(row.id);
    ElMessage.success('已删除');
    fetchApiKeys();
  } catch (e) { /* cancel */ }
};

const handleToggleExternal = async (row) => {
  try {
    await toggleExternalKeyStatus(row.id);
    ElMessage.success('状态已更新');
  } catch (e) {
    row.enabled = !row.enabled; // rollback
  }
};

const maskKey = (key) => {
  if (!key) return '';
  if (key.length <= 8) return '********';
  return key.substring(0, 4) + '****************' + key.substring(key.length - 4);
};

const isKeyVisible = (id) => visibleKeys.value.has(id);

const toggleKeyVisibility = (id) => {
  if (visibleKeys.value.has(id)) {
    visibleKeys.value.delete(id);
  } else {
    visibleKeys.value.add(id);
  }
};

onMounted(() => {
  fetchApiKeys();
  fetchProviders();

  // 监听全局刷新事件
  window.addEventListener('global-refresh', fetchApiKeys);
  window.addEventListener('page-refresh', fetchApiKeys);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', fetchApiKeys);
  window.removeEventListener('page-refresh', fetchApiKeys);
  if (secretAutoHideTimer) {
    clearTimeout(secretAutoHideTimer);
    secretAutoHideTimer = null;
  }
});
</script>

<style scoped>
.page-container {
  padding: 0;
  max-width: 1800px;
}

.tab-wrapper-card,
.governance-summary {
  margin-bottom: 16px;
}

.embedded-access-toolbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
  margin-bottom: 16px;
}

.embedded-access-toolbar h3 {
  margin: 4px 0 6px;
  color: #172033;
  font-size: 18px;
  line-height: 1.3;
}

.embedded-access-toolbar p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.command-eyebrow {
  display: block;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.embedded-access-actions {
  flex: 0 0 auto;
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

.usage-dialog {
  min-height: 180px;
}

.usage-events-table {
  margin-top: 16px;
}

.readonly-form-note {
  color: #64748b;
  font-size: 13px;
}
</style>
