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
            <el-table-column
              prop="conditionExpr"
              label="触发条件"
              min-width="180"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ getRuleConditionSummary(row) }}
              </template>
            </el-table-column>
            <el-table-column prop="severity" label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notificationChannels" label="通知渠道" min-width="150">
              <template #default="{ row }">
                <div class="channel-list">
                  <template v-if="getRuleChannels(row).length">
                    <el-tag
                      v-for="channel in getRuleChannels(row)"
                      :key="channel"
                      size="small"
                      effect="plain"
                    >
                      {{ getChannelText(channel) }}
                    </el-tag>
                  </template>
                  <el-tag v-else size="small" type="info" effect="plain">
                    未配置
                  </el-tag>
                </div>
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
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <div class="rule-actions">
                  <el-button size="small" :icon="View" @click="viewRule(row)">
                    编辑
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
                </div>
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
            说明：这里只展示由当前告警规则触发的记录；未配置规则时不会生成新的告警历史。
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
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
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

const router = useRouter()
const showRulesTab = computed(() => props.mode === 'all' || props.mode === 'rules')
const showHistoryTab = computed(() => props.mode === 'all' || props.mode === 'history')
const singleTabMode = computed(() => Number(showRulesTab.value) + Number(showHistoryTab.value) <= 1)
const activeTab = ref(props.initialTab)
const loading = ref(false)
const loadingHistory = ref(false)
const rules = ref([])
const history = ref([])
const stats = ref({
  totalRules: 0,
  enabledRules: 0,
  activeAlerts: 0,
  totalAlerts: 0
})

const formatRatio = value => Number(value || 0).toFixed(2)
const formatNumber = value => Number(value || 0).toString()
const percentText = value => `${Math.round(Number(value || 0) * 100)}%`
const normalizeTarget = value => String(value || '').trim().toUpperCase()

