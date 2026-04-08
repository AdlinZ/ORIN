<template>
  <div class="page-container">
    <OrinPageShell
      title="审计中心"
      description="统一追踪访问行为、系统操作与关键配置变更"
      icon="List"
      domain="系统与网关"
      maturity="available"
    >
      <template #actions>
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
    </OrinPageShell>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="实时审计记录" name="logs">
        <el-card shadow="never">
          <OrinAsyncState :status="logsState.status" empty-text="暂无审计记录" @retry="loadLogs">
            <OrinAuditTable :rows="auditRows" />
          </OrinAsyncState>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="审计存储配置" name="config">
        <el-row :gutter="16">
          <el-col :xs="24" :lg="12">
            <el-card shadow="never">
              <template #header>
                <div class="card-head">
                  审计策略
                </div>
              </template>
              <el-form label-width="130px">
                <el-form-item label="全局审计开关">
                  <el-switch v-model="config.auditEnabled" />
                </el-form-item>
                <el-form-item label="日志分级">
                  <el-select v-model="config.logLevel" style="width: 220px">
                    <el-option label="ALL" value="ALL" />
                    <el-option label="AUDIT_ONLY" value="AUDIT_ONLY" />
                    <el-option label="ERROR_ONLY" value="ERROR_ONLY" />
                  </el-select>
                </el-form-item>
                <el-form-item label="保留天数">
                  <el-input-number v-model="config.retentionDays" :min="1" :max="365" />
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <el-col :xs="24" :lg="12">
            <el-card shadow="never">
              <template #header>
                <div class="card-head">
                  存储统计
                </div>
              </template>
              <OrinAsyncState :status="statsState.status" empty-text="暂无统计数据" @retry="loadStats">
                <div class="stat-line">
                  <span>日志总量</span>
                  <strong>{{ stats.totalCount }}</strong>
                </div>
                <div class="stat-line">
                  <span>占用空间(MB)</span>
                  <strong>{{ stats.estimatedSizeMb }}</strong>
                </div>
                <div class="stat-line">
                  <span>最早日志</span>
                  <strong>{{ formatDate(stats.oldestLog) }}</strong>
                </div>
                <div class="ops-row">
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
              </OrinAsyncState>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane label="日志控制台" name="loggers">
        <el-card shadow="never">
          <template #header>
            <div class="card-head with-actions">
              <span>运行时 Logger 级别</span>
              <div class="head-actions">
                <el-button :icon="Refresh" size="small" @click="loadLoggers">
                  刷新
                </el-button>
                <el-button
                  :icon="RefreshLeft"
                  size="small"
                  type="warning"
                  @click="resetAllLoggers"
                >
                  全部重置
                </el-button>
              </div>
            </div>
          </template>
          <el-table
            v-loading="loadingLoggers"
            :data="loggers"
            border
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
                  style="width: 100%"
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
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Refresh, RefreshLeft } from '@element-plus/icons-vue'
import request from '@/utils/request'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinAuditTable from '@/components/orin/OrinAuditTable.vue'
import { createAsyncState, markEmpty, markError, markLoading, markSuccess } from '@/viewmodels'

const activeTab = ref('logs')
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

const CONFIG_KEYS = {
  AUDIT_ENABLED: 'log.audit.enabled',
  LOG_LEVEL: 'log.level',
  RETENTION: 'log.retention.days'
}

const toAuditRows = (payload) => {
  const source = Array.isArray(payload) ? payload : (payload?.records || payload?.content || [])
  return source.map((item) => ({
    time: item.createdAt ? dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    actor: item.userName || item.userId || '-',
    action: item.providerId || item.operationType || '-',
    resource: item.endpoint || '-',
    result: item.success ? 'SUCCESS' : 'FAILED',
    traceId: item.traceId || item.conversationId || '-'
  }))
}

const loadLogs = async () => {
  markLoading(logsState)
  try {
    const response = await request.get('/audit/logs', { params: { page: 1, size: 20 } })
    auditRows.value = toAuditRows(response)
    if (auditRows.value.length === 0) markEmpty(logsState)
    else markSuccess(logsState)
  } catch (error) {
    markError(logsState, error)
  }
}

const loadConfig = async () => {
  try {
    const response = await request.get('/system/log-config')
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
      request.put(`/system/log-config/${CONFIG_KEYS.AUDIT_ENABLED}`, { value: String(config.auditEnabled) }),
      request.put(`/system/log-config/${CONFIG_KEYS.LOG_LEVEL}`, { value: config.logLevel }),
      request.put(`/system/log-config/${CONFIG_KEYS.RETENTION}`, { value: String(config.retentionDays) })
    ])
    ElMessage.success('审计配置已保存')
  } finally {
    saving.value = false
  }
}

const loadStats = async () => {
  markLoading(statsState)
  try {
    const response = await request.get('/system/log-config/stats')
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
    await request.post('/system/log-config/cleanup', null, { params: { days: cleanupDays.value } })
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
    const response = await request.get('/system/log-config/loggers')
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
    await request.put(`/system/log-config/loggers/${row.name}`, { level: row.newLevel })
    row.level = row.newLevel
    ElMessage.success(`Logger ${row.name} 已更新为 ${row.newLevel}`)
  } catch (error) {
    row.newLevel = row.level
    ElMessage.error('日志级别更新失败')
  }
}

const resetLogger = async (row) => {
  try {
    await request.delete(`/system/log-config/loggers/${row.name}`)
    ElMessage.success(`Logger ${row.name} 已重置`)
    await loadLoggers()
  } catch (error) {
    ElMessage.error('重置失败')
  }
}

const resetAllLoggers = async () => {
  try {
    await ElMessageBox.confirm('确认将所有 Logger 重置为默认级别？', '重置确认', { type: 'warning' })
    await request.post('/system/log-config/loggers/reset-all')
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
</script>

<style scoped>
.card-head {
  font-weight: 600;
}

.stat-line {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}

.ops-row {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  align-items: center;
}

.with-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.head-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 992px) {
  .ops-row {
    flex-direction: column;
    align-items: stretch;
  }

  .with-actions {
    flex-wrap: wrap;
    gap: 8px;
  }

  .head-actions {
    width: 100%;
  }
}
</style>
