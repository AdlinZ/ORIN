<template>
  <div class="kb-detail-page">
    <!-- Header with Breadcrumb -->
    <div class="detail-header">
      <div class="breadcrumb">
        <span class="back-link" @click="$router.push('/dashboard/knowledge/list')">知识库</span>
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
              <el-button size="default">批量设置</el-button>
              <el-button type="primary" size="default" :icon="Plus">添加文件</el-button>
            </div>
          </div>

          <!-- Document Table -->
          <el-table :data="documents" style="width: 100%" class="dify-table">
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
                  <span class="dot accepted"></span>
                  <span>可用</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" size="small" />
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

      <!-- Retrieval Settings Tab -->
      <el-tab-pane label="检索设置" name="retrieval">
        <el-form label-position="top" style="max-width: 400px; margin-top: 20px;">
          <el-form-item label="Top K">
            <el-slider v-model="retrievalParams.topK" :max="20" />
          </el-form-item>
          <el-form-item label="语义权重">
            <el-slider v-model="retrievalParams.weight" :max="1" :step="0.1" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      
      <!-- Settings Tab -->
      <el-tab-pane label="设置" name="settings">
        <div class="settings-panel" style="padding: 20px 0; max-width: 500px;">
          <el-form label-position="top">
            <el-form-item label="知识库名称">
              <el-input v-model="form.name" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="form.remark" type="textarea" :rows="3" />
            </el-form-item>
            <el-button type="primary" :loading="submitting" @click="onSubmit">保存更改</el-button>
            <el-divider />
            <el-button type="danger" plain @click="handleDelete">删除知识库</el-button>
          </el-form>
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
  DataLine, Cpu, Opportunity
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const route = useRoute();
const router = useRouter();

const kbId = ref(route.params.id);
const kbData = ref({});
const documents = ref([]);
const activeTab = ref('docs');
const searchKeyword = ref('');
const submitting = ref(false);
const form = reactive({ name: '', remark: '' });
const retrievalParams = reactive({ topK: 5, weight: 0.7 });

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

const loadKBData = () => {
  // Load from localStorage
  const allKBs = JSON.parse(localStorage.getItem('orin_mock_kbs') || '[]');
  const kb = allKBs.find(k => k.id === kbId.value);
  
  if (!kb) {
    ElMessage.error('知识库不存在');
    router.push('/dashboard/knowledge/list');
    return;
  }
  
  kbData.value = kb;
  form.name = kb.name;
  form.remark = kb.description || '';
  
  // Load documents if UNSTRUCTURED
  if (kb.type === 'UNSTRUCTURED') {
    const stored = localStorage.getItem(`orin_mock_docs_${kbId.value}`);
    if (stored) {
      documents.value = JSON.parse(stored);
    } else {
      generateMockDocs();
    }
  }
};

const generateMockDocs = () => {
  const docs = [];
  const names = [
    'Architecture_Orin_V2.pdf', 'Product_Manual_EN.docx', 'API_Reference_v1.md', 
    'Deployment_Guide_K8s.pdf', 'Security_Whitepaper.pdf', 'Q3_Roadmap.pptx'
  ];
  for(let i=0; i<8; i++) {
    docs.push({
      id: `doc-${Date.now()}-${i}`,
      name: names[i % names.length],
      mode: '自动',
      wordCount: Math.floor(Math.random() * 50000) + 1000,
      hitCount: Math.floor(Math.random() * 100),
      uploadTime: '2026-02-02 14:20',
      enabled: true,
      status: 'ready'
    });
  }
  documents.value = docs;
  localStorage.setItem(`orin_mock_docs_${kbId.value}`, JSON.stringify(docs));
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

const handleDelete = () => {
  ElMessageBox.confirm(`确认删除知识库 [${kbData.value.name}] 吗？`, '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    const allKBs = JSON.parse(localStorage.getItem('orin_mock_kbs') || '[]');
    const filtered = allKBs.filter(k => k.id !== kbId.value);
    localStorage.setItem('orin_mock_kbs', JSON.stringify(filtered));
    ElMessage.success('已删除');
    router.push('/dashboard/knowledge/list');
  });
};

const openDocument = (doc) => {
  router.push(`/dashboard/knowledge/${kbId.value}/document/${doc.id}`);
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
</style>
