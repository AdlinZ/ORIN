<template>
  <div class="setup-container">
    <div class="setup-shell">
      <section class="setup-brand-panel">
        <BrandingLogo :height="62" class="setup-logo" />
        <div class="brand-copy">
          <span class="brand-kicker">ORIN First-run Setup</span>
          <h1>初始化企业 AI 中枢</h1>
          <p>
            确认运行依赖、安全边界和第一个管理入口，随后进入统一工作台继续配置智能体、知识资产与 MCP
            服务。
          </p>
        </div>
        <div class="trust-grid">
          <div class="trust-item">
            <span class="trust-label">依赖</span>
            <strong>{{ dependencyOkCount }}/{{ dependencies.length || 0 }}</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">安全</span>
            <strong>{{ securityOkCount }}/{{ security.length || 0 }}</strong>
          </div>
          <div class="trust-item">
            <span class="trust-label">状态</span>
            <strong>{{ readinessLabel }}</strong>
          </div>
        </div>
      </section>

      <section class="setup-form-panel">
        <div class="setup-form-wrapper">
          <div class="form-heading">
            <div>
              <h2 class="form-title">{{ currentStep.title }}</h2>
              <p class="form-subtitle">{{ currentStep.description }}</p>
            </div>
            <div class="setup-status">
              <span class="status-dot" :class="readinessClass" />
              {{ readinessLabel }}
            </div>
          </div>

          <div class="setup-step-strip" aria-label="初始化步骤">
            <div
              v-for="(label, index) in stepNav"
              :key="label"
              class="step-chip"
              :class="{ active: activeStep === index, done: activeStep > index }"
            >
              <span>{{ index + 1 }}</span>
              <strong>{{ label }}</strong>
            </div>
          </div>

          <section v-if="activeStep === 0" class="setup-step-body">
            <div class="step-toolbar">
              <span>运行检查</span>
              <el-button :loading="loadingStatus" @click="loadStatus">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>

            <el-alert
              v-if="status && !status.canInitialize && !status.completed"
              type="warning"
              :closable="false"
              title="当前环境未开放首次初始化写入。生产环境需要显式设置 ORIN_SETUP_ENABLED=true。"
            />

            <div class="check-block">
              <div class="section-title">
                <h3>依赖</h3>
                <span>{{ dependencyOkCount }} / {{ dependencies.length }} 正常</span>
              </div>
              <div v-for="item in dependencies" :key="item.key" class="check-row">
                <el-tag :type="tagType(item)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
                <div>
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.message }}</span>
                </div>
              </div>
            </div>

            <div class="check-block">
              <div class="section-title">
                <h3>安全</h3>
                <span>{{ securityOkCount }} / {{ security.length }} 正常</span>
              </div>
              <div v-for="item in security" :key="item.key" class="check-row">
                <el-tag :type="tagType(item)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
                <div>
                  <strong>{{ item.name }}</strong>
                  <span>{{ item.message }}</span>
                </div>
              </div>
            </div>

            <div class="panel-actions">
              <el-button type="primary" :disabled="!canContinueFromStatus" @click="activeStep = 1">
                继续
              </el-button>
            </div>
          </section>

          <section v-else-if="activeStep === 1" class="setup-step-body">
            <el-form ref="adminFormRef" :model="adminForm" :rules="adminRules" label-position="top">
              <el-form-item label="用户名" prop="username">
                <el-input v-model="adminForm.username" autocomplete="username" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="adminForm.password"
                  type="password"
                  show-password
                  autocomplete="new-password"
                />
              </el-form-item>
              <el-form-item label="显示名称" prop="nickname">
                <el-input v-model="adminForm.nickname" />
              </el-form-item>
              <el-form-item label="邮箱" prop="email">
                <el-input v-model="adminForm.email" />
              </el-form-item>
            </el-form>

            <div class="panel-actions">
              <el-button @click="activeStep = 0">返回</el-button>
              <el-button type="primary" @click="validateAdminAndNext">继续</el-button>
            </div>
          </section>

          <section v-else-if="activeStep === 2" class="setup-step-body">
            <div class="step-toolbar">
              <span>模型 Provider</span>
              <el-switch v-model="providerEnabled" active-text="配置" inactive-text="跳过" />
            </div>

            <el-alert
              v-if="encryptionMissing && providerEnabled"
              type="warning"
              :closable="false"
              title="未配置 ENCRYPTION_KEY 时不能保存 Provider Key。可以先跳过，或只填写 endpoint/model。"
            />

            <el-form :model="providerForm" label-position="top" :disabled="!providerEnabled">
              <el-form-item label="Provider">
                <el-select v-model="providerForm.provider" style="width: 100%">
                  <el-option label="SiliconFlow" value="siliconflow" />
                  <el-option label="Ollama" value="ollama" />
                  <el-option label="Dify" value="dify" />
                </el-select>
              </el-form-item>
              <el-form-item label="Endpoint">
                <el-input
                  v-model="providerForm.endpoint"
                  placeholder="https://api.siliconflow.cn/v1"
                />
              </el-form-item>
              <el-form-item label="模型">
                <el-input v-model="providerForm.model" placeholder="Qwen/Qwen2.5-7B-Instruct" />
              </el-form-item>
              <el-form-item label="API Key">
                <el-input
                  v-model="providerForm.apiKey"
                  type="password"
                  show-password
                  autocomplete="off"
                />
              </el-form-item>
            </el-form>

            <div class="panel-actions">
              <el-button @click="activeStep = 1">返回</el-button>
              <el-button
                :disabled="!providerEnabled"
                :loading="testingProvider"
                @click="testProvider"
              >
                测试连接
              </el-button>
              <el-button type="primary" @click="activeStep = 3">继续</el-button>
            </div>
          </section>

          <section v-else-if="activeStep === 3" class="setup-step-body">
            <div class="step-toolbar">
              <span>API Key / MCP</span>
              <el-switch
                v-model="clientAccessForm.create"
                active-text="创建"
                inactive-text="跳过"
              />
            </div>

            <p class="muted">
              CLIENT_ACCESS Key 用于 `/v1/mcp` 和 OpenAI 兼容入口。密钥只会在完成页显示一次。
            </p>

            <el-form
              :model="clientAccessForm"
              label-position="top"
              :disabled="!clientAccessForm.create"
            >
              <el-form-item label="名称">
                <el-input v-model="clientAccessForm.name" />
              </el-form-item>
              <el-form-item label="描述">
                <el-input v-model="clientAccessForm.description" type="textarea" :rows="3" />
              </el-form-item>
            </el-form>

            <div class="panel-actions">
              <el-button @click="activeStep = 2">返回</el-button>
              <el-button type="primary" :loading="initializing" @click="initialize"
                >完成初始化</el-button
              >
            </div>
          </section>

          <section v-else class="setup-step-body setup-complete-body">
            <el-alert
              type="success"
              :closable="false"
              title="系统初始化完成。请保存一次性密钥，并在生产环境移除临时初始化开关。"
            />

            <div v-if="result?.clientAccessKey?.secretKey" class="secret-box">
              <label>CLIENT_ACCESS Key</label>
              <code>{{ result.clientAccessKey.secretKey }}</code>
              <el-button size="small" @click="copy(result.clientAccessKey.secretKey)"
                >复制</el-button
              >
            </div>

            <div class="smoke-box">
              <h3>下一步验收</h3>
              <pre>
