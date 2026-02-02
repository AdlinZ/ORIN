<template>
  <div class="page-container">
    <PageHeader 
      title="知识智理架构" 
      description="ORIN 2026 琥珀流光 - 全领域知识资产的一站式管控与深度挖掘"
      icon="Reading"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增知识资产</el-button>
        <el-button :icon="Refresh" @click="fetchData">同步数据</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="knowledge-bento-container">
      <!-- Bento Grid Layout -->
      <div class="bento-grid">
        <!-- Unstructured Knowledge (Large Card) -->
        <div 
          v-for="kb in categories.unstructured" 
          :key="kb.id" 
          class="bento-item large"
          @click="openInspector(kb)"
        >
          <el-card shadow="hover" class="bento-card glass">
            <div class="pulse-dot"></div>
            <div class="card-type-tag">UNSTRUCTURED</div>
            <div class="card-main-content">
              <el-icon class="main-icon"><Document /></el-icon>
              <h3 class="kb-title">{{ kb.name }}</h3>
              <p class="kb-desc">{{ kb.description || '非结构化文档知识库' }}</p>
              
              <div class="stats-footer">
                <div class="stat-pill">
                  <span class="val">{{ kb.stats.documentCount || 0 }}</span>
                  <span class="lab">Documents</span>
                </div>
                <div class="stat-pill">
                  <span class="val">{{ formatNumber(kb.stats.chunkCount) }}</span>
                  <span class="lab">Chunks</span>
                </div>
              </div>
            </div>
          </el-card>
        </div>

        <!-- Structured Knowledge -->
        <div 
          v-for="kb in categories.structured" 
          :key="kb.id" 
          class="bento-item medium"
          @click="openInspector(kb)"
        >
          <el-card shadow="hover" class="bento-card">
            <div class="pulse-dot"></div>
            <div class="card-type-tag">STRUCTURED</div>
            <div class="card-compact-content">
               <el-icon class="type-icon"><DataLine /></el-icon>
               <div class="info">
                 <div class="title">{{ kb.name }}</div>
                 <div class="meta">{{ kb.stats.tableCount || 0 }} Tables Connected</div>
               </div>
            </div>
          </el-card>
        </div>

        <!-- Procedural Knowledge -->
        <div 
          v-for="kb in categories.procedural" 
          :key="kb.id" 
          class="bento-item medium"
          @click="openInspector(kb)"
        >
          <el-card shadow="hover" class="bento-card">
            <div class="pulse-dot"></div>
            <div class="card-type-tag">PROCEDURAL</div>
            <div class="card-compact-content">
               <el-icon class="type-icon"><Cpu /></el-icon>
               <div class="info">
                 <div class="title">{{ kb.name }}</div>
                 <div class="meta">{{ kb.stats.skillCount || 0 }} Skills Defined</div>
               </div>
            </div>
          </el-card>
        </div>

        <!-- Meta Memory -->
        <div 
          v-for="kb in categories.meta" 
          :key="kb.id" 
          class="bento-item medium"
          @click="openInspector(kb)"
        >
          <el-card shadow="hover" class="bento-card special-gradient">
            <div class="pulse-dot"></div>
            <div class="card-type-tag">META & MEMORY知识</div>
            <div class="card-compact-content">
               <el-icon class="type-icon"><Opportunity /></el-icon>
               <div class="info">
                 <div class="title">{{ kb.name }}</div>
                 <div class="meta">{{ kb.stats.memoryEntryCount || 0 }} Memory Pulsed</div>
               </div>
            </div>
          </el-card>
        </div>
      </div>
    </div>

    <!-- Inspector Pattern (Side Panel) -->
    <Transition name="slide-fade">
      <div v-if="inspectorVisible" class="knowledge-inspector">
        <div class="inspector-mask" @click="closeInspector"></div>
        <div class="inspector-content">
          <div class="inspector-header">
            <div class="type-badge">{{ selectedKB.type }}</div>
            <h2>{{ selectedKB.name }}</h2>
            <el-button circle :icon="Close" @click="closeInspector" class="close-btn" />
          </div>

          <div class="inspector-body">
            <el-tabs v-model="activeTab" class="inspector-tabs">
              <!-- Unstructured Detail -->
              <el-tab-pane v-if="selectedKB.type === 'UNSTRUCTURED'" label="文档管治" name="docs">
                <div class="doc-manager">
                  <div class="tool-bar">
                    <el-input placeholder="搜索文档..." :prefix-icon="Search" size="small" />
                    <el-button type="primary" size="small">上传</el-button>
                  </div>
                  <div class="doc-list-mini">
                    <div v-for="i in 5" :key="i" class="doc-row">
                      <el-icon><Document /></el-icon>
                      <span class="name">Architecture_Orin_V2.pdf</span>
                      <span class="status">Indexed</span>
                    </div>
                  </div>
                </div>
              </el-tab-pane>

              <!-- Structured Detail -->
              <el-tab-pane v-if="selectedKB.type === 'STRUCTURED'" label="SQL & 表结构" name="sql">
                 <div class="sql-preview">
                    <div class="schema-tree">
                       <div v-for="i in 3" :key="i" class="table-node">
                         <el-icon><Grid /></el-icon>
                         <span>user_behavior_logs_{{ i }}</span>
                       </div>
                    </div>
                    <div class="query-box">
                       <el-input type="textarea" :rows="4" placeholder="SELECT * FROM table..." />
                       <el-button type="primary" size="small" style="margin-top: 10px;">执行预览</el-button>
                    </div>
                 </div>
              </el-tab-pane>

              <!-- Procedural Detail -->
              <el-tab-pane v-if="selectedKB.type === 'PROCEDURAL'" label="技能/编排" name="workflow">
                 <div class="workflow-mini">
                    <el-empty description="工作流编辑器加载中..." :image-size="40" />
                 </div>
              </el-tab-pane>

              <!-- Meta Detail -->
              <el-tab-pane v-if="selectedKB.type === 'META_MEMORY'" label="记忆时间轴" name="memory">
                 <div class="memory-timeline">
                    <el-timeline>
                      <el-timeline-item
                        v-for="(m, index) in 5"
                        :key="index"
                        timestamp="2026-01-26 10:20"
                        color="var(--orin-amber)"
                      >
                        用户偏好: 偏好使用深色模式琥珀视觉
                      </el-timeline-item>
                    </el-timeline>
                 </div>
              </el-tab-pane>

              <el-tab-pane label="检索参数" name="retrieval">
                 <el-form label-position="top">
                    <el-form-item label="Top K">
                      <el-slider v-model="retrievalParams.topK" :max="20" />
                    </el-form-item>
                    <el-form-item label="语义权重">
                      <el-slider v-model="retrievalParams.weight" :max="1" :step="0.1" />
                    </el-form-item>
                 </el-form>
              </el-tab-pane>
            </el-tabs>
          </div>
          
          <div class="inspector-footer">
            <el-button type="danger" plain @click="handleDelete(selectedKB)">删除知识资产</el-button>
            <el-button type="primary" @click="handleEdit(selectedKB)">基础配置</el-button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Base Edit Dialog -->
    <el-dialog v-model="dialogVisible" title="知识资产配置" width="500px">
      <el-form :model="form" label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model.trim="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue';
