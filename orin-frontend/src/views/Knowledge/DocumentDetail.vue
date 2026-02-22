<template>
  <div class="document-detail-page">
    <!-- Header with Breadcrumb -->
    <div class="detail-header">
      <div class="breadcrumb">
        <span class="back-link" @click="$router.push(ROUTES.RESOURCES.KNOWLEDGE)">知识库</span>
        <span class="separator">/</span>
        <span class="back-link" @click="goBackToKB">{{ kbName }}</span>
        <span class="separator">/</span>
        <span class="current">{{ documentData.name }}</span>
      </div>
    </div>

    <!-- Document Info Section -->
    <div class="doc-info-section">
      <div class="icon-wrapper">
        <el-icon><Document /></el-icon>
      </div>
      <div class="info-text">
        <h2>{{ documentData.name }}</h2>
        <div class="meta-info">
          <span>上传时间: {{ documentData.uploadTime }}</span>
          <span class="separator">·</span>
          <span>{{ (documentData.wordCount / 1000).toFixed(1) }}k 字符</span>
          <span class="separator">·</span>
          <span>{{ documentData.hitCount }} 次召回</span>
        </div>
      </div>
      <div class="header-actions">
        <el-button 
          v-if="documentData.vectorStatus !== 'SUCCESS'"
          type="primary" 
          :loading="vectorizing"
          @click="handleVectorize"
        >立即向量化</el-button>
        <el-button :icon="Setting">设置</el-button>
        <el-button type="danger" plain :icon="Delete" @click="handleDelete">删除</el-button>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="doc-tabs">
      <!-- Segments Tab -->
      <el-tab-pane label="文档分段" name="segments">
        <div class="segments-container">
          <div class="tool-bar">
            <div class="left-tools">
              <el-input 
                v-model="searchKeyword"
                placeholder="搜索分段内容" 
                :prefix-icon="Search" 
                class="search-input"
                clearable 
              />
            </div>
            <div class="right-tools">
              <el-button :icon="Refresh" @click="loadSegments">刷新</el-button>
              <el-button type="primary" :icon="Plus">添加分段</el-button>
            </div>
          </div>

          <!-- Segments List -->
          <div class="segments-list" v-loading="loading">
            <template v-if="filteredSegments.length > 0">
              <div 
                v-for="(segment, index) in filteredSegments" 
                :key="segment.id"
                class="segment-card"
                @click="selectSegment(segment)"
                :class="{ 'selected': selectedSegment?.id === segment.id }"
              >
                <div class="segment-header">
                  <div class="segment-number">分段 {{ index + 1 }}</div>
                  <div class="segment-meta">
                    <span>{{ segment.wordCount }} 字符</span>
                    <span class="separator">·</span>
                    <span>{{ segment.hitCount }} 次召回</span>
                  </div>
                </div>
                <div class="segment-content">
                  {{ segment.content }}
                </div>
                <div class="segment-footer">
                  <el-tag size="small" :type="segment.status === 'indexed' ? 'success' : 'info'" effect="plain">
                    {{ segment.status === 'indexed' ? '已索引' : '待处理' }}
                  </el-tag>
                  <div class="segment-actions">
                    <el-button link type="primary" size="small" @click.stop="editSegment(segment)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button link type="danger" size="small" @click.stop="deleteSegment(segment)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="empty-segments">
              <el-empty description="该文档尚未进行分段向量化">
                <el-button type="primary" @click="handleVectorize" :loading="vectorizing">
                  点击开始向量化
                </el-button>
              </el-empty>
            </div>
          </div>

          <div class="pagination-wrapper">
            <el-pagination 
              layout="prev, pager, next" 
              :total="segments.length" 
              :page-size="10"
              background 
              small 
            />
          </div>
        </div>
      </el-tab-pane>

      <!-- Settings Tab -->
      <el-tab-pane label="文档设置" name="settings">
        <div class="settings-panel">
          <el-form label-position="top" style="max-width: 500px;">
            <el-form-item label="文档名称">
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="分段模式">
              <el-select v-model="form.mode" style="width: 100%;">
                <el-option label="自动" value="auto" />
                <el-option label="手动" value="manual" />
                <el-option label="智能" value="smart" />
              </el-select>
            </el-form-item>
            <el-form-item label="分段长度">
              <el-slider v-model="form.chunkSize" :min="100" :max="2000" :step="100" />
              <span class="slider-value">{{ form.chunkSize }} 字符</span>
            </el-form-item>
            <el-form-item label="分段重叠">
              <el-slider v-model="form.chunkOverlap" :min="0" :max="500" :step="10" />
              <span class="slider-value">{{ form.chunkOverlap }} 字符</span>
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-button type="primary" :loading="submitting" @click="onSubmit">保存更改</el-button>
          </el-form>
        </div>
      </el-tab-pane>

      <!-- History Tab -->
      <el-tab-pane label="修改历史" name="history">
        <div class="history-timeline">
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in history"
              :key="index"
              :timestamp="item.timestamp"
              :color="item.type === 'create' ? '#10B981' : '#0284C7'"
            >
              {{ item.description }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- Edit Segment Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑分段" width="700px">
      <el-form label-position="top">
        <el-form-item label="分段内容">
          <el-input 
            v-model="editingSegment.content" 
            type="textarea" 
            :rows="10"
            placeholder="输入分段内容..."
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSegment">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  Document, Search, Plus, Setting, Delete, Refresh, Edit
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

const route = useRoute();
const router = useRouter();

const kbId = ref(route.params.kbId);
const docId = ref(route.params.docId);
const kbName = ref('');
const documentData = ref({});
const segments = ref([]);
const selectedSegment = ref(null);
const activeTab = ref('segments');
const searchKeyword = ref('');
const loading = ref(false);
const vectorizing = ref(false);
const editingSegment = reactive({ id: '', content: '' });

const form = reactive({ 
  name: '', 
  mode: 'auto',
  chunkSize: 500,
  chunkOverlap: 50,
  enabled: true 
});

const history = ref([]);

const filteredSegments = computed(() => {
  if (!searchKeyword.value) return segments.value;
  return segments.value.filter(s => 
    s.content.toLowerCase().includes(searchKeyword.value.toLowerCase())
  );
});

const loadDocumentData = async () => {
  try {
    // Load document data from real API
    const doc = await request.get(`/knowledge/documents/${docId.value}`);
    if (!doc) {
      ElMessage.error('文档不存在');
      router.push(ROUTES.RESOURCES.KNOWLEDGE);
      return;
    }
    
    documentData.value = {
        id: doc.id,
        name: doc.fileName,
        uploadTime: formatDate(doc.uploadTime),
        wordCount: doc.charCount || 0,
        hitCount: 0,
        vectorStatus: doc.vectorStatus,
        mode: doc.vectorStatus === 'SUCCESS' ? '自动' : '待处理',
        enabled: true
    };
    
    form.name = doc.fileName;
    form.mode = doc.chunkMethod || 'auto';
    form.chunkSize = doc.chunkSize || 500;
    form.chunkOverlap = doc.chunkOverlap || 50;
    form.enabled = true;

    // Try to find KB name from local storage or simplified way
    kbName.value = '知识库'; // Fallback
    try {
        const kbs = await request.get('/knowledge/list');
        const kb = kbs.find(k => k.id === kbId.value);
        if (kb) kbName.value = kb.name;
    } catch (e) {
        console.warn('Failed to load KB name');
    }
    
    
    await loadSegments();
    await loadHistory();
  } catch (error) {
    ElMessage.error('加载文档详情失败: ' + error.message);
  }
};

const loadHistory = async () => {
    try {
        const res = await request.get(`/knowledge/documents/${docId.value}/history`);
        history.value = res || [];
    } catch (error) {
        console.warn('Failed to load history', error);
    }
};

const loadSegments = async () => {
    loading.value = true;
    try {
        const res = await request.get(`/knowledge/documents/${docId.value}/chunks`);
        if (Array.isArray(res)) {
            segments.value = res.map((chunk, idx) => ({
                id: chunk.id || `chunk-${idx}`,
                content: chunk.content || chunk.text || '',
                wordCount: (chunk.content || chunk.text || '').length,
                hitCount: chunk.score ? Math.round(chunk.score * 100) : 0,
                status: 'indexed'
            }));
        } else {
            segments.value = [];
        }
    } catch (error) {
        console.error('Failed to load segments:', error);
        segments.value = [];
    } finally {
        loading.value = false;
    }
};

const handleVectorize = async () => {
    vectorizing.value = true;
    try {
        await request.post(`/knowledge/documents/${docId.value}/vectorize`);
        ElMessage.success('已提交向量化任务，请稍后刷新');
        // Poll for status or just wait
        setTimeout(loadDocumentData, 2000);
    } catch (error) {
        ElMessage.error('触发向量化失败: ' + error.message);
    } finally {
        vectorizing.value = false;
    }
};

const formatDate = (val) => {
    if (!val) return '-';
    if (Array.isArray(val)) {
        const [year, month, day, hour = 0, minute = 0] = val;
        return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`;
    }
    return new Date(val).toLocaleString();
};

// generateMockSegments removed as we use real API now

const selectSegment = (segment) => {
  selectedSegment.value = segment;
};

const editSegment = (segment) => {
  editingSegment.id = segment.id;
  editingSegment.content = segment.content;
  editDialogVisible.value = true;
};

const saveSegment = async () => {
  try {
    const payload = { content: editingSegment.content };
    await request.put(`/knowledge/documents/chunks/${editingSegment.id}`, payload);
    ElMessage.success('分段已更新');
    editDialogVisible.value = false;
    loadSegments(); // reload to get exact changes including charCount updates
  } catch (err) {
    ElMessage.error('保存分段失败: ' + err.message);
  }
};

const deleteSegment = (segment) => {
  ElMessageBox.confirm('确认删除此分段吗？', '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
      await request.delete(`/knowledge/documents/chunks/${segment.id}`);
      ElMessage.success('已删除');
      loadSegments(); // refresh segments list
    } catch (err) {
      ElMessage.error('删除分段失败: ' + err.message);
    }
  });
};

const onSubmit = async () => {
  submitting.value = true;
  try {
    const payload = {
        name: form.name,
        enabled: form.enabled,
        mode: form.mode,
        chunkSize: form.chunkSize,
        chunkOverlap: form.chunkOverlap
    };
    await request.put(`/knowledge/documents/${docId.value}`, payload);
    documentData.value.name = form.name;
    documentData.value.enabled = form.enabled;
    ElMessage.success('保存成功');
  } catch (err) {
    ElMessage.error('保存失败: ' + err.message);
  } finally {
    submitting.value = false;
  }
};

const handleDelete = () => {
  ElMessageBox.confirm(`确认删除文档 [${documentData.value.name}] 吗？`, '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    const docs = JSON.parse(localStorage.getItem(`orin_mock_docs_${kbId.value}`) || '[]');
    const filtered = docs.filter(d => d.id !== docId.value);
    localStorage.setItem(`orin_mock_docs_${kbId.value}`, JSON.stringify(filtered));
    ElMessage.success('已删除');
    router.push(`/dashboard/resources/knowledge/detail/${kbId.value}`);
  });
};

const goBackToKB = () => {
  router.push(`/dashboard/resources/knowledge/detail/${kbId.value}`);
};

onMounted(() => {
  loadDocumentData();
});
</script>

<style scoped>
.document-detail-page {
  padding: 24px 32px;
  background: #FCFCFD;
  min-height: 100vh;
}

.detail-header {
  margin-bottom: 24px;
}

.breadcrumb {
  font-size: 14px;
  color: var(--neutral-gray-500);
  display: flex;
  align-items: center;
  gap: 8px;
}

.back-link {
  cursor: pointer;
  transition: color 0.2s;
}
.back-link:hover { color: var(--orin-blue); }

.separator {
  color: var(--neutral-gray-300);
}

.current {
  font-weight: 600;
  color: var(--neutral-gray-800);
}

.doc-info-section {
  display: flex;
  gap: 16px;
  margin-bottom: 32px;
  align-items: flex-start;
  background: white;
  padding: 20px;
  border-radius: 10px;
  border: 1px solid var(--neutral-gray-200);
}

.icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
  background: #E0F2FE;
  color: #0284C7;
}

.info-text {
  flex: 1;
}

.info-text h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.meta-info {
  font-size: 13px;
  color: var(--neutral-gray-500);
  display: flex;
  align-items: center;
  gap: 8px;
}

.meta-info .separator {
  color: var(--neutral-gray-300);
}

.header-actions {
  display: flex;
  gap: 12px;
}

.doc-tabs {
  background: white;
  border-radius: 8px;
  padding: 16px 24px;
}

.segments-container {
  padding: 8px 0;
}

.tool-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.left-tools {
  display: flex;
  gap: 12px;
}

.search-input {
  width: 300px;
}

.right-tools {
  display: flex;
  gap: 12px;
}

.segments-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.segment-card {
  background: #FAFAFA;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.segment-card:hover {
  border-color: var(--orin-amber);
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.segment-card.selected {
  border-color: var(--orin-amber);
  background: #FFFBEB;
}

.segment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.segment-number {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-700);
}

.segment-meta {
  font-size: 12px;
  color: var(--neutral-gray-500);
  display: flex;
  align-items: center;
  gap: 6px;
}

.segment-content {
  font-size: 14px;
  line-height: 1.6;
  color: var(--neutral-gray-700);
  margin-bottom: 12px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.segment-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.segment-actions {
  display: flex;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
}

.settings-panel {
  padding: 20px 0;
}

.slider-value {
  margin-left: 12px;
  font-size: 13px;
  color: var(--neutral-gray-500);
}

.history-timeline {
  padding: 20px 0;
  max-width: 600px;
}
</style>
