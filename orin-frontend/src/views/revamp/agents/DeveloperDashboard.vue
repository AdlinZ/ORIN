<template>
  <div class="page-container">
    <OrinPageShell
      title="开发者工作台"
      description="我创建的智能体、最近调用链路与 API Key 快速管理"
      icon="Monitor"
      domain="智能体管理"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="loadAll">
          刷新
        </el-button>
      </template>
    </OrinPageShell>

    <OrinMetricStrip :metrics="kpiMetrics" class="stats-row" />

    <el-row :gutter="16" class="dev-grid">
      <!-- 我的智能体 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>我的智能体</span>
              <el-button link type="primary" @click="router.push(ROUTES.AGENTS.LIST)">
                查看全部
              </el-button>
            </div>
          </template>
          <OrinAsyncState :status="agentsState.status" empty-text="暂无智能体">
            <OrinDataTable compact>
              <el-table :data="summary.myAgents?.agents || []" stripe size="small">
                <el-table-column prop="name" label="名称" min-width="120" />
                <el-table-column prop="providerType" label="Provider" width="100" />
                <el-table-column prop="modelName" label="模型" min-width="120" show-overflow-tooltip />
                <el-table-column prop="mcpExposed" label="MCP 暴露" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.mcpExposed ? 'success' : 'info'">
                      {{ row.mcpExposed ? '是' : '否' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" size="small" @click="router.push(ROUTES.AGENTS.WORKSPACE)">
                      对话
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </OrinDataTable>
          </OrinAsyncState>
        </el-card>
      </el-col>

      <!-- 我的 API Key -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>我的 API Key</span>
              <el-button link type="primary" @click="router.push(ROUTES.SYSTEM.API_KEYS)">
                管理
              </el-button>
            </div>
          </template>
          <OrinAsyncState :status="keysState.status" empty-text="暂无 API Key">
            <OrinDataTable compact>
              <el-table :data="summary.myApiKeys?.keys || []" stripe size="small">
                <el-table-column prop="name" label="名称" min-width="120" />
                <el-table-column prop="keyPrefix" label="前缀" width="130">
                  <template #default="{ row }">
                    <code class="key-prefix">{{ row.keyPrefix }}***</code>
                  </template>
                </el-table-column>
                <el-table-column prop="enabled" label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.enabled ? 'success' : 'danger'">
                      {{ row.enabled ? '启用' : '禁用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="usedTokens" label="已用 Token" width="100">
                  <template #default="{ row }">
                    {{ formatTokens(row.usedTokens) }}
                  </template>
                </el-table-column>
              </el-table>
            </OrinDataTable>
          </OrinAsyncState>
        </el-card>
      </el-col>

      <!-- 最近 Trace -->
      <el-col :xs="24">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>最近调用链路</span>
              <el-button link type="primary" @click="router.push(ROUTES.MONITOR.TRACES)">
                查看全部
              </el-button>
            </div>
          </template>
          <OrinAsyncState :status="tracesState.status" empty-text="暂无 Trace 记录">
            <OrinDataTable compact>
              <el-table :data="summary.recentTraces || []" stripe size="small">
                <el-table-column prop="traceId" label="Trace ID" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-button link type="primary" size="small" @click="openTrace(row.traceId)">
                      {{ row.traceId }}
                    </el-button>
                  </template>
                </el-table-column>
                <el-table-column prop="operationName" label="操作" min-width="180" show-overflow-tooltip />
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
                      {{ row.status || '-' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="durationMs" label="耗时" width="90">
                  <template #default="{ row }">
                    {{ row.durationMs ? row.durationMs + 'ms' : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="时间" width="160">
                  <template #default="{ row }">
                    {{ row.startTime ? formatTime(row.startTime) : '-' }}
                  </template>
                </el-table-column>
              </el-table>
            </OrinDataTable>
          </OrinAsyncState>
        </el-card>
      </el-col>

      <!-- 快速入口 -->
      <el-col :xs="24">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-header">
              <span>快速入口</span>
            </div>
          </template>
          <div class="quick-links">
            <el-button
              v-for="link in summary.quickLinks"
              :key="link.path"
              type="primary"
              plain
              @click="router.push(link.path)"
            >
              {{ link.title }}
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { ROUTES } from '@/router/routes'
import { getDeveloperSummary } from '@/api/developer'

const router = useRouter()
const summary = ref({})
const loadingState = reactive({ status: 'loading' })
const agentsState = reactive({ status: 'loading' })
const keysState = reactive({ status: 'loading' })
const tracesState = reactive({ status: 'loading' })

const summaryState = computed(() => {
  const s = summary.value
  const agents = s.myAgents?.agents || []
  const keys = s.myApiKeys?.keys || []
  const traces = s.recentTraces || []
  const total = agents.length + keys.length + traces.length
  if (total > 0) return { status: 'success' }
  return loadingState
})

const kpiMetrics = computed(() => [
  {
    label: '我的智能体',
    value: summary.value.myAgents?.total ?? 0,
    meta: '已创建'
  },
  {
    label: '活跃 Key',
    value: summary.value.myApiKeys?.activeKeys ?? 0,
    meta: '启用中'
  },
  {
    label: 'API Key 总数',
    value: summary.value.myApiKeys?.total ?? 0,
    meta: '全部 Key'
  },
  {
    label: '最近 Trace',
    value: summary.value.recentTraces?.length ?? 0,
    meta: '最近 10 条'
  }
])

const loadSummary = async () => {
  loadingState.status = 'loading'
  agentsState.status = 'loading'
  keysState.status = 'loading'
  tracesState.status = 'loading'
  try {
    const res = await getDeveloperSummary()
    summary.value = res
    agentsState.status = 'success'
    keysState.status = 'success'
    tracesState.status = 'success'
    loadingState.status = 'success'
  } catch (e) {
    loadingState.status = 'error'
    agentsState.status = 'error'
    keysState.status = 'error'
    tracesState.status = 'error'
    ElMessage.error('开发者工作台加载失败')
  }
}

const loadAll = () => {
  loadSummary()
}

const openTrace = (traceId) => {
  if (traceId) {
    router.push(ROUTES.MONITOR.TRACE_DETAIL.replace(':traceId', traceId))
  }
}

const formatTokens = (val) => {
  if (!val) return '0'
  if (val >= 1000000) return (val / 1000000).toFixed(1) + 'M'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'K'
  return String(val)
}

const formatTime = (val) => {
  if (!val) return '-'
  const t = typeof val === 'string' ? val : String(val)
  const d = new Date(t)
  if (isNaN(d.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadSummary()
})
</script>

<style scoped>
.page-container {
  padding: 16px;
}

.stats-row {
  margin-bottom: 16px;
}

.dev-grid {
  margin-bottom: 16px;
}

.panel-card {
  height: 100%;
  margin-bottom: 16px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.key-prefix {
  font-size: 12px;
  color: #64748b;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
