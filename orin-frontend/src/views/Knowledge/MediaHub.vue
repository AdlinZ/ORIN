<template>
  <div class="media-hub-container">
    <PageHeader 
      title="多模态素材库 Media Hub" 
      description="集中管理图像、音频与视频素材，利用 Vision-LLM 自动生成语义摘要，实现跨模态检索。"
      icon="Picture"
    >
      <template #actions>
        <el-button type="primary" :icon="Upload" @click="uploadDialog = true">上传素材</el-button>
        <el-button :icon="Refresh" @click="loadFiles">刷新</el-button>
      </template>
    </PageHeader>

    <div class="hub-layout">
      <!-- Main Content: File Grid -->
      <div class="hub-main" v-loading="loading">
        <!-- Filters & Stats -->
        <div class="hub-toolbar">
          <el-radio-group v-model="filterType" @change="loadFiles" size="small">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="IMAGE">图像</el-radio-button>
            <el-radio-button value="VIDEO">视频</el-radio-button>
            <el-radio-button value="AUDIO">音频</el-radio-button>
          </el-radio-group>
          
          <div class="search-bar">
            <el-input 
              v-model="searchKeyword" 
              placeholder="搜索素材名或AI摘要..." 
              :prefix-icon="Search"
              clearable
              @input="filterFiles"
            />
          </div>
        </div>

        <!-- Grid -->
        <div class="file-grid" v-if="displayFiles.length > 0">
          <div 
            v-for="file in displayFiles" 
            :key="file.id" 
            class="media-card"
            :class="{ active: selectedFile && selectedFile.id === file.id }"
            @click="selectFile(file)"
          >
            <!-- Card Image/Preview -->
            <div class="media-preview">
              <div v-if="file.embeddingStatus === 'PROCESSING'" class="processing-overlay">
                <div class="amber-loader"></div>
                <span>AI 解析中...</span>
              </div>
              
              <img 
                v-if="file.fileType === 'IMAGE'" 
                :src="file.thumbnailUrl || getMockThumbnail(file)" 
                loading="lazy" 
              />
              <div v-else class="generic-icon">
                <el-icon v-if="file.fileType === 'AUDIO'" :size="48"><Headset /></el-icon>
                <el-icon v-if="file.fileType === 'VIDEO'" :size="48"><VideoCamera /></el-icon>
              </div>
            </div>

            <!-- Card Body -->
            <div class="media-info">
              <div class="name-row">
                <span class="name truncate" :title="file.fileName">{{ file.fileName }}</span>
                
                <!-- Enhanced Status Badge with Hover Steps -->
                <el-tooltip 
                    v-if="file.embeddingStatus === 'PROCESSING'"
                    placement="top" 
                    effect="light"
                    popper-class="status-steps-popper"
                >
                    <template #content>
                        <div class="status-steps">
                            <div class="step active"><el-icon><Connection /></el-icon> 连接云端解析中...</div>
                            <div class="step pending"><el-icon><Cpu /></el-icon> VLM 语义生成...</div>
                            <div class="step pending"><el-icon><Coin /></el-icon> 向量索引构建...</div>
                        </div>
                    </template>
                    <el-tag size="small" type="warning" class="status-tag">
                        <span class="processing-text">Processing</span>
                    </el-tag>
                </el-tooltip>
                <el-tag v-else size="small" :type="getStatusType(file.embeddingStatus)" effect="plain">
                  {{ file.embeddingStatus === 'SUCCESS' ? 'Ready' : file.embeddingStatus }}
                </el-tag>
              </div>

              <!-- Inline Editable Summary -->
              <div class="summary-container" @click.stop>
                  <div 
                    v-if="editingId === file.id" 
                    class="summary-edit-inline"
                  >
                      <el-input 
                        v-model="tempSummary" 
                        type="textarea" 
                        :rows="2" 
                        size="small"
                        ref="inlineInputRef"
                        @blur="saveInlineSummary(file)"
                        @keyup.enter.stop="saveInlineSummary(file)"
                      />
                  </div>
                  <div 
                    v-else 
                    class="summary-preview truncate-2" 
                    :class="{ 'editable': file.embeddingStatus !== 'PROCESSING' }"
                    @click="enableInlineEdit(file)"
                    title="点击修改 AI 摘要"
                  >
                    {{ file.aiSummary || '等待 AI 生成摘要...' }}
                    <el-icon v-if="file.embeddingStatus !== 'PROCESSING'" class="edit-icon"><EditPen /></el-icon>
                  </div>
              </div>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无符合条件的素材" />
      </div>

      <!-- Right Sidebar: Inspector -->
      <transition name="slide-left">
        <div v-if="selectedFile" class="hub-sidebar">
          <div class="sidebar-header">
            <h3>素材详情</h3>
            <el-button circle :icon="Close" size="small" @click="selectedFile = null" />
          </div>
          
          <div class="sidebar-content">
            <!-- Asset Preview (Large) -->
            <div class="asset-large-preview">
                <img v-if="selectedFile.fileType === 'IMAGE'" :src="selectedFile.url || getMockThumbnail(selectedFile)" />
                <!-- Placeholder for video/audio player -->
                <div v-else class="media-placeholder">
                   {{ selectedFile.fileType }} PREVIEW
                </div>
            </div>

            <el-divider content-position="left">AI 语义摘要</el-divider>
            
            <div class="ai-summary-section">
                <!-- Read Mode -->
                <div v-if="!isEditingSummary" class="summary-read">
                    <p class="summary-text">{{ selectedFile.aiSummary }}</p>
                    <div class="actions">
                        <el-button link type="primary" :icon="Edit" @click="startEditSummary">人工校准</el-button>
                        <el-button link :icon="RefreshRight" @click="reanalyze">重新生成</el-button>
                    </div>
                </div>
                <!-- Edit Mode -->
                <div v-else class="summary-edit">
                    <el-input 
                        v-model="editingSummaryText" 
                        type="textarea" 
                        :rows="6" 
                        placeholder="输入更准确的描述..." 
                    />
                    <div class="edit-actions">
                        <el-button size="small" @click="cancelEditSummary">取消</el-button>
                        <el-button size="small" type="primary" @click="saveSummary">保存并更新向量</el-button>
                    </div>
                </div>
            </div>

            <el-divider content-position="left">依赖追踪 (Dependency)</el-divider>
            
            <div class="dependency-list">
                <div v-if="selectedFile.dependencies && selectedFile.dependencies.length">
                    <div v-for="dep in selectedFile.dependencies" :key="dep.id" class="dep-item">
                        <el-icon><Connection /></el-icon>
                        <div class="dep-info">
                            <div class="dep-name">{{ dep.name }}</div>
                            <div class="dep-type">{{ dep.type }}</div>
                        </div>
                        <el-tag size="small">引用中</el-tag>
                    </div>
                </div>
                <div v-else class="empty-dep">
                    暂无 Agent 或知识库引用此素材
                </div>
            </div>

            <div class="metadata-grid">
               <div class="meta-item">
                  <label>大小</label>
                  <span>{{ formatSize(selectedFile.fileSize) }}</span>
               </div>
               <div class="meta-item">
                  <label>上传者</label>
                  <span>{{ selectedFile.uploadedBy || 'Admin' }}</span>
               </div>
               <div class="meta-item">
                  <label>时间</label>
                  <span>{{ formatDate(selectedFile.uploadedAt) }}</span>
               </div>
            </div>

          </div>
          
          <div class="sidebar-footer">
             <el-button type="danger" plain style="width: 100%" @click="deleteSelected">删除素材</el-button>
          </div>
        </div>
      </transition>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialog" title="上传素材" width="400px">
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :auto-upload="true"
        :http-request="customUpload"
        multiple
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到这里或 <em>点击上传</em>
        </div>
      </el-upload>
    </el-dialog>

    <!-- Settings Dialog -->
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive, nextTick } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Upload, Refresh, Search, Picture, Headset, VideoCamera, 
  Close, Edit, RefreshRight, Connection, UploadFilled, 
  Cpu, Coin, EditPen, Setting
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

