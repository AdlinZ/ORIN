<template>
  <div class="unified-gateway-workbench page-container fade-in">
    <section class="gateway-console">
      <header class="gateway-hero">
        <div class="gateway-hero-row">
          <div class="gateway-hero-main">
            <div class="gateway-icon">
              <el-icon><Connection /></el-icon>
            </div>
            <div class="gateway-title-block">
              <h1>统一网关</h1>
              <p>模型 API、后台控制面、服务代理、访问凭据与流量保护的统一网关控制台。</p>
            </div>
          </div>

          <div class="gateway-hero-actions">
            <el-button
              :icon="Refresh"
              :loading="workbenchState.status === 'loading'"
              @click="refreshCurrentWorkspace"
            >
              刷新
            </el-button>
            <el-button type="primary" :icon="Connection" @click="openRouteTest">
              入口测试
            </el-button>
          </div>
        </div>

        <div class="gateway-summary" data-testid="gateway-workspaces">
          <button
            v-for="item in workspaces"
            :key="item.key"
            type="button"
            :class="['gateway-summary-card', { active: activeWorkspace === item.key }]"
            @click="activeWorkspace = item.key"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>
              <strong>{{ item.label }}</strong>
              <small>{{ workspaceSummaryMap[item.key] }}</small>
            </span>
          </button>
        </div>
      </header>

      <section class="gateway-content-panel">
        <section v-show="activeWorkspace === 'overview'" class="workspace-panel">
          <OrinAsyncState
            :status="workbenchState.status"
            empty-text="暂无统一网关运行数据，请先创建入口或验证入口流量"
            empty-action-label="刷新"
            @retry="loadWorkbench"
            @empty-action="loadWorkbench"
          >
            <section class="runtime-hero">
              <div class="runtime-hero-main">
                <div class="runtime-hero-head">
                  <div>
                    <span class="command-eyebrow">运行总览</span>
                    <h2>统一网关运行态</h2>
                  </div>
                  <span class="hero-updated">实时工作台数据</span>
                </div>

                <div class="hero-metric-grid" aria-label="统一网关核心运行指标">
                  <article
                    v-for="metric in heroMetrics"
                    :key="metric.key"
                    class="hero-metric-card"
                    :class="metric.intent ? `intent-${metric.intent}` : ''"
                  >
                    <span>{{ metric.label }}</span>
                    <strong>{{ metric.value }}</strong>
                    <small>{{ metric.meta }}</small>
                  </article>
                </div>
              </div>

              <aside class="operations-card" :class="`intent-${operationsSummary.intent}`">
                <span class="command-eyebrow">运行结论</span>
                <h3>{{ operationsSummary.title }}</h3>
                <p>{{ operationsSummary.description }}</p>
                <div class="operation-facts">
                  <span>{{ operationsSummary.badge }}</span>
                  <strong>{{ operationsSummary.summary }}</strong>
                </div>
                <div class="command-actions">
                  <el-button
                    v-for="action in primaryActions"
                    :key="action.key"
                    size="small"
                    :type="action.type"
                    @click="action.handler"
                  >
                    {{ action.label }}
                  </el-button>
                </div>
              </aside>
            </section>

            <div class="overview-secondary-grid block-gap">
              <OrinDetailPanel title="入口状态" eyebrow="链路分布">
                <div class="entry-map compact" aria-label="统一网关入口状态">
                  <article
                    v-for="lane in entryLanes"
                    :key="lane.key"
                    class="entry-lane"
                    :class="`lane-${lane.intent}`"
                  >
                    <div class="lane-head">
                      <span>{{ lane.label }}</span>
                      <strong>{{ lane.value }}</strong>
                    </div>
                    <p>{{ lane.meta }}</p>
                  </article>
                </div>
              </OrinDetailPanel>

              <OrinDetailPanel title="快速操作" eyebrow="入口动作">
                <div class="quick-action-grid">
                  <button
                    v-for="action in quickActions"
                    :key="action.key"
                    type="button"
                    @click="action.handler"
                  >
                    <span>{{ action.label }}</span>
                    <small>{{ action.description }}</small>
                  </button>
                </div>
              </OrinDetailPanel>
            </div>

            <div class="overview-grid block-gap">
              <OrinDataTable>
                <template #header>
                  <div class="table-head">
                    <span>最近失败</span>
                    <el-button
                      size="small"
                      text
                      type="primary"
                      @click="activeWorkspace = 'api'"
                    >
                      定位入口
                    </el-button>
                  </div>
                </template>
                <el-table :data="workbench.recentFailures || []" stripe>
                  <el-table-column prop="method" label="方法" width="82" />
                  <el-table-column
                    prop="path"
                    label="路径"
                    min-width="180"
                    show-overflow-tooltip
                  />
                  <el-table-column prop="statusCode" label="状态码" width="90" />
                  <el-table-column
                    prop="errorMessage"
                    label="错误"
                    min-width="180"
                    show-overflow-tooltip
                  />
                  <template #empty>
                    <OrinEmptyState description="暂无失败请求，当前网关没有需要优先处理的异常" />
                  </template>
                </el-table>
              </OrinDataTable>

              <OrinDataTable>
                <template #header>
                  <div class="table-head">
                    <span>需要处理的入口</span>
                    <span>{{ attentionEndpointCount }} 个入口需要处理</span>
                  </div>
                </template>
                <el-table :data="controlPlaneEndpointPreview" stripe>
                  <el-table-column label="状态" width="130">
                    <template #default="{ row }">
                      <el-tag size="small" :type="coverageStatusType(row.status)">
                        {{ coverageStatusLabel(row.status) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="pathPattern"
                    label="入口路径"
                    min-width="220"
                    show-overflow-tooltip
                  />
                  <el-table-column label="方法" width="120">
                    <template #default="{ row }">
                      {{ formatMethods(row.methods) }}
                    </template>
                  </el-table-column>
                  <el-table-column
                    prop="reason"
                    label="说明"
                    min-width="220"
                    show-overflow-tooltip
                  />
                  <template #empty>
                    <OrinEmptyState description="当前没有需要处理的入口异常" />
                  </template>
                </el-table>
              </OrinDataTable>
            </div>

            <section class="runtime-section block-gap">
              <div class="section-title">
                <span>巡检指标</span>
                <small>用于补充判断，不抢占首屏核心指标</small>
              </div>
              <OrinMetricStrip :metrics="secondaryRuntimeMetrics" />
              <OrinStatusSummary :items="statusItems" class="block-gap" />
            </section>

            <OrinDataTable class="block-gap">
              <template #header>
                <div class="table-head">
                  <span>热门入口 TOP 5</span>
                  <span>{{ workbench.overview?.activeRoutes || 0 }} 个活跃入口</span>
                </div>
              </template>
              <el-table :data="workbench.overview?.topRoutes || []" stripe>
                <el-table-column prop="routeName" label="入口" min-width="180" />
                <el-table-column
                  prop="requestCount"
                  label="请求数"
                  width="120"
                  align="right"
                />
                <el-table-column label="平均延迟" width="140" align="right">
                  <template #default="{ row }">
                    {{ row.avgLatencyMs || 0 }}ms
                  </template>
                </el-table-column>
                <template #empty>
                  <OrinEmptyState description="暂无入口流量，请先通过入口测试或客户端发起请求" />
                </template>
              </el-table>
            </OrinDataTable>
          </OrinAsyncState>
        </section>

        <section v-if="activeWorkspace === 'api'" class="workspace-panel">
          <OrinDataTable>
            <template #header>
              <div class="table-head">
                <span>统一入口</span>
                <div class="head-actions">
                  <el-button size="small" :icon="Refresh" @click="loadRoutes">
                    刷新
                  </el-button>
                  <el-button
                    size="small"
                    type="primary"
                    :icon="Connection"
                    @click="openRouteTest"
                  >
                    测试入口
                  </el-button>
                </div>
              </div>
            </template>
            <OrinAsyncState
              :status="routesState.status"
              empty-text="暂无入口配置，请添加模型 API、服务代理或后台入口策略"
              @retry="loadRoutes"
            >
              <el-table :data="routes" stripe>
                <el-table-column prop="name" label="入口名称" min-width="140" />
                <el-table-column label="路径/方法" min-width="220" show-overflow-tooltip>
                  <template #default="{ row }">
                    <el-tag size="small" effect="plain">
                      {{ row.method || 'ALL' }}
                    </el-tag>
                    <span class="path-text">{{ row.pathPattern }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="入口类型" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" :type="entryType(row).tag">
                      {{ entryType(row).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="目标" min-width="200" show-overflow-tooltip>
                  <template #default="{ row }">
                    <span class="target-text">{{ routeTarget(row) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="策略" width="150">
                  <template #default="{ row }">
                    <el-space wrap>
                      <el-tag v-if="row.authRequired" size="small" effect="plain">
                        认证
                      </el-tag>
                      <el-tag
                        v-if="row.rateLimitPolicyId"
                        size="small"
                        type="warning"
                        effect="plain"
                      >
                        限流
                      </el-tag>
                      <el-tag
                        v-if="row.circuitBreakerPolicyId"
                        size="small"
                        type="danger"
                        effect="plain"
                      >
                        熔断
                      </el-tag>
                      <el-tag
                        v-if="row.retryPolicyId"
                        size="small"
                        type="success"
                        effect="plain"
                      >
                        重试
                      </el-tag>
                    </el-space>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="92">
                  <template #default="{ row }">
                    <el-switch v-model="row.enabled" @change="toggleRoute(row)" />
                  </template>
                </el-table-column>
                <el-table-column label="诊断" width="120" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" @click="openRouteDetail(row)">
                      生效链路
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </OrinAsyncState>
          </OrinDataTable>

          <OrinDataTable class="block-gap">
            <template #header>
              <div class="table-head">
                <span>后台入口配置</span>
                <span>{{ attentionControlPlaneEndpoints.length }} 个入口需要处理</span>
              </div>
            </template>
            <el-table :data="attentionControlPlaneEndpoints" stripe>
              <el-table-column
                prop="pathPattern"
                label="入口路径"
                min-width="240"
                show-overflow-tooltip
              />
              <el-table-column label="方法" width="120">
                <template #default="{ row }">
                  {{ formatMethods(row.methods) }}
                </template>
              </el-table-column>
              <el-table-column
                prop="reason"
                label="需要处理"
                min-width="220"
                show-overflow-tooltip
              />
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" @click="createLocalControlPlaneRoute(row)">
                    添加单独配置
                  </el-button>
                </template>
              </el-table-column>
              <template #empty>
                <OrinEmptyState description="当前没有需要处理的后台入口" />
              </template>
            </el-table>
          </OrinDataTable>

          <div class="api-entry-grid block-gap">
            <UnifiedGatewayRoutesTab mode="actions" />
            <UnifiedGatewayServicesTab />
          </div>
        </section>

        <section v-if="activeWorkspace === 'access'" class="workspace-panel access-workspace">
          <ApiKeyManagement embedded />
          <div class="access-list-section block-gap">
            <div class="workspace-section-head">
              <span class="command-eyebrow">访问名单</span>
              <h3>ACL 与 API Key 要求</h3>
              <p>按 IP、路径和凭据要求控制哪些调用方可以进入统一网关。</p>
            </div>
            <UnifiedGatewayAclTab />
          </div>
        </section>

        <section v-if="activeWorkspace === 'traffic'" class="workspace-panel traffic-workspace">
          <div class="workspace-section-head">
            <span class="command-eyebrow">入口策略</span>
            <h3>限流、熔断与重试</h3>
            <p>维护可复用的入口级策略，并在统一入口中绑定到具体路径。</p>
          </div>
          <UnifiedGatewayPoliciesTab />

          <div class="workspace-section-head block-gap">
            <span class="command-eyebrow">平台底线</span>
            <h3>系统级默认限流</h3>
            <p>配置统一网关的全局保护阈值，作为入口策略之外的默认防线。</p>
          </div>
          <UnifiedGatewayRateLimitTab />
        </section>
      </section>
    </section>

    <el-drawer v-model="routeDrawerVisible" title="入口生效链路" size="520px">
      <OrinAsyncState :status="detailState.status" empty-text="请选择一个入口查看生效配置" @retry="reloadSelectedRoute">
        <template v-if="effectiveConfig">
          <OrinDetailPanel :title="effectiveConfig.route?.name" :eyebrow="effectiveConfig.targetType">
            <div class="route-summary">
              <span>{{ effectiveConfig.route?.method || 'ALL' }}</span>
              <strong>{{ effectiveConfig.route?.pathPattern }}</strong>
            </div>
            <el-alert
              v-for="warning in effectiveConfig.warnings || []"
              :key="warning"
              type="warning"
              :closable="false"
              class="warning-line"
              :title="warning"
            />
          </OrinDetailPanel>

          <ol class="chain-list">
            <li v-for="step in effectiveConfig.chain || []" :key="step.key">
              <span class="chain-label">{{ step.label }}</span>
              <el-tag size="small" effect="plain">
                {{ step.status }}
              </el-tag>
              <p>{{ step.detail }}</p>
            </li>
          </ol>

          <OrinDetailPanel title="目标实例" class="block-gap">
            <el-table :data="effectiveConfig.allInstances || []" size="small">
              <el-table-column prop="host" label="主机" />
              <el-table-column prop="port" label="端口" width="80" />
              <el-table-column prop="status" label="状态" width="90" />
              <template #empty>
                <OrinEmptyState description="此入口由后台处理或直连目标承接，没有服务实例" />
              </template>
            </el-table>
          </OrinDetailPanel>
        </template>
      </OrinAsyncState>
    </el-drawer>

    <el-dialog v-model="testDialogVisible" title="入口诊断测试器" width="560px">
      <el-form :model="testForm" label-width="90px">
        <el-form-item label="请求路径">
          <el-input v-model="testForm.path" placeholder="/api/v1/example" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="testForm.method" style="width: 100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="PATCH" value="PATCH" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="testResult"
        :type="testResult.success ? 'success' : 'warning'"
        :closable="false"
        class="block-gap"
      >
        <template #title>
          <span v-if="testResult.success">
            匹配 {{ testResult.matchedRoute }}，目标 {{ testResult.targetUrl || testResult.targetService || 'ORIN 后台处理' }}
          </span>
          <span v-else>未匹配任何入口</span>
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="testDialogVisible = false">
          关闭
        </el-button>
        <el-button type="primary" :loading="testing" @click="runTest">
          测试
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Connection,
  Key,
  Operation,
  Refresh,
  Share,
  TrendCharts
} from '@element-plus/icons-vue'
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue'
import OrinStatusSummary from '@/components/orin/OrinStatusSummary.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDetailPanel from '@/components/orin/OrinDetailPanel.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import UnifiedGatewayRoutesTab from './components/gateway/UnifiedGatewayRoutesTab.vue'
import UnifiedGatewayServicesTab from './components/gateway/UnifiedGatewayServicesTab.vue'
import UnifiedGatewayAclTab from './components/gateway/UnifiedGatewayAclTab.vue'
import UnifiedGatewayPoliciesTab from './components/gateway/UnifiedGatewayPoliciesTab.vue'
import UnifiedGatewayRateLimitTab from './components/gateway/UnifiedGatewayRateLimitTab.vue'
import ApiKeyManagement from './ApiKeyManagement.vue'
import { useUnifiedGatewayWorkbench } from './composables/useUnifiedGatewayWorkbench'
import { useUnifiedGatewayRoutes } from './composables/useUnifiedGatewayRoutes'
import { useUnifiedGatewayPolicies } from './composables/useUnifiedGatewayPolicies'

const workspaces = [
  { key: 'overview', label: '运行态', icon: TrendCharts },
  { key: 'api', label: '统一入口', icon: Share },
  { key: 'access', label: '访问控制', icon: Key },
  { key: 'traffic', label: '流量策略', icon: Operation }
]

const activeWorkspace = ref('overview')
const routeDrawerVisible = ref(false)
const selectedRouteId = ref(null)
const testDialogVisible = ref(false)
const testing = ref(false)
const testForm = reactive({ path: '', method: 'GET' })

const {
  state: workbenchState,
  workbench,
  metrics,
  statusItems,
  loadWorkbench
} = useUnifiedGatewayWorkbench()

const {
  state: routesState,
  routes,
  controlPlaneCoverage,
  effectiveConfig,
  detailState,
  testResult,
  loadRoutes,
  loadEffectiveConfig,
  toggleRoute,
  runRouteTest,
  createLocalControlPlaneRoute
} = useUnifiedGatewayRoutes()

const { loadPolicies } = useUnifiedGatewayPolicies()

const controlPlaneEndpointPreview = computed(() => {
  const endpoints = workbench.value.controlPlaneCoverage?.endpoints || []
  return [
    ...endpoints.filter((item) => item.status === 'ATTENTION_REQUIRED')
  ].slice(0, 8)
})

const attentionControlPlaneEndpoints = computed(() =>
  (controlPlaneCoverage.value.endpoints || []).filter((item) => item.status === 'ATTENTION_REQUIRED')
)

const workbenchAttentionEndpoints = computed(() =>
  (workbench.value.controlPlaneCoverage?.endpoints || []).filter((item) => item.status === 'ATTENTION_REQUIRED')
)

const coverageSummary = computed(() => workbench.value.controlPlaneCoverage?.summary || {})

const attentionEndpointCount = computed(() => coverageSummary.value.attentionRequiredEndpoints || 0)

const workspaceSummaryMap = computed(() => {
  const overview = workbench.value.overview || {}
  const activeRoutes = overview.activeRoutes || 0
  const attention = attentionEndpointCount.value
  return {
    overview: `${formatNumber(overview.totalRequests)} 请求 · ${overview.avgLatencyMs ?? 0}ms`,
    api: `${activeRoutes} 个活跃入口 · ${attention} 个待处理`,
    access: 'API Key、ACL 与凭据要求',
    traffic: '限流、熔断、重试与默认防线'
  }
})

const heroMetrics = computed(() => {
  const overview = workbench.value.overview || {}
  const healthy = overview.healthyInstances ?? 0
  const unhealthy = overview.unhealthyInstances ?? 0
  const totalInstances = healthy + unhealthy
  return [
    { key: 'requests', label: '总请求数', value: formatNumber(overview.totalRequests), meta: '累计网关流量' },
    { key: 'qps', label: '当前 QPS', value: overview.qps ?? 0, meta: '最近 6 分钟估算' },
    { key: 'latency', label: '平均延迟', value: `${overview.avgLatencyMs ?? 0}ms`, meta: '近 1 小时成功请求' },
    {
      key: 'errorRate',
      label: '错误率',
      value: `${overview.errorRate ?? 0}%`,
      meta: '异常请求占比',
      intent: Number(overview.errorRate || 0) > 0 ? 'danger' : 'success'
    },
    {
      key: 'activeRoutes',
      label: '活跃入口',
      value: overview.activeRoutes ?? 0,
      meta: '当前参与匹配的入口数量',
      intent: (overview.activeRoutes ?? 0) > 0 ? 'success' : 'warning'
    },
    {
      key: 'serviceHealth',
      label: '服务健康',
      value: `${healthy}/${totalInstances}`,
      meta: unhealthy > 0 ? `${unhealthy} 个异常实例` : '实例健康状态正常',
      intent: unhealthy > 0 ? 'danger' : 'success'
    }
  ]
})

const operationsSummary = computed(() => {
  const attention = attentionEndpointCount.value
  const failures = workbench.value.recentFailures?.length || 0
  const unhealthy = workbench.value.overview?.unhealthyInstances || 0
  const totalEvents = attention + failures + unhealthy
  if (totalEvents > 0) {
    return {
      title: `${totalEvents} 个入口问题需要处理`,
      description: '核心运行指标仍放在首位；这里只收敛会影响调用成功率、访问安全或上游健康的事项。',
      badge: '待处理项',
      summary: buildFocusSummary(attention, failures, unhealthy),
      intent: 'warning'
    }
  }
  return {
    title: '入口运行正常',
    description: '模型 API、服务代理和后台控制面没有失败诊断。下一步通常是测试核心入口或补充单独配置。',
    badge: '当前结论',
    summary: '没有需要立即处理的入口异常',
    intent: 'success'
  }
})

const entryLanes = computed(() => [
  {
    key: 'open-api',
    label: '开放能力面 /v1',
    value: `${workbench.value.overview?.activeRoutes || 0} 个入口`,
    meta: '模型 API 与 OpenAI 兼容入口。',
    intent: 'success'
  },
  {
    key: 'control-plane',
    label: '后台控制面 /api/v1',
    value: `${coverageSummary.value.baselineGovernedEndpoints || 0}/${coverageSummary.value.totalEndpoints || 0}`,
    meta: attentionEndpointCount.value > 0
      ? `${attentionEndpointCount.value} 个入口需要处理。`
      : '后台入口默认经过基础保护链路。',
    intent: attentionEndpointCount.value > 0 ? 'warning' : 'success'
  },
  {
    key: 'rescue',
    label: '救援入口',
    value: coverageSummary.value.rescueReservedEndpoints || 0,
    meta: '登录、健康检查、统一网关修复入口保留直连，配置错误时仍能救回系统。',
    intent: 'neutral'
  }
])

const primaryActions = computed(() => {
  if (attentionEndpointCount.value > 0) {
    return [
      { key: 'policy', label: '添加单独配置', type: 'primary', handler: handlePrimaryEntryAction },
      { key: 'test', label: '测试入口', type: 'default', handler: openRouteTest }
    ]
  }
  if ((workbench.value.recentFailures?.length || 0) > 0) {
    return [
      { key: 'test', label: '测试入口', type: 'primary', handler: openRouteTest },
      { key: 'api', label: '查看统一入口', type: 'default', handler: () => { activeWorkspace.value = 'api' } }
    ]
  }
  return [
    { key: 'test', label: '测试入口', type: 'primary', handler: openRouteTest },
    { key: 'traffic', label: '查看流量策略', type: 'default', handler: () => { activeWorkspace.value = 'traffic' } }
  ]
})

const quickActions = computed(() => [
  {
    key: 'test',
    label: '测试入口',
    description: '输入路径和方法，确认匹配、策略和目标。',
    handler: openRouteTest
  },
  {
    key: 'open-api',
    label: '配置统一入口',
    description: '维护 /v1、后台控制面或服务代理入口。',
    handler: () => { activeWorkspace.value = 'api' }
  },
  {
    key: 'proxy',
    label: '配置上游服务',
    description: '把上游服务、实例和健康检查接入统一入口。',
    handler: () => { activeWorkspace.value = 'api' }
  },
  {
    key: 'traffic',
    label: '流量策略',
    description: '维护限流、熔断、重试和平台底线。',
    handler: () => { activeWorkspace.value = 'traffic' }
  }
])

const secondaryRuntimeMetrics = computed(() =>
  metrics.value.filter((metric) => metric.key === 'coverage')
)

watch(activeWorkspace, (workspace) => {
  if (workspace === 'overview') loadWorkbench()
  if (workspace === 'api') loadRoutes()
  if (workspace === 'traffic') loadPolicies()
})

onMounted(() => {
  loadWorkbench()
})

const refreshCurrentWorkspace = () => {
  if (activeWorkspace.value === 'overview') loadWorkbench()
  if (activeWorkspace.value === 'api') loadRoutes()
  if (activeWorkspace.value === 'traffic') loadPolicies()
}

const openRouteDetail = async (row) => {
  selectedRouteId.value = row.id
  routeDrawerVisible.value = true
  await loadEffectiveConfig(row.id)
}

const reloadSelectedRoute = () => {
  if (selectedRouteId.value) loadEffectiveConfig(selectedRouteId.value)
}

function openRouteTest() {
  testDialogVisible.value = true
}

function buildFocusSummary(attention, failures, unhealthy) {
  const parts = []
  if (failures > 0) parts.push(`${failures} 条失败`)
  if (attention > 0) parts.push(`${attention} 个策略问题`)
  if (unhealthy > 0) parts.push(`${unhealthy} 个上游异常`)
  return parts.join('，')
}

function formatNumber(value) {
  const num = Number(value || 0)
  if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`
  if (num >= 1000) return `${(num / 1000).toFixed(1)}K`
  return String(num)
}

async function handlePrimaryEntryAction() {
  const firstAttention = workbenchAttentionEndpoints.value[0]
  if (firstAttention) {
    await createLocalControlPlaneRoute(firstAttention)
    await loadWorkbench()
    activeWorkspace.value = 'api'
    return
  }
  if (workbench.value.recentFailures?.length) {
    activeWorkspace.value = 'api'
    return
  }
  openRouteTest()
}

const runTest = async () => {
  if (!testForm.path) {
    ElMessage.warning('请输入请求路径')
    return
  }
  testing.value = true
  try {
    await runRouteTest(testForm)
  } finally {
    testing.value = false
  }
}

const entryType = (row) => {
  if (row.pathPattern?.startsWith('/v1')) return { label: '开放 API', tag: 'success' }
  if (row.targetUrl) return { label: '直连上游', tag: 'success' }
  if (row.serviceId) return { label: '服务代理', tag: 'primary' }
  return { label: '后台控制面', tag: 'info' }
}

const routeTarget = (row) => {
  if (row.targetUrl) return row.targetUrl
  if (row.serviceName) return row.serviceName
  if (row.serviceId) return `Service#${row.serviceId}`
  return 'ORIN 后台处理'
}

const formatMethods = (methods = []) => {
  if (!methods.length) return 'ALL'
  return methods.join(', ')
}

const coverageStatusLabel = (status) => {
  if (status === 'POLICY_ENFORCED') return '单独配置'
  if (status === 'BASELINE_GOVERNED') return '基础保护'
  if (status === 'RESCUE_RESERVED') return '救援保留'
  return '需要处理'
}

const coverageStatusType = (status) => {
  if (status === 'POLICY_ENFORCED' || status === 'BASELINE_GOVERNED') return 'success'
  if (status === 'RESCUE_RESERVED') return 'info'
  return 'warning'
}
</script>

<style scoped>
.unified-gateway-workbench {
  color: #243244;
}

.gateway-console {
  overflow: visible;
  border: 1px solid var(--orin-border, #e2e8f0);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
  box-shadow: 0 14px 36px -34px rgba(15, 23, 42, 0.5);
}

.gateway-hero {
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--orin-border, #e2e8f0);
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.8), rgba(255, 255, 255, 0.96) 48%),
    var(--neutral-white, #ffffff);
}

.gateway-hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.gateway-hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.gateway-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(240, 253, 250, 0.78);
  color: var(--orin-primary, #0d9488);
  font-size: 18px;
}

.gateway-title-block {
  min-width: 0;
}

.gateway-title-block h1 {
  margin: 0;
  color: #0f172a;
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: 0;
}

.gateway-title-block p {
  margin: 7px 0 0;
  max-width: 780px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.gateway-hero-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.gateway-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.gateway-summary-card {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.gateway-summary-card:hover,
.gateway-summary-card.active {
  border-color: rgba(15, 118, 110, 0.22);
  background: #ffffff;
  box-shadow: 0 8px 18px -16px rgba(15, 23, 42, 0.45);
}

.gateway-summary-card .el-icon {
  margin-top: 2px;
  color: var(--orin-primary, #0d9488);
}

.gateway-summary-card span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.gateway-summary-card strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.gateway-summary-card small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gateway-content-panel {
  padding: 14px;
  background: transparent;
  overflow: visible;
}

.workspace-panel {
  margin-top: 0;
}

.command-eyebrow {
  display: block;
  color: #0f766e;
  font-size: 12px;
  font-weight: 800;
}

.runtime-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.7fr) minmax(300px, 0.8fr);
  gap: 16px;
  align-items: stretch;
}

.runtime-hero-main,
.operations-card {
  min-width: 0;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.runtime-hero-main {
  padding: 16px;
}

.runtime-hero-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.runtime-hero-head h2,
.operations-card h3 {
  margin: 6px 0 0;
  color: #172033;
  letter-spacing: 0;
}

.runtime-hero-head h2 {
  font-size: 20px;
  line-height: 1.25;
}

.hero-updated {
  flex: 0 0 auto;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.hero-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr));
  gap: 10px;
}

.hero-metric-card {
  min-width: 0;
  min-height: 112px;
  padding: 14px;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.hero-metric-card span,
.hero-metric-card small,
.operation-facts span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
}

.hero-metric-card span,
.operation-facts span {
  font-weight: 800;
}

.hero-metric-card strong {
  display: block;
  margin: 10px 0 8px;
  color: #172033;
  font-size: 28px;
  line-height: 1;
}

.hero-metric-card.intent-success strong {
  color: #047857;
}

.hero-metric-card.intent-warning strong {
  color: #b45309;
}

.hero-metric-card.intent-danger {
  border-color: #fecaca;
  background: #fff7f7;
}

.hero-metric-card.intent-danger strong {
  color: #dc2626;
}

.operations-card {
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.operations-card h3 {
  font-size: 17px;
  line-height: 1.35;
}

.operations-card p {
  margin: 0;
  color: #53657d;
  font-size: 13px;
  line-height: 1.65;
}

.operation-facts {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #f8fafc;
}

.operation-facts strong {
  color: #172033;
  font-size: 15px;
  line-height: 1.35;
}

.operations-card.intent-warning .operation-facts {
  border-color: #f1b84b;
  background: #fffaf0;
}

.operations-card.intent-warning .operation-facts strong {
  color: #b45309;
}

.operations-card.intent-success .operation-facts {
  border-color: #b6ead6;
  background: #f0fdf7;
}

.operations-card.intent-success .operation-facts strong {
  color: #047857;
}

.command-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.overview-secondary-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.entry-map {
  display: grid;
  gap: 10px;
  padding: 16px;
}

.entry-map.compact {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.entry-lane {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid #dbe4ee;
  border-radius: 8px;
  background: #ffffff;
}

.lane-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.lane-head span {
  color: #475569;
  font-size: 13px;
  font-weight: 800;
}

.lane-head strong {
  color: #172033;
  font-size: 18px;
  line-height: 1;
  text-align: right;
}

.entry-lane p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.55;
}

.lane-warning {
  border-color: #f8c26a;
  background: #fffaf0;
}

.lane-warning .lane-head strong {
  color: #d97706;
}

.lane-success .lane-head strong {
  color: #059669;
}

.block-gap {
  margin-top: 16px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(260px, 0.75fr) minmax(420px, 1.25fr);
  gap: 16px;
}

.table-head,
.head-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.table-head {
  width: 100%;
  color: #334155;
  font-size: 14px;
  font-weight: 700;
}

.table-head > span:last-child {
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
}

.quick-action-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 14px 16px 16px;
}

.quick-action-grid button {
  display: grid;
  gap: 6px;
  min-height: 76px;
  padding: 12px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.quick-action-grid button:hover {
  border-color: #0d9488;
  background: #f0fdfa;
}

.quick-action-grid span {
  color: #172033;
  font-size: 13px;
  font-weight: 800;
}

.quick-action-grid small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.runtime-section {
  padding: 16px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.api-entry-grid {
  display: grid;
  gap: 16px;
}

.access-workspace,
.traffic-workspace {
  display: grid;
  gap: 16px;
}

.access-list-section {
  min-width: 0;
}

.workspace-section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 16px;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #fbfdff;
}

.workspace-section-head h3 {
  margin: 4px 0 0;
  color: #172033;
  font-size: 18px;
  line-height: 1.3;
}

.workspace-section-head p {
  max-width: 560px;
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
  text-align: right;
}

.access-workspace :deep(.gateway-acl-tab),
.traffic-workspace :deep(.gateway-policies-tab),
.traffic-workspace :deep(.gateway-rate-limit-tab) {
  min-width: 0;
}

.security-workspace {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.security-sidebar,
.security-main {
  min-width: 0;
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  background: #ffffff;
}

.security-sidebar {
  position: sticky;
  top: 16px;
  overflow: hidden;
}

.security-sidebar-head {
  padding: 16px;
  border-bottom: 1px solid #e5edf5;
}

.security-sidebar-head h2,
.security-main-head h3 {
  margin: 6px 0 0;
  color: #172033;
  letter-spacing: 0;
}

.security-sidebar-head h2 {
  font-size: 18px;
  line-height: 1.3;
}

.security-sidebar-head p,
.security-main-head p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.6;
}

.security-stat-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border-bottom: 1px solid #e5edf5;
}

.security-stat-list article {
  min-width: 0;
  padding: 12px 10px;
  border-right: 1px solid #e5edf5;
  background: #fbfdff;
}

.security-stat-list article:last-child {
  border-right: 0;
}

.security-stat-list span,
.security-stat-list small {
  display: block;
  color: #64748b;
  font-size: 11px;
  line-height: 1.35;
}

.security-stat-list span {
  font-weight: 800;
}

.security-stat-list strong {
  display: block;
  margin: 8px 0 5px;
  color: #172033;
  font-size: 20px;
  line-height: 1;
}

.security-nav {
  display: grid;
  gap: 6px;
  padding: 10px;
}

.security-nav button {
  display: grid;
  gap: 5px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.security-nav button:hover {
  background: #f8fafc;
}

.security-nav button.active {
  border-color: #0d9488;
  background: #ecfdf9;
}

.security-nav span {
  color: #172033;
  font-size: 13px;
  font-weight: 800;
}

.security-nav small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
}

.security-main {
  padding: 0;
  overflow: hidden;
}

.security-main-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 16px;
  border-bottom: 1px solid #e5edf5;
  background: #fbfdff;
}

.security-main-head h3 {
  font-size: 18px;
  line-height: 1.3;
}

.security-main-head p {
  max-width: 520px;
  text-align: right;
}

.security-main :deep(.gateway-policies-tab),
.security-main :deep(.gateway-acl-tab),
.security-main :deep(.gateway-rate-limit-tab) {
  padding: 16px;
}

.security-main :deep(.section-card),
.security-main :deep(.el-card) {
  border-radius: 8px;
  border-color: #d8e0e8;
  box-shadow: none;
}

.security-main :deep(.policy-nav) {
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title span {
  color: #172033;
  font-size: 15px;
  font-weight: 800;
}

.section-title small {
  color: #64748b;
  font-size: 12px;
}

.path-text,
.target-text {
  margin-left: 8px;
  color: #334155;
  font-size: 13px;
}

.route-summary {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
}

.route-summary span {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.route-summary strong {
  color: #1e293b;
  font-size: 15px;
}

.warning-line {
  margin: 0 16px 12px;
}

.chain-list {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
}

.chain-list li {
  border: 1px solid #d8e0e8;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.chain-label {
  margin-right: 8px;
  color: #1e293b;
  font-weight: 700;
}

.chain-list p {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .gateway-hero-row {
    flex-direction: column;
  }

  .gateway-hero-actions {
    justify-content: flex-start;
  }

  .gateway-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .runtime-hero,
  .overview-secondary-grid,
  .overview-grid,
  .security-workspace {
    grid-template-columns: 1fr;
  }

  .security-sidebar {
    position: static;
  }

  .hero-metric-grid,
  .entry-map.compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .quick-action-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .gateway-hero {
    padding: 14px 14px 16px;
  }

  .gateway-summary {
    grid-template-columns: 1fr;
  }

  .gateway-content-panel {
    padding: 10px;
  }

  .runtime-hero-head,
  .table-head,
  .head-actions,
  .workspace-section-head,
  .security-main-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-metric-grid,
  .entry-map.compact,
  .security-stat-list {
    grid-template-columns: 1fr;
  }

  .security-stat-list article {
    border-right: 0;
    border-bottom: 1px solid #e5edf5;
  }

  .security-stat-list article:last-child {
    border-bottom: 0;
  }

  .security-main-head p {
    max-width: none;
    text-align: left;
  }

  .workspace-section-head p {
    max-width: none;
    text-align: left;
  }
}

html.dark .gateway-console {
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.74), rgba(15, 23, 42, 0.94)),
    var(--neutral-gray-900, #0f172a);
  box-shadow: none;
}

html.dark .gateway-hero {
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(15, 23, 42, 0.94) 52%),
    var(--neutral-gray-900, #0f172a);
}

html.dark .gateway-title-block h1,
html.dark .gateway-summary-card strong {
  color: #f8fafc;
}

html.dark .gateway-title-block p,
html.dark .gateway-summary-card small {
  color: #94a3b8;
}
</style>
