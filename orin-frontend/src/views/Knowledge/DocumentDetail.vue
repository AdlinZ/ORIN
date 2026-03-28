<template>
  <div class="document-detail-page">
    <PageHeader
      title="文档详情"
      description="查看原文、索引内容与分段向量化状态"
      icon="Document"
    />
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
        >
          立即向量化
        </el-button>
        <el-button :icon="Setting">
          设置
        </el-button>
        <el-button
          type="danger"
          plain
          :icon="Delete"
          @click="handleDelete"
        >
          删除
        </el-button>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="doc-tabs">
      <!-- Original Content Tab -->
      <el-tab-pane label="原文内容" name="original">
        <div v-loading="originalLoading" class="original-content-container">
          <!-- Text Content -->
          <div v-if="originalContentInfo.mediaType === 'text' || originalContentInfo.mediaType === 'pdf'" class="original-text">
            <pre v-if="originalContentInfo.text">{{ originalContentInfo.text }}</pre>
            <el-empty v-else description="文本内容尚未解析或为空" />
          </div>
          
          <!-- Image Content -->
          <div v-else-if="originalContentInfo.mediaType === 'image'" class="original-media-preview">
            <template v-if="imageLoading">
              <el-skeleton :rows="5" animated />
            </template>
            <template v-else-if="imageError">
              <el-result
                icon="error"
                title="加载失败"
                sub-title="无法加载图片，请检查文件是否正确上传"
              />
            </template>
            <template v-else>
              <el-image
                :src="imageBlobUrl"
                fit="contain"
                :preview-src-list="imageBlobUrl ? [imageBlobUrl] : []"
                class="preview-image"
                @load="onImageLoad"
                @error="onImageError"
              >
                <template #error>
                  <div class="image-error-placeholder">
                    <el-icon :size="48">
                      <Picture />
                    </el-icon>
                    <span>图片加载失败</span>
                  </div>
                </template>
              </el-image>
              <div v-if="imageBlobUrl" class="image-actions">
                <el-button type="primary" link @click="downloadOriginalFile">
                  <el-icon><Download /></el-icon>
                  下载原图
                </el-button>
              </div>
            </template>
          </div>

          <!-- Audio Content -->
          <div v-else-if="originalContentInfo.mediaType === 'audio'" class="original-media-preview">
            <div class="audio-player-wrapper">
              <el-icon :size="64" class="media-icon">
                <Headset />
              </el-icon>
              <audio controls :src="mediaBlobUrl" class="media-player" />
              <div class="file-name">
                {{ originalContentInfo.fileName }}
              </div>
            </div>
          </div>

          <!-- Video Content -->
          <div v-else-if="originalContentInfo.mediaType === 'video'" class="original-media-preview">
            <div class="video-player-wrapper">
              <video controls :src="mediaBlobUrl" class="media-player video-player" />
              <div class="file-name">
                {{ originalContentInfo.fileName }}
              </div>
            </div>
          </div>

          <el-empty v-else-if="!originalLoading" description="暂无原文内容" />
        </div>
      </el-tab-pane>

      <!-- Index File Tab -->
      <el-tab-pane label="索引文件" name="indexfile">
        <div v-loading="retrievalLoading" class="index-file-container">
          <template v-if="retrievalInfo && retrievalInfo.fullContent">
            <div class="index-file-header">
              <div class="index-status">
                <el-tag :type="retrievalInfo.parseStatus === 'PARSED' ? 'success' : (retrievalInfo.parseStatus === 'PARSING' ? 'warning' : 'info')" size="small">
                  {{ retrievalInfo.parseStatus === 'PARSED' ? '已解析' : (retrievalInfo.parseStatus === 'PARSING' ? '解析中' : '待处理') }}
                </el-tag>
                <el-tag
                  v-if="retrievalInfo.vectorStatus"
                  :type="retrievalInfo.vectorStatus === 'SUCCESS' ? 'success' : (retrievalInfo.vectorStatus === 'INDEXING' ? 'warning' : 'info')"
                  size="small"
                  style="margin-left: 8px;"
                >
                  {{ retrievalInfo.vectorStatus === 'SUCCESS' ? '已向量化' : (retrievalInfo.vectorStatus === 'INDEXING' ? '向量化中' : '待向量化') }}
                </el-tag>
                <span class="index-char-count">索引文件共 {{ retrievalInfo.indexCharCount || 0 }} 字符</span>
              </div>
              <div class="index-actions">
                <el-button
                  v-if="retrievalInfo.parseStatus === 'PARSED' && retrievalInfo.vectorStatus !== 'SUCCESS'"
                  type="primary"
                  size="small"
                  :loading="vectorizing"
                  @click="handleVectorize"
                >
                  立即向量化
                </el-button>
                <el-button :icon="Refresh" size="small" @click="loadRetrievalInfo">
                  刷新
                </el-button>
                <el-button
                  type="primary"
                  :icon="DocumentCopy"
                  size="small"
                  @click="copyIndexContent"
                >
                  复制全文
                </el-button>
              </div>
            </div>

            <div class="index-full-content">
              <pre>{{ retrievalInfo.fullContent }}</pre>
            </div>

            <div class="index-stats">
              <el-row :gutter="20">
                <el-col :span="8">
                  <el-statistic title="总字符数" :value="retrievalInfo.indexCharCount || 0" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="父分块" :value="chunkStats.parentCount || 0" />
                </el-col>
                <el-col :span="8">
                  <el-statistic title="子分块" :value="chunkStats.childCount || 0" />
                </el-col>
              </el-row>
            </div>
          </template>
          <template v-else-if="!retrievalLoading">
            <div class="index-empty">
              <template v-if="retrievalInfo?.parseStatus === 'PARSING'">
                <el-icon class="is-loading" :size="48">
                  <Loading />
                </el-icon>
                <p>正在解析文档...</p>
              </template>
              <template v-else-if="!retrievalInfo?.parseStatus || retrievalInfo?.parseStatus === 'PENDING' || retrievalInfo?.parseStatus === 'FAILED'">
                <el-result icon="info" title="暂无索引内容">
                  <template #extra>
                    <el-button type="primary" :loading="parsing" @click="handleParse">
                      立即解析
                    </el-button>
                  </template>
                </el-result>
              </template>
              <template v-else-if="retrievalInfo?.parseStatus === 'PARSED' && retrievalInfo?.vectorStatus !== 'SUCCESS'">
                <el-result icon="success" title="索引文件已生成">
                  <template #description>
                    <p>文档已解析完成，共 {{ retrievalInfo.indexCharCount || 0 }} 字符</p>
                  </template>
                  <template #extra>
                    <el-button type="primary" :loading="vectorizing" @click="handleVectorize">
                      立即向量化
                    </el-button>
                  </template>
                </el-result>
              </template>
              <template v-else>
                <el-result icon="warning" title="状态未知">
                  <template #extra>
                    <el-button @click="loadRetrievalInfo">
                      刷新状态
                    </el-button>
                  </template>
                </el-result>
              </template>
            </div>
          </template>
        </div>
      </el-tab-pane>

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
              <el-button :icon="Refresh" @click="loadSegments">
                刷新
              </el-button>
              <el-button type="primary" :icon="Plus">
                添加分段
              </el-button>
            </div>
          </div>

          <!-- Segments List -->
          <div v-loading="loading" class="segments-list">
            <template v-if="filteredSegments.length > 0">
              <div 
                v-for="(segment, index) in filteredSegments" 
                :key="segment.id"
                class="segment-card"
                :class="{ 'selected': selectedSegment?.id === segment.id }"
                @click="selectSegment(segment)"
              >
                <div class="segment-header">
                  <div class="segment-number">
                    分段 {{ index + 1 }}
                  </div>
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
                  <el-tag
                    v-if="segment.chunkType"
                    size="small"
                    type="warning"
                    effect="plain"
                  >
                    {{ segment.chunkType === 'parent' ? 'Parent' : 'Child' }}
                  </el-tag>
                  <div class="segment-actions">
                    <el-button
                      v-if="segment.chunkType !== 'parent'"
                      link
                      type="success"
                      size="small"
                      @click.stop="loadChunkVector(segment.id)"
                    >
                      <el-icon><DataLine /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="primary"
                      size="small"
                      @click.stop="editSegment(segment)"
                    >
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click.stop="deleteSegment(segment)"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="empty-segments">
              <el-empty description="该文档尚未进行分段向量化">
                <el-button type="primary" :loading="vectorizing" @click="handleVectorize">
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

            <!-- Parent-Child 分块信息 -->
            <el-form-item label="分块模式">
              <el-tag type="success">
                {{ chunkStats.chunkingMode || 'PARENT_CHILD' }}
              </el-tag>
            </el-form-item>
            <el-form-item label="分块统计">
              <div class="chunk-stats-display">
                <el-statistic title="父分块 (Parent)" :value="chunkStats.parentCount" />
                <el-statistic title="子分块 (Child)" :value="chunkStats.childCount" />
              </div>
            </el-form-item>

            <!-- 隐藏旧的配置项，因为现在使用固定的 Parent-Child 分块 -->
            <el-form-item label="分段模式">
              <el-select v-model="form.mode" style="width: 100%;" disabled>
                <el-option label="自动" value="auto" />
                <el-option label="手动" value="manual" />
                <el-option label="智能" value="smart" />
              </el-select>
              <div class="form-tip">
                当前使用 Parent-Child 分块策略，配置已固定
              </div>
            </el-form-item>
            <el-form-item label="分段长度" style="display: none;">
              <el-slider
                v-model="form.chunkSize"
                :min="100"
                :max="2000"
                :step="100"
              />
              <span class="slider-value">{{ form.chunkSize }} 字符</span>
            </el-form-item>
            <el-form-item label="分段重叠" style="display: none;">
              <el-slider
                v-model="form.chunkOverlap"
                :min="0"
                :max="500"
                :step="10"
              />
              <span class="slider-value">{{ form.chunkOverlap }} 字符</span>
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="form.enabled" />
            </el-form-item>
            <el-button type="primary" :loading="submitting" @click="onSubmit">
              保存更改
            </el-button>
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
        <el-button @click="editDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" @click="saveSegment">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- 向量数据展示对话框 -->
    <el-dialog
      v-model="vectorDialogVisible"
      v-loading="vectorLoading"
      title="向量数据"
      width="800px"
    >
      <div v-if="vectorData" class="vector-info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Chunk ID">
            {{ vectorData.chunkId }}
          </el-descriptions-item>
          <el-descriptions-item label="Doc ID">
            {{ vectorData.docId }}
          </el-descriptions-item>
          <el-descriptions-item label="Chunk Type">
            {{ vectorData.chunkType }}
          </el-descriptions-item>
          <el-descriptions-item label="向量维度">
            {{ vectorData.embeddingDimension }}
          </el-descriptions-item>
          <el-descriptions-item label="内容" :span="2">
            <div class="vector-content">
              {{ vectorData.content }}
            </div>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="vectorData.embedding" class="vector-array-section">
          <h4>向量数据 (前20维预览)</h4>
          <div class="vector-array">
            {{ vectorData.embedding.slice(0, 20).map(v => v.toFixed(4)).join(', ') }}
            <span v-if="vectorData.embedding.length > 20">
              ... (共 {{ vectorData.embedding.length }} 维)
            </span>
          </div>
        </div>
      </div>
      <el-empty v-else-if="!vectorLoading" description="暂无向量数据" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import {
  Document, Search, Plus, Setting, Delete, Refresh, Edit,
  Headset, VideoCamera, Picture, Download, DocumentCopy, Loading, DataLine
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';
import { useUserStore } from '@/stores/user';
import PageHeader from '@/components/PageHeader.vue';

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
const parsing = ref(false);
const editingSegment = reactive({ id: '', content: '' });
const chunkStats = ref({ parentCount: 0, childCount: 0, chunkingMode: 'PARENT_CHILD' });
const retrievalInfo = ref(null);
const retrievalLoading = ref(false);
const activeIndexCollapse = ref([]);
const submitting = ref(false);

const form = reactive({
  name: '',
  mode: 'auto',
  chunkSize: 500,
  chunkOverlap: 50,
  enabled: true
});

const history = ref([]);
const originalLoading = ref(false);

// 图片预览相关状态
const imageLoading = ref(false);
const imageError = ref(false);
const imageBlobUrl = ref('');

// 音频/视频 Blob URL
const mediaBlobUrl = ref('');

const originalContentInfo = reactive({
    text: '',
    mediaType: 'text',
    url: '',
    fileName: ''
});

// 向量数据展示相关状态
const vectorDialogVisible = ref(false);
const vectorData = ref(null);
const vectorLoading = ref(false);

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
    await loadOriginalContent();
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

const loadOriginalContent = async () => {
    originalLoading.value = true;
    imageError.value = false;
    imageBlobUrl.value = '';

    // 清理之前的 Blob URLs
    if (imageBlobUrl.value) {
        URL.revokeObjectURL(imageBlobUrl.value);
        imageBlobUrl.value = '';
    }
    if (mediaBlobUrl.value) {
        URL.revokeObjectURL(mediaBlobUrl.value);
        mediaBlobUrl.value = '';
    }

    try {
        const res = await request.get(`/knowledge/documents/${docId.value}/content`);
        originalContentInfo.text = res.text || '';
        originalContentInfo.mediaType = res.mediaType || 'text';
        originalContentInfo.fileName = res.fileName || '';

        // 对于图片类型，使用 Blob URL 加载以支持认证
        if (originalContentInfo.mediaType === 'image') {
            await loadImageAsBlob();
        }
        // 对于音频/视频，也使用 Blob URL
        else if (originalContentInfo.mediaType === 'audio' || originalContentInfo.mediaType === 'video') {
            await loadMediaAsBlob();
        }
    } catch (error) {
        console.warn('Failed to load original content', error);
        originalContentInfo.text = '';
    } finally {
        originalLoading.value = false;
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
                status: 'indexed',
                chunkType: chunk.chunkType || 'child',
                parentId: chunk.parentId || ''
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

const loadChunkStats = async () => {
    try {
        const res = await request.get(`/knowledge/documents/${docId.value}/chunks/stats`);
        if (res) {
            chunkStats.value = res;
        }
    } catch (error) {
        console.warn('Failed to load chunk stats:', error);
    }
};

// 获取 chunk 的向量数据
const loadChunkVector = async (chunkId) => {
    vectorLoading.value = true;
    vectorData.value = null;
    vectorDialogVisible.value = true;
    try {
        const res = await request.get(`/knowledge/chunks/${chunkId}/vector`);
        if (res && res.success) {
            vectorData.value = res.data;
        } else {
            // 显示详细错误信息
            const errorMsg = res?.error || '未找到向量数据';
            ElMessage.error(`获取向量数据失败: ${errorMsg}`);
        }
    } catch (error) {
        console.error('Failed to load chunk vector:', error);
        // 显示详细错误信息
        const errorMsg = error?.response?.data?.message || error?.message || '网络错误';
        ElMessage.error(`获取向量数据失败: ${errorMsg}`);
    } finally {
        vectorLoading.value = false;
    }
};

const loadRetrievalInfo = async () => {
    retrievalLoading.value = true;
    try {
        const res = await request.get(`/knowledge/documents/${docId.value}/retrieval-info`);
        if (res) {
            retrievalInfo.value = res;
        }
    } catch (error) {
        console.warn('Failed to load retrieval info:', error);
    } finally {
        retrievalLoading.value = false;
    }
};

// 解析文档（生成索引文件）
const handleParse = async () => {
    parsing.value = true;
    try {
        await request.post(`/knowledge/documents/${docId.value}/parse`);
        ElMessage.success('已提交解析任务，请稍后刷新');
        // 轮询检查状态
        setTimeout(() => {
            loadRetrievalInfo();
        }, 2000);
    } catch (error) {
        ElMessage.error('提交解析任务失败: ' + error.message);
    } finally {
        parsing.value = false;
    }
};

const handleVectorize = async () => {
    vectorizing.value = true;
    try {
        await request.post(`/knowledge/documents/${docId.value}/vectorize`);
        // 检查当前状态来决定提示文案
        if (retrievalInfo.value?.parseStatus === 'PARSED') {
            ElMessage.success('已提交向量化任务，请稍后刷新');
        } else {
            ElMessage.success('已提交解析任务，请稍后刷新');
        }
        // Poll for status or just wait
        setTimeout(() => {
            loadDocumentData();
            loadRetrievalInfo();
        }, 2000);
    } catch (error) {
        ElMessage.error('触发向量化失败: ' + error.message);
    } finally {
        vectorizing.value = false;
    }
};

// 将图片加载为 Blob URL（解决认证问题）
const loadImageAsBlob = async () => {
    imageLoading.value = true;
    imageError.value = false;

    try {
        // 使用 fetch 配合认证来获取图片
        const userStore = useUserStore();
        const token = userStore.token;

        const response = await fetch(`/api/v1/knowledge/documents/${docId.value}/download`, {
            headers: {
                'Authorization': token ? `Bearer ${token}` : ''
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const blob = await response.blob();
        // 创建 Blob URL
        imageBlobUrl.value = URL.createObjectURL(blob);
    } catch (error) {
        console.error('Failed to load image:', error);
        imageError.value = true;
    } finally {
        imageLoading.value = false;
    }
};

const onImageLoad = () => {
    imageError.value = false;
};

const onImageError = () => {
    imageError.value = true;
};

// 将音频/视频加载为 Blob URL
const loadMediaAsBlob = async () => {
    try {
        const userStore = useUserStore();
        const token = userStore.token;

        const response = await fetch(`/api/v1/knowledge/documents/${docId.value}/download`, {
            headers: {
                'Authorization': token ? `Bearer ${token}` : ''
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        const blob = await response.blob();
        mediaBlobUrl.value = URL.createObjectURL(blob);
    } catch (error) {
        console.error('Failed to load media:', error);
    }
};

const downloadOriginalFile = () => {
    if (imageBlobUrl.value) {
        const link = document.createElement('a');
        link.href = imageBlobUrl.value;
        link.download = originalContentInfo.fileName || 'image';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    }
};

// 复制索引文件全部内容
const copyIndexContent = async () => {
    if (!retrievalInfo.value?.fullContent) return;

    try {
        await navigator.clipboard.writeText(retrievalInfo.value.fullContent);
        ElMessage.success('索引文件内容已复制到剪贴板');
    } catch (error) {
        ElMessage.error('复制失败');
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
  loadChunkStats();
  loadRetrievalInfo();
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

.chunk-stats-display {
  display: flex;
  gap: 32px;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 4px;
}

.retrieval-info-panel {
  background: #fafafa;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 8px;
  padding: 16px;
  min-height: 100px;
}

.retrieval-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.retrieval-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.retrieval-samples {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.retrieval-sample-item {
  background: white;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 6px;
  padding: 12px;
}

.sample-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.sample-chars {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.sample-content {
  font-size: 13px;
  line-height: 1.5;
  color: var(--neutral-gray-700);
  max-height: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  line-clamp: 3;
  -webkit-box-orient: vertical;
}

.history-timeline {
  padding: 20px 0;
  max-width: 600px;
}

.original-content-container {
  padding: 20px;
  background: #fff;
  border-radius: 8px;
  min-height: 400px;
}

.original-text {
  max-height: 600px;
  overflow-y: auto;
}

.original-text pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu', monospace;
  font-size: 14px;
  line-height: 1.6;
  color: #374151;
  margin: 0;
  padding: 0;
}

.original-media-preview {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
  background: #f9fafb;
  border-radius: 8px;
  padding: 20px;
}

.audio-player-wrapper, .video-player-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  width: 100%;
}

.media-player {
  width: 100%;
  max-width: 600px;
}

.video-player {
  max-height: 500px;
  background: #000;
  border-radius: 8px;
}

.media-icon {
  color: var(--orin-blue);
}

.file-name {
  font-size: 14px;
  color: var(--neutral-gray-600);
  font-weight: 500;
}

.preview-image {
  max-width: 100%;
  max-height: 600px;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.image-error-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: var(--neutral-gray-400);
  gap: 12px;
}

.image-actions {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

.index-file-container {
  padding: 20px;
  min-height: 400px;
}

.index-file-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--neutral-gray-200);
}

.index-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.index-char-count {
  font-size: 13px;
  color: var(--neutral-gray-500);
}

.index-actions {
  display: flex;
  gap: 8px;
}

.index-segments {
  margin-bottom: 24px;
}

.index-full-content {
  background: #f9fafb;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 24px;
  max-height: 500px;
  overflow-y: auto;
}

.index-full-content pre {
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--neutral-gray-700);
  margin: 0;
}

.index-stats {
  padding: 20px;
  background: #f9fafb;
  border-radius: 8px;
}

.vector-info {
  padding: 10px;
}

.vector-content {
  max-height: 150px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
}

.vector-array-section {
  margin-top: 20px;
}

.vector-array-section h4 {
  margin-bottom: 10px;
  color: var(--text-primary);
}

.vector-array {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 6px;
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
}
</style>
