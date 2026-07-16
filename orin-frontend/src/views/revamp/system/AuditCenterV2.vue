<template>
  <div class="audit-center page-container" :class="{ 'is-embedded': !props.showHeader }">
    <OrinPageShell
      v-if="props.showHeader"
      title="审计中心"
      description="统一追踪访问行为、系统操作与关键配置变更"
      icon="List"
      domain="组织权限"
    >
      <template v-if="props.showHeaderActions" #actions>
        <el-button :icon="Refresh" @click="loadAll">
          刷新
        </el-button>
        <el-button
          type="primary"
          :icon="Check"
          :loading="saving"
          @click="saveConfig"
        >
          保存配置
        </el-button>
      </template>
      <template #filters>
        <div class="audit-workbar">
          <div class="workbar-heading">
            <h2>{{ activeTabMeta.label }}</h2>
            <span>{{ activeTabMeta.summary }}</span>
          </div>

          <div class="workbar-controls">
            <div
              v-if="showConfigTab"
              class="audit-mode-switch"
              role="tablist"
              aria-label="审计中心工作区"
            >
              <button
                v-for="tab in auditTabs"
                :key="tab.key"
                type="button"
                role="tab"
                :aria-selected="activeTab === tab.key"
                :class="{ active: activeTab === tab.key }"
                :data-test="`audit-tab-${tab.key}`"
                @click="activeTab = tab.key"
              >
                {{ tab.label }}
              </button>
            </div>

            <div class="audit-workbar-stats" aria-label="审计状态">
              <span :class="{ warning: !config.auditEnabled }">
                <strong>{{ config.auditEnabled ? '已开启' : '已关闭' }}</strong> 全局审计
              </span>
              <span><strong>{{ config.retentionDays }}</strong> 天保留</span>
            </div>

            <template v-if="activeTab === 'loggers'">
              <el-button :icon="Refresh" size="small" @click="loadLoggers">
                刷新 Logger
              </el-button>
              <el-button
                :icon="RefreshLeft"
                size="small"
                type="warning"
                @click="resetAllLoggers"
              >
                全部重置
              </el-button>
            </template>
          </div>
        </div>
      </template>
    </OrinPageShell>

    <OrinDataTable v-if="activeTab === 'logs'" class="audit-content-surface" compact>
      <template v-if="!props.showHeader" #header>
        <div class="embedded-table-head">
          <strong>实时审计记录</strong>
          <span>{{ auditRows.length }} 条最近记录</span>
        </div>
      </template>
      <OrinAsyncState :status="logsState.status" empty-text="暂无审计记录" @retry="loadLogs">
        <OrinAuditTable :rows="auditRows" />
      </OrinAsyncState>
    </OrinDataTable>

    <section v-else-if="activeTab === 'config' && showConfigTab" class="audit-content-surface audit-config-surface">
      <header class="config-surface-head">
        <div>
          <h3>审计策略与存储</h3>
          <p>统一维护审计开关、日志级别、保留周期和历史数据清理。</p>
        </div>
      </header>

      <div class="config-grid">
        <article class="config-panel">
          <h4>审计策略</h4>
          <el-form label-width="130px">
            <el-form-item label="全局审计开关">
              <el-switch v-model="config.auditEnabled" />
            </el-form-item>
            <el-form-item label="日志分级">
              <el-select v-model="config.logLevel" class="config-select">
                <el-option label="ALL" value="ALL" />
                <el-option label="AUDIT_ONLY" value="AUDIT_ONLY" />
                <el-option label="ERROR_ONLY" value="ERROR_ONLY" />
              </el-select>
            </el-form-item>
            <el-form-item label="保留天数">
              <el-input-number v-model="config.retentionDays" :min="1" :max="365" />
            </el-form-item>
          </el-form>
        </article>

        <article class="config-panel">
          <h4>存储统计</h4>
          <OrinAsyncState :status="statsState.status" empty-text="暂无统计数据" @retry="loadStats">
            <dl class="storage-stats">
              <div><dt>日志总量</dt><dd>{{ stats.totalCount }}</dd></div>
              <div><dt>占用空间</dt><dd>{{ stats.estimatedSizeMb }} MB</dd></div>
              <div><dt>最早日志</dt><dd>{{ formatDate(stats.oldestLog) }}</dd></div>
            </dl>
            <div class="cleanup-row">
              <span>清理指定天数以前的历史日志</span>
              <div>
                <el-input-number v-model="cleanupDays" :min="0" :max="365" />
                <el-button
                  type="danger"
                  plain
                  :loading="cleaning"
                  @click="cleanupLogs"
                >
                  清理历史日志
                </el-button>
              </div>
            </div>
          </OrinAsyncState>
        </article>
      </div>
    </section>

    <OrinDataTable v-else-if="activeTab === 'loggers' && showLoggersTab" class="audit-content-surface" compact>
      <el-table
        v-loading="loadingLoggers"
        :data="loggers"
        stripe
      >
        <el-table-column prop="name" label="Logger 名称" min-width="280" />
        <el-table-column prop="level" label="当前级别" width="140">
          <template #default="{ row }">
            <el-tag :type="getLevelTagType(row.level)" size="small">
              {{ row.level || 'NULL' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设置级别" width="220">
          <template #default="{ row }">
            <el-select
              v-model="row.newLevel"
              placeholder="选择级别"
              size="small"
              class="logger-level-select"
              @change="applyLoggerLevel(row)"
            >
              <el-option label="继承默认" value="NULL" />
              <el-option
                v-for="level in supportedLevels"
                :key="level"
                :label="level"
                :value="level"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="danger"
              @click="resetLogger(row)"
            >
              重置
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </OrinDataTable>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import {
  cleanupLogConfig,
  getGatewayAuditLogs,
  getLogConfig,
  getLogConfigStats,
  getLoggerLevels,
  resetAllLoggerLevels,
  resetLoggerLevel,
  updateLogConfig,
  updateLoggerLevel
} from '@/api/audit'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinAuditTable from '@/components/orin/OrinAuditTable.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess } from '@/viewmodels'

const props = defineProps({
  mode: {
    type: String,
    default: 'all' // all | logs
  },
  showHeader: {
    type: Boolean,
    default: true
  },
  showHeaderActions: {
    type: Boolean,
    default: true
  },
  initialTab: {
    type: String,
    default: 'logs'
  }
})

const showConfigTab = computed(() => props.mode === 'all')
const showLoggersTab = computed(() => props.mode === 'all')
const activeTab = ref(props.initialTab)
const auditTabs = [
  { key: 'logs', label: '实时记录' },
  { key: 'config', label: '存储配置' },
  { key: 'loggers', label: 'Logger 控制台' }
]
const saving = ref(false)
const cleaning = ref(false)
const cleanupDays = ref(30)
const loadingLoggers = ref(false)

const logsState = reactive(createAsyncState())
const statsState = reactive(createAsyncState())
const auditRows = ref([])
const stats = reactive({
  totalCount: 0,
  estimatedSizeMb: 0,
  oldestLog: null
})
const config = reactive({
  auditEnabled: true,
  logLevel: 'ALL',
  retentionDays: 30
})
const loggers = ref([])
const supportedLevels = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF']

const activeTabMeta = computed(() => {
  if (activeTab.value === 'config') {
    return { label: '审计存储配置', summary: `${stats.totalCount} 条日志` }
  }
  if (activeTab.value === 'loggers') {
    return { label: 'Logger 控制台', summary: `${loggers.value.length} 个 Logger` }
  }
  return { label: '实时审计记录', summary: `${auditRows.value.length} 条最近记录` }
})

const CONFIG_KEYS = {
  AUDIT_ENABLED: 'log.audit.enabled',
  LOG_LEVEL: 'log.level',
  RETENTION: 'log.retention.days'
}

const toAuditRows = (payload) => {
  const source = Array.isArray(payload) ? payload : (payload?.records || payload?.content || [])
  return source.map((item) => ({
    id: item.id ?? '-',
    time: item.createdAt ? dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    actor: item.userName || item.userId || '-',
    action: item.providerId || item.operationType || '-',
    resource: item.endpoint || '-',
    method: item.method || '-',
    model: item.model || '-',
    providerType: item.providerType || '-',
    result: item.success ? 'SUCCESS' : 'FAILED',
    statusCode: item.statusCode ?? '-',
    errorMessage: item.errorMessage || '-',
    traceId: item.traceId || item.conversationId || '-',
    conversationId: item.conversationId || '-',
    workflowId: item.workflowId || '-',
    apiKeyId: item.apiKeyId || '-',
    responseTime: item.responseTime ?? '-',
    promptTokens: item.promptTokens ?? 0,
    completionTokens: item.completionTokens ?? 0,
    totalTokens: item.totalTokens ?? 0,
    requestParams: item.requestParams || '',
    responseContent: item.responseContent || '',
    endpoint: item.endpoint || '-'
  }))
}

const loadLogs = async () => {
  markLoading(logsState)
  try {
    const response = await getGatewayAuditLogs({ page: 0, size: 20 })
    auditRows.value = toAuditRows(response)
    if (auditRows.value.length === 0) markEmpty(logsState)
    else markSuccess(logsState)
  } catch (error) {
    markError(logsState, error)
  }
}

const loadConfig = async () => {
  try {
    const response = await getLogConfig()
    if (!Array.isArray(response)) return
    for (const item of response) {
      if (item.configKey === CONFIG_KEYS.AUDIT_ENABLED) config.auditEnabled = item.configValue === 'true'
      if (item.configKey === CONFIG_KEYS.LOG_LEVEL) config.logLevel = item.configValue || 'ALL'
      if (item.configKey === CONFIG_KEYS.RETENTION) config.retentionDays = Number(item.configValue || 30)
    }
  } catch (error) {
    ElMessage.warning('配置读取失败')
  }
}

const saveConfig = async () => {
  saving.value = true
  try {
    await Promise.all([
      updateLogConfig(CONFIG_KEYS.AUDIT_ENABLED, String(config.auditEnabled)),
      updateLogConfig(CONFIG_KEYS.LOG_LEVEL, config.logLevel),
      updateLogConfig(CONFIG_KEYS.RETENTION, String(config.retentionDays))
    ])
    ElMessage.success('审计配置已保存')
  } finally {
    saving.value = false
  }
}

const loadStats = async () => {
  markLoading(statsState)
  try {
    const response = await getLogConfigStats()
    stats.totalCount = Number(response?.totalCount || 0)
    stats.estimatedSizeMb = Number(response?.estimatedSizeMb || 0)
    stats.oldestLog = response?.oldestLog || null
    markSuccess(statsState)
  } catch (error) {
    markError(statsState, error)
  }
}

const cleanupLogs = async () => {
  cleaning.value = true
  try {
    await cleanupLogConfig(cleanupDays.value)
    ElMessage.success('日志清理任务已提交')
    await loadStats()
    await loadLogs()
  } finally {
    cleaning.value = false
  }
}

const loadLoggers = async () => {
  loadingLoggers.value = true
  try {
    const response = await getLoggerLevels()
    loggers.value = Object.entries(response || {}).map(([name, level]) => ({
      name,
      level,
      newLevel: level || 'NULL'
    }))
  } catch (error) {
    ElMessage.error('Logger 列表加载失败')
  } finally {
    loadingLoggers.value = false
  }
}

const getLevelTagType = (level) => {
  const map = {
    TRACE: 'info',
    DEBUG: 'primary',
    INFO: 'success',
    WARN: 'warning',
    ERROR: 'danger',
    OFF: 'info',
    NULL: ''
  }
  return map[level] || ''
}

const applyLoggerLevel = async (row) => {
  try {
    await updateLoggerLevel(row.name, row.newLevel)
    row.level = row.newLevel
    ElMessage.success(`Logger ${row.name} 已更新为 ${row.newLevel}`)
  } catch (error) {
    row.newLevel = row.level
    ElMessage.error('日志级别更新失败')
  }
}

const resetLogger = async (row) => {
  try {
    await resetLoggerLevel(row.name)
    ElMessage.success(`Logger ${row.name} 已重置`)
    await loadLoggers()
  } catch (error) {
    ElMessage.error('重置失败')
  }
}

const resetAllLoggers = async () => {
  try {
    await ElMessageBox.confirm('确认将所有 Logger 重置为默认级别？', '重置确认', { type: 'warning' })
    await resetAllLoggerLevels()
    ElMessage.success('全部 Logger 已重置')
    await loadLoggers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重置失败')
    }
  }
}

const loadAll = async () => {
  await Promise.all([loadLogs(), loadStats(), loadConfig(), loadLoggers()])
}

const formatDate = (value) => value ? dayjs(value).format('YYYY-MM-DD') : '-'

onMounted(loadAll)

watch(
  () => props.mode,
  (mode) => {
    if (mode === 'logs' && activeTab.value !== 'logs') {
      activeTab.value = 'logs'
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.audit-workbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 18px;
}

.workbar-heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.workbar-heading h2 {
  margin: 0;
  color: var(--neutral-gray-900);
  font-size: 16px;
  font-weight: var(--font-semibold);
}

.workbar-heading > span {
  flex: none;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  background: var(--neutral-gray-100);
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.workbar-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  gap: 10px;
  flex-wrap: wrap;
}

.audit-mode-switch {
  display: inline-flex;
  padding: 3px;
  border: 1px solid var(--orin-border-strong);
  border-radius: var(--radius-base);
  background: var(--orin-surface-muted);
}

.audit-mode-switch button {
  min-height: 30px;
  padding: 0 11px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--neutral-gray-500);
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
}

.audit-mode-switch button:hover,
.audit-mode-switch button.active {
  background: var(--orin-surface);
  color: var(--orin-primary);
}

.audit-workbar-stats {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-workbar-stats span {
  color: var(--neutral-gray-500);
  font-size: 12px;
  white-space: nowrap;
}

.audit-workbar-stats strong {
  color: var(--neutral-gray-700);
}

.audit-workbar-stats .warning strong {
  color: var(--error-color);
}

.embedded-table-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.embedded-table-head span {
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.audit-config-surface {
  overflow: hidden;
  border: 1px solid var(--orin-border-strong);
  border-radius: var(--radius-base);
  background: var(--orin-surface);
}

.config-surface-head {
  padding: 16px 18px;
  border-bottom: 1px solid var(--orin-border-strong);
}

.config-surface-head h3,
.config-panel h4 {
  margin: 0;
  color: var(--neutral-gray-900);
}

.config-surface-head h3 {
  font-size: 15px;
}

.config-surface-head p {
  margin: 5px 0 0;
  color: var(--neutral-gray-500);
  font-size: 13px;
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.config-panel {
  min-width: 0;
  padding: 18px;
}

.config-panel + .config-panel {
  border-left: 1px solid var(--orin-border-strong);
}

.config-panel h4 {
  margin-bottom: 16px;
  font-size: 14px;
}

.config-select,
.logger-level-select {
  width: 100%;
  max-width: 220px;
}

.storage-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.storage-stats div {
  min-width: 0;
  padding: 12px;
  border-radius: var(--radius-base);
  background: var(--orin-surface-muted);
}

.storage-stats dt {
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.storage-stats dd {
  margin: 6px 0 0;
  color: var(--neutral-gray-900);
  font-size: 16px;
  font-weight: 700;
}

.cleanup-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid var(--orin-border);
  color: var(--neutral-gray-500);
  font-size: 13px;
}

.cleanup-row > div {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 992px) {
  .audit-workbar,
  .cleanup-row {
    flex-direction: column;
    align-items: stretch;
  }

  .workbar-controls {
    justify-content: flex-start;
  }

  .config-grid {
    grid-template-columns: 1fr;
  }

  .config-panel + .config-panel {
    border-top: 1px solid var(--orin-border-strong);
    border-left: 0;
  }
}

@media (max-width: 680px) {
  .audit-mode-switch,
  .audit-mode-switch button {
    width: 100%;
  }

  .audit-mode-switch button {
    flex: 1;
  }

  .audit-workbar-stats {
    width: 100%;
    justify-content: space-between;
  }

  .storage-stats {
    grid-template-columns: 1fr;
  }

  .cleanup-row > div {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
