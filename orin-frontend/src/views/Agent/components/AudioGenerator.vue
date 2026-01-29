<template>
  <div class="runner-container">
    <div class="input-section">
      <el-input
        v-model="prompt"
        type="textarea"
        :rows="6"
        maxlength="500"
        show-word-limit
        placeholder="请输入要转换成语音的文本内容..."
        resize="none"
        class="prompt-input"
      />
      
      <div class="action-bar">
        <el-button @click="prompt = ''" :disabled="!prompt || isProcessing">清空文本</el-button>
        <el-button type="primary" :loading="isProcessing" @click="handleGenerate" :disabled="!prompt">
          立即生成语音
        </el-button>
      </div>
    </div>

    <div class="result-section">
        <div class="section-title">生成结果</div>
        
        <div v-if="isProcessing" class="processing-placeholder">
            <el-skeleton animated :rows="3" />
            <div class="proc-text">语音合成中，请稍候...</div>
        </div>
        
        <div v-else-if="result" class="audio-player-wrapper">
            <audio controls autoplay :src="audioUrl" class="audio-player"></audio>
        </div>
        
        <div v-else class="empty-state">
            此处将显示生成的语音播放器
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');
const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const audioUrl = computed(() => {
    if (!result.value) return '';
    
    // Check both standard data wrapper and legacy direct structure
    const data = result.value.data || result.value;
    if (data && data.audio_url) {
        return data.audio_url;
    }
    
    return '';
});

const handleGenerate = async () => {
    if (!prompt.value) return;
    
    let voice = props.parameters?.voice;
    // Auto-prefix system voices if model is provided and prefix is missing
    if (voice && !voice.includes(':') && props.parameters?.model) {
        const systemVoices = ['alex', 'anna', 'bella', 'benjamin', 'charles', 'david', 'claire', 'diana'];
        if (systemVoices.includes(voice.toLowerCase())) {
            voice = `${props.parameters.model}:${voice}`;
        }
    }

    // Construct payload with sidebar parameters
    const payload = {
        input: prompt.value,
        model: props.parameters?.model || 'fishaudio/fish-speech-1.5',
        voice: voice,
        speed: props.parameters?.speed || 1.0,
        gain: props.parameters?.gain // Passing gain instead of volume
    };
    
    // Clean payload: remove only truly empty/undefined values
    Object.keys(payload).forEach(key => {
        if (payload[key] === undefined || payload[key] === '') {
            delete payload[key];
        }
    });
    
    console.log('[TTS Request]', payload);
    await interact(payload);
};
</script>

<style scoped>
.runner-container {
    padding: 24px;
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 32px;
    background: white;
}
.input-section {
    display: flex;
    flex-direction: column;
    gap: 16px;
}
.prompt-input :deep(.el-textarea__inner) {
    font-family: inherit;
    font-size: 16px;
    padding: 16px;
    border-radius: 8px;
    box-shadow: 0 1px 2px rgba(0,0,0,0.05);
}
.action-bar { 
    display: flex; 
    justify-content: flex-end; 
    gap: 12px;
}
.result-section {
    flex: 1;
    border-top: 1px solid #eee;
    padding-top: 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
}
.section-title { 
    font-weight: 600; 
    color: #1f2937; 
    font-size: 16px; 
}
.processing-placeholder {
    padding: 32px;
    background: #f9fafb;
    border-radius: 12px;
    text-align: center;
}
.proc-text {
    margin-top: 16px;
    color: #6b7280;
}
.audio-player-wrapper {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 32px;
    background: #f3f4f6;
    border-radius: 12px;
}
.audio-player {
    width: 100%;
    max-width: 600px;
}
.empty-state {
    color: #9ca3af;
    text-align: center;
    padding: 40px;
    font-style: italic;
}
</style>
