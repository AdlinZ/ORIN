<template>
  <div class="playground-stage">
    <!-- Main Content: Canvas -->
    <div class="canvas-container">
      <div class="canvas-inner" :class="{ 'has-image': imageUrl && !isProcessing }">
        <!-- Result Placeholder / History Watermark -->
        <div v-if="!result && !isProcessing" class="empty-canvas">
          <div class="orin-watermark">
            <img src="/logo.svg" alt="ORIN" class="watermark-logo">
            <div class="watermark-text">
              ORIN Studio
            </div>
          </div>
          <div class="empty-hint">
            在下方输入视觉灵感，开始创作
          </div>
        </div>

        <!-- Generation Progress -->
        <div v-if="isProcessing" class="canvas-loading">
          <div class="loading-animation">
            <div class="pulse-container">
              <div class="pulse-ring" />
              <el-icon class="is-loading brand-icon">
                <Picture />
              </el-icon>
            </div>
            <p class="loading-text">
              正在捕捉视觉粒子...
            </p>
          </div>
        </div>

        <!-- Result Canvas -->
        <div v-if="result && !isProcessing" class="result-canvas">
          <el-image 
            :src="imageUrl" 
            fit="contain" 
            :preview-src-list="[imageUrl]"
            class="main-generated-image"
          >
            <template #error>
              <div class="image-error">
                <el-icon :size="48">
                  <Warning />
                </el-icon>
                <span>图像资源加载失败</span>
              </div>
            </template>
          </el-image>
          
          <div class="canvas-actions">
            <el-button
              type="default"
              size="small"
              :icon="Download"
              @click="downloadImage"
            >
              保存图像
            </el-button>
            <el-button type="default" size="small" :icon="Share">
              分享
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Floating Input Area -->
    <div class="input-framer">
      <!-- Prompt Suggestion Tags -->
      <div v-if="!prompt" class="prompt-tags">
        <span class="tag-title">常用风格:</span>
        <el-tag 
          v-for="tag in styleTags" 
          :key="tag" 
          class="clickable-tag"
          effect="plain"
          size="small"
          @click="prompt = tag"
        >
          {{ tag }}
        </el-tag>
      </div>

      <div class="input-card">
        <el-input
          v-model="prompt"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="描述你想要生成的图像细节，如风格、场景、光影..."
          class="chat-textarea"
          resize="none"
        />
        <div class="input-footer">
          <div class="footer-left">
            <el-button link class="tool-btn">
              <el-icon><Operation /></el-icon>
            </el-button>
            <el-button link class="tool-btn">
              <el-icon><MagicStick /></el-icon>
            </el-button>
          </div>
          <div class="footer-right">
            <el-button 
              type="primary" 
              class="generate-btn"
              :loading="isProcessing"
              :disabled="!prompt"
              @click="handleGenerate"
            >
              <el-icon style="margin-right: 6px;">
                <MagicStick />
              </el-icon>
              立即生成
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="error" class="error-toast">
      {{ error }}
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { 
  Picture, Loading, Warning, Operation, MagicStick, 
  Download, Share 
} from '@element-plus/icons-vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');
const styleTags = ['赛博朋克 2077', '电影感 8K 光追', '宫崎骏动漫风格', '写实油画', '极简主义海报'];

const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const imageUrl = computed(() => {
    if (!result.value) return '';
    // result.value 已经是 data 对象 (由 useAgentInteraction 设置 res.data)
    if (result.value.image_url) {
        return result.value.image_url;
    }
    // 兼容 images 数组格式
    if (result.value.images && Array.isArray(result.value.images) && result.value.images.length > 0) {
        return result.value.images[0].url;
    }
    // 直接 URL 字符串
    if (typeof result.value === 'string') return result.value;
    return result.value.url || '';
});

const handleGenerate = async () => {
    if (!prompt.value) return;
    const payload = {
        prompt: prompt.value,
        image_size: props.parameters?.imageSize || '1328x1328',
        guidance_scale: props.parameters?.guidanceScale || 7.5,
        num_inference_steps: props.parameters?.inferenceSteps || 20
    };
    if (props.parameters?.seed) payload.seed = parseInt(props.parameters.seed);
    if (props.parameters?.negativePrompt) payload.negative_prompt = props.parameters.negativePrompt;
    await interact(payload);
};

const downloadImage = () => {
    if (imageUrl.value) {
        window.open(imageUrl.value, '_blank');
    }
};
</script>

