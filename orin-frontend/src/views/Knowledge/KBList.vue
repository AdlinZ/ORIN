<template>
  <div class="page-container">
    <PageHeader 
      title="知识库" 
      description="管理和编排您的知识资产，为 AI 智能体提供上下文支持。"
      icon="Reading"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="fetchData">同步数据</el-button>
      </template>
    </PageHeader>

    <div v-loading="loading" class="knowledge-grid-container">
      <!-- Uniform Grid Layout (Dify Style) -->
      <div class="kb-grid">
        
        <!-- 1. Create Knowledge Card -->
        <div class="kb-grid-item create-card" @click="handleAdd">
          <div class="create-content">
            <div class="create-icon">
              <el-icon><Plus /></el-icon>
            </div>
            <span class="create-text">创建知识库</span>
          </div>
        </div>

        <!-- 2. Knowledge List Loop -->
        <div 
          v-for="kb in allKBs" 
          :key="kb.id" 
          class="kb-grid-item"
          @click="openInspector(kb)"
        >
          <el-card shadow="hover" class="kb-card">
            <!-- Header: Icon + Title + More -->
            <div class="kb-header">
              <div class="icon-wrapper" :class="getIconClass(kb.type)">
                <el-icon><component :is="getIcon(kb.type)" /></el-icon>
              </div>
              <div class="kb-info">
                 <h3 class="kb-name text-ellipsis">{{ kb.name }}</h3>
              </div>
              <div class="kb-more" @click.stop>
                <el-dropdown trigger="click">
                   <el-icon class="more-btn"><MoreFilled /></el-icon>
                   <template #dropdown>
                      <el-dropdown-menu>
                         <el-dropdown-item @click="handleEdit(kb)">设置</el-dropdown-item>
                         <el-dropdown-item @click="handleDelete(kb)" divided class="text-danger">删除</el-dropdown-item>
                      </el-dropdown-menu>
                   </template>
                </el-dropdown>
              </div>
            </div>

            <!-- Body: Description -->
            <div class="kb-body">
               <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
            </div>

            <!-- Footer: Meta Info -->
            <div class="kb-footer">
               <div class="kb-tags">
                  <el-tag size="small" effect="plain" class="type-tag">{{ getTypeName(kb.type) }}</el-tag>
               </div>
               <div class="kb-stats">
                  <span v-if="kb.type === 'UNSTRUCTURED'">{{ kb.stats.documentCount || 0 }} 文档</span>
                  <span v-else-if="kb.type === 'STRUCTURED'">{{ kb.stats.tableCount || 0 }} 表</span>
                  <span v-else-if="kb.type === 'PROCEDURAL'">{{ kb.stats.skillCount || 0 }} 技能</span>
                  <span v-else>{{ kb.stats.memoryEntryCount || 0 }} 条记忆</span>
               </div>
            </div>
          </el-card>
        </div>

      </div>
    </div>

    <!-- Inspector / Drawer (Detailed View) -->
    <Transition name="slide-fade">
      <div v-if="inspectorVisible" class="knowledge-inspector">
        <div class="inspector-mask" @click="closeInspector"></div>
        <div class="inspector-content">
          <div class="inspector-header">
            <div class="header-breadcrumb">
               <span class="back-link" @click="closeInspector">知识库列表</span>
               <span class="separator">/</span>
               <span class="current">{{ selectedKB.name }}</span>
            </div>
            <div class="header-actions">
              <el-button circle :icon="Close" @click="closeInspector" class="close-btn-simple" />
            </div>
          </div>

          <div class="inspector-body">
             <div class="kb-detail-intro">
                <div class="icon-big" :class="getIconClass(selectedKB.type)">
                  <el-icon><component :is="getIcon(selectedKB.type)" /></el-icon>
                </div>
                <div class="intro-text">
                   <h2>{{ selectedKB.name }}</h2>
                   <p>{{ selectedKB.description }}</p>
                </div>
             </div>

            <el-tabs v-model="activeTab" class="inspector-tabs Dify-tabs">
              <!-- Unstructured Detail (Document Table) -->
              <el-tab-pane v-if="selectedKB.type === 'UNSTRUCTURED'" label="文档" name="docs">
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

                  <!-- Dify-like Table -->
                  <el-table border :data="mockDocs" style="width: 100%" class="dify-table">
                     <el-table-column type="selection" width="40" />
                     <el-table-column label="#" width="60">
                        <template #default="scope">{{ scope.$index + 1 }}</template>
                     </el-table-column>
                     <el-table-column label="名称" min-width="250">
                        <template #default="{ row }">
                           <div class="doc-name-cell">
                              <el-icon class="file-icon"><Document /></el-icon>
                              <span class="name text-ellipsis" :title="row.name">{{ row.name }}</span>
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
                 <el-form label-position="top" style="max-width: 400px; margin-top: 20px;">
                    <el-form-item label="Top K">
                      <el-slider v-model="retrievalParams.topK" :max="20" />
                    </el-form-item>
                    <el-form-item label="语义权重">
                      <el-slider v-model="retrievalParams.weight" :max="1" :step="0.1" />
                    </el-form-item>
                 </el-form>
              </el-tab-pane>
              
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
                        <el-button type="danger" plain @click="handleDelete(selectedKB)">删除知识库</el-button>
                     </el-form>
                  </div>
              </el-tab-pane>
            </el-tabs>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Base Edit Dialog (Only for quick edit if needed, but primary is navigation) -->
    <el-dialog v-model="dialogVisible" title="知识库设置" width="500px">
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
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  Plus, Refresh, Document, Search, Close, 
  DataLine, Cpu, Opportunity, Edit, Delete, Grid, MoreFilled,
  ArrowDown, Setting
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '@/utils/request';

