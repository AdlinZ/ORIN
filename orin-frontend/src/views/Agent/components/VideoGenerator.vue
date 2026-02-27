<template>
  <div class="playground-stage">
    <!-- Main Content: Canvas -->
    <div class="canvas-container">
      <div class="canvas-inner" :class="{ 'has-video': videoUrl && !isProcessing }">
        <!-- Result Placeholder / History Watermark -->
        <div v-if="!videoUrl && !isProcessing" class="empty-canvas">
          <div class="orin-watermark">
            <img src="/logo.svg" alt="ORIN" class="watermark-logo" />
            <div class="watermark-text">ORIN Motion</div>
          </div>
          <div class="empty-hint">输入场景描述或上传首帧参考图，开始导演之旅</div>
        </div>

        <!-- Generation Progress -->
        <div v-if="isProcessing" class="canvas-loading">
          <div class="loading-animation">
            <div class="pulse-container">
              <div class="pulse-ring"></div>
              <el-icon class="is-loading brand-icon"><VideoCamera /></el-icon>
            </div>
            <p class="loading-text">{{ statusTip }}</p>
            <div class="wave-loader">
              <span></span><span></span><span></span><span></span><span></span>
            </div>
          </div>
        </div>

        <!-- Result Canvas -->
        <div class="result-canvas" v-if="videoUrl && !isProcessing">
          <video 
            ref="videoPlayer"
            :src="videoUrl" 
            controls
            autoplay
            loop
            class="main-generated-video"
          ></video>
          
          <div class="canvas-actions">
            <el-button type="default" size="small" @click="downloadVideo" :icon="Download">保存视频</el-button>
            <el-button type="default" size="small" @click="shareVideo" :icon="Share">分享</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Floating Input Area -->
    <div class="input-framer">
      <!-- Image Upload Area for I2V -->
      <div v-if="isI2V" class="image-upload-row">
        <div class="upload-box" :class="{ 'has-image': referenceImage }">
          <el-upload
            class="reference-uploader"
            action="/api/v1/multimodal/upload"
            :show-file-list="false"
            :on-success="handleUploadSuccess"
            :before-upload="beforeUpload"
          >
            <div v-if="referenceImage" class="ref-img-container">
              <img :src="referenceImage" class="ref-preview" />
              <div class="ref-overlay">
                <el-icon><Refresh /></el-icon>
                <span>更换图片</span>
              </div>
            </div>
            <div v-else class="upload-placeholder">
              <el-icon class="uploader-icon"><Plus /></el-icon>
              <span>待上传视频首帧</span>
            </div>
          </el-upload>
          <div v-if="referenceImage" class="remove-ref" @click.stop="removeReferenceImage">
            <el-icon><Close /></el-icon>
          </div>
        </div>
        <div class="upload-info">
          <div class="upload-title">参考图 (Image Reference)</div>
          <div class="upload-desc">上传一张图片作为视频的第一帧或参考风格。Wan-I2V 模型必备。</div>
        </div>
      </div>

      <!-- Prompt Suggestion Tags -->
      <div class="prompt-tags" v-if="!prompt">
        <span class="tag-title">常用创意:</span>
        <el-tag 
          v-for="s in filteredSuggestions" 
          :key="s" 
          class="clickable-tag"
          @click="prompt = s"
          effect="plain"
          size="small"
        >{{ s }}</el-tag>
      </div>

      <div class="input-card">
        <el-input
          v-model="prompt"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="描述视频发生的动作、场景和风格..."
          class="chat-textarea"
          resize="none"
        />
        <div class="input-footer">
          <div class="footer-left">
            <el-button link class="tool-btn"><el-icon><Operation /></el-icon></el-button>
            <el-button link class="tool-btn"><el-icon><MagicStick /></el-icon></el-button>
          </div>
          <div class="footer-right">
            <el-button 
              type="primary" 
              class="generate-btn"
              @click="handleGenerate"
              :loading="isProcessing"
              :disabled="!prompt || (isI2V && !referenceImage)"
            >
              <el-icon style="margin-right: 6px;"><VideoCamera /></el-icon>
              开始生成视频
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-if="error" class="error-toast">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { 
  VideoCamera, Loading, Download, Share, MagicStick, 
  Plus, Close, Refresh, Operation
} from '@element-plus/icons-vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';
import { ElMessage } from 'element-plus';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');
const referenceImage = ref('');
const referenceFileId = ref('');
const statusTip = ref('正在链接服务器...');
const tipTimer = ref(null);

