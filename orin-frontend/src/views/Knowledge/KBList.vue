<template>
  <div class="page-container">
    <PageHeader 
      title="知识库管理" 
      description="构建专属于您的 AI 知识资产，支持多种文档格式接入与智能索引"
      icon="Reading"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增知识库</el-button>
        <el-button type="danger" plain :icon="Delete" :disabled="!selectedRows.length" :loading="batchDeleteLoading" @click="handleBatchDelete">批量删除</el-button>
      </template>
      <template #filters>
        <el-input 
          v-model="searchQuery" 
          placeholder="关键词搜索..." 
          :prefix-icon="Search" 
          clearable 
          class="search-input"
        />
      </template>
    </PageHeader>


    <!-- KB Stats -->
    <el-row :gutter="24" class="kb-stats" style="margin-bottom: 24px;">
      <el-col :span="8">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="text-secondary" style="margin-bottom: 8px;">知识库总量</div>
          <div class="page-title" style="margin-bottom: 0;">{{ total }} <small class="text-secondary" style="font-weight: 400; font-size: 14px;">个</small></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="text-secondary" style="margin-bottom: 8px;">累计文档数</div>
          <div class="page-title" style="margin-bottom: 0;">{{ totalDocs }} <small class="text-secondary" style="font-weight: 400; font-size: 14px;">份</small></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="text-secondary" style="margin-bottom: 8px;">存量字符数</div>
          <div class="page-title" style="margin-bottom: 0;">{{ totalChars }} <small class="text-secondary" style="font-weight: 400; font-size: 14px;">万</small></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <ResizableTable 
        v-loading="loading" 
        :data="tableData"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column type="index" label="序号" width="80" align="center" />
        
        <el-table-column prop="name" label="知识库名称" min-width="200">
           <template #default="{ row }">
             <div class="kb-name">
                <el-icon><Reading /></el-icon>
                <span style="margin-left: 8px">{{ row.name }}</span>
             </div>
           </template>
        </el-table-column>

        <el-table-column prop="docCount" label="文档数" width="120" align="center">
           <template #default="{ row }">
             <span style="font-weight: 600; font-size: 15px;">{{ row.docCount }}</span>
           </template>
        </el-table-column>
        
        <el-table-column prop="charCount" label="总字符数" width="150" align="center">
           <template #default="{ row }">
              <span style="font-weight: 600; font-size: 15px;">{{ (row.charCount / 1000).toFixed(1) }}k</span>
           </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="180" align="center" />

        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
             <el-button link type="primary" :icon="Search" @click="handleRecallTest(row)">召回测试</el-button>
             <el-button link type="primary" :icon="Document" @click="handleViewDocs(row)">查看文档</el-button>
             <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
             <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </ResizableTable>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
        />
      </div>
    </el-card>

    <!-- Add/Edit Dialog (Redesigned) -->
    <el-dialog v-model="dialogVisible" :title="dialogType === 'add' ? '新增知识库' : '编辑知识库'" width="500px" class="kb-dialog">
      <el-form :model="form" label-position="top" class="kb-form">
        <el-form-item label="名称" required>
          <el-input v-model.trim="form.name" placeholder="输入知识库名称，如：产品说明书" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input 
            v-model="form.remark" 
            type="textarea" 
            :rows="4" 
            placeholder="简要描述知识库的用途（选填）"
            resize="none"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="kb-dialog-footer">
          <el-button @click="dialogVisible = false" link>取消</el-button>
          <el-button class="kb-save-btn" @click="onSubmit" :loading="submitting">确认保存</el-button>
        </div>
      </template>
    </el-dialog>


    <!-- View Documents Dialog (Redesigned) -->
    <el-dialog v-model="docViewerVisible" title="文档列表" width="850px" class="doc-list-dialog">
      <div class="doc-upload-bar">
        <span class="label">文件上传</span>
        <el-upload
          action="#"
          :auto-upload="false"
          :on-change="handleFileSelect"
          :show-file-list="false"
          accept=".pdf,.docx,.txt"
          class="inline-upload"
        >
          <div class="custom-upload-trigger">
            <button class="select-btn">选择文件</button>
            <span class="file-name">{{ selectedFileName || '未选择文件' }}</span>
          </div>
        </el-upload>
        <el-button class="add-doc-btn" @click="handleDocUpload" :disabled="!selectedFileName">新增</el-button>
      </div>

      <ResizableTable :data="mockDocs" table-class="doc-table" v-loading="docLoading">
        <el-table-column prop="filename" label="名称" min-width="280" />
        <el-table-column prop="charCount" label="字符数" width="120" align="center" />
        <el-table-column prop="createTime" label="创建时间" width="200" align="center" />
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <el-button link type="primary" class="operation-link green" @click="() => ElMessage.info('查看分段中...')">查看分段</el-button>
            <el-button link type="danger" class="operation-link red" @click="() => ElMessage.warning('模拟删除文档')">删除</el-button>
          </template>
        </el-table-column>
      </ResizableTable>
    </el-dialog>

    <!-- Recall Test Dialog (Redesigned) -->
    <el-dialog v-model="recallVisible" :title="currentKB ? `${currentKB.name}... 的召回测试` : '召回测试'" width="1000px" custom-class="recall-dialog">
      <div class="recall-layout">
        <!-- Left: Settings Panel -->
        <div class="settings-panel">
          <el-form label-width="120px" label-position="right">
            <el-form-item label="检索类型方式">
              <el-select v-model="recallParams.type" style="width: 100%">
                <el-option label="混合检索" value="hybrid" />
                <el-option label="语义检索" value="semantic" />
                <el-option label="全文检索" value="fulltext" />
              </el-select>
            </el-form-item>
            
            <el-form-item label="检索描述">
              <el-input 
                value="索引文档中的所有词汇，从而允许用户查询任意词汇，并返..." 
                disabled 
                placeholder="自动生成说明"
              />
            </el-form-item>

            <el-form-item label="Top K">
              <el-input-number v-model="recallParams.topK" :min="1" :max="50" style="width: 100%" />
            </el-form-item>

            <el-form-item label="Score 阈值">
              <div style="display: flex; align-items: center; gap: 4px; width: 100%;">
                <el-switch v-model="recallParams.thresholdEnabled" />
                <el-input-number 
                  v-model="recallParams.threshold" 
                  :min="0" :max="1" :step="0.1" 
                  :disabled="!recallParams.thresholdEnabled" 
                  style="flex: 1"
                />
              </div>
            </el-form-item>

            <el-form-item label="语义">
              <el-input-number v-model="recallParams.semanticWeight" :min="0" :max="1" :step="0.1" style="width: 100%" />
            </el-form-item>

            <el-form-item label="关键词">
              <el-input-number v-model="recallParams.keywordWeight" :min="0" :max="1" :step="0.1" style="width: 100%" />
            </el-form-item>

            <el-form-item label="源文本">
              <el-input 
                v-model="recallParams.query" 
                type="textarea" 
                :rows="6" 
                placeholder="请输入需要检索的文本"
                @keyup.enter.ctrl="doRecall"
              />
            </el-form-item>

            <div style="padding-left: 120px;">
              <el-button class="test-btn" :loading="recalling" @click="doRecall">测试</el-button>
            </div>
          </el-form>
        </div>

        <!-- Right: Result Display -->
        <div class="result-panel">
          <div class="result-summary" v-if="recallResults.length > 0">
            {{ recallResults.length }}个召回段落
          </div>
          <div v-if="recallResults.length > 0" class="segment-list">
            <div v-for="(res, i) in recallResults" :key="i" class="segment-item">
              <div class="segment-header">
                <div class="meta">
                   位置: {{ res.position }} —— {{ res.content.length }}字符
                </div>
                <div class="score-badge">SCORE {{ res.score }}</div>
              </div>
              <div class="segment-content">
                {{ res.content }}
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无召回结果，请填写源文本并点击测试" :image-size="60" />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue';
import { Plus, Delete, Download, Reading, Document, Edit, Search } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import ResizableTable from '@/components/ResizableTable.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getKnowledgeList, addKnowledge, deleteKnowledge } from '@/api/knowledge';

