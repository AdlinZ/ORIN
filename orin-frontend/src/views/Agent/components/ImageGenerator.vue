<template>
  <div class="runner-container">
    <div class="input-section">
      <el-input
        v-model="prompt"
        type="textarea"
        :rows="4"
        placeholder="Describe the image you want to generate..."
        resize="none"
      />
      
      <div class="params-row">
        <el-button type="primary" :loading="isProcessing" @click="handleGenerate" :disabled="!prompt" style="width: 100%">
          Generate <el-icon class="el-icon--right"><Picture /></el-icon>
        </el-button>
      </div>
    </div>

    <div class="result-section">
        <div class="result-placeholder" v-if="!result && !isProcessing">
            <el-icon :size="48"><Picture /></el-icon>
            <p>Enter a prompt to start generation</p>
        </div>
        
        <div class="processing-placeholder" v-if="isProcessing">
             <div class="loading-animation">
                <!-- Simple pulse or loader -->
                <el-icon class="is-loading" :size="32"><Loading /></el-icon>
             </div>
             <p>Generating Image...</p>
        </div>

        <div class="image-result" v-if="result && !isProcessing">
             <el-image 
                :src="imageUrl" 
                fit="contain" 
                :preview-src-list="[imageUrl]"
                class="generated-image"
             >
                <template #error>
                    <div class="image-error">
                        <el-icon><Warning /></el-icon>
                        <span>Failed to load image</span>
                    </div>
                </template>
             </el-image>
             <div class="result-actions">
                <el-button link type="primary" @click="downloadImage">Download</el-button>
             </div>
        </div>
        
        <div v-if="error" class="error-msg">{{ error }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { Picture, Loading, Warning } from '@element-plus/icons-vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');

const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const imageUrl = computed(() => {
    if (!result.value) return '';
    
    console.log('Image generation result:', result.value);
    
    // SiliconFlow 格式: { "images": [{"url": "..."}], "timings": {...}, "seed": 123 }
    if (result.value.images && Array.isArray(result.value.images) && result.value.images.length > 0) {
        const firstImage = result.value.images[0];
        if (firstImage.url) {
            console.log('Found image URL:', firstImage.url);
            return firstImage.url;
        }
    }
    
    // 如果是字符串 URL
    if (typeof result.value === 'string') return result.value;
    
    // 如果直接有 url 字段
    if (result.value.url) return result.value.url;
    
    console.warn('Could not extract image URL from result:', result.value);
    return '';
});

const handleGenerate = async () => {
    if (!prompt.value) return;
    
    // Construct payload using parameters from parent component (sidebar config)
    const payload = {
        prompt: prompt.value,
        image_size: props.parameters?.imageSize || '1328x1328',
        guidance_scale: props.parameters?.guidanceScale || 7.5,
        num_inference_steps: props.parameters?.inferenceSteps || 20
    };
    
    // 添加可选参数
    if (props.parameters?.seed && props.parameters.seed.trim() !== '') {
        payload.seed = parseInt(props.parameters.seed);
    }
    
    if (props.parameters?.negativePrompt && props.parameters.negativePrompt.trim() !== '') {
        payload.negative_prompt = props.parameters.negativePrompt;
    }
    
    console.log('Sending image generation request with params:', payload);
    
    await interact(payload);
};

const downloadImage = () => {
    if (imageUrl.value) {
        const a = document.createElement('a');
        a.href = imageUrl.value;
        a.download = `generated-${Date.now()}.png`; // Might not work for cross-origin without proper headers
        a.target = '_blank';
        a.click();
    }
};

</script>

<style scoped>
.runner-container {
    padding: 20px;
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 20px;
    background: white;
}
.input-section {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.params-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.result-section {
    flex: 1;
    border-radius: 8px;
    background: #f8f9fa;
    display: flex;
    justify-content: center;
    align-items: center;
    overflow: hidden;
    position: relative;
    border: 1px dashed #ddd;
}
.result-placeholder, .processing-placeholder {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
    color: #999;
}
.image-result {
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
}
.generated-image {
    max-width: 100%;
    max-height: 100%;
}
.result-actions {
    position: absolute;
    bottom: 12px;
    right: 12px;
    background: rgba(255,255,255,0.9);
    padding: 4px 12px;
    border-radius: 16px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}
.error-msg {
    position: absolute;
    bottom: 12px;
    color: red;
    font-size: 12px;
}
</style>
