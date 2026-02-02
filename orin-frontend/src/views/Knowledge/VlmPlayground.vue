<template>
  <div class="vlm-playground-container">
    <PageHeader 
      title="VLM 视觉实验室 Visual Lab" 
      description="专注于多模态模型（VLM）的视觉理解能力测试。输入图片内容，观察模型的理解精度。"
      icon="View"
    />

    <el-container class="lab-layout">
      <!-- Main Content: Material Stream -->
      <el-main class="material-stream">
        <!-- Input & Config Header -->
        <div class="stream-header-card">
           <div class="input-row">
               <el-select v-model="selectedVlmModel" placeholder="选择 VLM 模型" style="width: 240px" size="large">
                  <el-option v-for="m in vlmModels" :key="m.modelId" :label="m.name" :value="m.modelId" />
               </el-select>

               <el-input 
                  v-model="imageUrl" 
                  placeholder="请输入图片 URL" 
                  class="search-input"
                  size="large"
                  clearable
                  @keyup.enter="handleTest"
                >
                   <template #prefix><el-icon><Picture /></el-icon></template>
                </el-input>
                
                <el-upload
                  class="local-upload"
                  action="#"
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="handleFileUpload"
                  accept="image/*"
                >
                  <el-button size="large" :icon="UploadFilledIcon">本地</el-button>
                </el-upload>

                <el-button type="primary" size="large" :loading="loading" @click="handleTest">开始解析</el-button>
           </div>
        </div>

        <!-- Cards Grid -->
        <div class="cards-grid">
            <div 
                v-for="task in historyList" 
                :key="task.id" 
                class="task-card"
                :class="{ 'active': currentViewTask?.id === task.id }"
                @click="viewTask(task)"
            >
                <div class="card-image-box">
                    <el-image 
                        :src="task.targetUrl" 
                        fit="cover" 
                        class="card-img" 
                        lazy
                    >
                        <template #placeholder>
                            <div class="image-slot-loading">
                                <el-icon class="is-loading"><Loading /></el-icon>
                            </div>
                        </template>
                        <template #error>
                            <div class="image-slot-error">
                                <el-icon><Picture /></el-icon>
                            </div>
                        </template>
                    </el-image>

                    <!-- Amber Processing Overlay -->
                    <div v-if="task.status === 'RUNNING'" class="processing-overlay">
                        <div class="amber-flow"></div>
                        <span class="processing-text">AI 正在思考...</span>
                    </div>

                    <div class="card-badges">
                        <el-tag size="small" :type="getStatusType(task.status)" effect="dark" class="status-badge">
                            {{ task.status }}
                        </el-tag>
                    </div>
                </div>
                
                <div class="card-footer">
                    <div class="model-name" :title="task.modelName">{{ task.modelName }}</div>
                    <div class="time-info">{{ formatTime(task.createdAt) }}</div>
                </div>
            </div>
        </div>
      </el-main>

      <!-- Right Aside: Detail Dashboard -->
      <el-aside width="400px" class="detail-dashboard">
        <div v-if="currentViewTask" class="dashboard-content">
            <!-- Top: Image Preview -->
            <div class="detail-section image-preview-section">
                <div class="large-preview">
                    <el-image 
                        :src="currentViewTask.targetUrl" 
                        fit="contain" 
                        :preview-src-list="[currentViewTask.targetUrl]"
                        class="detail-img"
                    />
                </div>
                <div class="action-bar">
                    <el-button type="primary" plain size="small" :icon="Refresh" @click="reanalyzeTask">重新解析</el-button>
                    <el-button type="info" plain size="small" :icon="CopyDocument" @click="copyDescription">复制结果</el-button>
                </div>
            </div>

            <!-- Middle: AI Summary -->
            <div class="detail-section summary-section">
                <div class="section-title">
                    <el-icon><Cpu /></el-icon> AI 视觉分析
                    <span v-if="isValidExecTime(currentViewTask.executionTime)" class="exec-time">{{ (currentViewTask.executionTime / 1000).toFixed(1) }}s</span>
                </div>
                <div class="summary-editor-box">
                    <el-input
                        v-model="currentViewTask.result"
                        type="textarea"
                        :autosize="{ minRows: 6, maxRows: 15 }"
                        placeholder="等待分析结果..."
                        class="borderless-textarea"
                        :readonly="false" 
                    />
                </div>
            </div>

            <!-- Bottom: Related Meta -->
            <div class="detail-section meta-section">
                <div class="section-title"><el-icon><Connection /></el-icon> 关联上下文</div>
                <div class="tags-group">
                    <div class="tag-row">
                        <span class="label">Agents:</span>
                        <el-tag size="small" type="info">暂无关联</el-tag>
                    </div>
                    <div class="tag-row">
                        <span class="label">知识库:</span>
                        <div class="kb-tags">
                             <el-tag size="small" effect="plain">多模态素材库</el-tag>
                             <el-tag size="small" effect="plain" type="success">默认集合</el-tag>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div v-else class="empty-dashboard">
            <el-empty description="请选择左侧任务查看详情" />
        </div>
      </el-aside>
    </el-container>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Picture, View, InfoFilled, Cpu, UploadFilled as UploadFilledIcon, CopyDocument, Warning, Check, Refresh, Pointer, Loading, Connection } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';

