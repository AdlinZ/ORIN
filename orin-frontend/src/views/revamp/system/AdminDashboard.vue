<template>
  <div class="page-container">
    <OrinPageShell
      title="系统设置"
      description="先判断平台治理是否需要处理，再进入用户、审计或高级运维。"
      icon="Setting"
      domain="系统设置"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="summaryState.status === 'loading'" @click="loadSummary">
          刷新
        </el-button>
      </template>
    </OrinPageShell>

    <OrinAsyncState :status="summaryState.status" @retry="loadSummary">
      <section :class="['governance-decision', `decision-${governanceDecision.intent}`]">
        <div>
          <span class="eyebrow">当前治理状态</span>
          <h2>{{ governanceDecision.title }}</h2>
          <p>{{ governanceDecision.description }}</p>
        </div>
        <el-button
          :type="governanceDecision.intent === 'danger' ? 'danger' : 'primary'"
          @click="router.push(governanceDecision.path)"
        >
          {{ governanceDecision.action }}
        </el-button>
      </section>

      <section class="governance-facts" aria-label="治理事实">
        <article v-for="fact in governanceFacts" :key="fact.label">
          <div class="fact-head">
            <span>{{ fact.label }}</span>
            <el-tag size="small" :type="fact.intent">
              {{ fact.state }}
            </el-tag>
          </div>
          <strong>{{ fact.value }}</strong>
          <p>{{ fact.meta }}</p>
        </article>
      </section>

      <section class="settings-grid" aria-label="系统设置入口">
        <article class="settings-card">
          <div>
            <span class="card-kicker">日常治理</span>
            <h3>用户与访问</h3>
            <p>创建、停用和检查组织账号。部门与角色作为用户维护中的辅助信息，不再单列主入口。</p>
          </div>
          <el-button type="primary" plain @click="router.push(ROUTES.SYSTEM.USERS)">
            管理用户
          </el-button>
        </article>

        <article class="settings-card">
          <div>
            <span class="card-kicker">问题追溯</span>
            <h3>审计记录</h3>
            <p>默认只查看访问与变更记录。保留策略、历史清理和 Logger 调试已移入高级设置。</p>
          </div>
          <el-button type="primary" plain @click="router.push(ROUTES.SYSTEM.AUDIT_LOGS)">
            查看记录
          </el-button>
        </article>

        <article class="settings-card advanced-card">
          <div>
            <span class="card-kicker">低频 · 高风险</span>
            <h3>高级运维</h3>
            <p>数据库、队列、存储与日志策略会影响整个平台。仅在部署变更或排障时进入。</p>
          </div>
          <div class="advanced-actions">
            <el-button @click="router.push(ROUTES.SYSTEM.SETTINGS_MONITOR)">
              环境参数
            </el-button>
            <el-button @click="router.push(ROUTES.SYSTEM.AUDIT_SETTINGS)">
              审计策略
            </el-button>
          </div>
        </article>
      </section>

      <section class="recent-panel">
        <div class="section-head">
          <div>
            <h3>最近异常请求</h3>
            <p>这里只保留最近的失败证据；完整行为记录进入审计记录查看。</p>
          </div>
          <el-button link type="primary" @click="router.push(ROUTES.SYSTEM.AUDIT_LOGS)">
            查看全部审计
          </el-button>
        </div>

        <OrinAsyncState :status="recentState.status" empty-text="最近没有异常请求">
          <OrinDataTable compact>
            <el-table :data="summary.topAlertEvents" stripe size="small">
              <el-table-column prop="method" label="方法" width="82" />
              <el-table-column prop="endpoint" label="接口" min-width="220" show-overflow-tooltip />
              <el-table-column prop="statusCode" label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.statusCode >= 500 ? 'danger' : 'warning'">
                    {{ row.statusCode || '-' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" width="160">
                <template #default="{ row }">
                  {{ formatTime(row.createdAt) }}
                </template>
              </el-table-column>
            </el-table>
          </OrinDataTable>
        </OrinAsyncState>
      </section>
    </OrinAsyncState>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { getDashboardSummary } from '@/api/dashboard'
import { ROUTES } from '@/router/routes'
import { toDashboardSummaryViewModel } from '@/viewmodels'

const router = useRouter()
const summaryState = reactive({ status: 'loading' })
const summary = ref(toDashboardSummaryViewModel())

const governanceDecision = computed(() => {
  const activeAlerts = summary.value.adminStats.activeAlerts
  if (activeAlerts > 0) {
    return {
      title: `${activeAlerts} 条运行告警需要关注`,
      description: '先确认告警是否仍在影响运行，再决定是否调整平台配置。',
      action: '查看运行告警',
      path: ROUTES.MONITOR.ALERTS,
      intent: 'danger'
    }
  }

  const failedRequests = summary.value.topAlertEvents.length
  if (failedRequests > 0) {
    return {
      title: `${failedRequests} 条最近失败请求待追溯`,
      description: '平台没有触发中告警，但最近请求留下了失败记录。',
      action: '查看审计记录',
      path: ROUTES.SYSTEM.AUDIT_LOGS,
      intent: 'warning'
    }
  }

  return {
    title: '当前没有待处理的治理异常',
    description: '日常只需维护用户与审计记录；环境参数无需频繁调整。',
    action: '管理用户',
    path: ROUTES.SYSTEM.USERS,
    intent: 'success'
  }
})

const governanceFacts = computed(() => {
  const backendReady = summary.value.systemHealth.backend.status === 'UP'
  const aiReady = summary.value.systemHealth.aiEngine.reachable

  return [
    {
      label: '平台账户',
      state: '可管理',
      value: summary.value.adminStats.totalUsers,
      meta: '当前系统账户',
      intent: 'success'
    },
    {
      label: '访问密钥',
      state: '已登记',
      value: summary.value.adminStats.totalApiKeys,
      meta: '平台 API Key',
      intent: 'info'
    },
    {
      label: 'Java 后端',
      state: backendReady ? '正常' : '需检查',
      value: summary.value.systemHealth.backend.status,
      meta: '业务与持久化主控层',
      intent: backendReady ? 'success' : 'danger'
    },
    {
      label: 'AI Engine',
      state: aiReady ? '可达' : '不可达',
      value: summary.value.systemHealth.aiEngine.status,
      meta: '工作流与协作执行层',
      intent: aiReady ? 'success' : 'danger'
    }
  ]
})

const recentState = computed(() => ({
  status: summary.value.topAlertEvents.length > 0 ? 'success' : 'empty'
}))

const loadSummary = async () => {
  summaryState.status = 'loading'
  try {
    summary.value = toDashboardSummaryViewModel(await getDashboardSummary())
    summaryState.status = 'success'
  } catch (error) {
    summaryState.status = 'error'
    ElMessage.error('系统设置概览加载失败')
  }
}

const formatTime = (value) => {
  const parsed = dayjs(value)
  return value && parsed.isValid() ? parsed.format('MM-DD HH:mm') : '-'
}

onMounted(loadSummary)
</script>

<style scoped>
.page-container {
  padding: 16px;
}

.governance-decision {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 22px 24px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  border-left-width: 4px;
  border-radius: 12px;
  background: var(--el-bg-color);
}

.decision-danger {
  border-left-color: var(--el-color-danger);
}

.decision-warning {
  border-left-color: var(--el-color-warning);
}

.decision-success {
  border-left-color: var(--el-color-success);
}

.eyebrow,
.card-kicker {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.governance-decision h2,
.settings-card h3,
.recent-panel h3 {
  margin: 6px 0;
}

.governance-decision p,
.settings-card p,
.section-head p,
.governance-facts p {
  margin: 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.governance-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.governance-facts article,
.settings-card,
.recent-panel {
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.governance-facts article {
  padding: 16px;
}

.fact-head,
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.governance-facts strong {
  display: block;
  margin: 12px 0 4px;
  font-size: 24px;
}

.governance-facts p {
  font-size: 13px;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.settings-card {
  display: flex;
  min-height: 190px;
  flex-direction: column;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 20px;
}

.advanced-card {
  border-style: dashed;
  background: var(--el-fill-color-lighter);
}

.advanced-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.recent-panel {
  padding: 20px;
}

.section-head {
  margin-bottom: 16px;
}

@media (max-width: 992px) {
  .governance-facts,
  .settings-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .governance-decision,
  .section-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .governance-facts,
  .settings-grid {
    grid-template-columns: 1fr;
  }
}
</style>