<style scoped>
.playground-stage {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 16px;
  background: #f9fafb;
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
}

.canvas-container {
  min-height: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 28px clamp(18px, 4vw, 48px) 0;
  overflow: hidden;
  box-sizing: border-box;
}

.canvas-inner {
  width: min(100%, 1120px);
  height: clamp(300px, 52vh, 560px);
  min-height: 0;
  margin: 0 auto;
  background: #ffffff;
  border-radius: 24px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 4px 20px rgba(0,0,0,0.03);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden;
  transition: all 0.4s;
}
.canvas-inner.has-image {
  background: transparent;
  border: none;
  box-shadow: none;
}

/* Empty State / Watermark */
.empty-canvas {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.orin-watermark {
  display: flex;
  flex-direction: column;
  align-items: center;
  opacity: 0.1;
  filter: grayscale(1);
}
.watermark-logo { width: 100px; margin-bottom: 12px; }
.watermark-text {
  font-size: 28px;
  font-weight: 900;
  letter-spacing: 6px;
  text-transform: uppercase;
  color: #000;
}
.empty-hint {
  margin-top: 32px;
  color: #9ca3af;
  font-size: 15px;
}

/* Generation Loading */
.canvas-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.pulse-container {
  position: relative;
  width: 80px;
  height: 80px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24px;
}
.pulse-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 4px solid #0d9488;
  border-radius: 50%;
  animation: pulse 2s infinite cubic-bezier(0.215, 0.61, 0.355, 1);
}
@keyframes pulse {
  0% { transform: scale(0.33); opacity: 0; }
  25% { opacity: 0.1; }
  50% { transform: scale(1); opacity: 0; }
  100% { opacity: 0; }
}
.brand-icon { font-size: 32px; color: #0d9488; }
.loading-text { font-size: 14px; color: #64748b; font-weight: 600; }

/* Result Action Bar */
.result-canvas { width: 100%; height: 100%; position: relative; }
.main-generated-image { width: 100%; height: 100%; }
.canvas-actions {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 8px;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(12px);
  padding: 6px 12px;
  border-radius: 20px;
  border: 1px solid rgba(0,0,0,0.05);
  box-shadow: 0 4px 15px rgba(0,0,0,0.1);
}

/* Floating Input */
.input-framer {
  position: relative;
  padding: 0 clamp(18px, 4vw, 48px) 28px;
  z-index: 100;
  box-sizing: border-box;
}
.prompt-tags {
  max-width: 960px;
  margin: 0 auto 12px auto;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.tag-title { font-size: 12px; font-weight: 700; color: #9ca3af; margin-right: 4px; }
.clickable-tag { cursor: pointer; border-radius: 12px; transition: all 0.2s; }
.clickable-tag:hover { background: #0d9488; color: #fff; border-color: #0d9488; }

.input-card {
  max-width: 960px;
  margin: 0 auto;
  background: #ffffff;
  border: 1px solid #d1d5db;
  border-radius: 16px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}
.chat-textarea :deep(.el-textarea__inner) {
  min-height: 86px !important;
  border: none;
  box-shadow: none;
  padding: 20px;
  font-size: 15px;
  line-height: 1.5;
  background: transparent;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid #f3f4f6;
}
.tool-btn { color: #9ca3af; font-size: 18px; padding: 6px; }
.generate-btn {
  min-width: 138px;
  padding: 10px 24px;
  border-radius: 12px;
  font-weight: 700;
  background: #0d9488 !important;
  border: none !important;
  box-shadow: 0 4px 10px rgba(13, 148, 136, 0.3);
}

.error-toast {
  position: fixed;
  top: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: #ef4444;
  color: #fff;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 13px;
  z-index: 2000;
}

@media (max-width: 900px) {
  .playground-stage {
    gap: 12px;
  }

  .canvas-container {
    padding: 18px 16px 0;
  }

  .canvas-inner {
    height: clamp(260px, 46vh, 440px);
    border-radius: 18px;
  }

  .input-framer {
    padding: 0 16px 18px;
  }

  .input-footer {
    gap: 12px;
  }
}

@media (max-width: 560px) {
  .prompt-tags {
    display: none;
  }

  .input-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .footer-left,
  .footer-right {
    display: flex;
    justify-content: space-between;
  }

  .generate-btn {
    width: 100%;
  }
}
</style>