const imageUrl = ref('');
const selectedVlmModel = ref('');
const vlmModels = ref([]);
const loading = ref(false);

const historyList = ref([]);
const currentViewTask = ref(null);
let pollTimer = null;

function handleFileUpload(file) {
    const reader = new FileReader();
    reader.onload = (e) => {
        imageUrl.value = e.target.result;
        ElMessage.success(`本地图片 [${file.name}] 已加载`);
    };
    reader.readAsDataURL(file.raw);
}

const copyDescription = () => {
    if (!currentViewTask.value?.result) return;
    navigator.clipboard.writeText(currentViewTask.value.result);
    ElMessage.success('描述内容已复制到剪贴板');
};

const reanalyzeTask = () => {
    if (!currentViewTask.value) return;
    imageUrl.value = currentViewTask.value.targetUrl;
    selectedVlmModel.value = currentViewTask.value.modelName; // Restore model choice
    handleTest();
};

onMounted(async () => {
    await fetchModels();
    await fetchHistory();
    // Default select first
    if (historyList.value.length > 0) {
        currentViewTask.value = historyList.value[0];
    }
    // Start polling for running tasks
    pollTimer = setInterval(checkRunningTasks, 3000);
});

async function fetchModels() {
    try {
        const modelsRes = await request.get('/models');
        const modelsData = modelsRes || [];
        vlmModels.value = modelsData.filter(m => {
            const type = m.type?.toUpperCase();
            const isLLM = type === 'LLM' || type === 'CHAT';
            const isVLM = type === 'VLM' || type === 'VISUAL' || type === 'MULTIMODAL';
            const hasVisionKeywords = (m.modelId + m.name + m.description).toLowerCase().match(/vl|vision|multimodal|gpt-4o|claude-3/);
            return (isVLM || (isLLM && hasVisionKeywords)) && m.status === 'ENABLED';
        });
        
        if (vlmModels.value.length > 0 && !selectedVlmModel.value) {
            selectedVlmModel.value = vlmModels.value[0].modelId;
        }
    } catch (e) {
        ElMessage.error('模型加载失败');
    }
}

async function fetchHistory() {
    try {
        const res = await request.get('/multimodal/tasks/list');
        historyList.value = res || [];
        // Update current view if it exists in list
        if (currentViewTask.value) {
            const updated = historyList.value.find(t => t.id === currentViewTask.value.id);
            if (updated) {
                // Preserve local edits if typing? Ideally logic would be more complex, 
                // but for now let's auto-update to see latest result
                if (updated.status !== currentViewTask.value.status || (!currentViewTask.value.result && updated.result)) {
                     currentViewTask.value = updated;
                }
            }
        }
    } catch (e) {
        console.error('Fetch history failed', e);
    }
}

function checkRunningTasks() {
    const hasRunning = historyList.value.some(t => t.status === 'RUNNING');
    if (hasRunning) {
        fetchHistory();
    }
}

function viewTask(task) {
    currentViewTask.value = task;
}

function formatTime(timeStr) {
    if (!timeStr) return '';
    const date = new Date(timeStr);
    return date.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
}

function getStatusType(status) {
    switch(status) {
        case 'SUCCESS': return 'success';
        case 'FAILED': return 'danger';
        case 'RUNNING': return 'warning';
        default: return 'info';
    }
}

function isValidExecTime(ms) {
    // Filter out huge numbers which are likely timestamps from old data (e.g. > 1 hour)
    return ms && ms < 3600 * 1000;
}

const handleTest = async () => {
    if (!imageUrl.value.trim()) {
        ElMessage.warning('请输入图片 URL');
        return;
    }
    
    loading.value = true;
    try {
        const task = await request.post('/multimodal/tasks/submit', {
            imageUrl: imageUrl.value,
            modelName: selectedVlmModel.value
        });
        
        ElMessage.success('任务已开始后台分析');
        await fetchHistory();
        // Select the newly created task (assuming it's first)
        currentViewTask.value = historyList.value[0];
        // Clear input URL to indicate readiness for next
        imageUrl.value = ''; 
    } catch (e) {
        ElMessage.error('分析提交失败: ' + (e.response?.data?.message || e.message));
    } finally {
        loading.value = false;
    }
};
</script>