const dependencyOptions = [
  { label: '全部依赖', value: '' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Redis', value: 'REDIS' },
  { label: 'Milvus', value: 'MILVUS' }
]

const dependencyText = value => {
  const option = dependencyOptions.find(item => item.value === normalizeTarget(value))
  return option?.label || value || '全部依赖'
}

const providerText = value => value?.trim() || '全部 Provider'

const rulePresets = [
  {
    key: 'collab-overall-red',
    ruleType: 'COLLAB_HEALTH',
    name: '协作整体变红',
    description: '当协作健康总状态进入 RED 时触发。',
    sentence: '当协作整体状态为红色时触发。',
    defaultName: '多智能体协作健康告警',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [],
    conditionBuilder: () => 'overallLevel == RED',
    thresholdValueBuilder: () => 1,
    summaryBuilder: () => '协作整体状态为红色'
  },
  {
    key: 'collab-success-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '成功率低于阈值',
    description: '适合发现协作任务连续失败或空转。',
    sentence: '当协作成功率低于设置阈值时触发。',
    defaultName: '协作成功率过低',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [
      {
        key: 'threshold',
        label: '成功率低于',
        type: 'number',
        unit: '比例',
        min: 0,
        max: 1,
        step: 0.05,
        precision: 2,
        defaultValue: 0.7,
        help: '0.80 表示 80%。空样本窗口不会按 0% 触发。'
      }
    ],
    conditionBuilder: params => `successRate <= ${formatRatio(params.threshold)}`,
    thresholdValueBuilder: params => Number(params.threshold || 0),
    summaryBuilder: params => `协作成功率 <= ${percentText(params.threshold)}`
  },
  {
    key: 'collab-p95-critical',
    ruleType: 'COLLAB_HEALTH',
    name: 'P95 延迟过高',
    description: '适合监控协作链路是否明显变慢。',
    sentence: '当协作 P95 延迟超过设置秒数时触发。',
    defaultName: '协作延迟过高',
    defaultSeverity: 'ERROR',
    defaultCooldown: 15,
    fields: [
      {
        key: 'seconds',
        label: 'P95 延迟超过',
        type: 'number',
        unit: '秒',
        min: 1,
        max: 600,
        step: 5,
        precision: 0,
        defaultValue: 60
      }
    ],
    conditionBuilder: params => `p95LatencyMs >= ${formatNumber(Number(params.seconds || 0) * 1000)}`,
    thresholdValueBuilder: params => Number(params.seconds || 0) * 1000,
    summaryBuilder: params => `协作 P95 延迟 >= ${formatNumber(params.seconds)} 秒`
  },
  {
    key: 'collab-dlq-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '死信队列积压',
    description: '适合发现协作消息无法被正常消费。',
    sentence: '当协作死信队列积压达到设置数量时触发。',
    defaultName: '协作队列积压',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [
      {
        key: 'count',
        label: '积压达到',
        type: 'number',
        unit: '条',
        min: 1,
        max: 1000,
        step: 1,
        precision: 0,
        defaultValue: 20
      }
    ],
    conditionBuilder: params => `dlqBacklog >= ${formatNumber(params.count)}`,
    thresholdValueBuilder: params => Number(params.count || 0),
    summaryBuilder: params => `死信队列积压 >= ${formatNumber(params.count)} 条`
  },
  {
    key: 'collab-critique-high',
    ruleType: 'COLLAB_HEALTH',
    name: '反复修正过多',
    description: '适合发现多智能体协作质量下降。',
    sentence: '当平均 Critique 轮次超过设置阈值时触发。',
    defaultName: '协作反复修正过多',
    defaultSeverity: 'WARNING',
    defaultCooldown: 15,
    fields: [
      {
        key: 'rounds',
        label: '平均轮次超过',
        type: 'number',
        unit: '轮',
        min: 0,
        max: 20,
        step: 0.5,
        precision: 1,
        defaultValue: 3.5
      }
    ],
    conditionBuilder: params => `avgCritiqueRounds >= ${formatNumber(params.rounds)}`,
    thresholdValueBuilder: params => Number(params.rounds || 0),
    summaryBuilder: params => `平均 Critique 轮次 >= ${formatNumber(params.rounds)}`
  },
  {
    key: 'collab-bidding-success-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '竞标后成功率过低',
    description: '适合检查调度竞标后的实际完成质量。',
    sentence: '当竞标后协作成功率低于设置阈值时触发。',
    defaultName: '竞标后协作成功率过低',
    defaultSeverity: 'WARNING',
    defaultCooldown: 15,
    fields: [
      {
        key: 'threshold',
        label: '成功率低于',
        type: 'number',
        unit: '比例',
        min: 0,
        max: 1,
        step: 0.05,
        precision: 2,
        defaultValue: 0.75,
        help: '没有触发竞标的窗口不会参与异常判定。'
      }
    ],
    conditionBuilder: params => `biddingPostSuccessRate <= ${formatRatio(params.threshold)}`,
    thresholdValueBuilder: params => Number(params.threshold || 0),
    summaryBuilder: params => `竞标后成功率 <= ${percentText(params.threshold)}`
  },
  {
    key: 'system-health-down',
    ruleType: 'SYSTEM_HEALTH',
    name: '系统依赖异常',
    description: '兼容系统健康类告警入口。',
    sentence: '当系统健康检查返回 DOWN 时触发。',
    defaultName: '系统依赖健康告警',
    defaultSeverity: 'ERROR',
    defaultCooldown: 5,
    fields: [],
    conditionBuilder: () => 'health == DOWN',
    thresholdValueBuilder: () => 1,
    summaryBuilder: () => '系统健康状态为 DOWN'
  },
  {
    key: 'api-failure',
    ruleType: 'ERROR_RATE',
    name: 'API 调用失败',
    description: '兼容 API 失败类告警入口。',
    sentence: '当 API 错误次数达到设置数量时触发。',
    defaultName: 'API 调用失败告警',
    defaultSeverity: 'ERROR',
    defaultCooldown: 5,
    fields: [
      {
        key: 'count',
        label: '错误次数达到',
        type: 'number',
        unit: '次',
        min: 1,
        max: 1000,
        step: 1,
        precision: 0,
        defaultValue: 1
      }
    ],
    conditionBuilder: params => `errorCount >= ${formatNumber(params.count)}`,
    thresholdValueBuilder: params => Number(params.count || 0),
    summaryBuilder: params => `API 错误次数 >= ${formatNumber(params.count)}`
  }
]

const currentPage = ref(1)
const pageSize = ref(20)
const totalHistory = ref(0)

const enabledRuleCount = computed(() => rules.value.filter(rule => rule.enabled).length)
const disabledRuleCount = computed(() => Math.max(rules.value.length - enabledRuleCount.value, 0))
const channelCount = computed(() => {
  const channels = new Set()
  rules.value.forEach(rule => {
    getRuleChannels(rule).forEach(channel => channels.add(channel))
  })
  return channels.size
})

const getRuleChannels = (rule) => {
  return String(rule?.notificationChannels || '')
    .split(',')
    .map(channel => channel.trim())
    .filter(Boolean)
}

const getChannelText = (channel) => {
  const map = {
    EMAIL: '邮件',
    DINGTALK: '钉钉',
    WECHAT: '企业微信',
    WECOM: '企业微信'
  }
  return map[channel] || channel
}

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

const createPresetParams = (preset) => {
  return (preset?.fields || []).reduce((params, field) => {
    params[field.key] = field.defaultValue
    return params
  }, {})
}

const showCreateDialog = () => {
  router.push('/dashboard/runtime/alerts/rules/create')
}

const viewRule = (rule) => {
  router.push(`/dashboard/runtime/alerts/rules/${rule.id}/edit`)
}

const matchesPresetExpression = (rule, preset) => {
  const params = inferPresetParams(rule, preset)
  return compareConditionExpr(rule.conditionExpr, preset.conditionBuilder(params))
}

const inferPresetParams = (rule, preset) => {
  const params = createPresetParams(preset)
  const expr = rule?.conditionExpr || ''
  const numericMatch = expr.match(/(-?\d+(?:\.\d+)?)\s*$/)
  const value = numericMatch ? Number(numericMatch[1]) : Number(rule?.thresholdValue)

  if (preset.key === 'collab-success-critical' || preset.key === 'collab-bidding-success-critical') {
    params.threshold = Number.isFinite(value) ? value : params.threshold
  } else if (preset.key === 'collab-p95-critical') {
    params.seconds = Number.isFinite(value) ? value / 1000 : params.seconds
  } else if (preset.key === 'collab-dlq-critical' || preset.key === 'api-failure') {
    params.count = Number.isFinite(value) ? value : params.count
  } else if (preset.key === 'collab-critique-high') {
    params.rounds = Number.isFinite(value) ? value : params.rounds
  }

  return params
}

const parseConditionExpr = (expr) => {
  const match = String(expr || '').trim().match(/^([A-Za-z][\w]*)\s*(==|!=|>=|<=|>|<)\s*([A-Za-z0-9_.-]+)$/)
  if (!match) return null
  return {
    field: match[1],
    operator: match[2],
    value: match[3]
  }
}

const compareConditionExpr = (left, right) => {
  const leftExpr = parseConditionExpr(left)
  const rightExpr = parseConditionExpr(right)
  if (!leftExpr || !rightExpr) {
    return String(left || '').trim() === String(right || '').trim()
  }
  if (leftExpr.field !== rightExpr.field || leftExpr.operator !== rightExpr.operator) {
    return false
  }

  const leftNumber = Number(leftExpr.value)
  const rightNumber = Number(rightExpr.value)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
    return Math.abs(leftNumber - rightNumber) < 0.000001
  }
  return leftExpr.value === rightExpr.value
}