import { 
  Plus, Refresh, Document, Search, Close, 
  DataLine, Cpu, Opportunity, Edit, Delete, Grid 
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

const loading = ref(false);
const inspectorVisible = ref(false);
const selectedKB = ref(null);
const activeTab = ref('docs');
const rawData = ref([]);
const total = ref(0);
const dialogVisible = ref(false);
const dialogType = ref('add');
const submitting = ref(false);
const form = reactive({ name: '', remark: '' });
const retrievalParams = reactive({ topK: 5, weight: 0.7 });

// API Endpoint - Unified knowledge
const fetchData = async () => {
  loading.value = true;
  try {
    // In a real app, agentId would come from state or route
    // Here we use a fallback or placeholder
    const agentId = 'default-agent'; 
    const res = await request.get(`/knowledge/agents/${agentId}/unified`);
    rawData.value = res || [];
    total.value = rawData.value.length;
  } catch (e) {
    console.warn('Backend API not ready or failed, using mock data for demo', e);
    generateMockData();
  } finally {
    loading.value = false;
  }
};

const generateMockData = () => {
  rawData.value = [
    {
      id: 'kb-1',
      name: '核心产品文档库',
      description: '包含 ORIN 2026 技术规格说明、用户手册及接入指南。',
      type: 'UNSTRUCTURED',
      status: 'ENABLED',
      stats: { documentCount: 124, chunkCount: 45200 }
    },
    {
      id: 'kb-2',
      name: '生产运行数据库',
      description: '实时同步生产环境的核心业务表结构与元数据。',
      type: 'STRUCTURED',
      status: 'ENABLED',
      stats: { tableCount: 18 }
    },
    {
      id: 'kb-3',
      name: '标准作业 SOP 集',
      description: '自动化执行流程与专家经验的程序化抽象。',
      type: 'PROCEDURAL',
      status: 'ENABLED',
      stats: { skillCount: 42 }
    },
    {
      id: 'kb-4',
      name: '用户画像与意图记忆',
      description: '基于多轮对话动态沉淀的用户长期偏好记忆。',
      type: 'META_MEMORY',
      status: 'ENABLED',
      stats: { memoryEntryCount: 1258 }
    }
  ];
};

const categories = computed(() => ({
  unstructured: rawData.value.filter(kb => kb.type === 'UNSTRUCTURED'),
  structured: rawData.value.filter(kb => kb.type === 'STRUCTURED'),
  procedural: rawData.value.filter(kb => kb.type === 'PROCEDURAL'),
  meta: rawData.value.filter(kb => kb.type === 'META_MEMORY')
}));

const openInspector = (kb) => {
  selectedKB.value = kb;
  // Dynamic default tab
  if (kb.type === 'UNSTRUCTURED') activeTab.value = 'docs';
  else if (kb.type === 'STRUCTURED') activeTab.value = 'sql';
  else if (kb.type === 'PROCEDURAL') activeTab.value = 'workflow';
  else activeTab.value = 'memory';
  
  inspectorVisible.value = true;
};

const closeInspector = () => {
  inspectorVisible.value = false;
};

const handleAdd = () => {
  dialogType.value = 'add';
  form.name = '';
  form.remark = '';
  dialogVisible.value = true;
};

const handleEdit = (kb) => {
  dialogType.value = 'edit';
  form.name = kb.name;
  form.remark = kb.description || '';
  dialogVisible.value = true;
};

const onSubmit = async () => {
  submitting.value = true;
  setTimeout(() => {
    ElMessage.success('配置已保存');
    submitting.value = false;
    dialogVisible.value = false;
    fetchData();
  }, 800);
};

const handleDelete = (kb) => {
  ElMessageBox.confirm(`确认删除知识资产 [${kb.name}] 吗？此操作不可撤销。`, '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    ElMessage.success('已移除知识资产');
    closeInspector();
    fetchData();
  });
};

