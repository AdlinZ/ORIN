<template>
  <div class="alert-manager" :class="{ embedded: !props.showHeader }">
    <PageHeader
      v-if="props.showHeader"
      title="异常告警"
      description="集中管理告警规则、历史记录与处理状态"
      icon="Bell"
    />
    <el-tabs v-model="activeTab" class="config-tabs" :class="{ 'single-tab': singleTabMode }">
      <!-- 告警规则 Tab -->
      <el-tab-pane v-if="showRulesTab" label="告警规则" name="rules">
        <el-card class="premium-card alert-work-panel">
          <template #header>
            <div class="alert-panel-head">
              <div class="alert-panel-title">
                <span class="panel-icon"><el-icon><Bell /></el-icon></span>
                <div>
                  <h2>告警规则配置</h2>
                  <p>维护触发条件、通知渠道和冷却策略</p>
                </div>
              </div>
              <el-button type="primary" :icon="Plus" @click="showCreateDialog">
                创建规则
              </el-button>
            </div>
          </template>

          <div class="alert-summary-grid">
            <div class="alert-summary-item">
              <span>规则总数</span>
              <strong>{{ rules.length }}</strong>
            </div>
            <div class="alert-summary-item">
              <span>已启用</span>
              <strong>{{ enabledRuleCount }}</strong>
            </div>
            <div class="alert-summary-item">
              <span>已停用</span>
              <strong>{{ disabledRuleCount }}</strong>
            </div>
            <div class="alert-summary-item">
              <span>通知渠道</span>
              <strong>{{ channelCount }}</strong>
            </div>
          </div>

          <el-table
            v-loading="loading"
            class="alert-table"
            border
            :data="rules"
            stripe
          >
            <el-table-column prop="ruleName" label="规则名称" min-width="150" />
            <el-table-column prop="ruleType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">
                  {{ getRuleTypeText(row.ruleType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="severity" label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notificationChannels" label="通知渠道" width="150">
              <template #default="{ row }">
                <el-tag
                  v-for="channel in row.notificationChannels?.split(',')"
                  :key="channel"
                  size="small"
                  class="mr-1"
                >
                  {{ channel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  @change="toggleRule(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :icon="View" @click="viewRule(row)">
                  查看
                </el-button>
                <el-button size="small" :icon="Notification" @click="testRule(row)">
                  测试
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  :icon="Delete"
                  @click="deleteRule(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 告警历史 Tab -->
      <el-tab-pane v-if="showHistoryTab" label="告警历史" name="history">
        <el-card class="premium-card alert-work-panel">
          <template #header>
            <div class="alert-panel-head">
              <div class="alert-panel-title">
                <span class="panel-icon"><el-icon><Clock /></el-icon></span>
                <div>
                  <h2>告警历史记录</h2>
                  <p>跟踪触发、抑制和解决状态</p>
                </div>
              </div>
            </div>
          </template>

          <div class="alert-summary-grid history-summary">
            <div class="alert-summary-item danger">
              <span>活跃告警</span>
              <strong>{{ stats.activeAlerts }}</strong>
            </div>
            <div class="alert-summary-item">
              <span>总告警数</span>
              <strong>{{ stats.totalAlerts }}</strong>
            </div>
            <div class="alert-summary-item">
              <span>当前页</span>
              <strong>{{ history.length }}</strong>
            </div>
          </div>

          <div class="history-tip">
            说明：历史会包含“规则触发告警”和“系统健康告警”；规则名称为空时按系统健康告警展示。
          </div>

          <el-table
            v-loading="loadingHistory"
            class="alert-table"
            border
            :data="history"
            stripe
          >
            <el-table-column label="规则名称" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                {{ historyRuleName(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="alertMessage" label="告警消息" min-width="200" />
            <el-table-column prop="severity" label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="agentId" label="智能体" width="150" />
            <el-table-column prop="triggeredAt" label="触发时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.triggeredAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  v-if="row.status === 'RESOLVED'"
                  type="success"
                  size="small"
                >
                  已解决
                </el-tag>
                <el-tag
                  v-else-if="row.status === 'SUPPRESSED'"
                  type="info"
                  size="small"
                >
                  已抑制
                </el-tag>
                <el-tag
                  v-else
                  type="warning"
                  size="small"
                >
                  待处理
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'TRIGGERED'"
                  size="small"
                  type="success"
                  @click="resolveAlert(row)"
                >
                  解决
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="totalHistory"
            layout="total, prev, pager, next"
            class="mt-4"
            @current-change="loadHistory"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建/编辑规则对话框 -->
    <el-dialog
      v-model="ruleDialog"
      :title="editingRule ? '编辑规则' : '创建规则'"
      width="600px"
    >
      <el-form :model="ruleForm" label-width="120px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" placeholder="例如: CPU 使用率过高" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" placeholder="选择类型">
            <el-option label="健康检查" value="HEALTH_CHECK" />
            <el-option label="性能监控" value="PERFORMANCE" />
            <el-option label="错误率" value="ERROR_RATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件表达式">
          <el-input
            v-model="ruleForm.conditionExpr"
            placeholder="例如: cpu_usage > 80"
          />
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="ruleForm.thresholdValue" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="ruleForm.severity">
            <el-option label="信息" value="INFO" />
            <el-option label="警告" value="WARNING" />
            <el-option label="错误" value="ERROR" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="selectedChannels">
            <el-checkbox label="EMAIL">
              邮件
            </el-checkbox>
            <el-checkbox label="DINGTALK">
              钉钉
            </el-checkbox>
            <el-checkbox label="WECHAT">
              企业微信
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="接收人列表">
          <el-input
            v-model="ruleForm.recipientList"
            type="textarea"
            :rows="2"
            placeholder="多个接收人用逗号分隔"
          />
        </el-form-item>
        <el-form-item label="冷却时间">
          <el-input-number v-model="ruleForm.cooldownMinutes" :min="1" :max="60" />
          <span class="ml-2">分钟</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveRule">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'
import {
  Bell, Plus, View, Notification, Delete, Clock
} from '@element-plus/icons-vue'

const props = defineProps({
  mode: {
    type: String,
    default: 'all' // all | rules | history
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  initialTab: {
    type: String,
    default: 'rules'
  }
})

const showRulesTab = computed(() => props.mode === 'all' || props.mode === 'rules')
const showHistoryTab = computed(() => props.mode === 'all' || props.mode === 'history')
const singleTabMode = computed(() => Number(showRulesTab.value) + Number(showHistoryTab.value) <= 1)
const activeTab = ref(props.initialTab)
const loading = ref(false)
const loadingHistory = ref(false)
const saving = ref(false)
const rules = ref([])
const history = ref([])
const stats = ref({
  totalRules: 0,
  enabledRules: 0,
  activeAlerts: 0,
  totalAlerts: 0
})

const ruleDialog = ref(false)
const editingRule = ref(null)
const selectedChannels = ref([])
const ruleForm = ref({
  ruleName: '',
  ruleType: 'PERFORMANCE',
  conditionExpr: '',
  thresholdValue: 80,
  severity: 'WARNING',
  notificationChannels: '',
  recipientList: '',
  cooldownMinutes: 5,
  enabled: true
})

const currentPage = ref(1)
const pageSize = ref(20)
const totalHistory = ref(0)

const enabledRuleCount = computed(() => rules.value.filter(rule => rule.enabled).length)
const disabledRuleCount = computed(() => Math.max(rules.value.length - enabledRuleCount.value, 0))
const channelCount = computed(() => {
  const channels = new Set()
  rules.value.forEach(rule => {
    rule.notificationChannels
      ?.split(',')
      .map(channel => channel.trim())
      .filter(Boolean)
      .forEach(channel => channels.add(channel))
  })
  return channels.size
})

const loadRules = async () => {
  loading.value = true
  try {
    const res = await request.get('/alerts/rules')
    rules.value = res || []
  } catch (error) {
    ElMessage.error('加载告警规则失败')
  } finally {
    loading.value = false
  }
}

const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const res = await request.get('/alerts/history', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    history.value = res.content || []
    totalHistory.value = res.totalElements || 0
  } catch (error) {
    ElMessage.error('加载告警历史失败')
  } finally {
    loadingHistory.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await request.get('/alerts/stats')
    stats.value = res || {}
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const showCreateDialog = () => {
  editingRule.value = null
  ruleForm.value = {
    ruleName: '',
    ruleType: 'PERFORMANCE',
    conditionExpr: '',
    thresholdValue: 80,
    severity: 'WARNING',
    notificationChannels: '',
    recipientList: '',
    cooldownMinutes: 5,
    enabled: true
  }
  selectedChannels.value = []
  ruleDialog.value = true
}

const viewRule = (rule) => {
  editingRule.value = rule
  ruleForm.value = { ...rule }
  selectedChannels.value = rule.notificationChannels?.split(',') || []
  ruleDialog.value = true
}

const saveRule = async () => {
  ruleForm.value.notificationChannels = selectedChannels.value.join(',')
  
  saving.value = true
  try {
    if (editingRule.value) {
      await request.put(`/alerts/rules/${editingRule.value.id}`, ruleForm.value)
      ElMessage.success('规则更新成功')
    } else {
      await request.post('/alerts/rules', ruleForm.value)
      ElMessage.success('规则创建成功')
    }
    ruleDialog.value = false
    await loadRules()
    await loadStats()
  } catch (error) {
    ElMessage.error('保存规则失败')
  } finally {
    saving.value = false
  }
}

const toggleRule = async (rule) => {
  try {
    await request.put(`/alerts/rules/${rule.id}`, rule)
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已禁用')
  } catch (error) {
    ElMessage.error('更新规则状态失败')
    rule.enabled = !rule.enabled
  }
}

const testRule = async (rule) => {
  try {
    await request.post(`/alerts/rules/${rule.id}/test`)
    ElMessage.success('测试通知已发送')
  } catch (error) {
    ElMessage.error('发送测试通知失败')
  }
}

const deleteRule = async (rule) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除规则 "${rule.ruleName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/alerts/rules/${rule.id}`)
    ElMessage.success('规则删除成功')
    await loadRules()
    await loadStats()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除规则失败')
    }
  }
}

const resolveAlert = async (alert) => {
  try {
    await request.post(`/alerts/history/${alert.id}/resolve`)
    ElMessage.success('告警已解决')
    await loadHistory()
    await loadStats()
  } catch (error) {
    ElMessage.error('解决告警失败')
  }
}

const getRuleTypeText = (type) => {
  const map = {
    'HEALTH_CHECK': '健康检查',
    'PERFORMANCE': '性能监控',
    'ERROR_RATE': '错误率',
    'COLLAB_HEALTH': '协作健康'
  }
  return map[type] || type
}

const getSeverityType = (severity) => {
  const map = {
    'INFO': 'info',
    'WARNING': 'warning',
    'ERROR': 'danger',
    'CRITICAL': 'danger'
  }
  return map[severity] || 'info'
}

const historyRuleName = (row) => {
  return row?.ruleName || row?.ruleId || '系统健康告警'
}

const formatTime = (time) => {
  if (!time) return ''
  // Handle Spring Boot LocalDateTime array format: [year, month, day, hour, minute, second, ns]
  if (Array.isArray(time)) {
    if (time.length >= 3) {
      const year = time[0]
      const month = String(time[1]).padStart(2, '0')
      const day = String(time[2]).padStart(2, '0')
      const hour = time.length >= 4 ? String(time[3]).padStart(2, '0') : '00'
      const minute = time.length >= 5 ? String(time[4]).padStart(2, '0') : '00'
      const second = time.length >= 6 ? String(time[5]).padStart(2, '0') : '00'
      return `${year}/${month}/${day} ${hour}:${minute}:${second}`
    }
  }
  return dayjs(time).format('YYYY/MM/DD HH:mm:ss')
}

watch(activeTab, (newTab) => {
  if (newTab === 'history') {
    loadHistory()
  }
})

watch(
  () => props.mode,
  (mode) => {
    if (mode === 'rules') activeTab.value = 'rules'
    if (mode === 'history') activeTab.value = 'history'
  },
  { immediate: true }
)

onMounted(() => {
  loadRules()
  loadStats()
  if (activeTab.value === 'history') {
    loadHistory()
  }
  window.addEventListener('page-refresh', handleRefresh)
})

const handleRefresh = () => {
  loadRules()
  loadStats()
  window.dispatchEvent(new Event('page-refresh-done'))
}

onUnmounted(() => {
  window.removeEventListener('page-refresh', handleRefresh)
})
</script>

<style scoped>
.alert-manager {
  padding: 20px;
}

.alert-manager.embedded {
  padding: 0;
}

.premium-card {
  overflow: hidden;
  border-radius: 16px;
  border: 1px solid rgba(203, 213, 225, 0.78);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
}

.alert-work-panel :deep(.el-card__header) {
  padding: 16px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.86);
  background: rgba(248, 250, 252, 0.72);
}

.alert-work-panel :deep(.el-card__body) {
  padding: 16px 18px 18px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.alert-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.alert-panel-title {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.panel-icon {
  width: 34px;
  height: 34px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 11px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--orin-primary, #0d9488);
}

.alert-panel-title h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  letter-spacing: 0;
  color: var(--text-primary, #0f172a);
}

.alert-panel-title p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.alert-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.alert-summary-grid.history-summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.alert-summary-item {
  min-width: 0;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(226, 232, 240, 0.86);
  background: rgba(248, 250, 252, 0.78);
}

.alert-summary-item span {
  display: block;
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.alert-summary-item strong {
  display: block;
  font-size: 24px;
  line-height: 1.1;
  color: var(--text-primary, #0f172a);
}

.alert-summary-item.danger strong {
  color: #dc2626;
}

.alert-table {
  border-radius: 12px;
  overflow: hidden;
}

.alert-table :deep(th.el-table__cell) {
  background: rgba(248, 250, 252, 0.92);
  color: var(--text-secondary, #64748b);
  font-weight: 700;
}

.ml-2 {
  margin-left: 8px;
}

.ml-4 {
  margin-left: 16px;
}

.mr-1 {
  margin-right: 4px;
}

.mt-4 {
  margin-top: 16px;
}

.config-tabs.single-tab :deep(.el-tabs__header) {
  display: none;
}

.history-tip {
  margin-bottom: 12px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  color: #64748b;
  background: rgba(241, 245, 249, 0.75);
  border: 1px dashed #cbd5e1;
}

html.dark .premium-card {
  border-color: rgba(100, 116, 139, 0.46);
  background: rgba(30, 41, 59, 0.82);
  box-shadow: 0 14px 32px rgba(2, 6, 23, 0.32);
}

html.dark .alert-work-panel :deep(.el-card__header) {
  border-bottom-color: rgba(100, 116, 139, 0.42);
  background: rgba(15, 23, 42, 0.32);
}

html.dark .alert-panel-title h2,
html.dark .alert-summary-item strong {
  color: #f1f5f9;
}

html.dark .alert-panel-title p,
html.dark .alert-summary-item span {
  color: #cbd5e1;
}

html.dark .alert-summary-item,
html.dark .history-tip {
  border-color: rgba(100, 116, 139, 0.46);
  background: rgba(15, 23, 42, 0.38);
}

html.dark .alert-table :deep(th.el-table__cell) {
  background: rgba(15, 23, 42, 0.58);
  color: #cbd5e1;
}

@media (max-width: 900px) {
  .alert-summary-grid,
  .alert-summary-grid.history-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .alert-panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .alert-summary-grid,
  .alert-summary-grid.history-summary {
    grid-template-columns: 1fr;
  }
}
</style>