<style scoped>
.vlm-playground-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
    background: #f8fafc;
}

.lab-layout {
    flex: 1;
    overflow: hidden;
}

/* Material Stream (Left) */
.material-stream {
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 24px;
    background: #f1f5f9;
}

.stream-header-card {
    background: white;
    padding: 20px;
    border-radius: 16px;
    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.input-row {
    display: flex;
    gap: 12px;
}

.search-input {
    flex: 1;
}

.cards-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 20px;
    padding-bottom: 40px;
}

/* Card Styling */
.task-card {
    background: white;
    border-radius: 12px;
    overflow: hidden;
    cursor: pointer;
    box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1), box-shadow 0.3s;
    border: 2px solid transparent;
}

.task-card:hover {
    transform: translateY(-5px);
    box-shadow: 0 10px 20px rgba(0,0,0,0.1);
}

.task-card.active {
    border-color: #409eff;
    box-shadow: 0 0 0 4px rgba(64, 158, 255, 0.1);
}

.card-image-box {
    position: relative;
    height: 160px;
    background: #e2e8f0;
    overflow: hidden;
}

.card-img {
    width: 100%;
    height: 100%;
    display: block;
}

.image-slot-loading,
.image-slot-error {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    height: 100%;
    color: #94a3b8;
    background: #f8fafc;
}

/* Amber Flow Animation */
.processing-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 10;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0,0,0,0.3);
    backdrop-filter: blur(2px);
    overflow: hidden;
}

.amber-flow {
    position: absolute;
    top: 0;
    left: -100%;
    width: 200%;
    height: 100%;
    background: linear-gradient(90deg, transparent, rgba(255, 191, 0, 0.4), transparent);
    animation: flow 1.5s infinite linear;
}

@keyframes flow {
    0% { transform: translateX(0); }
    100% { transform: translateX(50%); }
}

.processing-text {
    position: relative;
    z-index: 11;
    color: white;
    font-weight: 600;
    font-size: 13px;
    text-shadow: 0 1px 2px rgba(0,0,0,0.5);
    display: flex;
    align-items: center;
    gap: 6px;
}

.processing-text::before {
    content: '';
    width: 8px;
    height: 8px;
    background: #fbbf24;
    border-radius: 50%;
    box-shadow: 0 0 8px #fbbf24;
    animation: blink 1s infinite;
}

@keyframes blink { 50% { opacity: 0.5; } }

.card-badges {
    position: absolute;
    top: 8px;
    right: 8px;
}

.card-footer {
    padding: 12px;
}

.model-name {
    font-weight: 600;
    font-size: 14px;
    color: #334155;
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.time-info {
    font-size: 12px;
    color: #94a3b8;
}

/* Detail Dashboard (Right) */
.detail-dashboard {
    background: white;
    border-left: 1px solid #e2e8f0;
    display: flex;
    flex-direction: column;
}

.dashboard-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
}

.detail-section {
    padding: 24px;
    border-bottom: 1px solid #f1f5f9;
}

/* Image Preview */
.image-preview-section {
    background: #f8fafc;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
}

.large-preview {
    width: 100%;
    height: 240px;
    border-radius: 8px;
    overflow: hidden;
    background: white;
    border: 1px solid #e2e8f0;
    display: flex;
    align-items: center;
    justify-content: center;
}

.detail-img {
    max-width: 100%;
    max-height: 100%;
}

.action-bar {
    display: flex;
    gap: 12px;
    width: 100%;
}

.action-bar .el-button {
    flex: 1;
}

/* Summary Section */
.section-title {
    font-size: 16px;
    font-weight: 600;
    color: #1e293b;
    margin-bottom: 16px;
    display: flex;
    align-items: center;
    gap: 8px;
}

.exec-time {
    margin-left: auto;
    font-size: 12px;
    color: #10b981;
    background: #ecfdf5;
    padding: 2px 8px;
    border-radius: 12px;
    font-weight: normal;
}

/* Borderless textarea magic */
.summary-editor-box :deep(.el-textarea__inner) {
    box-shadow: none !important;
    background: transparent;
    padding: 0;
    color: #334155;
    line-height: 1.6;
    font-size: 14px;
}

.summary-editor-box :deep(.el-textarea__inner:focus) {
    box-shadow: 0 0 0 1px #e2e8f0 !important;
    background: white;
    padding: 12px;
}

.meta-section {
    border-bottom: none;
}

.tags-group {
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.tag-row {
    display: flex;
    align-items: center;
    gap: 12px;
}

.tag-row .label {
    width: 60px;
    font-size: 13px;
    color: #64748b;
}

.kb-tags {
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
}

.empty-dashboard {
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
}
</style>