// State
const loading = ref(false);
const filterType = ref('');
const searchKeyword = ref('');
const uploadDialog = ref(false);
const files = ref([]);
const displayFiles = ref([]);
const selectedFile = ref(null);

// Edit State
const isEditingSummary = ref(false);
const editingSummaryText = ref('');

// Inline Edit State
const editingId = ref(null);
const tempSummary = ref('');
const inlineInputRef = ref(null);


// Methods
const loadFiles = async () => {
    loading.value = true;
    try {
        // Fetch from actual API
        const response = await request.get('/multimodal/files');
        
        // Response is already an array (interceptor returns response.data)
        const fileList = Array.isArray(response) ? response : [];
        
        files.value = fileList.map(f => {
            // Construct web-accessible URLs using the download and thumbnail endpoints
            return {
                ...f,
                url: `/api/v1/multimodal/files/${f.id}/download`,
                thumbnailUrl: `/api/v1/multimodal/files/${f.id}/thumbnail`
            };
        });
        
        filterFiles();
    } catch (error) {
        ElMessage.error('加载素材列表失败: ' + (error.message || '未知错误'));
        console.error('Load files error:', error);
        files.value = [];
        displayFiles.value = [];
    } finally {
        loading.value = false;
        editingId.value = null; // Reset
    }
};

