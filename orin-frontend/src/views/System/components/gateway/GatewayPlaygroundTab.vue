<template>
  <div class="gateway-playground-tab">
    <section class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#0d9488"><Monitor /></el-icon>
          可视测试台
        </div>
        <div class="header-actions">
          <el-button size="small" :loading="loadingRoutes" @click="loadRoutes">
            <el-icon><Refresh /></el-icon>
            刷新路由
          </el-button>
          <el-button type="primary" size="small" plain :loading="seeding" @click="seedDemoRoutes">
            <el-icon><Plus /></el-icon>
            创建演示路由
          </el-button>
        </div>
      </div>

      <div class="playground-body">
        <aside class="scenario-list">
          <button
            v-for="scenario in scenarios"
            :key="scenario.key"
            class="scenario-item"
            :class="{ active: activeScenarioKey === scenario.key }"
            @click="applyScenario(scenario)"
          >
            <span class="scenario-method">{{ scenario.method }}</span>
            <span class="scenario-text">
              <strong>{{ scenario.title }}</strong>
              <small>{{ scenario.path }}</small>
            </span>
          </button>
        </aside>

        <main class="request-panel">
          <div class="request-editor">
            <el-form label-width="84px">
              <el-form-item label="请求方法">
                <el-select v-model="testForm.method" style="width: 180px">
                  <el-option label="GET" value="GET" />
                  <el-option label="POST" value="POST" />
                  <el-option label="PUT" value="PUT" />
                  <el-option label="DELETE" value="DELETE" />
                </el-select>
              </el-form-item>
              <el-form-item label="请求路径">
                <el-input v-model="testForm.path" placeholder="/v1/models" />
              </el-form-item>
              <el-form-item label="请求体">
                <el-input v-model="requestBody" type="textarea" :rows="8" resize="none" />
              </el-form-item>
            </el-form>
            <div class="request-actions">
              <el-button type="primary" :loading="testing" @click="runTest">
                <el-icon><VideoPlay /></el-icon>
                运行测试
              </el-button>
            </div>
          </div>

          <div class="result-panel">
            <div class="flow">
              <div v-for="step in flowSteps" :key="step.key" class="flow-step" :class="step.state">
                <div class="flow-dot">{{ step.index }}</div>
                <div>
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.desc }}</span>
                </div>
              </div>
            </div>

            <div class="result-box" :class="resultState">
              <div class="result-head">
                <el-tag :type="resultTagType" effect="dark">{{ resultLabel }}</el-tag>
                <span v-if="testResult?.latencyMs !== undefined">{{ testResult.latencyMs }}ms</span>
              </div>
              <div class="result-grid">
                <span>命中路由</span>
                <strong>{{ testResult?.matchedRoute || '-' }}</strong>
                <span>路由类型</span>
                <strong>{{ testResult?.routeType || '-' }}</strong>
                <span>认证要求</span>
                <strong>{{ authLabel }}</strong>
                <span>目标地址</span>
                <strong class="mono">{{ displayTarget }}</strong>
              </div>
            </div>
          </div>
        </main>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Monitor, Plus, Refresh, VideoPlay } from '@element-plus/icons-vue'
import { createRoute, getRoutes, testRoute } from '@/api/gateway'

const loadingRoutes = ref(false)
const seeding = ref(false)
const testing = ref(false)
const routes = ref([])
const testResult = ref(null)
const activeScenarioKey = ref('models')
const requestBody = ref('')

const testForm = reactive({
  method: 'GET',
  path: '/v1/models'
})

const scenarios = [
  {
    key: 'models',
    title: '模型列表入口',
    method: 'GET',
    path: '/v1/models',
    body: ''
  },
  {
    key: 'chat',
    title: '聊天补全入口',
    method: 'POST',
    path: '/v1/chat/completions',
    body: JSON.stringify({
      model: 'gpt-4o-mini',
      messages: [{ role: 'user', content: '请用一句话介绍 ORIN 网关' }],
      stream: false
    }, null, 2)
  },
  {
    key: 'admin',
    title: '平台管理接口',
    method: 'GET',
    path: '/api/v1/system/gateway/overview',
    body: ''
  },
  {
    key: 'miss',
    title: '未命中路径',
    method: 'GET',
    path: '/not-found/demo',
    body: ''
  }
]

const demoRoutes = [
  {
    name: '演示 - OpenAI 兼容网关',
    pathPattern: '/v1/**',
    method: 'ALL',
    targetUrl: '',
    serviceId: null,
    stripPrefix: false,
    authRequired: false,
    priority: 900,
    enabled: true,
    description: '用于可视测试台演示 /v1 模型、聊天、Embedding 等本地统一 API 入口'
  },
  {
    name: '演示 - 平台管理 API',
    pathPattern: '/api/v1/**',
    method: 'ALL',
    targetUrl: '',
    serviceId: null,
    stripPrefix: false,
    authRequired: true,
    priority: 800,
    enabled: true,
    description: '用于可视测试台演示平台管理接口的本地策略路由'
  }
]

const applyScenario = (scenario) => {
  activeScenarioKey.value = scenario.key
  testForm.method = scenario.method
  testForm.path = scenario.path
  requestBody.value = scenario.body
  testResult.value = null
}

const loadRoutes = async () => {
  loadingRoutes.value = true
  try {
    routes.value = await getRoutes()
  } catch {
    ElMessage.error('加载路由失败')
  } finally {
    loadingRoutes.value = false
  }
}

