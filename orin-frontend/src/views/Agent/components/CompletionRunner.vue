<template>
  <div class="completion-runner">
    <div class="runner-header">
      <h4>Text Generation</h4>
      <p class="desc">Enter a prompt to generate text.</p>
    </div>

    <div class="main-area">
        <div class="input-area">
             <el-input 
               v-model="prompt" 
               type="textarea" 
               :rows="6" 
               placeholder="Enter your prompt here..."
               resize="none"
             />
             <div class="actions">
                <el-button type="primary" @click="generate" :loading="loading" class="gen-btn">
                   Generate
                </el-button>
             </div>
        </div>

        <div class="output-area">
            <div class="output-label">Result</div>
            <div class="output-content" v-loading="loading">
                <div v-if="result" class="markdown-body">{{ result }}</div>
                <div v-else class="placeholder">Generated text will appear here...</div>
            </div>
            <div v-if="error" class="error-msg">{{ error }}</div>
        </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { chatAgent } from '@/api/agent'; // Reuse chat API
import { ElMessage } from 'element-plus';

const props = defineProps({
  agentId: { type: String, required: true },
  parameters: { type: Object, default: () => ({}) }
});

const prompt = ref('');
const loading = ref(false);
const result = ref('');
const error = ref(null);

const generate = async () => {
    if (!prompt.value.trim()) return;
    loading.value = true;
    error.value = null;
    result.value = '';
    
    try {
        const systemPrompt = props.parameters?.systemPrompt;
        const enableThinking = props.parameters?.enableThinking;
        const thinkingBudget = props.parameters?.thinkingBudget;
        const res = await chatAgent(props.agentId, prompt.value, null, systemPrompt, null, enableThinking, thinkingBudget);
        // Completion response commonly has 'answer' or 'message.content'
        if (typeof res === 'string') {
            result.value = res;
        } else {
            result.value = res.answer || res.message?.content || JSON.stringify(res);
        }
    } catch (e) {
        error.value = e.message || 'Generation failed';
        ElMessage.error('Generation failed');
    } finally {
        loading.value = false;
    }
};
</script>

<style scoped>
.completion-runner {
    padding: 20px;
    height: 100%;
    display: flex;
    flex-direction: column;
}

.runner-header { margin-bottom: 20px; }
.runner-header h4 { margin: 0 0 4px 0; }
.desc { margin: 0; color: #999; font-size: 12px; }

.main-area {
    display: flex;
    flex-direction: column;
    gap: 20px;
    flex: 1;
}

.input-area {
    display: flex;
    gap: 12px;
    align-items: flex-start;
}

.input-area .el-input { flex: 1; }
.gen-btn { height: 100%; min-height: 130px; width: 100px; writing-mode: vertical-lr; letter-spacing: 4px; }

.output-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    border: 1px solid #eee;
    border-radius: 8px;
    background: #fff;
    overflow: hidden;
}

.output-label {
    padding: 8px 12px;
    background: #f5f7fa;
    border-bottom: 1px solid #eee;
    font-size: 12px;
    font-weight: 700;
    color: #666;
}

.output-content {
    padding: 16px;
    flex: 1;
    overflow-y: auto;
    white-space: pre-wrap;
    font-size: 14px;
    line-height: 1.6;
}

.placeholder { color: #ccc; font-style: italic; }
.error-msg { padding: 8px 16px; color: #f56c6c; background: #fef0f0; font-size: 12px; }
</style>