const getRuleConditionSummary = (rule) => {
  if (rule.ruleType === 'SYSTEM_HEALTH' && compareConditionExpr(rule.conditionExpr, 'status == DOWN')) {
    const target = rule.targetScope === 'DEPENDENCY' ? dependencyText(rule.targetId) : '全部依赖'
    return `${target} 状态为 DOWN`
  }
  if (rule.ruleType === 'ERROR_RATE') {
    const target = rule.targetScope === 'PROVIDER' ? providerText(rule.targetId) : '全部 Provider'
    const window = rule.metricWindowMinutes || 5
    const condition = parseConditionExpr(rule.conditionExpr)
    if (condition?.field === 'lastFailure') {
      return `${target} 出现单次 API 失败`
    }
    if (condition?.field === 'errorCount') {
      return `${target} ${window} 分钟内 API 错误次数 >= ${condition.value}`
    }
    if (condition?.field === 'errorRate') {
      return `${target} ${window} 分钟内 API 失败率 >= ${percentText(condition.value)}，样本 >= ${rule.minSampleCount || 1}`
    }
  }
  const preset = rulePresets.find(item => item.ruleType === rule.ruleType && matchesPresetExpression(rule, item))
  if (preset) {
    return preset.summaryBuilder(inferPresetParams(rule, preset))
  }
  return rule.conditionExpr ? `自定义条件：${rule.conditionExpr}` : '按类型默认触发'
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
    'ERROR_RATE': '错误率',
    'SYSTEM_HEALTH': '系统健康',
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

.channel-list {
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.rule-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.rule-actions :deep(.el-button) {
  margin-left: 0;
}

.rule-actions :deep(.el-button + .el-button) {
  margin-left: 0;
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