curl -fsS http://localhost:8080/v1/health
curl -fsS http://localhost:8000/health
bash scripts/smoke-test.sh</pre
              >
            </div>

            <el-button type="primary" class="login-btn" @click="goLogin">去登录</el-button>
          </section>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSetupStatus, initializeSetup, testSetupProvider } from '@/api/setup'
import { ROUTES } from '@/router/routes'
import BrandingLogo from '@/components/BrandingLogo.vue'

const router = useRouter()
const activeStep = ref(0)
const loadingStatus = ref(false)
const testingProvider = ref(false)
const initializing = ref(false)
const status = ref(null)
const result = ref(null)
const adminFormRef = ref(null)
const providerEnabled = ref(true)

const adminForm = reactive({
  username: 'admin',
  password: '',
  nickname: 'Administrator',
  email: 'admin@orin.com'
})

const providerForm = reactive({
  provider: 'siliconflow',
  endpoint: 'https://api.siliconflow.cn/v1',
  model: '',
  apiKey: ''
})

const clientAccessForm = reactive({
  create: true,
  name: 'First-run MCP access key',
  description: 'Created by ORIN setup wizard'
})

const steps = [
  {
    title: '环境与安全检查',
    description: '确认 MySQL、Redis、AI Engine、RabbitMQ 降级状态和关键安全配置。'
  },
  {
    title: '超级管理员',
    description: '创建或修复首个超级管理员账号，避免依赖默认密码进入系统。'
  },
  {
    title: '模型 Provider',
    description: '可选配置首个模型供应商；缺少加密密钥时先跳过 API Key。'
  },
  {
    title: 'API Key / MCP',
    description: '创建一次性 CLIENT_ACCESS Key，用于 OpenAI 兼容入口和 /v1/mcp。'
  },
  {
    title: '完成验收',
    description: '保存一次性密钥，并运行健康检查和 smoke 脚本确认闭环。'
  }
]