const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const isI2V = computed(() => {
    const model = props.parameters?.model || props.agentInfo?.modelName || '';
    return model.toUpperCase().includes('I2V');
});

const videoUrl = computed(() => {
    if (!result.value) return '';
    return result.value.url || result.value.video_url || '';
});

const statusTipsList = ['正在排队中...', 'AI 正在构思场景...', '渲染月影光效...', '生成流畅动作...', '完成最后润色...'];

const handleGenerate = async () => {
    if (!prompt.value) return;
    if (isI2V.value && !referenceImage.value) {
        ElMessage.warning('请先上传基础参考图');
        return;
    }
    
    let tipIdx = 0;
    statusTip.value = statusTipsList[0];
    tipTimer.value = setInterval(() => {
        tipIdx = (tipIdx + 1) % statusTipsList.length;
        statusTip.value = statusTipsList[tipIdx];
    }, 8000);
    
    const payload = {
        prompt: prompt.value,
        model: props.parameters?.model || props.agentInfo?.modelName,
        seed: props.parameters?.seed ? parseInt(props.parameters.seed) : undefined,
    };
    if (isI2V.value) payload.reference_image = referenceImage.value;
    
    await interact(payload);
    clearInterval(tipTimer.value);
};

const suggestions = ['梦幻般的极光照亮寂静的针叶林', '清晨雨后的鹅卵石小道，雾气缭绕', '宇航员在荒芜的土卫六表面滑行'];
const i2vSuggestions = ['让图中的溪流水面缓慢泛起涟漪', '给图中静态的人物加上微微的笑容', '背景中的落叶在风中零星飘动'];
const filteredSuggestions = computed(() => isI2V.value ? i2vSuggestions : suggestions);

const handleUploadSuccess = (res) => {
    if (res.id || res.url) {
        referenceFileId.value = res.id;
        referenceImage.value = res.url.startsWith('http') ? res.url : `${window.location.origin}/api/v1/multimodal/files/${res.id}/download`;
        ElMessage.success('首帧图片采样成功');
    }
};

const beforeUpload = (file) => {
    const isImg = file.type === 'image/jpeg' || file.type === 'image/png';
    if (!isImg) ElMessage.error('仅支持 JPG/PNG 格式图片');
    return isImg;
};

const removeReferenceImage = () => { referenceImage.value = ''; referenceFileId.value = ''; };
const downloadVideo = () => videoUrl.value && window.open(videoUrl.value, '_blank');
const shareVideo = () => {
    if (videoUrl.value) { navigator.clipboard.writeText(videoUrl.value); ElMessage.success('视频资产已复制'); }
};

onUnmounted(() => tipTimer.value && clearInterval(tipTimer.value));
</script>

<style scoped>
.playground-stage {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f9fafb;
  position: relative;
  overflow: hidden;
}

.canvas-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px 40px 240px 40px;
}