const router = useRouter();
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
const searchKeyword = ref('');
const mockDocs = ref([]);

// API Endpoint - Unified knowledge
const fetchData = async () => {
  loading.value = true;
  try {
     const res = await request.get('/knowledge/list');
     if (Array.isArray(res)) {
        rawData.value = res.map(kb => ({
            ...kb,
            // Map backend fields to frontend expected fields if needed
            type: kb.type || 'UNSTRUCTURED',
            stats: kb.stats || { documentCount: kb.docCount || 0 }
        }));
     }
  } catch (e) {
    console.error('Failed to load KB list:', e);
    ElMessage.error('加载知识库列表失败');
  } finally {
    loading.value = false;
  }
};

// Mock data generation removed

const getIcon = (type) => {
  if (type === 'UNSTRUCTURED') return Document;
  if (type === 'STRUCTURED') return DataLine;
  if (type === 'PROCEDURAL') return Cpu;
  if (type === 'META_MEMORY') return Opportunity;
  return Document;
}

const getIconClass = (type) => {
  if (type === 'UNSTRUCTURED') return 'icon-blue';
  if (type === 'STRUCTURED') return 'icon-green';
  if (type === 'PROCEDURAL') return 'icon-purple';
  if (type === 'META_MEMORY') return 'icon-amber';
  return 'icon-default';
}

const getTypeName = (type) => {
   if (type === 'UNSTRUCTURED') return '通用';
   if (type === 'STRUCTURED') return '结构化';
   if (type === 'PROCEDURAL') return 'Workflow';
   if (type === 'META_MEMORY') return 'Memory';
   return '未知';
}

const allKBs = computed(() => rawData.value);

const generateMockDocs = (kb) => {
   // Try to load first
   const stored = localStorage.getItem(`orin_mock_docs_${kb.id}`);
   if (stored) {
      mockDocs.value = JSON.parse(stored);
      return;
   }

   // Generate fresh if not found
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
   mockDocs.value = docs;
   
   // Save so they persist
   localStorage.setItem(`orin_mock_docs_${kb.id}`, JSON.stringify(docs));
};

const openInspector = (kb) => {
  // Navigate to detail page instead of opening drawer
  router.push(`/dashboard/resources/knowledge/detail/${kb.id}`);
};

const closeInspector = () => {
  inspectorVisible.value = false;
  fetchData(); // Refresh to show updates
};

const handleAdd = () => {
  router.push(ROUTES.RESOURCES.KNOWLEDGE_CREATE);
};

const handleEdit = (kb) => {
  selectedKB.value = kb;
  dialogType.value = 'edit';
  form.name = kb.name;
  form.remark = kb.description || '';
  dialogVisible.value = true;
};

// Update existing KB
const onSubmit = async () => {
  submitting.value = true;
  try {
    const payload = {
        name: form.name,
        description: form.remark,
        // Include other fields if your API expects them
    };
    
    // Replace with real PUT API if exists, or common pattern
    // Note: Based on knowledgeManageService.java, updateStatus is available but 
    // full update might need a new endpoint or using create with ID if it overwrites.
    // For now, let's assume standard REST PUT /knowledge/{id}
    await request.put(`/knowledge/${selectedKB.value.id}`, payload);
    
    ElMessage.success('配置已更新');
    dialogVisible.value = false;
    fetchData(); // Refresh list
  } catch (err) {
    ElMessage.error('更新失败: ' + err.message);
  } finally {
    submitting.value = false;
  }
};