const loading = ref(false);
const batchDeleteLoading = ref(false);
const docLoading = ref(false);
const uploading = ref(false);
const segmenting = ref(false);
const rawData = ref([]);
const searchQuery = ref('');
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(10);
const selectedRows = ref([]);

const dialogVisible = ref(false);
const docViewerVisible = ref(false);
const recallVisible = ref(false);
const currentKB = ref(null);
const recalling = ref(false);
const recallParams = reactive({
  type: 'hybrid',
  topK: 3,
  thresholdEnabled: false,
  threshold: 0.5,
  semanticWeight: 0.7,
  keywordWeight: 0.3,
  query: ''
});
const recallResults = ref([]);
const dialogType = ref('add');
const submitting = ref(false);
const form = reactive({ name: '', remark: '' });
const selectedFileName = ref('');

const mockDocs = ref([
  { filename: '【系统名称】分工维护记录表（优化版...）', charCount: 1986, createTime: '2025-12-17 15:20:11' },
  { filename: '成都数智凌云公司管理制度.docx', charCount: 1917, createTime: '2025-12-17 14:33:29' }
]);

const handleViewDocs = (row) => {
  currentKB.value = row;
  docViewerVisible.value = true;
};

const handleFileSelect = (file) => {
  const isTypeOk = /\.(pdf|docx|txt)$/i.test(file.name);
  const isSizeOk = file.size / 1024 / 1024 < 100;
  
  if (!isTypeOk) return ElMessage.error('仅支持 pdf, docx, txt 格式');
  if (!isSizeOk) return ElMessage.error('文件大小不能超过 100MB');

  selectedFileName.value = file.name;
};

