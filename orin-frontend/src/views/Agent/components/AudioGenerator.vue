<template>
  <div class="playground-stage">
    <!-- Main Content: Canvas -->
    <div class="canvas-container">
      <div class="canvas-inner" :class="{ 'has-audio': audioUrl && !isProcessing }">
        <!-- Result Placeholder / History Watermark -->
        <div v-if="!audioUrl && !isProcessing" class="empty-canvas">
          <div class="orin-watermark">
            <img src="/logo.png" alt="ORIN" class="watermark-logo" />
            <div class="watermark-text">ORIN Studio</div>
          </div>
          <div class="empty-hint">输入文本描述，体验专属声音质感</div>
        </div>

        <!-- Generation Progress -->
        <div v-if="isProcessing" class="canvas-loading">
          <div class="loading-animation">
            <div class="pulse-container">
              <div class="pulse-ring"></div>
              <el-icon class="is-loading brand-icon"><Microphone /></el-icon>
            </div>
            <p class="loading-text">正在合成语音，请稍候...</p>
          </div>
          <div class="wave-loader">
            <span></span><span></span><span></span><span></span><span></span>
          </div>
        </div>

        <!-- Result Canvas -->
        <div class="result-canvas" v-if="audioUrl && !isProcessing">
           <div class="audio-player-wrapper">
             <div class="waveform-container" ref="waveformContainer" @click="handleSeek">
                <canvas ref="waveformCanvas" class="waveform-canvas"></canvas>
                <div class="playhead" :style="{ left: playheadPosition + '%' }"></div>
             </div>
             <audio ref="audioPlayer" controls autoplay :src="audioUrl" class="audio-player" @timeupdate="updatePlayhead" @loadedmetadata="initWaveform"></audio>
           </div>
          
          <div class="canvas-actions">
            <el-button type="default" size="small" @click="downloadAudio" :icon="Download">保存音频</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- Floating Input Area -->
    <div class="input-framer">
      <!-- Prompt Suggestion Tags -->
      <div class="prompt-tags" v-if="!prompt">
        <span class="tag-title">常用文本:</span>
        <el-tag 
          v-for="tag in styleTags" 
          :key="tag" 
          class="clickable-tag"
          @click="prompt = tag"
          effect="plain"
          size="small"
        >{{ tag }}</el-tag>
      </div>

      <div class="input-card">
        <el-input
          v-model="prompt"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          placeholder="请输入要转换成语音的文本内容..."
          class="chat-textarea"
          resize="none"
        />
        <div class="input-footer">
          <div class="footer-left">
            <el-button link class="tool-btn" @click="prompt = ''" :disabled="!prompt"><el-icon><Delete /></el-icon></el-button>
          </div>
          <div class="footer-right">
            <el-button 
              type="primary" 
              class="generate-btn"
              @click="handleGenerate"
              :loading="isProcessing"
              :disabled="!prompt"
            >
              <el-icon style="margin-right: 6px;"><Microphone /></el-icon>
              开始合成
            </el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Microphone, Delete, Download } from '@element-plus/icons-vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');
const styleTags = ['你好，我是一个人工智能助手。', '这是关于宇宙的浪漫诗篇。', '今天天气很好，出去走走吧。', '请耐心等待程序运行结果。'];

const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const audioUrl = computed(() => {
    if (!result.value) return '';
    console.log('[Audio Generation Result]', result.value);
    const data = result.value.data || result.value;
    if (data && data.audio_url) return data.audio_url;
    if (data && data.url) return data.url;
    if (typeof data === 'string') return data;
    if (typeof result.value === 'string') return result.value;
    return '';
});

const audioPlayer = ref(null);
const waveformCanvas = ref(null);
const waveformContainer = ref(null);
const playheadPosition = ref(0);
const audioContextRef = ref(null);

const updatePlayhead = () => {
    if (audioPlayer.value && audioPlayer.value.duration) {
        playheadPosition.value = (audioPlayer.value.currentTime / audioPlayer.value.duration) * 100;
    }
};

const handleSeek = (event) => {
    if (!waveformContainer.value || !audioPlayer.value || !audioPlayer.value.duration) return;
    const rect = waveformContainer.value.getBoundingClientRect();
    const clickX = event.clientX - rect.left;
    const percentage = clickX / rect.width;
    audioPlayer.value.currentTime = percentage * audioPlayer.value.duration;
};

const initWaveform = async () => {
    if (!audioUrl.value || !waveformCanvas.value) return;
    try {
        const response = await fetch(audioUrl.value);
        if (!response.ok) throw new Error('Network response was not ok');
        const arrayBuffer = await response.arrayBuffer();
        
        if (!audioContextRef.value) {
            audioContextRef.value = new (window.AudioContext || window.webkitAudioContext)();
        }
        
        const audioBuffer = await audioContextRef.value.decodeAudioData(arrayBuffer);
        const channelData = audioBuffer.getChannelData(0); 
        
        setTimeout(() => {
            drawWaveform(channelData);
        }, 50);
        
    } catch (error) {
        console.error('Error rendering waveform (might be a CORS issue from external URL):', error);
        const canvas = waveformCanvas.value;
        if (canvas) {
           const ctx = canvas.getContext('2d');
           canvas.width = canvas.offsetWidth;
           canvas.height = canvas.offsetHeight;
           ctx.fillStyle = '#0f172a';
           ctx.fillRect(0, 0, canvas.width, canvas.height);
           ctx.fillStyle = '#64748b';
           ctx.font = '14px Arial';
           ctx.textAlign = 'center';
           ctx.fillText('无法加载真实波形(跨域限制)，请使用下方常规播放器', canvas.width/2, canvas.height/2);
        }
    }
};