const stepNav = ['检查', '管理员', '模型', '密钥', '完成']

const adminRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' }
  ],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const dependencies = computed(() => status.value?.dependencies || [])
const security = computed(() => status.value?.security || [])
const allChecks = computed(() => [...dependencies.value, ...security.value])
const currentStep = computed(() => steps[activeStep.value] || steps[0])
const dependencyOkCount = computed(
  () => dependencies.value.filter(item => item.status === 'ok').length
)
const securityOkCount = computed(() => security.value.filter(item => item.status === 'ok').length)
const blockingCheckCount = computed(
  () => allChecks.value.filter(item => item.required && item.status === 'error').length
)
const warningCheckCount = computed(
  () => allChecks.value.filter(item => item.status === 'warning').length
)
const canContinueFromStatus = computed(() => {
  if (!status.value?.canInitialize) return false
  return blockingCheckCount.value === 0
})
const readinessClass = computed(() => {
  if (!status.value) return 'pending'
  if (!status.value.canInitialize || blockingCheckCount.value > 0) return 'blocked'
  if (warningCheckCount.value > 0) return 'warning'
  return 'ready'
})
const readinessLabel = computed(() => {
  if (!status.value) return '正在读取初始化状态'
  if (status.value.completed) return '初始化已完成'
  if (!status.value.setupEnabled) return '当前环境未开放初始化写入'
  if (blockingCheckCount.value > 0) return `存在 ${blockingCheckCount.value} 个阻断项`
  if (warningCheckCount.value > 0) return `可继续，${warningCheckCount.value} 个降级项待处理`
  return '环境可继续初始化'
})
const encryptionMissing = computed(() => {
  return security.value.some(item => item.key === 'encryption-key' && item.status !== 'ok')
})

onMounted(loadStatus)

async function loadStatus() {
  loadingStatus.value = true
  try {
    status.value = await getSetupStatus()
    if (status.value?.completed) {
      window.sessionStorage.setItem('orin_setup_completed', 'true')
      router.replace(ROUTES.LOGIN)
    }
  } catch {
    status.value = {
      completed: false,
      canInitialize: false,
      dependencies: [],
      security: []
    }
  } finally {
    loadingStatus.value = false
  }
}

async function validateAdminAndNext() {
  await adminFormRef.value?.validate()
  activeStep.value = 2
}

async function testProvider() {
  testingProvider.value = true
  try {
    const response = await testSetupProvider(providerPayload())
    if (response?.success) {
      ElMessage.success('Provider 连接可用')
    } else {
      ElMessage.warning('Provider 暂不可用，可先跳过后续再配置')
    }
  } finally {
    testingProvider.value = false
  }
}

async function initialize() {
  await adminFormRef.value?.validate()
  initializing.value = true
  try {
    const payload = {
      admin: { ...adminForm },
      provider: providerEnabled.value ? providerPayload() : null,
      clientAccess: { ...clientAccessForm }
    }
    result.value = await initializeSetup(payload)
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    activeStep.value = 4
    ElMessage.success('初始化完成')
  } finally {
    initializing.value = false
  }
}

function providerPayload() {
  return {
    provider: providerForm.provider,
    endpoint: providerForm.endpoint,
    model: providerForm.model,
    apiKey: providerForm.apiKey
  }
}

