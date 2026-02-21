<template>
  <div class="chat-app-container">
    <div class="chat-header">
      <div class="header-content">
        <div class="app-icon">🤖</div>
        <div class="app-info">
          <h1>{{ workflowName || 'Workflow Chat' }}</h1>
          <span class="status-badge">Running</span>
        </div>
      </div>
    </div>

    <div class="chat-messages" ref="messagesContainer">
      <div v-for="(msg, index) in messages" :key="index" class="message-row" :class="msg.role">
        <div class="avatar">
          {{ msg.role === 'user' ? '👤' : '🤖' }}
        </div>
        <div class="message-bubble">
          <div class="message-content">{{ msg.content }}</div>
        </div>
      </div>
      <div v-if="loading" class="message-row assistant">
        <div class="avatar">🤖</div>
        <div class="message-bubble loading">
          <span>●</span><span>●</span><span>●</span>
        </div>
      </div>
    </div>

    <div class="chat-input-area">
      <div class="input-wrapper">
        <el-input 
          v-model="userInput" 
          placeholder="Send a message..." 
          @keyup.enter="sendMessage"
          :disabled="loading"
        >
          <template #suffix>
            <el-button circle type="primary" :icon="Position" @click="sendMessage" :loading="loading" />
          </template>
        </el-input>
      </div>
      <div class="footer-text">Powered by ORIN Workflow Engine</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { Position } from '@element-plus/icons-vue';
import { executeWorkflow, getWorkflow, getWorkflowInstance } from '@/api/workflow';
import { ElMessage } from 'element-plus';

const route = useRoute();
const workflowId = route.params.id;
const workflowName = ref('');
const userInput = ref('');
const loading = ref(false);
const messages = ref([
    { role: 'assistant', content: 'Hello! How can I help you today?' }
]);
const messagesContainer = ref(null);

onMounted(async () => {
    if (workflowId) {
        try {
            const res = await getWorkflow(workflowId);
            const wf = res.data || res;
            workflowName.value = wf.workflowName;
        } catch (e) {
            console.error(e);
        }
    }
});

const scrollToBottom = async () => {
    await nextTick();
    if (messagesContainer.value) {
        messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
};

const sendMessage = async () => {
    if (!userInput.value.trim() || loading.value) return;

    const query = userInput.value;
    messages.value.push({ role: 'user', content: query });
    userInput.value = '';
    scrollToBottom();

    loading.value = true;

    try {
        const inputs = { query };
        const res = await executeWorkflow(workflowId, inputs);
        const data = res.data || res;
        const instanceId = data.instanceId;

        if (!instanceId) {
            throw new Error('No instance ID returned');
        }

        // Check handle real streaming or output parsing (Polling for now)
        let status = 'RUNNING';
        let instance = null;
        let retryCount = 0;
        const maxRetries = 60; // 60 seconds timeout

        while (status === 'RUNNING' && retryCount < maxRetries) {
            await new Promise(resolve => setTimeout(resolve, 1000));
            instance = await getWorkflowInstance(instanceId);
            const instData = instance.data || instance;
            status = instData.status;
            retryCount++;
            
            if (status === 'SUCCESS') {
                const outputs = instData.outputData || {};
                // Try to find a meaningful output field
                let reply = outputs.output || outputs.result || outputs.response || JSON.stringify(outputs);
                messages.value.push({ role: 'assistant', content: reply });
                break;
            } else if (status === 'FAILED') {
                throw new Error('Workflow execution failed');
            }
        }

        if (retryCount >= maxRetries) {
            throw new Error('Workflow execution timed out');
        }

    } catch (error) {
        console.error(error);
        ElMessage.error(error.message || 'Failed to send message');
        messages.value.push({ role: 'assistant', content: 'Error: ' + (error.message || 'Could not execute workflow.') });
    } finally {
        loading.value = false;
        scrollToBottom();
    }
};
</script>

<style scoped>
.chat-app-container {
    display: flex;
    flex-direction: column;
    height: 100vh;
    background-color: #f9fafb;
    font-family: 'Inter', sans-serif;
}

.chat-header {
    background: white;
    padding: 16px 24px;
    border-bottom: 1px solid #eaecf0;
    box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
}

.header-content {
    display: flex;
    align-items: center;
    gap: 12px;
    max-width: 800px;
    margin: 0 auto;
}

.app-icon {
    width: 40px;
    height: 40px;
    background: #eff6ff;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 24px;
}

.app-info h1 {
    font-size: 16px;
    font-weight: 600;
    color: #101828;
    margin: 0;
}

.status-badge {
    font-size: 12px;
    color: #027a48;
    background: #ecfdf5;
    padding: 2px 8px;
    border-radius: 12px;
    margin-top: 4px;
    display: inline-block;
}

.chat-messages {
    flex: 1;
    overflow-y: auto;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 24px;
    max-width: 800px;
    margin: 0 auto;
    width: 100%;
}

.message-row {
    display: flex;
    gap: 12px;
    align-items: flex-start;
}

.message-row.user {
    flex-direction: row-reverse;
}

.avatar {
    width: 32px;
    height: 32px;
    background: #f2f4f7;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    flex-shrink: 0;
}

.message-row.user .avatar {
    background: #eff6ff;
}

.message-bubble {
    background: white;
    padding: 12px 16px;
    border-radius: 12px;
    box-shadow: 0 1px 2px rgba(16, 24, 40, 0.05);
    max-width: 80%;
    border: 1px solid #eaecf0;
    font-size: 14px;
    line-height: 1.5;
    color: #101828;
}

.message-row.user .message-bubble {
    background: #155eef;
    color: white;
    border: none;
}

.chat-input-area {
    background: white;
    padding: 24px;
    border-top: 1px solid #eaecf0;
}

.input-wrapper {
    max-width: 800px;
    margin: 0 auto;
}

.footer-text {
    text-align: center;
    font-size: 12px;
    color: #98a2b3;
    margin-top: 12px;
}

.loading span {
    display: inline-block;
    animation: bounce 1.4s infinite ease-in-out both;
    font-size: 12px;
    margin: 0 1px;
}

.loading span:nth-child(1) { animation-delay: -0.32s; }
.loading span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
    0%, 80%, 100% { transform: scale(0); }
    40% { transform: scale(1); }
}
</style>
