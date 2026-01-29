<template>
  <div class="runner-container">
    <!-- Top Result Area -->
    <div class="result-section">
      <div class="result-placeholder" v-if="!videoUrl && !isProcessing">
        <el-icon :size="48"><VideoCamera /></el-icon>
        <p>输入提示词并点击生成以开始</p>
        <div class="hint-text">视频生成通常需要 1-3 分钟</div>
      </div>
      
      <div class="processing-placeholder" v-if="isProcessing">
        <div class="loading-wrapper">
          <div class="wave-loader">
            <span></span><span></span><span></span><span></span><span></span>
          </div>
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        </div>
        <p>正在努力生成视频中...</p>
        <div class="status-tips">{{ statusTip }}</div>
      </div>

      <div class="video-result" v-if="videoUrl && !isProcessing">
        <video 
          ref="videoPlayer"
          :src="videoUrl" 
          controls
          autoplay
          loop
          class="generated-video"
        ></video>
        <div class="result-actions">
          <el-button link type="primary" @click="downloadVideo">
            <el-icon><Download /></el-icon> 下载视频
          </el-button>
          <el-button link type="primary" @click="shareVideo">
            <el-icon><Share /></el-icon> 分享
          </el-button>
        </div>
      </div>
      
      <div v-if="error" class="error-wrapper">
        <el-alert :title="error" type="error" :closable="false" show-icon />
      </div>
    </div>

    <!-- Bottom Input Area -->
    <div class="input-area">
      <!-- Image Upload Area for I2V (Wan2.1-I2V, etc.) -->
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
              <span>待上传参考图</span>
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

      <div class="prompt-input-wrapper">
        <div class="input-main">
          <el-input
            v-model="prompt"
            placeholder="描述视频中发生的动作、场景和风格..."
            type="textarea"
            :rows="3"
            resize="none"
            class="prompt-textarea"
          />
          <el-button 
            type="primary" 
            class="generate-btn" 
            :loading="isProcessing" 
            @click="handleGenerate"
            :disabled="!prompt || (isI2V && !referenceImage)"
          >
            <div class="btn-content">
              <el-icon :size="24"><MagicStick /></el-icon>
              <span>开始生成</span>
            </div>
          </el-button>
        </div>
      </div>
      
      <!-- Prompt Suggestions -->
      <div class="footer-row">
        <div class="suggestions">
          <span class="label">常用提示:</span>
          <div class="tag-list">
            <el-tag 
              v-for="s in filteredSuggestions" 
              :key="s" 
              size="small" 
              effect="light" 
              round
              class="suggestion-tag"
              @click="prompt = s"
            >
              {{ s }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { 
  VideoCamera, Loading, Download, Share, MagicStick, 
  Plus, Close, Refresh, Warning 
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

const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const isI2V = computed(() => {
    const model = props.parameters?.model || props.agentInfo?.modelName || '';
    return model.toUpperCase().includes('I2V');
});

const videoUrl = computed(() => {
    if (!result.value) return '';
    
    // SiliconFlow 状态接口成功后返回 body: { status: 'Succeed', results: [{ url: '...' }] }
    // useAgentInteraction 会映射 body.results[0] 到 result.value
    if (result.value.url) return result.value.url;
    if (result.value.video_url) return result.value.video_url;
    
    return '';
});

const statusTips = [
    '正在排队中...',
    'AI 正在构思场景...',
    '渲染光影效果中...',
    '正在生成流畅动作...',
    '正在进行最后压缩...'
];
let tipTimer = null;

const startStatusTips = () => {
    let i = 0;
    statusTip.value = statusTips[0];
    tipTimer = setInterval(() => {
        i = (i + 1) % statusTips.length;
        statusTip.value = statusTips[i];
    }, 15000); // 视频生成比较慢，间隔长一点
};

const stopStatusTips = () => {
    if (tipTimer) clearInterval(tipTimer);
};

const suggestions = [
    '电影质感，赛博朋克风格的街道，霓虹灯闪烁，下着小雨。',
    '一只可爱的小猫在草地上追逐蝴蝶，温暖的阳光。',
    '宏伟的雪山延时摄影，云海快速流动，电影级光效。',
    '一个宇航员在火星表面行走，红色的沙尘暴正在逼近。',
    '二次元动漫风格，少女在樱花树下奔跑，花瓣飞舞。'
];

const i2vSuggestions = [
    '让图片中的人物微笑并挥手示意。',
    '图片中的背景云朵缓缓飘动，阳光洒下。',
    '镜头缓慢向图片中心平滑推入。',
    '让图片中的水面泛起涟漪，倒影波动。'
];

const filteredSuggestions = computed(() => {
    return isI2V.value ? i2vSuggestions : suggestions;
});

const handleGenerate = async () => {
    if (!prompt.value) return;
    if (isI2V.value && !referenceImage.value) {
        ElMessage.warning('请先上传参考图');
        return;
    }
    
    startStatusTips();
    
    const payload = {
        prompt: prompt.value,
        model: props.parameters?.model || props.agentInfo?.modelName,
        seed: props.parameters?.seed ? parseInt(props.parameters.seed) : undefined,
        negative_prompt: props.parameters?.negativePrompt,
    };
    
    // For I2V
    if (isI2V.value) {
        payload.reference_image = referenceImage.value; // SiliconFlow requires URL
    }
    
    await interact(payload);
    stopStatusTips();
};

const beforeUpload = (file) => {
  const isJPG = file.type === 'image/jpeg' || file.type === 'image/png';
  const isLt5M = file.size / 1024 / 1024 < 5;

  if (!isJPG) {
    ElMessage.error('参考图只能是 JPG 或 PNG 格式!');
  }
  if (!isLt5M) {
    ElMessage.error('图片大小不能超过 5MB!');
  }
  return isJPG && isLt5M;
};

const handleUploadSuccess = (response) => {
  // Assume response is { id: '...', url: '...' } or matches our saved format
  if (response.id || response.url) {
    referenceFileId.value = response.id;
    // We need the full URL for SiliconFlow API
    // If it's a relative path, prepend API base or use the download URL
    referenceImage.value = response.url.startsWith('http') 
        ? response.url 
        : `${window.location.origin}/api/v1/multimodal/files/${response.id}/download`;
    ElMessage.success('图片上传成功');
  }
};

const removeReferenceImage = () => {
    referenceImage.value = '';
    referenceFileId.value = '';
};

const downloadVideo = () => {
    if (videoUrl.value) {
        const a = document.createElement('a');
        a.href = videoUrl.value;
        a.download = `video-${Date.now()}.mp4`;
        a.target = '_blank';
        a.click();
    }
};

const shareVideo = () => {
    if (videoUrl.value) {
        navigator.clipboard.writeText(videoUrl.value);
        ElMessage.success('视频链接已复制到剪贴板');
    }
};

onUnmounted(() => {
    stopStatusTips();
});

</script>

<style scoped>
.runner-container {
    padding: 0;
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #fcfcfc;
}

/* Result Section */
.result-section {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    position: relative;
    background: #111; /* Dark background for video */
    margin: 20px;
    border-radius: 12px;
    box-shadow: inset 0 0 40px rgba(0,0,0,0.5);
}

.result-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    color: #666;
    text-align: center;
}

.hint-text {
    font-size: 12px;
    opacity: 0.6;
}

.processing-placeholder {
    color: #fff;
    text-align: center;
}

.loading-wrapper {
    margin-bottom: 24px;
    position: relative;
    display: flex;
    justify-content: center;
    align-items: center;
}

.status-tips {
    margin-top: 12px;
    font-size: 13px;
    color: #409eff;
    font-style: italic;
}

.video-result {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
}

.generated-video {
    max-width: 100%;
    max-height: 100%;
    box-shadow: 0 10px 30px rgba(0,0,0,0.5);
}

.result-actions {
    position: absolute;
    bottom: 20px;
    right: 20px;
    background: rgba(255,255,255,0.15);
    backdrop-filter: blur(10px);
    padding: 6px 16px;
    border-radius: 24px;
    border: 1px solid rgba(255,255,255,0.1);
}

.result-actions :deep(.el-button) {
    color: #fff;
}

/* Input Area */
.input-area {
    padding: 0 24px 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.image-upload-row {
    display: flex;
    align-items: center;
    gap: 20px;
    padding: 12px;
    background: #fff;
    border: 1px solid #efefef;
    border-radius: 12px;
}

.upload-box {
    position: relative;
    width: 100px;
    height: 100px;
    flex-shrink: 0;
}

.reference-uploader :deep(.el-upload) {
    width: 100px;
    height: 100px;
    border: 1px dashed #dcdfe6;
    border-radius: 8px;
    cursor: pointer;
    overflow: hidden;
    background: #f9f9f9;
    display: flex;
    justify-content: center;
    align-items: center;
}

.reference-uploader :deep(.el-upload:hover) {
    border-color: #409eff;
}

.ref-preview {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.ref-img-container {
    width: 100%;
    height: 100%;
    position: relative;
}

.ref-overlay {
    position: absolute;
    top: 0; left: 0; width: 100%; height: 100%;
    background: rgba(0,0,0,0.5);
    color: #fff;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    font-size: 12px;
    gap: 4px;
    opacity: 0;
    transition: opacity 0.2s;
}

.ref-img-container:hover .ref-overlay {
    opacity: 1;
}

.upload-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    font-size: 11px;
    color: #909399;
}

.remove-ref {
    position: absolute;
    top: -8px;
    right: -8px;
    width: 20px;
    height: 20px;
    background: #f56c6c;
    color: #fff;
    border-radius: 50%;
    display: flex;
    justify-content: center;
    align-items: center;
    cursor: pointer;
    z-index: 10;
}

.upload-info {
    flex: 1;
}

.upload-title {
    font-weight: 700;
    font-size: 14px;
    margin-bottom: 4px;
}

.upload-desc {
    font-size: 12px;
    color: #909399;
    line-height: 1.4;
}

/* Prompt Input */
.input-main {
    display: flex;
    gap: 12px;
}

.prompt-textarea :deep(.el-textarea__inner) {
    border-radius: 12px;
    padding: 12px 16px;
    background: #fff;
    box-shadow: 0 2px 12px rgba(0,0,0,0.03);
    border: 1px solid #efefef;
}

.generate-btn {
    width: 120px;
    height: auto;
    border-radius: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
    font-weight: 700;
}

.btn-content {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
}

/* Suggestions */
.footer-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.suggestions {
    display: flex;
    align-items: center;
    gap: 12px;
}

.suggestions .label {
    font-size: 12px;
    color: #909399;
    white-space: nowrap;
}

.tag-list {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.suggestion-tag {
    cursor: pointer;
    transition: all 0.2s;
    background: #fff;
}

.suggestion-tag:hover {
    background: #ecf5ff;
    border-color: #409eff;
    transform: translateY(-1px);
}

.error-wrapper {
    position: absolute;
    bottom: 20px;
    left: 20px;
    right: 20px;
}

/* Loader Animation */
.wave-loader {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4px;
  position: absolute;
  width: 100px;
  height: 100px;
}

.wave-loader span {
  width: 4px;
  height: 20px;
  background: #409eff;
  animation: wave 1s infinite ease-in-out;
}

.wave-loader span:nth-child(2) { animation-delay: 0.1s; }
.wave-loader span:nth-child(3) { animation-delay: 0.2s; }
.wave-loader span:nth-child(4) { animation-delay: 0.3s; }
.wave-loader span:nth-child(5) { animation-delay: 0.4s; }

@keyframes wave {
  0%, 40%, 100% { transform: scaleY(0.4); }
  20% { transform: scaleY(1); }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
