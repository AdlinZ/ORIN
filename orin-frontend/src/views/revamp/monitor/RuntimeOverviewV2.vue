<template>
  <div class="page-container">
    <OrinPageShell
      title="监控与运维总览"
      description="统一查看运行指标、调用成功率与 Langfuse 链路状态"
      icon="Monitor"
      domain="监控与运维"
      maturity="available"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
        <el-button type="primary" @click="$router.push('/dashboard/runtime/traces')">
          查看链路追踪
        </el-button>
      </template>
    </OrinPageShell>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="活跃智能体" :value="summary.activeAgents" icon="User" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="总调用量" :value="summary.totalCalls" icon="Operation" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="总 Token" :value="summary.totalTokens" icon="Coin" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="平均延迟(ms)" :value="summary.avgLatency" icon="Timer" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="成功率" :value="`${successRate.ratio}%`" icon="CircleCheck" />
      </el-col>
      <el-col :xs="12" :sm="8" :md="6" :lg="4">
        <StatCard label="错误率" :value="`${summary.errorRate}%`" icon="Warning" />
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              链路健康度
            </div>
          </template>
          <OrinAsyncState :status="state.status" empty-text="暂无链路数据" @retry="loadData">
            <div class="metric-line">
              <span>成功调用</span>
              <strong>{{ successRate.success }}</strong>
            </div>
            <div class="metric-line">
              <span>总调用</span>
              <strong>{{ successRate.total }}</strong>
            </div>
            <div class="metric-line">
              <span>链路成功率</span>
              <el-tag :type="successRate.ratio >= 90 ? 'success' : successRate.ratio >= 60 ? 'warning' : 'danger'">
                {{ successRate.ratio }}%
              </el-tag>
            </div>
          </OrinAsyncState>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card shadow="never">
          <template #header>
            <div class="card-title">
              Langfuse
            </div>
          </template>
          <OrinAsyncState :status="state.status" empty-text="暂无 Langfuse 信息" @retry="loadData">
            <div class="metric-line">
              <span>配置状态</span>
              <el-tag :type="langfuse.configured ? 'success' : 'info'">
                {{ langfuse.configured ? '已配置' : '未配置' }}
              </el-tag>
            </div>
            <div class="metric-line">
              <span>采集状态</span>
              <el-tag :type="langfuse.enabled ? 'success' : 'warning'">
                {{ langfuse.enabled ? '已启用' : '未启用' }}
              </el-tag>
            </div>
            <p class="langfuse-text">
              {{ langfuse.message }}
            </p>
            <el-link
              v-if="langfuse.dashboardLink"
              :href="langfuse.dashboardLink"
              target="_blank"
              type="primary"
            >
              打开 Langfuse 控制台
            </el-link>
          </OrinAsyncState>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import StatCard from '@/components/StatCard.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import { getCallSuccessRate, getGlobalSummary, getLangfuseStatus } from '@/api/monitor'
import {
  createAsyncState,
  markError,
  markLoading,
  markPartial,
  markSuccess,
  toLangfuseStatusViewModel,
  toRuntimeSummaryViewModel,
  toSuccessRateViewModel
} from '@/viewmodels'

const state = reactive(createAsyncState())
const summary = reactive(toRuntimeSummaryViewModel({}))
const successRate = reactive(toSuccessRateViewModel({}))
const langfuse = reactive(toLangfuseStatusViewModel({}))

const loadData = async () => {
  markLoading(state)
  const result = await Promise.allSettled([getGlobalSummary(), getCallSuccessRate(), getLangfuseStatus()])

  const summaryResult = result[0]
  const successResult = result[1]
  const langfuseResult = result[2]

  const failed = result.filter((item) => item.status === 'rejected').length

  if (summaryResult.status === 'fulfilled') {
    Object.assign(summary, toRuntimeSummaryViewModel(summaryResult.value))
  }
  if (successResult.status === 'fulfilled') {
    Object.assign(successRate, toSuccessRateViewModel(successResult.value))
  }
  if (langfuseResult.status === 'fulfilled') {
    Object.assign(langfuse, toLangfuseStatusViewModel(langfuseResult.value))
  }

  if (failed === 0) {
    markSuccess(state)
  } else if (failed < result.length) {
    markPartial(state)
    ElMessage.warning('部分监控数据加载失败，已展示可用数据')
  } else {
    markError(state)
  }
}

onMounted(loadData)
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}

.card-title {
  font-weight: 600;
}

.metric-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.langfuse-text {
  color: var(--text-secondary);
  margin-bottom: 12px;
}
</style>
