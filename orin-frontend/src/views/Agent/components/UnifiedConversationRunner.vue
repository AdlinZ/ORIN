<template>
  <div class="unified-runner">
    <div class="runner-header">
      <div class="header-left">
        <el-icon class="mode-icon">
          <ChatDotRound />
        </el-icon>
        <span class="title">{{ modeLabel }}</span>
        <el-tag size="small" effect="plain">
          {{ normalizedMode.toUpperCase() }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button link type="danger" @click="clearMessages">
          清空会话
        </el-button>
      </div>
    </div>

    <div ref="messagesRef" class="messages">
      <div v-if="messages.length === 0" class="empty-state">
        <img src="/logo.svg" alt="ORIN" class="empty-logo">
        <div class="empty-title">
          统一智能体控制台
        </div>
        <div class="empty-subtitle">
          在同一对话流中使用文本、图像、音频、视频与转写能力
        </div>
      </div>

      <div
        v-for="(item, index) in messages"
        :key="index"
        class="message-wrap"
        :class="item.role"
      >
        <div class="message-card">
          <div class="meta-row">
            <span class="role">{{ item.role === 'user' ? '你' : '助手' }}</span>
            <span class="type">{{ item.type }}</span>
          </div>

          <div v-if="item.kind === 'text'" class="text-content">
            {{ item.content }}
          </div>

          <div v-else-if="item.kind === 'image'" class="asset-content">
            <el-image
              :src="item.content"
              fit="contain"
              class="asset-image"
              :preview-src-list="[item.content]"
            />
          </div>

          <div v-else-if="item.kind === 'audio'" class="asset-content">
            <audio :src="item.content" controls class="asset-audio" />
          </div>

          <div v-else-if="item.kind === 'video'" class="asset-content">
            <video :src="item.content" controls class="asset-video" />
          </div>

          <div v-else class="text-content">
            {{ item.content }}
          </div>
        </div>
      </div>

      <div v-if="isProcessing" class="message-wrap assistant">
        <div class="message-card loading">
          <div class="meta-row">
            <span class="role">助手</span>
            <span class="type">PROCESSING</span>
          </div>
          <div class="typing">
            <span />
            <span />
            <span />
          </div>
        </div>
      </div>
    </div>

    <div class="composer">
      <div v-if="supportsFileUpload" class="upload-row">
        <el-upload
          action="#"
          :auto-upload="false"
          :show-file-list="false"
          :on-change="onFileChange"
          :accept="fileAccept"
        >
          <el-button size="small" plain>
            选择文件
          </el-button>
        </el-upload>
        <span v-if="selectedFileName" class="file-name">
          {{ selectedFileName }}
        </span>
      </div>

      <el-input
        v-model="input"
        type="textarea"
        :autosize="{ minRows: 2, maxRows: 6 }"
        :placeholder="placeholder"
        @keydown.enter="onEnter"
      />

      <div class="actions">
        <el-button
          type="primary"
          :loading="isProcessing"
          :disabled="sendDisabled"
          @click="send"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { ChatDotRound } from '@element-plus/icons-vue';
import { chatAgent, uploadMultimodalFile } from '@/api/agent';
import { useAgentInteraction } from '../composables/useAgentInteraction';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'chat' }
});

const normalizedMode = computed(() => {
  const mode = (props.mode || 'chat').toLowerCase();
  if (mode === 'audio') return 'stt';
  return mode;
});

const modeLabel = computed(() => {
  const map = {
    chat: '对话模式',
    completion: '文本补全',
    image: '图像生成',
    tts: '语音合成',
    stt: '语音转文字',
    video: '视频生成',
    workflow: '工作流执行'
  };
  return map[normalizedMode.value] || '对话模式';
});

const placeholder = computed(() => {
  const map = {
    chat: '输入你的问题...',
    completion: '输入需要补全的文本...',
    image: '描述想生成的图像...',
    tts: '输入要转换为语音的文本...',
    stt: '可直接发送说明，或上传音频文件后发送',
    video: '描述想生成的视频场景...',
    workflow: '输入工作流参数(JSON 或自然语言)...'
  };
  return map[normalizedMode.value] || '输入内容...';
});

