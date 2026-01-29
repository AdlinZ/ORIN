<template>
  <div class="runner-container">
    <div class="input-section">
      <el-upload
        class="audio-uploader"
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        accept="audio/*"
      >
        <el-icon class="el-icon--upload"><microphone /></el-icon>
        <div class="el-upload__text">
          Drop audio file here or <em>click to upload</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            Supported formats: mp3, wav, m4a, etc.
          </div>
        </template>
      </el-upload>
      
      <div class="action-bar">
        <el-button type="primary" :loading="isProcessing" @click="handleTranscribe" :disabled="!selectedFile">
          Start Transcription
        </el-button>
      </div>
    </div>

    <div class="result-section" v-if="result || isProcessing">
        <div class="section-title">Transcription Result</div>
        <el-input
            v-if="!isProcessing"
            v-model="resultText"
            type="textarea"
            :rows="10"
            readonly
        />
        <div v-else class="processing-placeholder">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>Processing audio...</span>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { Microphone, Loading } from '@element-plus/icons-vue';
import { useAgentInteraction } from '../composables/useAgentInteraction';
import { uploadMultimodalFile } from '@/api/agent';
import { ElMessage } from 'element-plus';

const props = defineProps({
  agentId: { type: String, required: true },
  agentInfo: { type: Object, default: () => ({}) }
});

const selectedFile = ref(null);
const { isProcessing, result, dataType, error, interact } = useAgentInteraction(props.agentId);

const resultText = computed(() => {
    if (!result.value) return '';
    
    // SiliconFlow STT 响应格式通常是 { "text": "..." }
    if (typeof result.value === 'object') {
        return result.value.text || JSON.stringify(result.value, null, 2);
    }
    return String(result.value);
});

const handleFileChange = (file) => {
    selectedFile.value = file.raw;
};

const handleTranscribe = async () => {
    if (!selectedFile.value) return;
    
    try {
        isProcessing.value = true;
        // 1. Upload to local ORIN storage
        const uploadRes = await uploadMultimodalFile(selectedFile.value);
        console.log('Upload response:', uploadRes);
        
        // uploadRes is the MultimodalFile entity
        const fileId = uploadRes.id;
        
        if (!fileId) {
            throw new Error("Failed to get file ID from server");
        }
        
        // 2. Interact with agent using the fileId
        // For STT, message text is empty
        await interact("", fileId);
        
    } catch (e) {
        ElMessage.error("Transcription failed: " + e.message);
        isProcessing.value = false;
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
    gap: 16px;
    align-items: center;
}
.action-bar { width: 100%; display: flex; justify-content: center; }
.result-section {
    flex: 1;
    border-top: 1px solid #eee;
    padding-top: 20px;
    display: flex;
    flex-direction: column;
    gap: 10px;
}
.section-title { font-weight: bold; color: #333; }
.processing-placeholder {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #666;
    justify-content: center;
    padding: 40px;
}
</style>
