<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="接入服务器"
    width="620px"
    :close-on-click-modal="false"
    @close="reset"
  >
    <!-- Step 1: 填写信息创建 Token -->
    <div v-if="step === 1">
      <el-form :model="form" label-width="100px" size="default">
        <el-form-item label="Runner 名称" required>
          <el-input v-model="form.name" placeholder="如 prod-web-1" maxlength="120" />
        </el-form-item>
        <el-form-item label="有效期">
          <el-select v-model="form.ttlMinutes" placeholder="默认 15 分钟">
            <el-option :value="null" label="默认（15 分钟）" />
            <el-option :value="5" label="5 分钟" />
            <el-option :value="15" label="15 分钟" />
            <el-option :value="30" label="30 分钟" />
            <el-option :value="60" label="60 分钟" />
            <el-option :value="120" label="120 分钟（上限）" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="creating" @click="createToken" :disabled="!form.name.trim()">
            生成接入命令
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- Step 2: 显示接入命令 -->
    <div v-else>
      <el-alert
        title="安全提醒"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
      >
        <template #default>
          <p style="margin:0;font-size:13px;">
            此命令包含一次性 Token，仅在创建时显示。请复制到目标服务器执行，不要在公开场合分享。
          </p>
        </template>
      </el-alert>

      <div class="enroll-command-box">
        <div class="command-header">
          <span>在目标服务器上执行：</span>
          <el-button link size="small" :icon="DocumentCopy" @click="copyCommand">复制命令</el-button>
        </div>
        <pre class="command-text"><code>{{ enrollCommand }}</code></pre>
      </div>

      <div class="token-info" v-if="tokenResponse">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="Token ID">{{ tokenResponse.id }}</el-descriptions-item>
          <el-descriptions-item label="过期时间">{{ formatExpiry(tokenResponse.expiresAt) }}</el-descriptions-item>
          <el-descriptions-item label="有效时长">{{ formatTtl(tokenResponse.ttlSec) }}</el-descriptions-item>
          <el-descriptions-item label="状态">等待 Runner 接入</el-descriptions-item>
        </el-descriptions>
      </div>

      <div class="dialog-footer">
        <el-button @click="$emit('update:visible', false)">关闭</el-button>
        <el-button type="primary" @click="doneAndRefresh">完成，刷新列表</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { DocumentCopy } from '@element-plus/icons-vue';
import { createEnrollmentToken } from '@/api/runner';

defineProps({ visible: Boolean });
const emit = defineEmits(['update:visible', 'enrolled']);

const step = ref(1);
const creating = ref(false);
const form = ref({ name: '', ttlMinutes: null });
const tokenResponse = ref(null);

// 优先使用后端返回的公开 Enrollment Endpoint；相对路径才回落到当前站点 origin。
const controlPlaneUrl = computed(() => {
  const endpoint = tokenResponse.value?.enrollmentEndpoint;
  if (!endpoint) return window.location.origin;
  try {
    return new URL(endpoint, window.location.origin).origin;
  } catch {
    return window.location.origin;
  }
});

function shellQuote(value) {
  return `'${String(value).replaceAll("'", `'"'"'`)}'`;
}

const enrollCommand = computed(() => {
  if (!tokenResponse.value) return '';
  const token = tokenResponse.value.token;
  const url = controlPlaneUrl.value;
  const name = form.value.name.trim();
  return `# 方式一：从 ORIN 仓库安装（需要 Python 3.11+ 与 Git）
python3 -m venv .orin-runner-venv \\
  && .orin-runner-venv/bin/pip install ${shellQuote('git+https://github.com/AdlinZ/ORIN.git@main#subdirectory=orin-ai-engine')} \\
  && ORIN_ENROLLMENT_TOKEN=${shellQuote(token)} .orin-runner-venv/bin/orin-runner enroll \\
    --name ${shellQuote(name)} \\
    --url ${shellQuote(url)}

# 方式二：从同一仓库构建本地 Docker 镜像后接入
docker build -t orin-runner:local -f Dockerfile.runner \\
  ${shellQuote('https://github.com/AdlinZ/ORIN.git#main:orin-ai-engine')} \\
  && docker run --rm -it \\
  -v orin-runner-cred:/root/.orin \\
  -e ORIN_ENROLLMENT_TOKEN=${shellQuote(token)} \\
  orin-runner:local enroll \\
    --name ${shellQuote(name)} \\
    --url ${shellQuote(url)}`;
});

async function createToken() {
  creating.value = true;
  try {
    const res = await createEnrollmentToken({
      name: form.value.name.trim(),
      ttlMinutes: form.value.ttlMinutes,
    });
    tokenResponse.value = res?.data ?? res;
    step.value = 2;
  } catch {
    ElMessage.error('创建接入 Token 失败');
  } finally {
    creating.value = false;
  }
}

function copyCommand() {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(enrollCommand.value).then(
      () => ElMessage.success('命令已复制到剪贴板'),
      () => ElMessage.warning('复制失败，请手动选择复制')
    );
  }
}

function doneAndRefresh() {
  emit('update:visible', false);
  emit('enrolled');
}

function reset() {
  step.value = 1;
  form.value = { name: '', ttlMinutes: null };
  tokenResponse.value = null;
}

function formatExpiry(ts) {
  if (!ts) return '-';
  return new Date(ts).toLocaleString();
}

function formatTtl(sec) {
  if (!sec && sec !== 0) return '-';
  if (sec < 60) return sec + 's';
  return Math.floor(sec / 60) + 'min';
}
</script>

<style scoped>
.enroll-command-box {
  background: #1e1e1e; border-radius: 6px; padding: 12px 16px;
  margin: 8px 0 16px;
}
.command-header {
  display: flex; justify-content: space-between; align-items: center;
  color: #ccc; font-size: 12px; margin-bottom: 8px;
}
.command-text {
  margin: 0; color: #8dc63f; font-family: monospace; font-size: 13px;
  white-space: pre-wrap; word-break: break-all; overflow-x: auto;
}
.token-info { margin-bottom: 16px; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
