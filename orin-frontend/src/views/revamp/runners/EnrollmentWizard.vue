<template>
  <el-dialog
    :model-value="visible"
    title="接入 Runner"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @close="reset"
  >
    <el-steps :active="step - 1" finish-status="success" class="enroll-steps">
      <el-step title="创建凭据" />
      <el-step title="启动 Runner" />
      <el-step title="确认上线" />
    </el-steps>

    <section v-if="step === 1" class="wizard-section">
      <div class="section-copy">
        <h3>先给执行节点起个容易识别的名字</h3>
        <p>名称会同时用于接入校验和之后的运行选择，建议体现机器或用途。</p>
      </div>
      <el-form :model="form" label-position="top">
        <el-form-item label="节点名称" required>
          <el-input
            v-model="form.name"
            placeholder="例如：本地开发机"
            maxlength="120"
            @keyup.enter="createToken"
          />
        </el-form-item>
        <details class="advanced-settings">
          <summary>接入凭据有效期</summary>
          <div class="advanced-content">
            <el-select v-model="form.ttlMinutes" style="width: 100%">
              <el-option :value="null" label="15 分钟（推荐）" />
              <el-option :value="5" label="5 分钟" />
              <el-option :value="30" label="30 分钟" />
              <el-option :value="60" label="60 分钟" />
              <el-option :value="120" label="120 分钟（上限）" />
            </el-select>
            <p class="field-hint">凭据只用于首次接入，成功后立即失效。</p>
          </div>
        </details>
      </el-form>
    </section>

    <section v-else-if="step === 2" class="wizard-section">
      <el-alert
        title="命令含一次性接入凭据，请勿分享或保存到脚本仓库"
        type="warning"
        :closable="false"
        show-icon
      />
      <div class="section-copy">
        <h3>在要执行任务的机器上运行命令</h3>
        <p>选择本机已有的运行方式。命令会安装 Runner、完成接入，并持续连接 ORIN。</p>
      </div>
      <el-radio-group v-model="installMethod" class="method-switch">
        <el-radio-button value="python">Python</el-radio-button>
        <el-radio-button value="docker">Docker</el-radio-button>
      </el-radio-group>
      <div class="enroll-command-box">
        <div class="command-header">
          <span>{{ installMethod === 'python' ? '需要 Python 3.11+ 与 Git' : '需要 Docker' }}</span>
          <el-button link size="small" :icon="DocumentCopy" @click="copyCommand">复制命令</el-button>
        </div>
        <pre class="command-text"><code>{{ selectedCommand }}</code></pre>
      </div>
      <p class="expiry-hint">
        接入凭据将在 {{ formatExpiry(tokenResponse?.expiresAt) }} 失效；过期后可关闭窗口重新生成。
      </p>
    </section>

    <section v-else class="wizard-section connection-check">
      <template v-if="connectedRunner">
        <el-result
          icon="success"
          title="Runner 已上线"
          sub-title="这个执行节点已经可以接收 Agent 任务。"
        >
          <template #extra>
            <div class="connected-summary">
              <strong>{{ connectedRunner.name }}</strong>
              <el-tag type="success">可以运行</el-tag>
              <span>{{ connectedRunner.hostname || '主机信息正在上报' }}</span>
            </div>
          </template>
        </el-result>
      </template>
      <template v-else>
        <div class="waiting-state">
          <el-icon class="is-loading" :size="34"><Loading /></el-icon>
          <h3>正在等待 {{ form.name }} 上线</h3>
          <p v-if="connectionTimedOut">
            暂时还没收到连接。请确认命令仍在运行、机器能访问 ORIN，然后再次检查。
          </p>
          <p v-else>保持命令运行，通常几秒内就能完成首次连接。</p>
          <el-button :loading="checking" @click="checkConnection">再次检查</el-button>
        </div>
      </template>
    </section>

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="step === 1" @click="$emit('update:visible', false)">取消</el-button>
        <el-button v-else-if="step === 2" @click="step = 1">上一步</el-button>
        <el-button v-else @click="finishHere">留在此页</el-button>

        <el-button
          v-if="step === 1"
          type="primary"
          :loading="creating"
          :disabled="!form.name.trim()"
          @click="createToken"
        >
          生成接入命令
        </el-button>
        <el-button v-else-if="step === 2" type="primary" @click="startChecking">
          已启动，检查连接
        </el-button>
        <el-button
          v-else
          type="primary"
          :disabled="!connectedRunner"
          @click="continueToRun"
        >
          {{ returningToRun ? '返回运行' : '开始第一次运行' }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DocumentCopy, Loading } from '@element-plus/icons-vue'
import { createEnrollmentToken, listRunners } from '@/api/runner'
import { resolveRunnerControlPlaneOrigin } from '@/domains/runner/enrollmentDelivery'

defineProps({
  visible: Boolean,
  returningToRun: Boolean,
})

const emit = defineEmits(['update:visible', 'enrolled', 'ready'])
const step = ref(1)
const creating = ref(false)
const checking = ref(false)
const connectionTimedOut = ref(false)
const installMethod = ref('python')
const form = ref({ name: '', ttlMinutes: null })
const tokenResponse = ref(null)
const connectedRunner = ref(null)
const pollAttempts = ref(0)
let pollTimer = null

const controlPlaneUrl = computed(() => {
  return resolveRunnerControlPlaneOrigin(
    tokenResponse.value?.enrollmentEndpoint,
    window.location.origin,
    import.meta.env.VITE_ORIN_PUBLIC_ORIGIN,
  )
})