const handleDocUpload = () => {
    uploading.value = true;
    setTimeout(() => {
        ElMessage.success(`文件 ${selectedFileName.value} 上传成功`);
        mockDocs.value.unshift({
            filename: selectedFileName.value,
            charCount: Math.floor(Math.random() * 2000) + 500,
            createTime: new Date().toLocaleString()
        });
        selectedFileName.value = '';
        uploading.value = false;
    }, 1000);
};

const handleGenerateSegment = (row) => {
  segmenting.value = true;
  setTimeout(() => {
    ElMessage.success('分段生成成功，共生成 42 条分段');
    segmenting.value = false;
  }, 2000);
};
const handleRecallTest = (row) => {
  currentKB.value = row;
  recallVisible.value = true;
  recallResults.value = [];
  recallParams.query = '';
};

const doRecall = () => {
  if (!recallParams.query) return ElMessage.warning('请输入源文本进行测试');
  recalling.value = true;
  setTimeout(() => {
    recallResults.value = [
      { 
        position: 4, 
        score: '0.65', 
        content: `字段顺序优化：
按 “时间→代码信息→更新内容→部署状态” 逻辑排序，符合维护流程（先确定分支/版本，再记录更新和部署），填写更顺畅。
补充实用字段：
基础信息新增“故障应急联系人”，应对突发问题时快速对接，提升响应效率。
三、Excel 专属设置补充（针对新增字段）
1.「代码有无更新」下拉框设置
选中列 E（代码有无更新）的单元格区域（如 E8:E20）；
「数据」→「数据验证」→「允许」选「序列」→「来源」输入：是, 否（英文逗号）；
确定后，单元格点击即可选择，避免手动输入错误。
2. 新增字段格式设置
代码分支/版本号：文本格式（右键→设置单元格格式→文本），支持输入字母+数字组合：
版本号统一规范：建议按 “V 主版本.次版本。修订号” 填写（如 V3.2.1），便于排序和回溯。
3. 批量调整整列宽
选中所有列（点击列标 A 左侧空白处）→双击任意两列之间的分隔线，自动适配所有内容宽度，无需手动调整。` 
      },
      { 
        position: 8, 
        score: '0.42', 
        content: '这是第二条检索结果示例内容，用于演示多段落召回后的排列效果。系统会根据 Score 阈值和匹配度智能排序...' 
      }
    ];
    recalling.value = false;
  }, 800);
};

const tableData = computed(() => {
  if (!searchQuery.value) return rawData.value;
  return rawData.value.filter(item => item.name.toLowerCase().includes(searchQuery.value.toLowerCase()));
});

const totalDocs = computed(() => rawData.value.reduce((acc, cur) => acc + cur.docCount, 0));
const totalChars = computed(() => (rawData.value.reduce((acc, cur) => acc + cur.charCount, 0) / 10000).toFixed(1));

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getKnowledgeList();
    rawData.value = res.data.list;
    total.value = res.data.total;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSelectionChange = (val) => {
  selectedRows.value = val;
};

const handleAdd = () => {
  dialogType.value = 'add';
  form.name = '';
  form.remark = '';
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  dialogType.value = 'edit';
  form.name = row.name;
  form.remark = row.remark || '';
  dialogVisible.value = true;
};

