<template>
  <div class="sync-container">
    <!-- Header -->
    <div class="sync-header">
      <div class="sync-header-title">
        <el-icon class="sync-header-icon"><Refresh /></el-icon>
        <div>
          <h2 class="sync-title">数据同步</h2>
          <p class="sync-desc">管理知识库端侧同步与 Dify 上游同步</p>
        </div>
      </div>

      <!-- Agent 选择器（仅端侧 tab 需要） -->
      <div v-if="activeTab !== 'dify'" class="agent-id-bar">
        <span class="agent-id-label">Agent</span>
        <el-select
          v-model="agentId"
          placeholder="选择 Agent"
          filterable
          class="agent-id-input"
          :loading="agentsLoading"
          @change="onAgentChange"
        >
          <el-option
            v-for="a in agentList"
            :key="a.agentId"
            :label="a.name || a.agentId"
            :value="a.agentId"
          >
            <span>{{ a.name || a.agentId }}</span>
            <span class="agent-option-id">{{ a.agentId }}</span>
          </el-option>
        </el-select>
      </div>
    </div>

    <!-- 无 Agent：引导态 -->
    <div v-if="!agentId" class="empty-guide">
      <el-empty
        description="请先选择一个 Agent 开始管理数据同步"
        :image-size="120"
      >
        <template #image>
          <el-icon class="empty-guide-icon"><Connection /></el-icon>
        </template>
      </el-empty>
    </div>

    <!-- 有 Agent ID：主内容 -->
    <template v-else>
      <!-- 状态栏 -->
      <div class="status-bar">
        <div class="status-item">
          <span class="status-label">当前 Agent</span>
          <el-tag type="info" size="small">{{ agentId }}</el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">最新检查点</span>
          <el-tag :type="checkpointData?.checkpoint ? 'success' : 'info'" size="small">
            {{ checkpointData?.checkpoint ? formatDateTime(checkpointData.checkpoint) : '暂无' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span class="status-label">待同步变更</span>
          <el-tag :type="pendingCount > 0 ? 'warning' : 'success'" size="small">
            {{ pendingCount }}
          </el-tag>
        </div>
        <el-button size="small" :icon="Refresh" circle :loading="statusLoading" @click="loadStatus" />
      </div>

      <!-- Tab 导航 -->
      <div class="sync-nav">
        <button
          v-for="tab in tabs"
          :key="tab.name"
          class="sync-nav-item"
          :class="{ active: activeTab === tab.name }"
          @click="switchTab(tab.name)"
        >
          <el-icon><component :is="tab.icon" /></el-icon>
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <!-- 变更记录 -->
      <div v-show="activeTab === 'changes'" class="tab-content">
        <div class="content-toolbar">
          <div class="toolbar-left">
            <el-button size="small" :icon="Refresh" :loading="changesLoading" @click="loadChanges">
              刷新
            </el-button>
          </div>
          <div class="toolbar-right">
            <el-button type="primary" size="small" :loading="syncing" @click="handleFullSync">
              全量同步
            </el-button>
            <el-button type="success" size="small" :loading="syncing" @click="handleIncrementalSync">
              增量同步
            </el-button>
          </div>
        </div>

        <el-table v-loading="changesLoading" :data="changes" stripe>
          <template #empty>
            <el-empty description="暂无变更记录" :image-size="80" />
          </template>
          <el-table-column prop="documentId" label="文档ID" width="200" show-overflow-tooltip />
          <el-table-column prop="knowledgeBaseId" label="知识库ID" width="150" show-overflow-tooltip />
          <el-table-column prop="changeType" label="变更类型" width="100">
            <template #default="{ row }">
              <el-tag :type="getChangeTypeTag(row.changeType)" size="small">{{ row.changeType }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="contentHash" label="Hash" width="150" show-overflow-tooltip />
          <el-table-column prop="changedAt" label="变更时间" width="180">
            <template #default="{ row }">{{ formatDateTime(row.changedAt) }}</template>
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
      </div>

      <!-- Webhook -->
      <div v-show="activeTab === 'webhooks'" class="tab-content">
        <div class="content-toolbar">
          <div class="toolbar-left" />
          <div class="toolbar-right">
            <el-button type="primary" size="small" @click="showWebhookDialog = true">
              添加 Webhook
            </el-button>
          </div>
        </div>

        <el-table v-loading="webhooksLoading" :data="webhooks">
          <template #empty>
            <el-empty description="暂无 Webhook 配置" :image-size="80" />
          </template>
          <el-table-column prop="webhookUrl" label="URL" show-overflow-tooltip />
          <el-table-column prop="eventTypes" label="事件类型" width="220">
            <template #default="{ row }">
              <el-tag
                v-for="event in (row.eventTypes || '').split(',').filter(Boolean)"
                :key="event"
                size="small"
                class="event-tag"
              >
                {{ event }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="enabled" label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.disabled ? 'danger' : row.enabled ? 'success' : 'info'" size="small">
                {{ row.disabled ? '已失效' : row.enabled ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failureCount" label="失败次数" width="90">
            <template #default="{ row }">
              <span :class="{ 'text-danger': row.failureCount > 0 }">{{ row.failureCount || 0 }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastFailureTime" label="最后失败" width="160">
            <template #default="{ row }">
              {{ row.lastFailureTime ? formatDateTime(row.lastFailureTime) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button v-if="row.disabled" type="primary" size="small" text @click="handleReenableWebhook(row.id)">
                重新启用
              </el-button>
              <el-button type="danger" size="small" text @click="handleDeleteWebhook(row.id)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Dify 同步 -->
      <div v-show="activeTab === 'dify'" class="tab-content">
        <div class="dify-layout">
          <!-- 左：配置 -->
          <div class="dify-config-card">
            <div class="section-title">Dify 连接配置</div>
            <el-form :model="difyConfig" label-width="90px" size="default">
              <el-form-item label="API 地址">
                <el-input v-model="difyConfig.apiUrl" placeholder="http://localhost:3000" />
              </el-form-item>
              <el-form-item label="API Key">
                <el-input v-model="difyConfig.apiKey" type="password" show-password placeholder="Dify App API Key" />
              </el-form-item>
              <el-form-item label="启用">
                <el-switch v-model="difyConfig.enabled" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="difySaving" @click="handleSaveDifyConfig">保存</el-button>
                <el-button :loading="difyTesting" @click="handleTestDifyConnection">测试连接</el-button>
              </el-form-item>
            </el-form>
          </div>

          <!-- 右：同步动作 + 概览 -->
          <div class="dify-action-card">
            <div class="section-title">同步动作</div>
            <div class="dify-actions">
              <el-button type="primary" :loading="difySyncing" style="width: 100%" @click="handleDifyFullSync">
                完整同步（知识库 + 工作流 + 应用）
              </el-button>
              <el-button type="default" :loading="difyWorkflowSyncing" style="width: 100%" @click="handleDifyWorkflowSync">
                仅同步工作流
              </el-button>
            </div>

            <el-alert
              v-if="difySyncResult"
              :title="difySyncResult.success ? '同步成功' : `同步失败: ${difySyncResult.message || '未知错误'}`"
              :type="difySyncResult.success ? 'success' : 'error'"
              show-icon
              closable
              class="sync-result"
              @close="difySyncResult = null"
            />

            <template v-if="difyOverview">
              <div class="section-title" style="margin-top: 24px">同步概览</div>
              <div class="overview-stats">
                <div class="overview-stat">
                  <span class="overview-num">{{ difyOverview.appsCount || 0 }}</span>
                  <span class="overview-label">应用</span>
                </div>
                <div class="overview-stat">
                  <span class="overview-num">{{ difyOverview.workflowsCount || 0 }}</span>
                  <span class="overview-label">工作流</span>
                </div>
                <div class="overview-stat">
                  <span class="overview-num">{{ difyOverview.datasetsCount || 0 }}</span>
                  <span class="overview-label">知识库</span>
                </div>
                <div class="overview-stat">
                  <span class="overview-num">{{ difyOverview.apiKeysCount || 0 }}</span>
                  <span class="overview-label">API Keys</span>
                </div>
              </div>
            </template>
            <div v-else class="overview-empty">
              <el-empty description="完整同步后可查看概览" :image-size="60" />
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>

  <!-- Webhook 对话框 -->
  <el-dialog v-model="showWebhookDialog" title="添加 Webhook" width="480px">
    <el-form :model="webhookForm" label-position="top">
      <el-form-item label="Webhook URL">
        <el-input v-model="webhookForm.webhookUrl" placeholder="https://example.com/webhook" />
      </el-form-item>
      <el-form-item label="密钥（可选）">
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
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Connection, List, Promotion, Link } from '@element-plus/icons-vue'
import {
  getClientChanges,
  getClientCheckpoint,
  getPendingChangeCount,
  getClientWebhooks,
  saveClientWebhook,
  deleteClientWebhook,
  reenableClientWebhook,
  triggerFullSync,
  triggerIncrementalSync,
  getDifySyncOverview,
  fullSyncDifyAll,
  syncDifyWorkflows
} from '@/api/knowledge'
import {
  getDifyConfig as getSystemDifyConfig,
  saveDifyConfig as saveSystemDifyConfig,
  testDifyConnection as testSystemDifyConnection
} from '@/api/integrations'
import { getAgentList } from '@/api/agent'

const route = useRoute()

const tabs = [
  { name: 'changes',  label: '变更记录', icon: List },
  { name: 'webhooks', label: 'Webhook',  icon: Link },
  { name: 'dify',     label: 'Dify 同步', icon: Promotion },
]

const activeTab = ref('changes')
const agentId = ref('')
const agentList = ref([])
const agentsLoading = ref(false)

// 状态栏
const checkpointData = ref(null)
const pendingCount = ref(0)
const statusLoading = ref(false)

// 变更记录
const changes = ref([])
const changesLoading = ref(false)
const changesPage = ref(1)
const changesSize = ref(10)
const changesTotal = ref(0)

// 同步
const syncing = ref(false)

// Webhook
const webhooks = ref([])
const webhooksLoading = ref(false)
const showWebhookDialog = ref(false)
const webhookForm = ref({ webhookUrl: '', webhookSecret: '', eventTypes: [] })

// Dify
const difyConfig = reactive({ apiUrl: '', apiKey: '', enabled: false })
const difySaving = ref(false)
const difyTesting = ref(false)
const difyOverview = ref(null)
const difyOverviewLoading = ref(false)
const difySyncing = ref(false)
const difyWorkflowSyncing = ref(false)
const difySyncResult = ref(null)

const loadAgentList = async () => {
  agentsLoading.value = true
  try {
    const res = await getAgentList()
    agentList.value = res.data || res || []
  } catch (e) {
    console.error('加载 Agent 列表失败:', e)
  } finally {
    agentsLoading.value = false
  }
}

const onAgentChange = () => {
  loadStatus()
  loadTabData(activeTab.value)
}

const switchTab = (name) => {
  activeTab.value = name
  loadTabData(name)
}

const loadTabData = (tab) => {
  if (tab === 'dify') {
    loadDifyConfig()
    loadDifyOverview()
    return
  }
  if (!agentId.value) return
  if (tab === 'changes') loadChanges()
  else if (tab === 'webhooks') loadWebhooks()
}

const loadStatus = async () => {
  if (!agentId.value) return
  statusLoading.value = true
  try {
    const [cpRes, countRes] = await Promise.all([
      getClientCheckpoint(agentId.value),
      getPendingChangeCount(agentId.value)
    ])
    checkpointData.value = cpRes.data || cpRes
    pendingCount.value = ((countRes.data || countRes).pendingCount) || 0
  } catch (e) {
    console.error('加载状态失败:', e)
  } finally {
    statusLoading.value = false
  }
}

const loadChanges = async () => {
  if (!agentId.value) return
  changesLoading.value = true
  try {
    const res = await getClientChanges(agentId.value, { page: changesPage.value - 1, size: changesSize.value })
    const data = res.data || res
    changes.value = data.content || []
    changesTotal.value = data.totalElements || data.total || 0
  } catch (e) {
    ElMessage.error('加载变更记录失败: ' + e.message)
  } finally {
    changesLoading.value = false
  }
}

const handleFullSync = async () => {
  syncing.value = true
  try {
    const res = await triggerFullSync(agentId.value)
    const data = res.data || res
    if (data.success) { ElMessage.success('全量同步已触发'); loadStatus() }
    else ElMessage.error(data.message || '全量同步失败')
  } catch (e) {
    ElMessage.error('全量同步失败: ' + e.message)
  } finally {
    syncing.value = false
  }
}

const handleIncrementalSync = async () => {
  syncing.value = true
  try {
    const res = await triggerIncrementalSync(agentId.value)
    const data = res.data || res
    if (data.success) { ElMessage.success('增量同步已触发'); loadStatus() }
    else ElMessage.error(data.message || '增量同步失败')
  } catch (e) {
    ElMessage.error('增量同步失败: ' + e.message)
  } finally {
    syncing.value = false
  }
}

const loadWebhooks = async () => {
  if (!agentId.value) return
  webhooksLoading.value = true
  try {
    const res = await getClientWebhooks(agentId.value)
    webhooks.value = res.data || res || []
  } catch (e) {
    ElMessage.error('加载 Webhooks 失败: ' + e.message)
  } finally {
    webhooksLoading.value = false
  }
}

const handleSaveWebhook = async () => {
  if (!webhookForm.value.webhookUrl) { ElMessage.warning('请输入 Webhook URL'); return }
  try {
    await saveClientWebhook(agentId.value, {
      ...webhookForm.value,
      eventTypes: webhookForm.value.eventTypes.join(','),
      enabled: true
    })
    ElMessage.success('Webhook 保存成功')
    showWebhookDialog.value = false
    webhookForm.value = { webhookUrl: '', webhookSecret: '', eventTypes: [] }
    loadWebhooks()
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

const handleDeleteWebhook = async (webhookId) => {
  try {
    await ElMessageBox.confirm('确定要删除此 Webhook 吗?', '确认删除', {
      confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    })
    await deleteClientWebhook(webhookId)
    ElMessage.success('删除成功')
    loadWebhooks()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + e.message)
  }
}

const handleReenableWebhook = async (webhookId) => {
  try {
    await reenableClientWebhook(webhookId)
    ElMessage.success('Webhook 已重新启用')
    loadWebhooks()
  } catch (e) {
    ElMessage.error('启用失败: ' + e.message)
  }
}

const loadDifyConfig = async () => {
  try {
    const res = await getSystemDifyConfig()
    const config = res.data || res || {}
    difyConfig.apiUrl = config.apiUrl || ''
    difyConfig.apiKey = config.apiKey || ''
    difyConfig.enabled = !!config.enabled
  } catch (e) {
    console.error('加载 Dify 配置失败:', e)
  }
}

const handleSaveDifyConfig = async () => {
  difySaving.value = true
  try {
    await saveSystemDifyConfig({ apiUrl: difyConfig.apiUrl, apiKey: difyConfig.apiKey, enabled: difyConfig.enabled })
    ElMessage.success('Dify 配置已保存')
  } catch (e) {
    ElMessage.error('保存 Dify 配置失败: ' + e.message)
  } finally {
    difySaving.value = false
  }
}

const handleTestDifyConnection = async () => {
  difyTesting.value = true
  try {
    const res = await testSystemDifyConnection()
    const data = res.data || res || {}
    data.success ? ElMessage.success(data.message || '连接成功') : ElMessage.error(data.message || '连接失败')
  } catch (e) {
    ElMessage.error('测试连接失败: ' + e.message)
  } finally {
    difyTesting.value = false
  }
}

const handleDifyFullSync = async () => {
  difySyncing.value = true
  difySyncResult.value = null
  try {
    const res = await fullSyncDifyAll()
    const data = res.data || res || {}
    difySyncResult.value = data
    if (data.success) {
      ElMessage.success('Dify 完整同步已完成')
      await loadDifyOverview()
    } else {
      ElMessage.error(data.message || 'Dify 完整同步失败')
    }
  } catch (e) {
    difySyncResult.value = { success: false, message: e.message }
    ElMessage.error('Dify 完整同步失败: ' + e.message)
  } finally {
    difySyncing.value = false
  }
}

const handleDifyWorkflowSync = async () => {
  difyWorkflowSyncing.value = true
  difySyncResult.value = null
  try {
    const res = await syncDifyWorkflows()
    const data = res.data || res || {}
    difySyncResult.value = data
    if (data.success) {
      ElMessage.success('工作流同步已完成')
      await loadDifyOverview()
    } else {
      ElMessage.error(data.message || '工作流同步失败')
    }
  } catch (e) {
    difySyncResult.value = { success: false, message: e.message }
    ElMessage.error('工作流同步失败: ' + e.message)
  } finally {
    difyWorkflowSyncing.value = false
  }
}

const loadDifyOverview = async () => {
  difyOverviewLoading.value = true
  try {
    const res = await getDifySyncOverview()
    difyOverview.value = res.data || res
  } catch (e) {
    console.error('加载 Dify 概览失败:', e)
  } finally {
    difyOverviewLoading.value = false
  }
}

const getChangeTypeTag = (type) => ({ ADDED: 'success', UPDATED: 'warning', DELETED: 'danger' }[type] || 'info')

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  const tab = route.query.tab
  if (typeof tab === 'string' && tabs.some(t => t.name === tab)) activeTab.value = tab
  loadAgentList()
  loadDifyConfig()
  loadDifyOverview()
})
</script>

<style scoped>
.sync-container {
  padding: 24px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* Header */
.sync-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.sync-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sync-header-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: var(--el-color-primary-light-9, #eff6ff);
  color: var(--el-color-primary, #2563eb);
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sync-title {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.sync-desc {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* Agent ID 输入栏 */
.agent-id-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.agent-id-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.agent-id-input {
  width: 240px;
}

.agent-option-id {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-left: 8px;
}

/* 引导空态 */
.empty-guide {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.empty-guide-icon {
  font-size: 64px;
  color: var(--el-color-info-light-5, #c0c4cc);
}

/* 状态栏 */
.status-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 10px 16px;
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* Tab 导航 */
.sync-nav {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 0;
}

.sync-nav-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.2s, border-color 0.2s;
  border-radius: 0;
}

.sync-nav-item:hover {
  color: var(--el-color-primary);
}

.sync-nav-item.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  font-weight: 500;
}

/* 内容区 */
.tab-content {
  animation: fadeIn 0.2s ease;
}

.content-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.event-tag {
  margin-right: 4px;
}

.text-danger {
  color: var(--el-color-danger);
  font-weight: 500;
}

/* Dify 布局 */
.dify-layout {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 20px;
  align-items: start;
}

.dify-config-card,
.dify-action-card {
  background: var(--el-fill-color-lighter, #fafafa);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.dify-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}

.sync-result {
  margin-top: 12px;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.overview-stat {
  text-align: center;
  padding: 16px 8px;
  background: var(--el-bg-color);
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
}

.overview-num {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.overview-label {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.overview-empty {
  padding: 20px 0;
}

@media (max-width: 900px) {
  .dify-layout {
    grid-template-columns: 1fr;
  }

  .sync-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