function shellQuote(value) {
  return `'${String(value).replaceAll("'", `'"'"'`)}'`
}

const pythonCommand = computed(() => {
  if (!tokenResponse.value) return ''
  return `python3 -m venv .orin-runner-venv \\
  && .orin-runner-venv/bin/pip install ${shellQuote('git+https://github.com/AdlinZ/ORIN.git@main#subdirectory=orin-ai-engine')} \\
  && NO_PROXY=${shellQuote('localhost,127.0.0.1')} \\
  no_proxy=${shellQuote('localhost,127.0.0.1')} \\
  ORIN_ENROLLMENT_TOKEN=${shellQuote(tokenResponse.value.token)} \\
  .orin-runner-venv/bin/orin-runner enroll \\
    --name ${shellQuote(form.value.name.trim())} \\
    --url ${shellQuote(controlPlaneUrl.value)}`
})

const dockerCommand = computed(() => {
  if (!tokenResponse.value) return ''
  return `docker build -t orin-runner:local -f Dockerfile.runner \\
  ${shellQuote('https://github.com/AdlinZ/ORIN.git#main:orin-ai-engine')} \\
  && docker run --rm -it \\
    -v orin-runner-cred:/root/.orin \\
    -e ORIN_ENROLLMENT_TOKEN=${shellQuote(tokenResponse.value.token)} \\
    orin-runner:local enroll \\
      --name ${shellQuote(form.value.name.trim())} \\
      --url ${shellQuote(controlPlaneUrl.value)}`
})

const selectedCommand = computed(() => (
  installMethod.value === 'docker' ? dockerCommand.value : pythonCommand.value
))

async function createToken() {
  if (!form.value.name.trim() || creating.value) return
  creating.value = true
  try {
    const res = await createEnrollmentToken({
      name: form.value.name.trim(),
      ttlMinutes: form.value.ttlMinutes,
    })
    tokenResponse.value = res?.data ?? res
    step.value = 2
  } catch {
    ElMessage.error('接入凭据创建失败，请检查名称是否已被使用')
  } finally {
    creating.value = false
  }
}

async function copyCommand() {
  try {
    await navigator.clipboard.writeText(selectedCommand.value)
    ElMessage.success('命令已复制')
  } catch {
    ElMessage.warning('复制失败，请手动选择命令')
  }
}

function clearPollTimer() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
}

async function checkConnection() {
  if (checking.value || connectedRunner.value) return
  checking.value = true
  clearPollTimer()
  try {
    const res = await listRunners({ page: 0, size: 100 })
    const data = res?.data ?? res
    const list = Array.isArray(data?.content) ? data.content : (Array.isArray(data) ? data : [])
    const match = list.find((runner) => runner.name === form.value.name.trim())
    if (match?.status === 'ONLINE') {
      connectedRunner.value = match
      connectionTimedOut.value = false
      emit('enrolled', match)
      return
    }
  } catch {
    // Keep polling: transient list errors should not interrupt a running enrollment command.
  } finally {
    checking.value = false
  }

  pollAttempts.value += 1
  if (pollAttempts.value >= 30) {
    connectionTimedOut.value = true
    return
  }
  pollTimer = setTimeout(checkConnection, 2000)
}

function startChecking() {
  step.value = 3
  pollAttempts.value = 0
  connectionTimedOut.value = false
  checkConnection()
}

function finishHere() {
  emit('enrolled', connectedRunner.value)
  emit('update:visible', false)
}

function continueToRun() {
  if (!connectedRunner.value) return
  const runner = connectedRunner.value
  emit('update:visible', false)
  emit('ready', runner)
}

function reset() {
  clearPollTimer()
  step.value = 1
  creating.value = false
  checking.value = false
  connectionTimedOut.value = false
  installMethod.value = 'python'
  form.value = { name: '', ttlMinutes: null }
  tokenResponse.value = null
  connectedRunner.value = null
  pollAttempts.value = 0
}

function formatExpiry(ts) {
  if (!ts) return '未知时间'
  return new Date(ts).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

onBeforeUnmount(clearPollTimer)
</script>

<style scoped>
.enroll-steps {
  margin: 4px 28px 28px;
}

.wizard-section {
  min-height: 290px;
}

.section-copy {
  margin: 20px 0 18px;
}

.section-copy h3 {
  margin: 0 0 6px;
  font-size: 17px;
}

.section-copy p,
.field-hint,
.expiry-hint {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.advanced-settings {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.advanced-settings summary {
  padding: 10px 12px;
  cursor: pointer;
}

.advanced-content {
  display: grid;
  gap: 8px;
  padding: 0 12px 12px;
}

.method-switch {
  margin-bottom: 12px;
}

.enroll-command-box {
  padding: 12px 16px;
  margin-bottom: 10px;
  border-radius: 7px;
  background: #111827;
}

.command-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: #cbd5e1;
  font-size: 12px;
}

.command-text {
  max-height: 235px;
  margin: 0;
  overflow: auto;
  color: #a7f3d0;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.connection-check {
  display: flex;
  align-items: center;
  justify-content: center;
}

.waiting-state {
  max-width: 460px;
  text-align: center;
}

.waiting-state .el-icon {
  color: var(--el-color-primary);
}

.waiting-state h3 {
  margin: 14px 0 6px;
}

.waiting-state p {
  margin: 0 0 18px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.connected-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--el-text-color-secondary);
}

.connected-summary strong {
  color: var(--el-text-color-primary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