.canvas-inner {
  width: 100%;
  max-width: 900px;
  min-height: 500px;
  aspect-ratio: 16 / 9;
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
  transition: background 0.4s;
}
.canvas-inner.has-video { background: #000; border: none; }

.empty-canvas { display: flex; flex-direction: column; align-items: center; }
.orin-watermark { display: flex; flex-direction: column; align-items: center; opacity: 0.1; filter: grayscale(1); }
.watermark-logo { width: 100px; margin-bottom: 12px; }
.watermark-text { font-size: 28px; font-weight: 900; letter-spacing: 6px; text-transform: uppercase; color: #000; }
.empty-hint { margin-top: 32px; color: #9ca3af; font-size: 15px; }

.canvas-loading { display: flex; flex-direction: column; align-items: center; z-index: 10; }
.pulse-container { position: relative; width: 80px; height: 80px; display: flex; align-items: center; justify-content: center; margin-bottom: 24px; }
.pulse-ring { position: absolute; width: 100%; height: 100%; border: 4px solid #0d9488; border-radius: 50%; animation: pulse 2s infinite cubic-bezier(0.215, 0.61, 0.355, 1); }
@keyframes pulse { 0% { transform: scale(0.33); opacity: 0; } 25% { opacity: 0.1; } 50% { transform: scale(1); opacity: 0; } 100% { opacity: 0; } }
.brand-icon { font-size: 32px; color: #0d9488; }
.loading-text { font-size: 14px; color: #64748b; font-weight: 600; margin-bottom: 16px; }

.wave-loader { display: flex; gap: 4px; }
.wave-loader span { width: 3px; height: 12px; background: #0d9488; animation: wave 1s infinite ease-in-out; }
.wave-loader span:nth-child(2) { animation-delay: 0.1s; }
.wave-loader span:nth-child(3) { animation-delay: 0.2s; }
@keyframes wave { 0%, 40%, 100% { transform: scaleY(0.4); } 20% { transform: scaleY(1); } }

.result-canvas { width: 100%; height: 100%; position: relative; }
.main-generated-video { width: 100%; height: 100%; object-fit: contain; }
.canvas-actions {
  position: absolute; bottom: 20px; right: 20px;
  display: flex; gap: 8px; background: rgba(255,255,255,0.2); backdrop-filter: blur(12px);
  padding: 6px 12px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.1);
}
.canvas-actions :deep(.el-button) { color: #fff; border: none; background: transparent; }

.input-framer { position: absolute; bottom: 24px; left: 0; right: 0; padding: 0 24px; z-index: 100; }
.image-upload-row { 
    max-width: 860px; margin: 0 auto 16px auto; 
    display: flex; align-items: center; gap: 16px; 
    padding: 12px; background: #fff; border: 1px solid #eef0f3; border-radius: 16px;
    box-shadow: 0 4px 10px rgba(0,0,0,0.02);
}
.upload-box { position: relative; width: 64px; height: 64px; flex-shrink: 0; }
.reference-uploader :deep(.el-upload) { width: 64px; height: 64px; border: 2px dashed #e2e8f0; border-radius: 12px; overflow: hidden; display: flex; justify-content: center; align-items: center; }
.ref-preview { width: 100%; height: 100%; object-fit: cover; }
.upload-placeholder { font-size: 10px; color: #94a3b8; text-align: center; }
.remove-ref { position: absolute; top: -6px; right: -6px; width: 18px; height: 18px; background: #ef4444; color: #fff; border-radius: 50%; display: flex; justify-content: center; align-items: center; font-size: 10px; cursor: pointer; }
.upload-info { flex: 1; }
.upload-title { font-size: 13px; font-weight: 700; color: #1e293b; }
.upload-desc { font-size: 11px; color: #64748b; }

.prompt-tags { max-width: 860px; margin: 0 auto 12px auto; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.tag-title { font-size: 12px; font-weight: 700; color: #9ca3af; margin-right: 4px; }
.clickable-tag { cursor: pointer; border-radius: 12px; transition: all 0.2s; }
.clickable-tag:hover { background: #0d9488; color: #fff; border-color: #0d9488; }

.input-card { max-width: 860px; margin: 0 auto; background: #ffffff; border: 1px solid #d1d5db; border-radius: 16px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
.chat-textarea :deep(.el-textarea__inner) { border: none; box-shadow: none; padding: 20px; font-size: 15px; }
.input-footer { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-top: 1px solid #f3f4f6; }
.generate-btn { padding: 10px 24px; border-radius: 12px; font-weight: 700; background: #0d9488 !important; border: none !important; }

.error-toast { position: fixed; top: 80px; left: 50%; transform: translateX(-50%); background: #ef4444; color: #fff; padding: 8px 20px; border-radius: 20px; font-size: 13px; z-index: 2000; }
</style>