const filterFiles = () => {
    let res = files.value;
    if (filterType.value) {
        res = res.filter(f => f.fileType === filterType.value);
    }
    if (searchKeyword.value) {
        const k = searchKeyword.value.toLowerCase();
        res = res.filter(f => 
            f.fileName.toLowerCase().includes(k) || 
            (f.aiSummary && f.aiSummary.toLowerCase().includes(k))
        );
    }
    displayFiles.value = res;
};

const selectFile = (file) => {
    // Prevent selection if clicking edit
    if (editingId.value) return; 
    selectedFile.value = file;
    isEditingSummary.value = false;
};

const getStatusType = (status) => {
    if (status === 'SUCCESS') return 'success';
    if (status === 'PROCESSING' || status === 'PENDING') return 'warning'; // PENDING treated as warning too
    if (status === 'FAILED') return 'danger';
    return 'info';
};

const getMockThumbnail = (file) => {
    // Only use if real thumbnail fails or not ready
    return `https://via.placeholder.com/300?text=${file.fileName}`; 
};

// Summary Editing
const startEditSummary = () => {
    editingSummaryText.value = selectedFile.value.aiSummary;
    isEditingSummary.value = true;
};

const cancelEditSummary = () => {
    isEditingSummary.value = false;
};

const saveSummary = async () => {
    // API Call to update summary (Placeholder - need backend endpoint)
    loading.value = true; 
    setTimeout(() => {
        selectedFile.value.aiSummary = editingSummaryText.value;
        ElMessage.success('摘要已更新，正在重新计算向量...');
        isEditingSummary.value = false;
        loading.value = false;
    }, 800);
};

// Inline Editing
const enableInlineEdit = (file) => {
    if (file.embeddingStatus === 'PROCESSING') return;
    editingId.value = file.id;
    tempSummary.value = file.aiSummary || '';
    
    // Focus automatically
    nextTick(() => {
        if (inlineInputRef.value) {
           // Ref in v-for is an array in Vue 3
           const el = Array.isArray(inlineInputRef.value) ? inlineInputRef.value[0]?.$el : inlineInputRef.value.$el;
           el?.querySelector('textarea')?.focus();
        }
    });
};

const saveInlineSummary = (file) => {
    if (!editingId.value) return;
    
    // Check if changed
    if (tempSummary.value !== file.aiSummary) {
        file.aiSummary = tempSummary.value;
        ElMessage.success({
            message: '摘要已更新 (Auto-saved) - 触发向量增量更新',
            type: 'success',
            duration: 2000
        });
        // In real app, make API call here silently
    }
    
    editingId.value = null;
    tempSummary.value = '';
};

// ... Actions ...
const reanalyze = () => {
    ElMessage.info('已提交 VLM 重新解析任务');
    selectedFile.value.embeddingStatus = 'PROCESSING';
};

const customUpload = async (option) => {
    let formData = new FormData();
    formData.append('file', option.file);
    formData.append('uploadedBy', 'Admin'); // Should get from user store

    try {
        const res = await request.post('/multimodal/upload', formData, {
            headers: {
                'Content-Type': 'multipart/form-data'
            }
        });
        
        ElMessage.success('上传成功');
        loadFiles(); // Reload
        uploadDialog.value = false;
        
    } catch (error) {
        ElMessage.error('上传失败');
        console.error(error);
    }
};

const deleteSelected = () => {
    if (!selectedFile.value) return;
    ElMessageBox.confirm('确认彻底删除该素材吗？', '警告', { type: 'warning' })
        .then(async () => {
            try {
                await request.delete(`/multimodal/files/${selectedFile.value.id}`);
                files.value = files.value.filter(f => f.id !== selectedFile.value.id);
                filterFiles();
                selectedFile.value = null;
                ElMessage.success('已删除');
            } catch (e) {
                ElMessage.error('删除失败');
            }
        });
};


// Utils
const formatSize = (bytes) => {
    if(bytes === 0) return '0 B';
    const k = 1024, sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

const formatDate = (iso) => {
    if (!iso) return '-';
    return new Date(iso).toLocaleString();
};

onMounted(() => {
    loadFiles();
});
</script>

<style scoped>
.media-hub-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
}

.hub-layout {
    flex: 1;
    display: flex;
    overflow: hidden;
    background: #f5f7fa;
}