const handleDelete = (kb) => {
  selectedKB.value = kb; // Ensure selectedKB is set if deleting from list
  ElMessageBox.confirm(`确认删除知识资产 [${kb.name}] 吗？`, '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    try {
        await request.delete(`/knowledge/${kb.id}`);
        ElMessage.success('已移除知识资产');
        closeInspector();
        fetchData();
    } catch (err) {
        ElMessage.error('删除失败: ' + err.message);
    }
  });
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
/* Reuse styles from previous step */
.knowledge-grid-container {
  padding: 24px 0;
}

/* Grid Layout */
.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.kb-grid-item.create-card {
  cursor: pointer;
  border: 1px dashed var(--neutral-gray-300);
  background: rgba(255,255,255,0.02);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 180px; 
  transition: all 0.3s ease;
}

.kb-grid-item.create-card:hover {
  border-color: var(--orin-amber);
  background: var(--neutral-gray-50);
}

.create-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--neutral-gray-500);
}

.create-icon {
  font-size: 24px;
}

.create-text {
  font-size: 14px;
  font-weight: 500;
}

/* KB Card */
.kb-card {
  height: 100%;
  cursor: pointer;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 10px;
  transition: all 0.2s ease;
  background: var(--surface-color, #fff); 
}

.kb-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.08);
  border-color: var(--orin-amber-glow);
}

.kb-card :deep(.el-card__body) {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

/* Header */
.kb-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.icon-wrapper {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

/* Icons */
.icon-blue { background: #E0F2FE; color: #0284C7; }
.icon-green { background: #DCFCE7; color: #16A34A; }
.icon-purple { background: #F3E8FF; color: #9333EA; }
.icon-amber { background: #FEF3C7; color: #D97706; }
.icon-default { background: #F3F4F6; color: #4B5563; }

.kb-info {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.kb-name {
  font-size: 16px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin: 0;
  line-height: 1.4;
}

.text-ellipsis {
 white-space: nowrap;
 overflow: hidden;
 text-overflow: ellipsis;
}

.kb-more {
  color: var(--neutral-gray-400);
  padding: 4px;
  border-radius: 4px;
}
.kb-more:hover {
  background: var(--neutral-gray-100);
}

.kb-body {
  flex: 1;
  margin-bottom: 20px;
}

.kb-desc {
  font-size: 13px;
  color: var(--neutral-gray-500);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin: 0;
}

.kb-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid var(--neutral-gray-100);
  padding-top: 12px;
  margin-top: auto;
}

.kb-stats {
  font-size: 12px;
  color: var(--neutral-gray-400);
}

.text-danger { color: #DC2626; }

/* Inspector (Detailed View) */
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
  width: 1000px; /* Wider for table */
  max-width: 90vw;
  height: 100%;
  background: white;
  box-shadow: -10px 0 50px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
}

.inspector-header {
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid var(--neutral-gray-200);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
}

.header-breadcrumb {
   font-size: 14px;
   color: var(--neutral-gray-500);
   display: flex;
   align-items: center;
   gap: 8px;
}

.back-link {
   cursor: pointer;
}
.back-link:hover { color: var(--orin-blue); }

.current {
   font-weight: 600;
   color: var(--neutral-gray-800);
}

.inspector-body {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
  background: #FCFCFD; /* Dify style bg */
}

/* Detail Intro */
.kb-detail-intro {
   display: flex;
   gap: 16px;
   margin-bottom: 24px;
}

.icon-big {
   width: 48px;
   height: 48px;
   border-radius: 10px;
   display: flex;
   align-items: center;
   justify-content: center;
   font-size: 24px;
}

.intro-text h2 {
   margin: 0 0 4px 0;
   font-size: 20px;
   font-weight: 700;
   color: var(--neutral-gray-900);
}

.intro-text p {
   margin: 0;
   font-size: 13px;
   color: var(--neutral-gray-500);
}

/* Tool Bar */
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

/* Table styles for Dify look */
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
}

.file-icon {
   color: var(--neutral-gray-400);
   font-size: 16px;
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