const formatNumber = (num) => {
  if (!num) return '0';
  return num > 1000 ? (num / 1000).toFixed(1) + 'k' : num;
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.knowledge-bento-container {
  padding: 24px 0;
}

.bento-card {
  height: 100%;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  display: flex;
  flex-direction: column;
}

.bento-card.glass {
  background: linear-gradient(135deg, rgba(255,255,255,0.7), rgba(255,255,255,0.3)) !important;
  backdrop-filter: blur(10px);
}

.bento-card.special-gradient {
  background: linear-gradient(135deg, #FFF9F0, #FFFFFF) !important;
}

.card-type-tag {
  font-size: 10px;
  font-weight: 800;
  color: var(--orin-amber);
  opacity: 0.6;
  letter-spacing: 1px;
  margin-bottom: 12px;
}

/* Large Card Style */
.card-main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.main-icon {
  font-size: 48px;
  color: var(--orin-amber);
  margin-bottom: 20px;
}

.kb-title {
  font-size: 22px;
  font-weight: 800;
  color: var(--neutral-gray-900);
  margin: 0 0 12px 0;
}

.kb-desc {
  font-size: 14px;
  color: var(--neutral-gray-500);
  line-height: 1.6;
  margin-bottom: 24px;
}

.stats-footer {
  margin-top: auto;
  display: flex;
  gap: 20px;
}

.stat-pill {
  display: flex;
  flex-direction: column;
}

.stat-pill .val {
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.stat-pill .lab {
  font-size: 11px;
  color: var(--neutral-gray-400);
  text-transform: uppercase;
}

/* Compact Card Style */
.card-compact-content {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-top: 10px;
}

.type-icon {
  font-size: 32px;
  color: var(--orin-amber);
}

.card-compact-content .title {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-gray-900);
  margin-bottom: 4px;
}

.card-compact-content .meta {
  font-size: 12px;
  color: var(--neutral-gray-500);
}

/* Inspector Styles */
.knowledge-inspector {
  position: fixed;
  top: 0;
  right: 0;
  width: 100%;
  height: 100vh;
  z-index: 2000;
  display: flex;
  justify-content: flex-end;
}

.inspector-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(4px);
}

.inspector-content {
  position: relative;
  width: 600px;
  height: 100%;
  background: white;
  box-shadow: -10px 0 50px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
}

.inspector-header {
  padding: 32px;
  border-bottom: 1px solid var(--neutral-gray-100);
}

.type-badge {
  display: inline-block;
  padding: 4px 12px;
  background: var(--orin-amber-glow);
  color: var(--orin-amber);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 800;
  margin-bottom: 12px;
}

.inspector-header h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
}

.close-btn {
  position: absolute;
  top: 24px;
  right: 24px;
}

.inspector-body {
  flex: 1;
  padding: 0 32px;
  overflow-y: auto;
}

.inspector-tabs {
  margin-top: 20px;
}

.inspector-footer {
  padding: 24px 32px;
  border-top: 1px solid var(--neutral-gray-100);
  display: flex;
  justify-content: space-between;
}

/* Sub-Managers */
.doc-manager { padding: 16px 0; }
.tool-bar { display: flex; gap: 12px; margin-bottom: 20px; }
.doc-row { 
  display: flex; 
  align-items: center; 
  gap: 12px; 
  padding: 12px; 
  border-bottom: 1px solid var(--neutral-gray-50); 
  font-size: 13px;
}

.sql-preview { padding: 16px 0; }
.table-node { 
  display: flex; 
  align-items: center; 
  gap: 10px; 
  margin-bottom: 12px; 
  font-size: 13px;
  color: var(--neutral-gray-600);
}
</style>