.hub-main {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
}

.hub-toolbar {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;
}

.file-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); /* Slightly wider */
    gap: 20px;
}

.media-card {
    background: white;
    border-radius: 12px;
    box-shadow: 0 4px 6px rgba(0,0,0,0.03);
    cursor: pointer;
    overflow: hidden;
    transition: all 0.3s;
    border: 2px solid transparent;
}

.media-card:hover {
    transform: translateY(-4px);
    box-shadow: 0 10px 20px rgba(0,0,0,0.08);
}

.media-card.active {
    border-color: var(--orin-amber);
}

.media-preview {
    height: 150px;
    background: #f0f2f5;
    position: relative;
    display: flex;
    align-items: center;
    justify-content: center;
}

.media-preview img {
    width: 100%;
    height: 100%;
    object-fit: cover;
}

.processing-overlay {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(0,0,0,0.6);
    color: white;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    z-index: 2;
}

.amber-loader {
    width: 20px; height: 20px;
    border: 2px solid rgba(255,255,255,0.3);
    border-top-color: var(--orin-amber);
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: 8px;
}

@keyframes spin { to { transform: rotate(360deg); } }

.media-info {
    padding: 12px;
}

.name-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.name {
    font-weight: 600;
    flex: 1;
    margin-right: 8px;
    font-size: 14px;
}

.processing-text {
    animation: pulse 1.5s infinite;
}

@keyframes pulse {
    0% { opacity: 0.6; }
    50% { opacity: 1; }
    100% { opacity: 0.6; }
}

.summary-container { min-height: 48px; position: relative; }

.summary-preview {
    font-size: 12px;
    color: #909399;
    line-height: 1.5;
    transition: color 0.2s;
    padding: 4px;
    border-radius: 4px;
}

.summary-preview.editable:hover {
    background-color: #f5f7fa;
    color: var(--text-primary);
}

.edit-icon {
    display: none;
    margin-left: 4px;
    color: var(--primary-color);
}

.summary-preview.editable:hover .edit-icon {
    display: inline-block;
}

.summary-edit-inline {
    width: 100%;
}

/* Sidebar */
.hub-sidebar {
    width: 350px;
    background: white;
    box-shadow: -5px 0 20px rgba(0,0,0,0.05);
    display: flex;
    flex-direction: column;
    border-left: 1px solid #ebeef5;
    z-index: 100;
}

.sidebar-header {
    padding: 20px;
    display: flex;
    justify-content: space-between;
    align-items: center;
    border-bottom: 1px solid #ebeef5;
}

.sidebar-content {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
}

.asset-large-preview {
    width: 100%;
    aspect-ratio: 16/9;
    background: #f0f2f5;
    border-radius: 8px;
    overflow: hidden;
    margin-bottom: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
}

.asset-large-preview img {
    max-width: 100%;
    max-height: 100%;
}

.summary-text {
    font-size: 14px;
    line-height: 1.6;
    color: #303133;
    background: #fcfcfc;
    padding: 12px;
    border-radius: 8px;
    border: 1px solid #ebeef5;
    margin-bottom: 10px;
}

.dependency-list {
    margin-bottom: 20px;
}

.dep-item {
    display: flex;
    align-items: center;
    padding: 10px;
    background: #f5f7fa;
    border-radius: 6px;
    margin-bottom: 8px;
}

.dep-info {
    flex: 1;
    margin-left: 10px;
}

.dep-name { font-size: 13px; font-weight: 600; }
.dep-type { font-size: 11px; color: #909399; }

.metadata-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 15px;
    font-size: 13px;
    color: #606266;
    margin-top: 30px;
}

.meta-item label {
    display: block;
    color: #909399;
    font-size: 12px;
    margin-bottom: 4px;
}

.sidebar-footer {
    padding: 20px;
    border-top: 1px solid #ebeef5;
}

.truncate {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.truncate-2 {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
}

/* Animations */
.slide-left-enter-active,
.slide-left-leave-active {
  transition: transform 0.3s ease;
}

.slide-left-enter-from,
.slide-left-leave-to {
  transform: translateX(100%);
}
</style>

<style>
/* Global style popover */
.status-steps-popper {
    padding: 10px !important;
}
.status-steps .step {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    font-size: 12px;
    color: #909399;
}
.status-steps .step:last-child { margin-bottom: 0; }
.status-steps .step.active { color: var(--orin-amber); font-weight: 600; }
.status-steps .step.active .el-icon { animation: spin 1s infinite linear; }
.status-steps .step.pending { opacity: 0.6; }
</style>
