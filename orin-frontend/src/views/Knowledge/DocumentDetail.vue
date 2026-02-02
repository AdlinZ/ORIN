<template>
  <div class="document-detail-page">
    <!-- Header with Breadcrumb -->
    <div class="detail-header">
      <div class="breadcrumb">
        <span class="back-link" @click="$router.push('/dashboard/knowledge/list')">知识库</span>
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
          <div class="segments-list">
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
                <el-tag size="small" effect="plain">{{ segment.status === 'indexed' ? '已索引' : '待索引' }}</el-tag>
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
import { 
  Document, Search, Plus, Setting, Delete, Refresh, Edit
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

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
const submitting = ref(false);
const editDialogVisible = ref(false);
const editingSegment = reactive({ id: '', content: '' });

const form = reactive({ 
  name: '', 
  mode: 'auto',
  chunkSize: 500,
  enabled: true 
});

const history = ref([
  { timestamp: '2026-02-02 14:20', type: 'create', description: '文档已上传' },
  { timestamp: '2026-02-02 14:25', type: 'update', description: '完成自动分段处理' },
  { timestamp: '2026-02-02 15:10', type: 'update', description: '索引已更新' }
]);

const filteredSegments = computed(() => {
  if (!searchKeyword.value) return segments.value;
  return segments.value.filter(s => 
    s.content.toLowerCase().includes(searchKeyword.value.toLowerCase())
  );
});

const loadDocumentData = () => {
  // Load KB name
  const allKBs = JSON.parse(localStorage.getItem('orin_mock_kbs') || '[]');
  const kb = allKBs.find(k => k.id === kbId.value);
  if (kb) {
    kbName.value = kb.name;
  }

  // Load document data
  const docs = JSON.parse(localStorage.getItem(`orin_mock_docs_${kbId.value}`) || '[]');
  const doc = docs.find(d => d.id === docId.value);
  
  if (!doc) {
    ElMessage.error('文档不存在');
    router.push(`/dashboard/knowledge/detail/${kbId.value}`);
    return;
  }
  
  documentData.value = doc;
  form.name = doc.name;
  form.mode = doc.mode === '自动' ? 'auto' : 'manual';
  form.enabled = doc.enabled;
  
  loadSegments();
};

const loadSegments = () => {
  // Load or generate segments
  const stored = localStorage.getItem(`orin_mock_segments_${docId.value}`);
  if (stored) {
    segments.value = JSON.parse(stored);
  } else {
    generateMockSegments();
  }
};

const generateMockSegments = () => {
  const sampleTexts = [
    'ORIN 是一个先进的 AI 助手平台，集成了多模态能力和知识库管理功能。它能够处理文本、图像和语音输入，为用户提供智能化的交互体验。',
    '系统架构采用微服务设计，前端使用 Vue 3 + Element Plus，后端基于 Spring Boot。数据库使用 PostgreSQL 存储结构化数据，向量数据库用于语义检索。',
    '知识库支持四种类型：非结构化文档、结构化数据、程序化技能和元记忆。每种类型都有专门的处理流程和检索策略。',
    '文档分段采用智能算法，能够根据语义边界自动切分文本。分段长度可以自定义，支持 100-2000 字符范围。',
    '检索系统使用混合策略，结合关键词匹配和语义相似度计算。Top K 参数可调，支持重排序优化。',
    '用户可以通过 Web 界面管理知识库，包括上传文档、编辑分段、调整检索参数等操作。系统提供实时预览和测试功能。'
  ];

  const segs = [];
  for (let i = 0; i < 6; i++) {
    segs.push({
      id: `seg-${Date.now()}-${i}`,
      content: sampleTexts[i],
      wordCount: sampleTexts[i].length,
      hitCount: Math.floor(Math.random() * 50),
      status: 'indexed'
    });
  }
  
  segments.value = segs;
  localStorage.setItem(`orin_mock_segments_${docId.value}`, JSON.stringify(segs));
};

const selectSegment = (segment) => {
  selectedSegment.value = segment;
};

const editSegment = (segment) => {
  editingSegment.id = segment.id;
  editingSegment.content = segment.content;
  editDialogVisible.value = true;
};

const saveSegment = () => {
  const segment = segments.value.find(s => s.id === editingSegment.id);
  if (segment) {
    segment.content = editingSegment.content;
    segment.wordCount = editingSegment.content.length;
    localStorage.setItem(`orin_mock_segments_${docId.value}`, JSON.stringify(segments.value));
    ElMessage.success('分段已更新');
  }
  editDialogVisible.value = false;
};

const deleteSegment = (segment) => {
  ElMessageBox.confirm('确认删除此分段吗？', '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    segments.value = segments.value.filter(s => s.id !== segment.id);
    localStorage.setItem(`orin_mock_segments_${docId.value}`, JSON.stringify(segments.value));
    ElMessage.success('已删除');
  });
};

const onSubmit = () => {
  submitting.value = true;
  setTimeout(() => {
    const docs = JSON.parse(localStorage.getItem(`orin_mock_docs_${kbId.value}`) || '[]');
    const doc = docs.find(d => d.id === docId.value);
    if (doc) {
      doc.name = form.name;
      doc.mode = form.mode === 'auto' ? '自动' : '手动';
      doc.enabled = form.enabled;
      localStorage.setItem(`orin_mock_docs_${kbId.value}`, JSON.stringify(docs));
      documentData.value = doc;
      ElMessage.success('保存成功');
    }
    submitting.value = false;
  }, 500);
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
    router.push(`/dashboard/knowledge/detail/${kbId.value}`);
  });
};

const goBackToKB = () => {
  router.push(`/dashboard/knowledge/detail/${kbId.value}`);
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