const drawWaveform = (data) => {
    const canvas = waveformCanvas.value;
    if (!canvas) return;
    
    // Setup high DPI canvas
    const dpr = window.devicePixelRatio || 1;
    const rect = canvas.getBoundingClientRect();
    
    canvas.width = rect.width * dpr;
    canvas.height = rect.height * dpr;
    
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);
    const width = rect.width;
    const height = rect.height;
    
    ctx.clearRect(0, 0, width, height);
    
    // Background (Audition style dark)
    ctx.fillStyle = '#0f172a'; 
    ctx.fillRect(0, 0, width, height);
    
    // Center line
    ctx.fillStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.fillRect(0, height / 2, width, 1);
    
    const step = Math.ceil(data.length / width);
    const amp = height / 2;
    
    // Waveform gradient
    const gradient = ctx.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, '#2dd4bf'); // teal-400
    gradient.addColorStop(0.5, '#0d9488'); // teal-600
    gradient.addColorStop(1, '#2dd4bf'); // teal-400
    
    ctx.fillStyle = gradient;
    ctx.beginPath();
    
    for (let i = 0; i < width; i++) {
        let min = 1.0;
        let max = -1.0;
        
        for (let j = 0; j < step; j++) {
            const datum = data[(i * step) + j];
            if (datum < min) min = datum;
            if (datum > max) max = datum;
        }
        
        if (max === -1.0) max = 0;
        if (min === 1.0) min = 0;
        
        const y1 = (1 + min) * amp;
        const y2 = (1 + max) * amp;
        
        const h = Math.max(1, y2 - y1);
        ctx.fillRect(i, y1, 1, h);
    }
};

const handleGenerate = async () => {
    if (!prompt.value) return;
    
    let voice = props.parameters?.voice;
    if (voice && !voice.includes(':') && props.parameters?.model) {
        const systemVoices = ['alex', 'anna', 'bella', 'benjamin', 'charles', 'david', 'claire', 'diana'];
        if (systemVoices.includes(voice.toLowerCase())) {
            voice = `${props.parameters.model}:${voice}`;
        }
    }

    const payload = {
        input: prompt.value,
        model: props.parameters?.model || 'fishaudio/fish-speech-1.5',
        voice: voice,
        speed: props.parameters?.speed || 1.0,
        gain: props.parameters?.gain
    };
    
    Object.keys(payload).forEach(key => {
        if (payload[key] === undefined || payload[key] === '') {
            delete payload[key];
        }
    });
    
    await interact(payload);
};

const downloadAudio = () => {
    if (audioUrl.value) {
        const a = document.createElement('a');
        a.href = audioUrl.value;
        a.download = `audio-${Date.now()}.mp3`;
        a.target = '_blank';
        a.click();
    }
};
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
  transition: all 0.4s;
}

.canvas-inner.has-audio {
  background: transparent;
  border: none;
  box-shadow: none;
}

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
.wave-loader span:nth-child(4) { animation-delay: 0.3s; }
.wave-loader span:nth-child(5) { animation-delay: 0.4s; }
@keyframes wave { 0%, 40%, 100% { transform: scaleY(0.4); } 20% { transform: scaleY(1); } }

.result-canvas { width: 100%; height: 100%; position: relative; display: flex; justify-content: center; align-items: center; }
.canvas-actions {
  position: absolute; bottom: 20px; right: 20px;
  display: flex; gap: 8px; background: rgba(255,255,255,0.2); backdrop-filter: blur(12px);
  padding: 6px 12px; border-radius: 20px; border: 1px solid rgba(255,255,255,0.1);
}
.canvas-actions :deep(.el-button) { background: transparent; }

.audio-player-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  width: 100%;
  max-width: 700px;
}
.waveform-container {
  width: 100%;
  height: 120px;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.waveform-canvas {
  width: 100%;
  height: 100%;
  display: block;
}
.playhead {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #ef4444; 
  box-shadow: 0 0 4px rgba(239, 68, 68, 0.8);
  pointer-events: none;
  z-index: 2;
}
.audio-player {
  width: 100%;
}

.input-framer { position: absolute; bottom: 24px; left: 0; right: 0; padding: 0 24px; z-index: 100; }
.prompt-tags { max-width: 860px; margin: 0 auto 12px auto; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.tag-title { font-size: 12px; font-weight: 700; color: #9ca3af; margin-right: 4px; }
.clickable-tag { cursor: pointer; border-radius: 12px; transition: all 0.2s; }
.clickable-tag:hover { background: #0d9488; color: #fff; border-color: #0d9488; }

.input-card { max-width: 860px; margin: 0 auto; background: #ffffff; border: 1px solid #d1d5db; border-radius: 16px; box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1); overflow: hidden; }
.chat-textarea :deep(.el-textarea__inner) { border: none; box-shadow: none; padding: 20px; font-size: 15px; background: transparent; }
.input-footer { display: flex; align-items: center; justify-content: space-between; padding: 12px 20px; border-top: 1px solid #f3f4f6; }
.tool-btn { color: #9ca3af; font-size: 18px; padding: 6px; }
.generate-btn { padding: 10px 24px; border-radius: 12px; font-weight: 700; background: #0d9488 !important; border: none !important; box-shadow: 0 4px 10px rgba(13, 148, 136, 0.3); }
</style>