function tagType(item) {
  if (item.status === 'ok') return 'success'
  if (item.status === 'warning') return 'warning'
  return 'danger'
}

function statusLabel(value) {
  if (value === 'ok') return '正常'
  if (value === 'warning') return '提示'
  return '异常'
}

async function copy(value) {
  await navigator.clipboard.writeText(value)
  ElMessage.success('已复制')
}

function goLogin() {
  router.replace(ROUTES.LOGIN)
}
</script>

<style scoped>
.setup-container {
  min-height: 100svh;
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow-x: hidden;
  overflow-y: hidden;
  padding: clamp(14px, 2.5vh, 24px);
}

.setup-shell {
  width: 1080px;
  max-width: 90%;
  height: min(640px, calc(100svh - 32px));
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 500px;
  overflow: hidden;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(12px);
  backdrop-filter: blur(12px);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.setup-shell,
.setup-shell * {
  box-sizing: border-box;
}

.setup-brand-panel {
  padding: clamp(30px, 5vh, 48px);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: clamp(18px, 3vh, 34px);
  border-right: 1px solid var(--orin-border-strong, #d8e0e8);
  background: #ffffff;
}

.setup-logo {
  height: 62px;
  width: auto;
  align-self: flex-start;
}

.brand-copy {
  max-width: 560px;
}

.brand-kicker {
  display: block;
  margin-bottom: 12px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.brand-copy h1 {
  margin: 0 0 14px;
  color: #0f172a;
  font-size: clamp(32px, 4.5vh, 38px);
  line-height: 1.15;
  letter-spacing: 0;
}

.brand-copy p {
  margin: 0;
  color: #475569;
  font-size: 16px;
  line-height: 1.65;
}

.trust-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trust-item {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  background: #ffffff;
}

.trust-label {
  display: block;
  margin-bottom: 8px;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.trust-item strong {
  display: block;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.35;
}

.setup-form-panel {
  padding: clamp(20px, 3.8vh, 32px);
  background: #ffffff;
  display: flex;
  align-items: stretch;
  justify-content: center;
  min-height: 0;
}

.setup-form-wrapper {
  width: 100%;
  max-width: 420px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: 0;
}

.form-heading {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: start;
  margin-bottom: 12px;
}

.form-title {
  margin: 0 0 6px;
  color: var(--neutral-gray-900);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
}

.form-subtitle {
  margin: 0;
  color: var(--neutral-gray-400);
  font-size: 14px;
  line-height: 1.45;
}

.setup-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 168px;
  padding: 6px 9px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
}

.status-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #f59e0b;
}

.status-dot.ready {
  background: #0f9f6e;
}

.status-dot.warning {
  background: #f59e0b;
}

.status-dot.blocked {
  background: #dc2626;
}

.status-dot.pending {
  background: #94a3b8;
}

.setup-step-strip {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 12px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: 8px;
  background: #ffffff;
  padding: 6px;
}

.step-chip {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 28px;
  border-radius: 7px;
  color: #94a3b8;
  font-size: 12px;
}

.step-chip span {
  width: 18px;
  height: 18px;
  display: inline-grid;
  place-items: center;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 11px;
  line-height: 1;
}

.step-chip strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 700;
}

.step-chip.active {
  background: #f0fdfa;
  color: #0f766e;
}

.step-chip.done {
  color: #0f766e;
}

.setup-step-body {
  min-height: 0;
}

.step-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  color: #0f172a;
  font-weight: 700;
}

.check-block {
  margin-top: 8px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  padding-top: 8px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 4px;
}

.section-title h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
}

.section-title span {
  color: #64748b;
  font-size: 12px;
}

.check-row {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 4px 0;
}

.check-row .el-tag {
  height: 20px;
  padding: 0 8px;
  font-size: 11px;
}

.check-row strong,
.check-row span {
  display: block;
}

.check-row strong {
  color: #172033;
  font-size: 12px;
  line-height: 1.3;
}

.check-row span,
.muted {
  color: #667085;
  font-size: 11px;
  line-height: 1.35;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 8px;
}

.panel-actions .el-button:only-child {
  width: 100%;
  height: 36px;
  margin-left: 0;
  font-weight: 600;
}