const supportsFileUpload = computed(() => normalizedMode.value === 'stt');
const fileAccept = computed(() => (normalizedMode.value === 'stt' ? 'audio/*' : '*'));

const input = ref('');
const messages = ref([]);
const messagesRef = ref(null);
const selectedFile = ref(null);
const selectedFileName = ref('');

const { isProcessing, result, error, interact } = useAgentInteraction(props.agentId);

watch(
  () => result.value,
  (val) => {
    if (!val) return;
    appendAssistantResult(val);
  }
);

watch(
  () => error.value,
  (val) => {
    if (!val) return;
    messages.value.push({ role: 'assistant', type: 'ERROR', kind: 'text', content: String(val) });
    scrollToBottom();
  }
);

watch(
  () => props.mode,
  () => {
    selectedFile.value = null;
    selectedFileName.value = '';
  }
);

const onEnter = (e) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    send();
  }
};

const sendDisabled = computed(() => {
  if (isProcessing.value) return true;
  if (normalizedMode.value === 'stt') {
    return !input.value.trim() && !selectedFile.value;
  }
  return !input.value.trim();
});

const clearMessages = () => {
  messages.value = [];
};

const onFileChange = (file) => {
  selectedFile.value = file.raw;
  selectedFileName.value = file.raw?.name || '';
};

const scrollToBottom = async () => {
  await nextTick();
  if (messagesRef.value) {
    messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
  }
};

const send = async () => {
  const text = input.value.trim();
  if (sendDisabled.value) return;

  messages.value.push({
    role: 'user',
    type: normalizedMode.value.toUpperCase(),
    kind: 'text',
    content: text || `[上传文件] ${selectedFileName.value || '音频'}`
  });
  input.value = '';
  await scrollToBottom();

  try {
    if (normalizedMode.value === 'stt' && selectedFile.value) {
      const upload = await uploadMultimodalFile(selectedFile.value);
      selectedFile.value = null;
      selectedFileName.value = '';
      await interact(text || '', upload.id);
      return;
    }

    if (normalizedMode.value === 'image') {
      const payload = {
        prompt: text,
        image_size: props.parameters?.imageSize || '1328x1328',
        guidance_scale: props.parameters?.guidanceScale || 7.5,
        num_inference_steps: props.parameters?.inferenceSteps || 20
      };
      if (props.parameters?.seed) payload.seed = parseInt(props.parameters.seed, 10);
      if (props.parameters?.negativePrompt) payload.negative_prompt = props.parameters.negativePrompt;
      await interact(payload);
      return;
    }

    if (normalizedMode.value === 'tts') {
      const payload = {
        input: text,
        model: props.parameters?.model || props.agentInfo?.modelName,
        voice: props.parameters?.voice,
        speed: props.parameters?.speed,
        gain: props.parameters?.gain
      };
      await interact(payload);
      return;
    }

    if (normalizedMode.value === 'video') {
      const payload = {
        prompt: text,
        model: props.parameters?.model || props.agentInfo?.modelName,
        seed: props.parameters?.seed ? parseInt(props.parameters.seed, 10) : undefined
      };
      await interact(payload);
      return;
    }

    // chat/completion/workflow fall back to standard chat endpoint
    const res = await chatAgent(
      props.agentId,
      text,
      null,
      props.parameters?.systemPrompt,
      null,
      props.parameters?.enableThinking,
      props.parameters?.thinkingBudget
    );

    const normalized = normalizeResponse(res);
    appendAssistantResult(normalized, res?.dataType || normalized?.dataType);
  } catch (e) {
    ElMessage.error(e.message || '发送失败');
    messages.value.push({ role: 'assistant', type: 'ERROR', kind: 'text', content: e.message || '发送失败' });
  } finally {
    scrollToBottom();
  }
};

