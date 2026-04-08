<template>
  <div class="page-container">
    <OrinPageShell
      title="重构灰度控制台"
      description="统一管理 V2 重构模块的启用状态、成熟度和灰度阶段。"
      icon="Operation"
      domain="系统与网关"
      maturity="beta"
    >
      <template #actions>
        <el-space wrap>
          <el-button @click="setAll(false)">
            全部关闭
          </el-button>
          <el-button type="primary" @click="setAll(true)">
            全部启用
          </el-button>
          <el-button :icon="Refresh" @click="resetDefaults">
            重置默认
          </el-button>
        </el-space>
      </template>
    </OrinPageShell>

    <el-card shadow="never" class="stage-card">
      <template #header>
        <div class="card-header">
          <span>建议灰度顺序</span>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="stage in rolloutStages"
          :key="stage.stage"
          :timestamp="stage.title"
          placement="top"
        >
          <div class="stage-body">
            <el-tag
              v-for="flag in stage.flags"
              :key="flag"
              class="stage-tag"
              size="small"
              :type="isEnabled(flag) ? 'success' : 'info'"
              effect="plain"
            >
              {{ flag }}
            </el-tag>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <el-card shadow="never" class="stage-card">
      <template #header>
        <div class="card-header">
          <span>核心路径一键冒烟</span>
          <el-space wrap>
            <el-button type="primary" @click="runSmokeCheck">
              一键冒烟检查
            </el-button>
            <el-button @click="copySmokeCommand">
              复制终端命令
            </el-button>
            <el-button @click="exportSmokeReport">
              导出 JSON
            </el-button>
          </el-space>
        </div>
      </template>
      <div class="smoke-summary">
        <el-tag :type="smokeReport.executed ? (smokeReport.passed ? 'success' : 'danger') : 'info'" effect="plain">
          {{ smokeReport.executed ? (smokeReport.passed ? '通过' : '未通过') : '未执行' }}
        </el-tag>
        <el-tag type="info" effect="plain">
          检查项 {{ smokeReport.total }}
        </el-tag>
        <el-tag type="warning" effect="plain">
          异常 {{ smokeReport.failed }}
        </el-tag>
        <span class="summary-time">最近检查：{{ smokeReport.lastRunAt || '-' }}</span>
      </div>
      <div class="smoke-tools">
        <el-space wrap>
          <el-button :type="smokeFilter === 'all' ? 'primary' : 'default'" @click="smokeFilter = 'all'">
            全部
          </el-button>
          <el-button :type="smokeFilter === 'failed' ? 'primary' : 'default'" @click="smokeFilter = 'failed'">
            仅失败项
          </el-button>
          <el-button :type="smokeFilter === 'passed' ? 'primary' : 'default'" @click="smokeFilter = 'passed'">
            仅通过项
          </el-button>
        </el-space>
      </div>
      <div v-if="failedReasonStats.length > 0" class="reason-stats">
        <el-tag
          v-for="item in failedReasonStats"
          :key="item.reason"
          size="small"
          type="danger"
          effect="plain"
        >
          {{ item.reason }}: {{ item.count }}
        </el-tag>
      </div>
      <el-table :data="filteredSmokeRows" border size="small">
        <el-table-column prop="title" label="模块" min-width="150" />
        <el-table-column prop="route" label="路径" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.passed ? 'success' : 'danger'" effect="plain">
              {{ scope.row.passed ? 'OK' : 'FAIL' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="说明" min-width="180" />
        <el-table-column label="操作" width="120" align="center">
          <template #default="scope">
            <el-button
              link
              type="primary"
              :disabled="scope.row.passed"
              @click="go(scope.row.route)"
            >
              去修复
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="history-panel">
        <div class="history-title">最近检查历史（本地）</div>
        <div v-if="smokeHistory.length === 0" class="history-empty">暂无记录</div>
        <ul v-else class="history-list">
          <li v-for="item in smokeHistory" :key="item.id">
            <el-tag :type="item.passed ? 'success' : 'danger'" effect="plain" size="small">
              {{ item.passed ? 'PASS' : 'FAIL' }}
            </el-tag>
            <span>{{ item.time }}</span>
            <span>异常 {{ item.failed }}/{{ item.total }}</span>
          </li>
        </ul>
      </div>
    </el-card>

    <el-table :data="tableRows" border stripe>
      <el-table-column prop="title" label="模块" min-width="180" />
      <el-table-column prop="domain" label="所属域" min-width="130" />
      <el-table-column prop="stage" label="阶段" width="90" align="center" />
      <el-table-column label="成熟度" width="120" align="center">
        <template #default="scope">
          <OrinMaturityBadge :level="scope.row.maturity" />
        </template>
      </el-table-column>
      <el-table-column prop="route" label="路由" min-width="260" show-overflow-tooltip />
      <el-table-column label="启用状态" width="150" align="center">
        <template #default="scope">
          <el-switch
            :model-value="scope.row.enabled"
            @change="(val) => toggleFlag(scope.row.key, val)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="go(scope.row.route)">
            打开页面
          </el-button>
          <el-button link @click="copyKey(scope.row.key)">
            复制 Key
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinMaturityBadge from '@/components/orin/OrinMaturityBadge.vue'
import { FEATURE_FLAGS, FEATURE_FLAG_KEYS, getFeatureFlagsSnapshot, resetFeatureFlags, setFeatureFlag, setFeatureFlagsBatch } from '@/config/featureFlags'
import { REVAMP_ROLLOUT_ITEMS, REVAMP_ROLLOUT_STAGES } from '@/config/revampRollout'
import { buildReasonStats, buildSmokeReport } from '@/utils/revampSmokeReport'

const router = useRouter()
const snapshot = ref(getFeatureFlagsSnapshot())

const rolloutStages = REVAMP_ROLLOUT_STAGES
const smokeCommand = 'npm run smoke:revamp && npm run test:revamp'
const SMOKE_HISTORY_KEY = 'orin_revamp_smoke_history'
const MAX_SMOKE_HISTORY = 10
const smokeFilter = ref('all')
const smokeReport = ref({
  executed: false,
  passed: true,
  total: REVAMP_ROLLOUT_ITEMS.length,
  failed: 0,
  lastRunAt: '',
  rows: REVAMP_ROLLOUT_ITEMS.map((item) => ({
    key: item.key,
    title: item.title,
    route: item.route,
    passed: true,
    reason: '未执行'
  }))
})
const smokeHistory = ref(loadSmokeHistory())

const tableRows = computed(() => REVAMP_ROLLOUT_ITEMS.map((item) => ({
  ...item,
  enabled: Boolean(snapshot.value[item.key])
})))

const filteredSmokeRows = computed(() => {
  if (smokeFilter.value === 'failed') {
    return smokeReport.value.rows.filter((row) => !row.passed)
  }
  if (smokeFilter.value === 'passed') {
    return smokeReport.value.rows.filter((row) => row.passed)
  }
  return smokeReport.value.rows
})

const failedReasonStats = computed(() => {
  return buildReasonStats(smokeReport.value.rows)
})

const refreshSnapshot = () => {
  snapshot.value = getFeatureFlagsSnapshot()
}

const isEnabled = (flag) => Boolean(snapshot.value[flag])

const toggleFlag = (flag, enabled) => {
  setFeatureFlag(flag, enabled)
  refreshSnapshot()
  ElMessage.success(`${flag} 已${enabled ? '启用' : '关闭'}`)
}

const setAll = (enabled) => {
  const next = Object.keys(FEATURE_FLAGS).reduce((acc, key) => {
    if (key.startsWith('revamp')) {
      acc[key] = enabled
    }
    return acc
  }, {})
  setFeatureFlagsBatch(next)
  refreshSnapshot()
  ElMessage.success(enabled ? '所有重构模块已启用' : '所有重构模块已关闭')
}

const resetDefaults = () => {
  resetFeatureFlags()
  refreshSnapshot()
  ElMessage.success('灰度开关已重置为默认值')
}

const go = (path) => router.push(path)

const copyKey = async (key) => {
  try {
    await navigator.clipboard.writeText(key)
    ElMessage.success(`已复制 ${key}`)
  } catch (error) {
    ElMessage.warning('复制失败，请手动复制')
  }
}

const formatNow = () => {
  const now = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

function loadSmokeHistory() {
  try {
    const raw = window.localStorage.getItem(SMOKE_HISTORY_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (error) {
    return []
  }
}

const saveSmokeHistory = (nextHistory) => {
  smokeHistory.value = nextHistory
  try {
    window.localStorage.setItem(SMOKE_HISTORY_KEY, JSON.stringify(nextHistory))
  } catch (error) {
    // ignore storage failure
  }
}

const appendSmokeHistory = (report) => {
  const next = [
    {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      time: report.lastRunAt,
      passed: report.passed,
      total: report.total,
      failed: report.failed
    },
    ...smokeHistory.value
  ].slice(0, MAX_SMOKE_HISTORY)
  saveSmokeHistory(next)
}

const runSmokeCheck = () => {
  const rows = REVAMP_ROLLOUT_ITEMS.map((item) => {
    const hasFlag = FEATURE_FLAG_KEYS.includes(item.key)
    const resolved = router.resolve(item.route)
    const hasRoute = Array.isArray(resolved?.matched) && resolved.matched.length > 0
    const passed = hasFlag && hasRoute
    let reason = '通过'
    if (!hasFlag) {
      reason = '缺少 Feature Flag 定义'
    } else if (!hasRoute) {
      reason = '路由无法解析'
    }
    return {
      key: item.key,
      title: item.title,
      route: item.route,
      passed,
      reason
    }
  })

  const failed = rows.filter((row) => !row.passed).length
  smokeReport.value = {
    executed: true,
    passed: failed === 0,
    total: rows.length,
    failed,
    lastRunAt: formatNow(),
    rows
  }
  appendSmokeHistory(smokeReport.value)

  if (failed === 0) {
    ElMessage.success('核心路径冒烟检查通过')
    return
  }
  ElMessage.warning(`核心路径冒烟发现 ${failed} 项异常`)
}

const copySmokeCommand = async () => {
  try {
    await navigator.clipboard.writeText(smokeCommand)
    ElMessage.success('已复制冒烟命令')
  } catch (error) {
    ElMessage.warning('复制失败，请手动执行 npm run smoke:revamp')
  }
}

const exportSmokeReport = () => {
  try {
    const payload = buildSmokeReport({
      source: 'ui-console',
      rows: smokeReport.value.rows,
      stageMatrix: REVAMP_ROLLOUT_STAGES,
      generatedAt: new Date().toISOString(),
      lastRunAt: smokeReport.value.lastRunAt,
      executed: smokeReport.value.executed
    })
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `orin-revamp-smoke-${Date.now()}.json`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('冒烟结果已导出')
  } catch (error) {
    ElMessage.warning('导出失败，请稍后重试')
  }
}
</script>

<style scoped>
.stage-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stage-body {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stage-tag {
  margin-right: 4px;
}

.smoke-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.summary-time {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.smoke-tools {
  margin-bottom: 8px;
}

.reason-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.history-panel {
  margin-top: 12px;
  border-top: 1px dashed var(--el-border-color);
  padding-top: 10px;
}

.history-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}

.history-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.history-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.history-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

@media (max-width: 768px) {
  .stage-card {
    margin-bottom: 12px;
  }
}
</style>