const onSubmit = async () => {
  if (!form.name) {
    ElMessage.warning('请输入名称');
    return;
  }
  submitting.value = true;
  try {
    await addKnowledge(form);
    ElMessage.success('操作成功');
    dialogVisible.value = false;
    fetchData();
  } finally {
    submitting.value = false;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm('确认删除该知识库吗？', '提示', { type: 'warning' })
    .then(async () => {
      await deleteKnowledge(row.id);
      ElMessage.success('删除成功');
      fetchData(); // In real app, re-fetch
    });
};

const handleBatchDelete = () => {
    ElMessage.info('批量删除功能演示');
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.page-container {
  padding: 0;
}
.kb-stats {
  margin-bottom: 20px;
}
.stat-mini-card {
  background: var(--neutral-white);
  padding: 15px 20px;
  border-radius: 8px;
  border: 1px solid var(--neutral-gray-2);
}
.stat-mini-card .label {
  font-size: 13px;
  color: var(--neutral-gray-4);
  margin-bottom: 5px;
}
.stat-mini-card .value {
  font-size: 20px;
  font-weight: 600;
  color: var(--neutral-black);
}
.action-bar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.kb-name {
    display: flex;
    align-items: center;
    font-weight: 500;
    color: var(--neutral-black);
}
.search-input {
  width: 240px;
  margin-right: 12px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.res-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.res-source {
  font-size: 12px;
  color: var(--neutral-gray-4);
  font-family: var(--font-heading);
}

.res-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--neutral-gray-600);
  margin: 0;
}

/* Recall Dialog Custom Styles */
.recall-layout {
  display: flex;
  gap: 32px;
  min-height: 500px;
}

.settings-panel {
  flex: 0 0 420px;
  border-right: 1px solid var(--neutral-gray-100);
  padding-right: 32px;
}

.result-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.test-btn {
  background-color: #a8c69f;
  border-color: #a8c69f;
  color: white;
  width: 80px;
  height: 36px;
  font-weight: 500;
}

.test-btn:hover {
  background-color: #97b58e;
  border-color: #97b58e;
  color: white;
}

.result-summary {
  font-size: 16px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin-bottom: 20px;
}

.segment-item {
  background: #fcfcfc;
  border: 1px solid var(--neutral-gray-200);
  border-radius: 8px;
  margin-bottom: 20px;
  overflow: hidden;
}

.segment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--neutral-gray-100);
  background: #fafafa;
}

.segment-header .meta {
  font-size: 13px;
  color: var(--neutral-gray-500);
}

.score-badge {
  background: #409eff;
  color: white;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 4px;
}

.segment-content {
  padding: 16px;
  font-size: 14px;
  line-height: 1.8;
  color: var(--neutral-gray-700);
  white-space: pre-wrap;
  font-family: 'Inter', system-ui, sans-serif;
}

/* Document List Dialog Custom Styles */
.doc-upload-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 32px;
  font-size: 14px;
}

.doc-upload-bar .label {
  color: var(--neutral-gray-600);
}

.custom-upload-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--neutral-gray-300);
  border-radius: 4px;
  padding: 2px;
  background: white;
}

.select-btn {
  background: #f5f5f5;
  border: 1px solid var(--neutral-gray-300);
  padding: 4px 12px;
  border-radius: 2px;
  cursor: pointer;
  font-size: 13px;
  color: var(--neutral-gray-900);
}

.file-name {
  color: var(--neutral-gray-500);
  padding-right: 12px;
  font-size: 13px;
}

.add-doc-btn {
  border-color: var(--neutral-gray-200);
  color: var(--neutral-gray-400);
  font-size: 13px;
  height: 32px;
}

.add-doc-btn:not(:disabled) {
  color: var(--primary-color);
  border-color: var(--primary-light);
}

.doc-table :deep(th) {
  background-color: transparent !important;
  color: var(--neutral-gray-900);
  font-weight: 700;
  border-bottom: 2px solid var(--neutral-gray-100);
}

.operation-link {
  font-size: 14px;
  padding: 0 4px;
}

.operation-link.green {
  color: #a8c69f;
}

.operation-link.red {
  color: #c8e0c4; /* Matching the muted greenish-grey delete in image */
  opacity: 0.8;
}

.operation-link.red:hover {
  color: var(--error-color);
}

/* KB Dialog Custom Styles */
.kb-dialog :deep(.el-dialog__header) {
  padding-bottom: 0;
}

.kb-form {
  padding: 10px 0;
}

.kb-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-bottom: 10px;
}

.kb-save-btn {
  background-color: #a8c69f;
  border-color: #a8c69f;
  color: white;
  padding: 8px 24px;
}

.kb-save-btn:hover {
  background-color: #97b58e;
  border-color: #97b58e;
  color: white;
}
</style>