const normalizeResponse = (res) => {
  if (res?.status === 'SUCCESS' && res?.data) return res.data;
  if (res?.status === 'PROCESSING') return { answer: '任务处理中，请稍候...' };
  return res;
};

const appendAssistantResult = (payload, dataType) => {
  const inferredDataType = (dataType || payload?.dataType || payload?.type || '').toUpperCase();

  if (inferredDataType === 'IMAGE' || payload?.image_url || payload?.url?.match(/\.(png|jpg|jpeg|webp|gif)(\?|$)/i)) {
    const url = payload?.image_url || payload?.url || payload?.images?.[0]?.url;
    if (url) {
      messages.value.push({ role: 'assistant', type: 'IMAGE', kind: 'image', content: url });
      scrollToBottom();
      return;
    }
  }

  if (inferredDataType === 'AUDIO' || payload?.audio_url) {
    const url = payload?.audio_url || payload?.url;
    if (url) {
      messages.value.push({ role: 'assistant', type: 'AUDIO', kind: 'audio', content: url });
      scrollToBottom();
      return;
    }
  }

  if (inferredDataType === 'VIDEO' || payload?.video_url) {
    const url = payload?.video_url || payload?.url;
    if (url) {
      messages.value.push({ role: 'assistant', type: 'VIDEO', kind: 'video', content: url });
      scrollToBottom();
      return;
    }
  }

  if (payload?.text) {
    messages.value.push({ role: 'assistant', type: inferredDataType || 'TEXT', kind: 'text', content: payload.text });
    scrollToBottom();
    return;
  }

  if (payload?.answer) {
    messages.value.push({ role: 'assistant', type: inferredDataType || 'TEXT', kind: 'text', content: payload.answer });
    scrollToBottom();
    return;
  }

  if (payload?.choices?.[0]?.message?.content) {
    messages.value.push({ role: 'assistant', type: 'TEXT', kind: 'text', content: payload.choices[0].message.content });
    scrollToBottom();
    return;
  }

  const fallback = typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2);
  messages.value.push({ role: 'assistant', type: inferredDataType || 'TEXT', kind: 'text', content: fallback });
  scrollToBottom();
};
</script>

<style scoped>
.unified-runner {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f6faf9 0%, #f8fafc 100%);
}

.runner-header {
  height: 56px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mode-icon {
  color: #0d9488;
}

.title {
  font-weight: 700;
  color: #0f172a;
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px 16px 140px;
}

.empty-state {
  margin: 70px auto;
  max-width: 420px;
  text-align: center;
  color: #64748b;
}

.empty-logo {
  width: 56px;
  opacity: 0.35;
}

.empty-title {
  margin-top: 10px;
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
}

.empty-subtitle {
  margin-top: 8px;
  font-size: 13px;
}

.message-wrap {
  display: flex;
  margin-bottom: 14px;
}

.message-wrap.user {
  justify-content: flex-end;
}

.message-wrap.assistant {
  justify-content: flex-start;
}

.message-card {
  width: min(840px, 88%);
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.03);
}

.message-wrap.user .message-card {
  border-color: #bae6fd;
  background: #f0f9ff;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  color: #64748b;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin-bottom: 8px;
}

.text-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: #0f172a;
  font-size: 14px;
}

.asset-content {
  border-radius: 10px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.asset-image {
  width: 100%;
  max-height: 420px;
}

.asset-audio {
  width: 100%;
  display: block;
}

.asset-video {
  width: 100%;
  max-height: 420px;
  display: block;
}

.typing {
  display: flex;
  gap: 6px;
}

.typing span {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #94a3b8;
  animation: pulse 1s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.composer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 16px;
  margin: 0 auto;
  width: min(900px, calc(100% - 24px));
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 14px;
  padding: 10px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.file-name {
  font-size: 12px;
  color: #475569;
}

.actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .message-card {
    width: 96%;
  }

  .composer {
    width: calc(100% - 12px);
    bottom: 8px;
  }
}
</style>
