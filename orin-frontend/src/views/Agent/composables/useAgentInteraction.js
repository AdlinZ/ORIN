import { ref, onUnmounted } from 'vue';
import { chatAgent, getJobStatus } from '@/api/agent';
import { ElMessage } from 'element-plus';

const formatErrorMessage = (msg) => {
    if (!msg) return '请求失败，请稍后重试';

    // Handle SiliconFlow specific error for missing voice/audio
    if (msg.includes('20052') || msg.includes('Voice or reference audio should be set')) {
        return '提示：请先在左侧配置中心选择一个“音色”再进行生成。';
    }

    // Clean up backend "SiliconFlow API Error: " prefix if present for other errors
    return msg.replace('SiliconFlow API Error: ', '');
};

export function useAgentInteraction(agentId) {
    const isProcessing = ref(false);
    const result = ref(null);
    const dataType = ref(null);
    const error = ref(null);
    let pollTimer = null;

    const stopPolling = () => {
        if (pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    };

    const interact = async (content, fileId, options = {}) => {
        isProcessing.value = true;
        result.value = null;
        error.value = null;
        stopPolling();

        try {
            const res = await chatAgent(agentId, typeof content === 'object' ? JSON.stringify(content) : content, fileId);

            if (res && res.status) {
                if (res.status === 'PROCESSING' && res.jobId) {
                    startPolling(res.jobId);
                    return;
                } else if (res.status === 'SUCCESS') {
                    result.value = res.data;
                    dataType.value = res.dataType;
                    isProcessing.value = false;
                } else {
                    throw new Error(res.errorMessage || 'Unknown error');
                }
            } else {
                result.value = res;
                dataType.value = null;
                isProcessing.value = false;
            }
        } catch (e) {
            const friendlyMsg = formatErrorMessage(e.message);
            error.value = friendlyMsg;
            isProcessing.value = false;
            ElMessage.error(friendlyMsg);
        }
    };

    const startPolling = (jobId) => {
        pollTimer = setInterval(async () => {
            try {
                const res = await getJobStatus(agentId, jobId);
                // Assuming status check returns same structure: { status, data }
                if (res.status === 'SUCCESS') {
                    result.value = res.data;
                    dataType.value = res.dataType;
                    isProcessing.value = false;
                    stopPolling();
                } else if (res.status === 'FAILED') {
                    error.value = res.errorMessage;
                    isProcessing.value = false;
                    stopPolling();
                }
                // If 'PROCESSING', continue
            } catch (e) {
                stopPolling();
                isProcessing.value = false;
                error.value = "Polling failed: " + e.message;
            }
        }, 5000);
    };

    onUnmounted(() => {
        stopPolling();
    });

    return { isProcessing, result, dataType, error, interact };
}