const seedDemoRoutes = async () => {
  seeding.value = true
  try {
    const existing = routes.value.length ? routes.value : await getRoutes()
    const existingKeys = new Set(existing.map(route => `${route.pathPattern}::${route.method}`))
    const tasks = demoRoutes
      .filter(route => !existingKeys.has(`${route.pathPattern}::${route.method}`))
      .map(route => createRoute(route))
    if (tasks.length) {
      await Promise.all(tasks)
      ElMessage.success('演示路由已创建')
    } else {
      ElMessage.info('演示路由已存在')
    }
    await loadRoutes()
  } catch (e) {
    ElMessage.error('创建演示路由失败')
  } finally {
    seeding.value = false
  }
}

const runTest = async () => {
  if (!testForm.path) {
    ElMessage.warning('请输入请求路径')
    return
  }
  testing.value = true
  try {
    testResult.value = await testRoute({
      method: testForm.method,
      path: testForm.path
    })
  } catch {
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

const resultState = computed(() => {
  if (!testResult.value) return 'idle'
  return testResult.value.success ? 'success' : 'warning'
})

const resultTagType = computed(() => {
  if (!testResult.value) return 'info'
  return testResult.value.success ? 'success' : 'warning'
})

const resultLabel = computed(() => {
  if (!testResult.value) return '等待测试'
  return testResult.value.success ? '已命中' : '未命中'
})

const authLabel = computed(() => {
  if (testResult.value?.authRequired === true) return '需要 JWT 或 API Key'
  if (testResult.value?.authRequired === false) return '不强制认证'
  return '-'
})

const displayTarget = computed(() => {
  if (!testResult.value) return '-'
  if (testResult.value.targetUrl) return testResult.value.targetUrl
  if (testResult.value.routeType === 'LOCAL') return 'ORIN 本地 Controller'
  return '-'
})

const flowSteps = computed(() => {
  const hasResult = !!testResult.value
  const matched = !!testResult.value?.success
  const routeType = testResult.value?.routeType
  return [
    {
      key: 'request',
      index: 1,
      title: '请求进入',
      desc: `${testForm.method} ${testForm.path || '-'}`,
      state: hasResult ? 'done' : 'idle'
    },
    {
      key: 'acl',
      index: 2,
      title: 'ACL 预检',
      desc: hasResult ? '进入路由匹配前置判断' : '等待运行',
      state: hasResult ? 'done' : 'idle'
    },
    {
      key: 'route',
      index: 3,
      title: '路由匹配',
      desc: matched ? testResult.value.matchedRoute : (hasResult ? '无匹配路由' : '等待运行'),
      state: !hasResult ? 'idle' : matched ? 'done' : 'fail'
    },
    {
      key: 'dispatch',
      index: 4,
      title: '分发结果',
      desc: matched ? (routeType === 'LOCAL' ? '本地处理' : '代理转发') : '不会进入上游',
      state: !hasResult ? 'idle' : matched ? 'done' : 'fail'
    }
  ]
})

onMounted(() => {
  applyScenario(scenarios[0])
  loadRoutes()
})
</script>

<style scoped>
.section-card {
  background: #fff;
  border: 1px solid var(--neutral-gray-100, #f0f0f0);
  border-radius: 10px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--neutral-gray-100, #f0f0f0);
}

.section-title,
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-700, #374151);
}

.playground-body {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  min-height: 520px;
}

.scenario-list {
  border-right: 1px solid var(--neutral-gray-100, #f0f0f0);
  padding: 12px;
  background: #f8fafc;
}

.scenario-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #334155;
  text-align: left;
  cursor: pointer;
}

.scenario-item + .scenario-item {
  margin-top: 6px;
}

.scenario-item.active,
.scenario-item:hover {
  background: #fff;
  border-color: #99f6e4;
}

.scenario-method {
  width: 48px;
  padding: 3px 0;
  border-radius: 5px;
  background: #0f766e;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  text-align: center;
}

.scenario-text {
  min-width: 0;
}

.scenario-text strong,
.scenario-text small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scenario-text small {
  margin-top: 3px;
  color: #64748b;
}

.request-panel {
  display: grid;
  grid-template-columns: minmax(360px, 0.92fr) minmax(360px, 1fr);
  gap: 16px;
  padding: 16px;
}

.request-editor,
.result-panel {
  min-width: 0;
}

.request-actions {
  display: flex;
  justify-content: flex-end;
}

.flow {
  display: grid;
  gap: 10px;
}

.flow-step {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.flow-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  background: #e2e8f0;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.flow-step strong,
.flow-step span {
  display: block;
}

.flow-step span {
  margin-top: 3px;
  color: #64748b;
  font-size: 12px;
}

.flow-step.done {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.flow-step.done .flow-dot {
  background: #0d9488;
  color: #fff;
}

.flow-step.fail {
  border-color: #fde68a;
  background: #fffbeb;
}

.flow-step.fail .flow-dot {
  background: #d97706;
  color: #fff;
}

.result-box {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.result-box.success {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.result-box.warning {
  border-color: #fde68a;
  background: #fffbeb;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.result-grid {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr);
  gap: 8px 12px;
  font-size: 13px;
}

.result-grid span {
  color: #64748b;
}

.result-grid strong {
  min-width: 0;
  color: #1f2937;
  overflow-wrap: anywhere;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
}

@media (max-width: 1180px) {
  .playground-body,
  .request-panel {
    grid-template-columns: 1fr;
  }

  .scenario-list {
    border-right: 0;
    border-bottom: 1px solid var(--neutral-gray-100, #f0f0f0);
  }
}
</style>