.panel-actions .el-button:not(:only-child).el-button--primary {
  min-width: 104px;
}

.secret-box {
  margin-top: 24px;
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 16px;
  border: 1px solid #b7e4ce;
  border-radius: 8px;
  background: #f0fdf6;
}

.setup-complete-body .secret-box {
  grid-template-columns: 124px minmax(0, 1fr) auto;
  gap: 10px;
  margin-top: 12px;
  padding: 12px;
}

.secret-box label {
  color: #334155;
  font-size: 13px;
  line-height: 1.45;
}

.secret-box code {
  overflow-wrap: anywhere;
  color: #0f5132;
  font-size: 13px;
  line-height: 1.45;
}

.smoke-box {
  margin-top: 24px;
}

.setup-complete-body .smoke-box {
  margin-top: 14px;
}

.smoke-box h3 {
  margin: 0 0 10px;
  color: #0f172a;
  font-size: 22px;
  line-height: 1.2;
}

.smoke-box pre {
  overflow-x: auto;
  margin: 0;
  padding: 12px 14px;
  border-radius: 8px;
  background: #172033;
  color: #e5eef8;
  font-size: 13px;
  line-height: 1.5;
}

.login-btn {
  width: 100%;
  height: 40px;
  margin-top: 14px;
  border-radius: var(--radius-lg);
  font-size: 15px;
  font-weight: 600;
}

@media (max-width: 960px) {
  .setup-container {
    height: auto;
    min-height: 100vh;
    align-items: center;
    justify-content: flex-start;
    overflow-y: auto;
    padding: 20px;
  }

  .setup-shell {
    width: min(100%, 720px);
    max-width: none;
    height: auto;
    min-height: 0;
    grid-template-columns: 1fr;
  }

  .setup-brand-panel {
    padding: 28px 32px;
    border-right: none;
    border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
    gap: 18px;
    justify-content: flex-start;
  }

  .setup-logo {
    height: 48px;
  }

  .brand-kicker {
    margin-bottom: 10px;
    font-size: 11px;
  }

  .brand-copy h1 {
    margin-bottom: 10px;
    font-size: 28px;
  }

  .brand-copy p {
    font-size: 14px;
    line-height: 1.65;
  }

  .setup-form-panel {
    padding: 32px;
  }

  .trust-grid {
    display: none;
  }
}

@media (max-width: 640px) {
  .setup-container {
    padding: 12px;
  }

  .setup-shell {
    border-radius: 10px;
  }

  .setup-brand-panel {
    padding: 22px 20px;
  }

  .setup-logo {
    height: 40px;
  }

  .brand-copy h1 {
    font-size: 24px;
  }

  .brand-copy p {
    font-size: 13px;
  }

  .setup-form-panel {
    padding: 26px 20px 24px;
    min-width: 0;
  }

  .setup-form-wrapper {
    max-width: none;
    min-width: 0;
  }

  .form-heading {
    flex-direction: column;
  }

  .form-title {
    font-size: 24px;
  }

  .setup-status {
    max-width: none;
  }

  .setup-step-strip {
    overflow-x: auto;
  }

  .setup-step-strip {
    min-width: 520px;
  }

  .panel-actions {
    flex-direction: column;
  }

  .secret-box {
    grid-template-columns: 1fr;
  }

  .panel-actions .el-button {
    width: 100%;
  }
}

html.dark .setup-container {
  background: #0b1118;
}

html.dark .setup-shell,
html.dark .setup-brand-panel,
html.dark .setup-form-panel,
html.dark .setup-step-strip {
  background: #111827;
  border-color: rgba(148, 163, 184, 0.22);
}

html.dark .brand-copy h1,
html.dark .trust-item strong,
html.dark .form-title,
html.dark .section-title h3,
html.dark .step-toolbar,
html.dark .check-row strong {
  color: #f8fafc;
}

html.dark .brand-copy p,
html.dark .form-subtitle,
html.dark .setup-status,
html.dark .check-row span,
html.dark .muted {
  color: #94a3b8;
}

html.dark .trust-item {
  background: #0f172a;
  border-color: rgba(148, 163, 184, 0.22);
}
</style>
