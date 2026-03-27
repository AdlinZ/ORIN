<template>
  <div class="kb-detail-page">
    <PageHeader
      title="知识库详情"
      description="查看知识库文档、检索配置与向量模型设置"
      icon="Collection"
    />
    <!-- Header with Breadcrumb -->
    <div class="detail-header">
      <div class="breadcrumb">
        <span class="back-link" @click="$router.push(ROUTES.RESOURCES.KNOWLEDGE)">知识库</span>
        <span class="separator">/</span>
        <span class="current">{{ kbData.name }}</span>
      </div>
    </div>

    <!-- KB Info Section -->
    <div class="kb-info-section">
      <div class="icon-wrapper" :class="getIconClass(kbData.type)">
        <el-icon><component :is="getIcon(kbData.type)" /></el-icon>
      </div>
      <div class="info-text">
        <h2>{{ kbData.name }}</h2>
        <p>{{ kbData.description || '暂无描述' }}</p>
      </div>
    </div>

    <!-- Tabs -->
    <el-tabs v-model="activeTab" class="kb-tabs">
      <!-- Documents Tab (for UNSTRUCTURED) -->
      <el-tab-pane v-if="kbData.type === 'UNSTRUCTURED'" label="文档" name="docs">
        <div class="doc-manager">
          <div class="tool-bar">
            <div class="left-tools">
              <el-dropdown>
                <el-button size="default">
                  全部 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item>全部</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              
              <el-input 
                v-model="searchKeyword"
                placeholder="搜索" 
                :prefix-icon="Search" 
                class="search-input"
                clearable 
              />
            </div>
            <div class="right-tools">
              <el-tooltip content="批量设置功能开发中" placement="top">
                <el-button size="default" disabled>批量设置</el-button>
              </el-tooltip>
              <el-button type="primary" size="default" :icon="Plus">添加文件</el-button>
            </div>
          </div>

          <!-- Document Table -->
          <el-table border :data="documents" style="width: 100%" class="dify-table">
            <el-table-column type="selection" width="40" />
            <el-table-column label="#" width="60">
              <template #default="scope">{{ scope.$index + 1 }}</template>
            </el-table-column>
            <el-table-column label="名称" min-width="250">
              <template #default="{ row }">
                <div class="doc-name-cell" @click="openDocument(row)">
                  <el-icon class="file-icon"><Document /></el-icon>
                  <span class="name text-ellipsis clickable" :title="row.name">{{ row.name }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="分段模式" width="100">
              <template #default="{ row }">
                <el-tag size="small" type="info" effect="plain">{{ row.mode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="字符数" width="100" prop="wordCount">
              <template #default="{ row }">{{ (row.wordCount / 1000).toFixed(1) }}k</template>
            </el-table-column>
            <el-table-column label="召回次数" width="100" prop="hitCount" />
            <el-table-column label="上传时间" width="180" prop="uploadTime" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <div class="status-dot">
                  <span class="dot" :class="row.status === 'SUCCESS' ? 'accepted' : (row.status === 'FAILED' ? 'rejected' : 'waiting')"></span>
                  <span>{{ row.status === 'SUCCESS' ? '可用' : (row.status === 'FAILED' ? '失败' : '处理中') }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" />
                <el-tooltip content="触发向量化" placement="top">
                    <el-button 
                        v-if="row.status !== 'SUCCESS'"
                        link type="primary" 
                        size="small" 
                        style="margin-left: 12px;"
                        @click="handleVectorize(row)"
                    >
                        <el-icon><Cpu /></el-icon>
                    </el-button>
                </el-tooltip>
                <el-button link type="primary" size="small" style="margin-left: 12px;"><el-icon><Setting /></el-icon></el-button>
                <el-button link type="danger" size="small"><el-icon><Delete /></el-icon></el-button>
              </template>
            </el-table-column>
          </el-table>
          
          <div class="table-pagination">
            <el-pagination layout="prev, pager, next" :total="50" background small />
          </div>
        </div>
      </el-tab-pane>

      <!-- Settings Tab (merged retrieval settings) -->
      <el-tab-pane label="设置" name="settings">
        <div class="settings-page-container">
          <div class="settings-top-actions">
            <el-button type="primary" :loading="submitting" :icon="Check" @click="onSubmit">
              保存更改
            </el-button>
          </div>

          <el-row :gutter="24">
            <el-col :lg="16">
              <el-card class="premium-card margin-bottom-lg" shadow="never">
                <template #header>
                  <div class="card-header">
                    <el-icon><Setting /></el-icon>
                    <span>基础设置</span>
                  </div>
                </template>

                <el-form label-position="top" class="config-form">
                  <el-form-item label="名称">
                    <el-input v-model="form.name" placeholder="请输入知识库名称" />
                    <p class="form-tip">用于标识此知识库的公开名称</p>
                  </el-form-item>
                  <el-form-item label="描述">
                    <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入描述" resize="none" />
                    <p class="form-tip">详细说明该知识库主要包含的内容和用途</p>
                  </el-form-item>
                </el-form>
              </el-card>

              <el-card class="premium-card margin-bottom-lg" shadow="never">
                <template #header>
                  <div class="card-header">
                    <el-icon><Filter /></el-icon>
                    <span>检索配置</span>
                  </div>
                </template>

                <el-form label-position="top" class="config-form">
                  <el-form-item label="Top K">
                    <el-slider v-model="retrievalParams.topK" :max="20" :min="1" show-input />
                    <p class="form-tip">召回的片段数量，数量越多可能包含更多信息，但也容易引入噪声</p>
                  </el-form-item>
                  <el-form-item label="语义权重">
                    <el-slider v-model="retrievalParams.weight" :max="1" :step="0.1" show-input />
                    <p class="form-tip">混合检索权重分配：1 表示完全依赖向量语义检索，0 表示完全依赖关键词全文检索</p>
                  </el-form-item>
                </el-form>
              </el-card>

              <el-card class="premium-card margin-bottom-lg" shadow="never">
                <template #header>
                  <div class="card-header">
                    <el-icon><Cpu /></el-icon>
                    <span>向量嵌入模型</span>
                  </div>
                </template>

                <el-form label-position="top" class="config-form">
                  <el-form-item label="向量嵌入服务提供商">
                    <el-select
                      v-model="modelConfig.embeddingProvider"
                      style="width: 100%"
                      placeholder="选择嵌入服务提供商"
                    >
                      <el-option value="SiliconFlow" label="SiliconFlow" />
                      <el-option value="Ollama" label="Ollama (本地)" />
                    </el-select>
                    <p class="form-tip">选择用于文档向量化的嵌入服务提供商</p>
                  </el-form-item>

                  <el-form-item label="API 密钥">
                    <el-select
                      v-model="modelConfig.embeddingApiKeyId"
                      style="width: 100%"
                      placeholder="选择已配置的 API 密钥"
                    >
                      <el-option value="key-1" label="SiliconFlow - 硅基流动" />
                    </el-select>
                    <p class="form-tip">
                      选择已在"API密钥管理"中配置的密钥
                      <el-button type="primary" link @click="router.push('/system/api-keys')">去配置</el-button>
                    </p>
                  </el-form-item>

                  <el-form-item label="Embedding 模型">
                    <el-select
                      v-model="modelConfig.embeddingModel"
                      style="width: 100%"
                      filterable
                      allow-create
                      default-first-option
                      placeholder="选择或输入模型名称"
                    >
                      <el-option value="Qwen/Qwen3-Embedding-8B" label="Qwen/Qwen3-Embedding-8B" />
                    </el-select>
                    <p class="form-tip">用于将文档向量化，支持中文推荐 bge-base-zh-v1.5</p>
                  </el-form-item>

                  <el-form-item>
                    <el-button type="success" plain :loading="testingEmbedding" @click="testEmbeddingConnection">
                      测试连接
                    </el-button>
                  </el-form-item>
                </el-form>
              </el-card>

              <el-card class="premium-card danger-card margin-bottom-lg" shadow="never">
                <template #header>
                  <div class="card-header text-danger">
                    <el-icon><Warning /></el-icon>
                    <span>危险区域</span>
                  </div>
                </template>
                <div class="danger-zone-content">
                  <div>
                    <div class="danger-title">删除知识库</div>
                    <p class="form-tip" style="margin: 0">彻底删除此知识库及其所有关联的文档。此操作无法恢复。</p>
                  </div>
                  <el-button type="danger" plain @click="handleDelete">删除知识库</el-button>
                </div>
              </el-card>
            </el-col>

            <el-col :lg="8">
              <el-card class="premium-card" shadow="never">
                <template #header>
                  <div class="card-header">
                    <el-icon><InfoFilled /></el-icon>
                    <span>设置说明</span>
                  </div>
                </template>
                <div class="model-list">
                  <div class="model-item">
                    <div class="model-name">Top K</div>
                    <div class="model-desc">检索时返回最相关的文档分段数量。设置过低可能导致大模型回答时缺乏信息，设置过高可能超出大模型的上下文窗口。</div>
                  </div>
                  <div class="model-item">
                    <div class="model-name">语义权重 (Hybrid Search)</div>
                    <div class="model-desc">在混合检索（语义检索 + 全文检索）时调整各自的比重。推荐的默认值是 0.7，兼顾语义理解与精准匹配。</div>
                  </div>
                  <div class="model-item">
                    <div class="model-name">Embedding 模型</div>
                    <div class="model-desc">将文本转换为向量，用于语义检索。每个知识库可单独指定用于向量化的模型。</div>
                  </div>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { 
  Document, Search, Plus, ArrowDown, Setting, Delete,
  DataLine, Cpu, Opportunity, Check, InfoFilled, Filter, Warning
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';
import { ROUTES } from '@/router/routes';
import { deleteKnowledge } from '@/api/knowledge';
import PageHeader from '@/components/PageHeader.vue';

const route = useRoute();
const router = useRouter();

const kbId = ref(route.params.id);
const kbData = ref({});
const documents = ref([]);
const activeTab = ref('docs');
const searchKeyword = ref('');
const submitting = ref(false);
const testingEmbedding = ref(false);
const form = reactive({ name: '', remark: '' });
const retrievalParams = reactive({ topK: 5, weight: 0.7 });
const modelConfig = reactive({
  embeddingProvider: 'SiliconFlow',
  embeddingApiKeyId: 'key-1',
  embeddingModel: 'Qwen/Qwen3-Embedding-8B'
});

const getIcon = (type) => {
  if (type === 'UNSTRUCTURED') return Document;
  if (type === 'STRUCTURED') return DataLine;
  if (type === 'PROCEDURAL') return Cpu;
  if (type === 'META_MEMORY') return Opportunity;
  return Document;
};

const getIconClass = (type) => {
  if (type === 'UNSTRUCTURED') return 'icon-blue';
  if (type === 'STRUCTURED') return 'icon-green';
  if (type === 'PROCEDURAL') return 'icon-purple';
  if (type === 'META_MEMORY') return 'icon-amber';
  return 'icon-default';
};

const getChunkModeName = (method) => {
  if (method === 'auto') return '自动';
  if (method === 'manual') return '手动';
  if (method === 'smart') return '智能';
  return '自动';
};

const loadKBData = async () => {
  try {
    // Load all KBs to find the current one (fallback if detail API not specific)
    const kbs = await request.get('/knowledge/list');
    const kb = kbs.find(k => k.id === kbId.value);
    
    if (!kb) {
      ElMessage.error('知识库不存在');
      router.push(ROUTES.RESOURCES.KNOWLEDGE);
      return;
    }
    
    kbData.value = kb;
    form.name = kb.name;
    form.remark = kb.description || '';
    
    // Load documents if UNSTRUCTURED or default
    if (kb.type === 'UNSTRUCTURED' || !kb.type) {
        const docs = await request.get(`/knowledge/${kbId.value}/documents`);
        if (Array.isArray(docs)) {
            documents.value = docs.map(doc => ({
                id: doc.id,
                name: doc.fileName,
                mode: getChunkModeName(doc.chunkMethod),
                wordCount: doc.charCount || 0,
                chunkSize: doc.chunkSize || 500,
                hitCount: doc.hitCount || 0,
                uploadTime: formatDate(doc.uploadTime),
                enabled: true,
                status: doc.vectorStatus
            }));
        }
    }
  } catch (error) {
    ElMessage.error('加载知识库详情失败: ' + error.message);
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

const testEmbeddingConnection = () => {
  testingEmbedding.value = true;
  setTimeout(() => {
    ElMessage.success('Embedding 模型可用！维度: 3584');
    testingEmbedding.value = false;
  }, 1000);
};

const onSubmit = () => {
  submitting.value = true;
  setTimeout(() => {
    const allKBs = JSON.parse(localStorage.getItem('orin_mock_kbs') || '[]');
    const kb = allKBs.find(k => k.id === kbId.value);
    if (kb) {
      kb.name = form.name;
      kb.description = form.remark;
      localStorage.setItem('orin_mock_kbs', JSON.stringify(allKBs));
      kbData.value = kb;
      ElMessage.success('保存成功');
    }
    submitting.value = false;
  }, 500);
};

const handleDelete = async () => {
  try {
    await ElMessageBox.confirm(`确认删除知识库 [${kbData.value.name}] 吗？`, '警告', {
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    });
    await deleteKnowledge(kbId.value);
    ElMessage.success('已删除');
    router.push(ROUTES.RESOURCES.KNOWLEDGE);
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败');
    }
  }
};

const handleVectorize = async (row) => {
    try {
        await request.post(`/knowledge/documents/${row.id}/vectorize`);
        ElMessage.success('已启动任务');
        loadKBDetail(); // Refresh
    } catch (e) {
        ElMessage.error('触发失败: ' + e.message);
    }
};

const openDocument = (doc) => {
  router.push(`/dashboard/resources/knowledge/${kbId.value}/document/${doc.id}`);
};

onMounted(() => {
  loadKBData();
});
</script>

<style scoped>
.kb-detail-page {
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

.current {
  font-weight: 600;
  color: var(--neutral-gray-800);
}

.kb-info-section {
  display: flex;
  gap: 16px;
  margin-bottom: 32px;
  align-items: flex-start;
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
}

.icon-blue { background: #E0F2FE; color: #0284C7; }
.icon-green { background: #DCFCE7; color: #16A34A; }
.icon-purple { background: #F3E8FF; color: #9333EA; }
.icon-amber { background: #FEF3C7; color: #D97706; }
.icon-default { background: #F3F4F6; color: #4B5563; }

.info-text h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.info-text p {
  margin: 0;
  font-size: 13px;
  color: var(--neutral-gray-500);
}

.kb-tabs {
  background: white;
  border-radius: 8px;
  padding: 16px 24px;
}

.doc-manager { padding: 8px 0; }
.tool-bar { 
  display: flex; 
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px; 
}

.left-tools {
  display: flex;
  gap: 12px;
}
.search-input { width: 240px; }

.right-tools {
  display: flex;
  gap: 12px;
}

.dify-table {
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-200);
  overflow: hidden;
}

.dify-table :deep(th) {
  background: #F9FAFB !important;
  color: var(--neutral-gray-500);
  font-weight: 500;
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.doc-name-cell:hover .name {
  color: var(--orin-blue);
}

.clickable {
  cursor: pointer;
  transition: color 0.2s;
}

.file-icon {
  color: var(--neutral-gray-400);
  font-size: 16px;
}

.text-ellipsis {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.status-dot {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}
.dot.accepted { background: #10B981; }

.table-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* --- Settings Page Styles --- */
.settings-page-container {
  padding: 16px 0 40px;
}

.settings-top-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 24px;
}

.premium-card {
  border-radius: var(--radius-xl, 12px) !important;
  border: 1px solid var(--neutral-gray-200) !important;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 15px;
  color: var(--neutral-gray-800);
}

.card-header.text-danger {
  color: var(--el-color-danger);
}

.margin-bottom-lg {
  margin-bottom: 24px;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-top: 6px;
  line-height: 1.5;
}

.model-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.model-item {
  padding: 12px;
  background: var(--neutral-gray-50, #F9FAFB);
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-200, #E5E7EB);
}

.model-name {
  font-weight: 600;
  color: var(--neutral-gray-800);
  margin-bottom: 4px;
}

.model-desc {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

.danger-card {
  border-color: #FCA5A5 !important;
  background: #FEF2F2 !important;
}

.danger-zone-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.danger-title {
  font-weight: 600;
  margin-bottom: 4px;
  color: var(--el-color-danger);
}

:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--neutral-gray-100);
}

/* Override element-plus input styles to match premium look */
:deep(.el-input__wrapper), :deep(.el-textarea__inner) {
  box-shadow: 0 0 0 1px var(--neutral-gray-200) inset !important;
  background-color: var(--neutral-gray-50);
  transition: all 0.2s;
}
:deep(.el-input__wrapper:focus-within), :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--orin-blue) inset !important;
  background-color: white;
}
</style>
