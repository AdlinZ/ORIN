<template>
  <div class="setup-page">
    <aside class="setup-rail">
      <div class="brand-mark">ORIN</div>
      <h1>首次初始化</h1>
      <p>完成管理员、安全检查和首个开放入口配置。</p>
      <el-steps class="setup-steps" :active="activeStep" direction="vertical" finish-status="success">
        <el-step title="环境检查" />
        <el-step title="管理员" />
        <el-step title="模型 Provider" />
        <el-step title="API Key / MCP" />
        <el-step title="完成" />
      </el-steps>
    </aside>

    <main class="setup-main">
      <section v-if="activeStep === 0" class="setup-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Step 1</p>
            <h2>环境与安全检查</h2>
          </div>
          <el-button :loading="loadingStatus" @click="loadStatus">刷新</el-button>
        </div>

        <el-alert
          v-if="status && !status.canInitialize && !status.completed"
          type="warning"
          :closable="false"
          title="当前环境未开放首次初始化写入。生产环境需要显式设置 ORIN_SETUP_ENABLED=true。"
        />

        <div class="check-grid">
          <div class="check-section">
            <h3>依赖</h3>
            <div v-for="item in dependencies" :key="item.key" class="check-row">
              <el-tag :type="tagType(item)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.message }}</span>
              </div>
            </div>
          </div>
          <div class="check-section">
            <h3>安全</h3>
            <div v-for="item in security" :key="item.key" class="check-row">
              <el-tag :type="tagType(item)" effect="plain">{{ statusLabel(item.status) }}</el-tag>
              <div>
                <strong>{{ item.name }}</strong>
                <span>{{ item.message }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="panel-actions">
          <el-button type="primary" :disabled="!canContinueFromStatus" @click="activeStep = 1">继续</el-button>
        </div>
      </section>

      <section v-else-if="activeStep === 1" class="setup-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Step 2</p>
            <h2>创建或修复超级管理员</h2>
          </div>
        </div>

        <el-form ref="adminFormRef" :model="adminForm" :rules="adminRules" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="adminForm.username" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="adminForm.password" type="password" show-password autocomplete="new-password" />
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

      <section v-else-if="activeStep === 2" class="setup-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Step 3</p>
            <h2>模型 Provider</h2>
          </div>
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
            <el-input v-model="providerForm.endpoint" placeholder="https://api.siliconflow.cn/v1" />
          </el-form-item>
          <el-form-item label="模型">
            <el-input v-model="providerForm.model" placeholder="Qwen/Qwen2.5-7B-Instruct" />
          </el-form-item>
          <el-form-item label="API Key">
            <el-input v-model="providerForm.apiKey" type="password" show-password autocomplete="off" />
          </el-form-item>
        </el-form>

        <div class="panel-actions">
          <el-button @click="activeStep = 1">返回</el-button>
          <el-button :disabled="!providerEnabled" :loading="testingProvider" @click="testProvider">测试连接</el-button>
          <el-button type="primary" @click="activeStep = 3">继续</el-button>
        </div>
      </section>

      <section v-else-if="activeStep === 3" class="setup-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Step 4</p>
            <h2>API Key / MCP</h2>
          </div>
          <el-switch v-model="clientAccessForm.create" active-text="创建" inactive-text="跳过" />
        </div>

        <p class="muted">
          CLIENT_ACCESS Key 用于 `/v1/mcp` 和 OpenAI 兼容入口。密钥只会在完成页显示一次。
        </p>

        <el-form :model="clientAccessForm" label-position="top" :disabled="!clientAccessForm.create">
          <el-form-item label="名称">
            <el-input v-model="clientAccessForm.name" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="clientAccessForm.description" type="textarea" :rows="3" />
          </el-form-item>
        </el-form>

        <div class="panel-actions">
          <el-button @click="activeStep = 2">返回</el-button>
          <el-button type="primary" :loading="initializing" @click="initialize">完成初始化</el-button>
        </div>
      </section>

      <section v-else class="setup-panel">
        <div class="panel-head">
          <div>
            <p class="eyebrow">Step 5</p>
            <h2>初始化已完成</h2>
          </div>
          <el-button type="primary" @click="goLogin">去登录</el-button>
        </div>

        <el-alert type="success" :closable="false" title="系统初始化完成。请保存一次性密钥，并在生产环境移除临时初始化开关。" />

        <div v-if="result?.clientAccessKey?.secretKey" class="secret-box">
          <label>CLIENT_ACCESS Key</label>
          <code>{{ result.clientAccessKey.secretKey }}</code>
          <el-button size="small" @click="copy(result.clientAccessKey.secretKey)">复制</el-button>
        </div>

        <div class="smoke-box">
          <h3>下一步验收</h3>
          <pre>curl -fsS http://localhost:8080/v1/health
curl -fsS http://localhost:8000/health
bash scripts/smoke-test.sh</pre>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSetupStatus, initializeSetup, testSetupProvider } from '@/api/setup'
import { ROUTES } from '@/router/routes'

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
const canContinueFromStatus = computed(() => {
  if (!status.value?.canInitialize) return false
  return !security.value.some(item => item.required && item.status === 'error')
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
.setup-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  background: #f6f8fb;
  color: #172033;
}

.setup-rail {
  padding: 36px 28px;
  background: #102033;
  color: #eef6ff;
}

.brand-mark {
  width: 64px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.28);
  font-weight: 700;
  margin-bottom: 32px;
}

.setup-rail h1 {
  margin: 0 0 10px;
  font-size: 28px;
}

.setup-rail p {
  margin: 0 0 32px;
  color: #bfd0e3;
  line-height: 1.6;
}

.setup-steps {
  --el-text-color-primary: #eef6ff;
  --el-text-color-secondary: #bfd0e3;
}

.setup-main {
  padding: 40px;
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.setup-panel {
  width: min(920px, 100%);
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 8px;
  padding: 28px;
  box-shadow: 0 16px 36px rgba(16, 32, 51, 0.08);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}

.eyebrow {
  margin: 0 0 6px;
  color: #1d75bd;
  font-size: 13px;
  font-weight: 700;
}

h2,
h3 {
  margin: 0;
}

.check-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 20px;
}

.check-section {
  border: 1px solid #e6ebf2;
  border-radius: 8px;
  padding: 18px;
}

.check-section h3 {
  margin-bottom: 14px;
  font-size: 16px;
}

.check-row {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 12px;
  align-items: start;
  padding: 12px 0;
  border-top: 1px solid #eef2f6;
}

.check-row:first-of-type {
  border-top: 0;
}

.check-row strong,
.check-row span {
  display: block;
}

.check-row span,
.muted {
  color: #667085;
  line-height: 1.55;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 28px;
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

.secret-box code {
  overflow-wrap: anywhere;
  color: #0f5132;
}

.smoke-box {
  margin-top: 24px;
}

.smoke-box pre {
  overflow-x: auto;
  padding: 16px;
  border-radius: 8px;
  background: #172033;
  color: #e5eef8;
}

@media (max-width: 860px) {
  .setup-page {
    grid-template-columns: 1fr;
  }

  .setup-rail {
    padding: 24px;
  }

  .setup-main {
    padding: 20px;
  }

  .check-grid {
    grid-template-columns: 1fr;
  }

  .secret-box {
    grid-template-columns: 1fr;
  }
}
</style>
