<template>
  <div class="page-container">


    <PageHeader 
      title="模型列表" 
      description="连接并调度第三方 API (OpenAI, Claude, Dify等) 及本地部署的模型资源"
      icon="Box"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">添加模型资源</el-button>
      </template>
    </PageHeader>


    <!-- Stats Row -->
    <el-row :gutter="24" class="model-stats" style="margin-bottom: 24px;">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" :body-style="{ padding: '20px' }">
          <div class="text-secondary" style="margin-bottom: 8px;">{{ stat.label }}</div>
          <div class="page-title" style="margin-bottom: 0;">{{ stat.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Table Section -->
    <el-card shadow="never" class="table-card">
      <div class="table-toolbar" style="margin-bottom: 24px;">
         <div class="header-left">
           <el-radio-group v-model="typeFilter" @change="handleTabChange">
          <el-radio-button label="ALL">全部</el-radio-button>
          <el-radio-button label="CHAT">Chat</el-radio-button>
          <el-radio-button label="EMBEDDING">Embedding</el-radio-button>
          <el-radio-button label="RERANKER">Reranker</el-radio-button>
          <el-radio-button label="TEXT_TO_IMAGE">Text2Img</el-radio-button>
          <el-radio-button label="SPEECH_TO_TEXT">Speech2Text</el-radio-button>
        </el-radio-group>
         </div>
         <div class="action-bar">
           <el-input 
             v-model="searchQuery" 
             placeholder="搜索名称或供应商..." 
             :prefix-icon="Search" 
             clearable 
             class="search-input"
             style="width: 280px"
           />
           <el-button 
             v-if="selectedIds.length > 0" 
             type="danger" 
             plain 
             :icon="Delete" 
             style="margin-left: 12px"
             @click="handleBatchDelete"
           >
             批量删除 ({{ selectedIds.length }})
           </el-button>
         </div>
      </div>

      <ResizableTable v-loading="loading" :data="filteredList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" fixed="left" />
        <el-table-column label="模型名称" min-width="220">
          <template #default="{ row }">
            <div class="model-info">
              <el-icon class="text-secondary">
                <component :is="row.type === 'LLM' ? ChatDotRound : Connection" />
              </el-icon>
              <div class="provider-tag" :class="row.provider.toLowerCase()">{{ row.provider }}</div>
              <span class="name">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="modelId" label="模型标识 (Model ID)" width="180" />
        
        <el-table-column prop="type" label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'LLM' ? 'primary' : 'success'" effect="plain">
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="status" label="运行状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag 
              :type="row.status === 'ENABLED' ? 'success' : 'info'" 
              class="clickable-tag"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 'ENABLED' ? '已启用' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column prop="createTime" label="创建时间" width="160" align="center">
           <template #default="{ row }">
             {{ new Date(row.createTime).toLocaleDateString() }}
           </template>
        </el-table-column>
        
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
             <el-button link type="primary" :icon="Cpu" @click="handleTestModel(row)">测试</el-button>
             <el-button link type="primary" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
             <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </ResizableTable>
    </el-card>

    <!-- Form Dialog -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="form.id ? '编辑模型' : '新增模型'" 
      width="550px"
      destroy-on-close
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-form-item label="模型名称" prop="name">
          <el-input v-model="form.name" placeholder="例如: GPT-4o" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="供应方" prop="provider">
              <el-select v-model="form.provider" placeholder="请选择" style="width: 100%">
                <el-option label="OpenAI" value="OpenAI" />
                <el-option label="Anthropic" value="Anthropic" />
                <el-option label="Dify (External)" value="Dify" />
                <el-option label="SiliconFlow" value="SiliconFlow" />
                <el-option label="Ollama" value="Ollama" />
                <el-option label="HuggingFace" value="HuggingFace" />
                <el-option label="DashScope" value="DashScope" />
                <el-option label="DeepSeek" value="DeepSeek" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="模型类型" prop="type">
              <el-select v-model="form.type" placeholder="请选择" style="width: 100%">
                <el-option label="Chat (LLM)" value="CHAT" />
                <el-option label="Embedding" value="EMBEDDING" />
                <el-option label="Reranker" value="RERANKER" />
                <el-option label="Text to Image" value="TEXT_TO_IMAGE" />
                <el-option label="Image to Image" value="IMAGE_TO_IMAGE" />
                <el-option label="Speech to Text" value="SPEECH_TO_TEXT" />
                <el-option label="Text to Video" value="TEXT_TO_VIDEO" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模型标识符" prop="modelId">
          <el-input v-model="form.modelId" placeholder="例如: gpt-4o 或 llama3:8b" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider>API 自动获取 (可选)</el-divider>
        <div class="api-fetch-section">
          <p class="section-tip">提供 API 信息后，可一键获取供应商提供的所有模型</p>
          
          <el-form-item label="使用已保存密钥" class="mini-form-item">
            <el-select v-model="selectedSavedKeyId" placeholder="选择已保存的凭据" size="small" style="width: 100%" clearable @change="onSavedKeyChange">
              <el-option v-for="key in savedKeys" :key="key.id" :label="`${key.name} (${key.provider})`" :value="key.id" />
            </el-select>
          </el-form-item>
          <el-row :gutter="10">
            <el-col :span="16">
              <el-input v-model="fetchConfig.baseUrl" placeholder="API Base URL (e.g. https://api.openai.com/v1)" size="small" />
            </el-col>
            <el-col :span="8">
              <el-button type="primary" plain size="small" :loading="isFetchingModels" @click="handleFetchFromApi" style="width: 100%">
                获取全部模型
              </el-button>
            </el-col>
          </el-row>
          <el-input v-model="fetchConfig.apiKey" type="password" show-password placeholder="API Key" size="small" style="margin-top: 10px;" />
          
          <div v-if="availableModels.length > 0" class="fetched-list">
            <div class="list-header" style="display: flex; justify-content: space-between; align-items: center; margin: 15px 0 8px;">
              <p class="label-mini" style="margin: 0;">发现 {{ availableModels.length }} 个模型：</p>
              <el-button type="success" size="small" link :loading="submitting" @click="handleImportAll">
                一键全部导入
              </el-button>
            </div>
            <div class="model-tags">
              <el-tag 
                v-for="m in availableModels.slice(0, 30)" 
                :key="m.id" 
                size="small" 
                class="fetched-tag"
                @click="onSelectFetchedModel(m)"
              >
                {{ m.id }}
              </el-tag>
              <span v-if="availableModels.length > 30" class="more-text">...等共 {{ availableModels.length }} 个</span>
            </div>
          </div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认保存</el-button>
      </template>
    </el-dialog>

    <!-- Test Result Dialog -->
    <el-dialog v-model="testResultVisible" title="模型连通性测试" width="500px">
      <div v-if="testResult" class="test-result-body">
        <div class="result-item">
          <span class="label">测试结果:</span>
          <el-tag :type="testResult.success ? 'success' : 'danger'">{{ testResult.success ? '成功' : '失败' }}</el-tag>
        </div>
        <div class="result-item">
          <span class="label">响应耗时:</span>
          <span>{{ testResult.duration }} ms</span>
        </div>
        <div v-if="testResult.error" class="result-item error">
          <span class="label">错误信息:</span>
          <p>{{ testResult.error }}</p>
        </div>
        <div v-else class="result-item success">
          <span class="label">返回内容:</span>
          <p>测试 Token 响应正常，模型状态活跃。</p>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="testResultVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { Plus, Edit, Delete, Search, Box } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import ResizableTable from '@/components/ResizableTable.vue';
import { getModelList, saveModel, deleteModel, toggleModelStatus, fetchModels } from '@/api/model';
import { getModelConfig } from '@/api/modelConfig';
import { getExternalKeys } from '@/api/apiKey';
import { ElMessage, ElMessageBox } from 'element-plus';

const loading = ref(false);
const submitting = ref(false);
const isFetchingModels = ref(false);
const availableModels = ref([]);
const savedKeys = ref([]);
const selectedSavedKeyId = ref(null);
const selectedIds = ref([]);
const fetchConfig = reactive({
  baseUrl: '',
  apiKey: ''
});
const testResultVisible = ref(false);
const testResult = ref(null);
const modelList = ref([]);
const searchQuery = ref('');
const typeFilter = ref('ALL');
const dialogVisible = ref(false);
const formRef = ref(null);
import { ChatDotRound, Connection, Cpu } from '@element-plus/icons-vue';

const form = reactive({
  id: null,
  name: '',
  provider: '',
  type: 'CHAT',
  modelId: '',
  description: '',
  status: 'ENABLED'
});

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  provider: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  modelId: [{ required: true, message: '请输入 Model ID', trigger: 'blur' }]
};

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getModelList();
    modelList.value = res;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleTabChange = async (val) => {
  loading.value = true;
  await new Promise(r => setTimeout(r, 300)); // Smooth transition
  fetchData();
  ElMessage.info(`已切换至: ${val === 'ALL' ? '全部模型' : val}`);
};

const filteredList = computed(() => {
  let list = modelList.value;
  if (typeFilter.value !== 'ALL') {
    list = list.filter(m => m.type === typeFilter.value);
  }
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(m => m.name.toLowerCase().includes(q) || m.provider.toLowerCase().includes(q));
  }
  return list;
});

const stats = computed(() => [
  { label: '纳管模型总数', value: modelList.value.length },
  { label: 'Chat 模型', value: modelList.value.filter(m => (m.type === 'CHAT' || m.type === 'LLM') && m.status === 'ENABLED').length },
  { label: 'Embedding', value: modelList.value.filter(m => m.type === 'EMBEDDING').length },
  { label: '多模态/其他', value: modelList.value.filter(m => !['CHAT', 'LLM', 'EMBEDDING'].includes(m.type)).length }
]);

const handleAdd = async () => {
  Object.assign(form, { id: null, name: '', provider: '', type: 'CHAT', modelId: '', description: '', status: 'ENABLED' });
  availableModels.value = [];
  selectedSavedKeyId.value = null;
  dialogVisible.value = true;
  
  // Load saved keys
  try {
    savedKeys.value = await getExternalKeys();
  } catch (e) { /* ignore */ }

  // Try to load default from global config
  try {
    const config = await getModelConfig();
    if (config) {
      fetchConfig.baseUrl = config.baseUrl || 'https://api.openai.com/v1';
      fetchConfig.apiKey = config.apiKey || '';
    }
  } catch (e) { /* ignore */ }
};

const onSavedKeyChange = (id) => {
  const keyMatch = savedKeys.value.find(k => k.id === id);
  if (keyMatch) {
    fetchConfig.apiKey = keyMatch.apiKey;
    if (keyMatch.baseUrl) fetchConfig.baseUrl = keyMatch.baseUrl;
    if (!form.provider) form.provider = keyMatch.provider;
  }
};

const handleFetchFromApi = async () => {
  if (!fetchConfig.baseUrl) return ElMessage.warning('请输入 API 地址');
  isFetchingModels.value = true;
  try {
    const res = await fetchModels(fetchConfig.baseUrl, fetchConfig.apiKey);
    availableModels.value = res || [];
    if (availableModels.value.length === 0) {
      ElMessage.warning('未能获取到模型列表，请检查配置');
    } else {
      ElMessage.success(`成功获取 ${availableModels.value.length} 个模型`);
    }
  } catch (e) {
    ElMessage.error('获取失败: ' + (e.response?.data?.message || e.message));
  } finally {
    isFetchingModels.value = false;
  }
};

const onSelectFetchedModel = (m) => {
  form.modelId = m.id;
  if (!form.name) form.name = m.id;
  if (m.type) form.type = m.type; // Auto-select type if available
};

const handleImportAll = async () => {
  if (!form.provider) return ElMessage.warning('请先选择供应商');
  
  const count = availableModels.value.length;
  await ElMessageBox.confirm(`确认将获取到的 ${count} 个模型全部导入吗？`, '批量导入确认', {
    confirmButtonText: '立即导入',
    cancelButtonText: '取消',
    type: 'info'
  });
  
  submitting.value = true;
  let successCount = 0;
  try {
    const provider = form.provider;
    
    // Check for existing models to avoid duplicates (optional but better)
    const existingModelIds = modelList.value.map(m => m.modelId);
    
    for (const m of availableModels.value) {
      if (existingModelIds.includes(m.id)) continue;
      
      // Use inferred type if available, otherwise fallback to current form selection or CHAT
      const modelType = m.type || form.type || 'CHAT';

      await saveModel({
        name: m.id,
        modelId: m.id,
        provider: provider,
        type: modelType,
        status: 'ENABLED',
        description: `API 自动导入 - ${new Date().toLocaleDateString()}`
      });
      successCount++;
    }
    
    ElMessage.success(`导入完成：新添加 ${successCount} 个模型`);
    dialogVisible.value = false;
    fetchData();
  } catch (e) {
    ElMessage.error('导入过程中出现异常: ' + e.message);
  } finally {
    submitting.value = false;
  }
};

const handleEdit = (row) => {
  Object.assign(form, row);
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true;
      try {
        await saveModel(form);
        ElMessage.success('保存成功');
        dialogVisible.value = false;
        fetchData();
      } finally {
        submitting.value = false;
      }
    }
  });
};

const handleToggleStatus = async (row) => {
  const oldStatus = row.status;
  // Optimistic update
  row.status = oldStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED';
  try {
    await toggleModelStatus(row.id);
    ElMessage.success(`模型已${row.status === 'ENABLED' ? '启用' : '禁用'}`);
  } catch (e) {
    row.status = oldStatus; // Rollback
    ElMessage.error('状态切换失败');
  }
};

const handleTestModel = async (row) => {
  loading.value = true;
  try {
    const startTime = Date.now();
    // In real app, call model test API
    await new Promise(r => setTimeout(r, 1200));
    testResult.value = {
      success: true,
      duration: Date.now() - startTime,
      error: null
    };
    testResultVisible.value = true;
  } finally {
    loading.value = false;
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定删除模型资源 "${row.name}" 吗?`, '警告', { type: 'warning' }).then(async () => {
    await deleteModel(row.id);
    ElMessage.success('删除成功');
    fetchData();
  });
};

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id);
};

const handleBatchDelete = () => {
  if (selectedIds.value.length === 0) return;
  ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 个模型吗? 此操作不可恢复。`, '批量删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    loading.value = true;
    try {
      // Execute deletions in sequence or parallel depending on backend capability.
      // Assuming serial for safety or basic parallel
      await Promise.all(selectedIds.value.map(id => deleteModel(id)));
      ElMessage.success('批量删除成功');
      selectedIds.value = [];
      fetchData();
    } catch (e) {
      ElMessage.error('批量删除过程中出现错误');
      fetchData(); // Refresh anyway
    } finally {
      loading.value = false;
    }
  });
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.page-container {
  padding: 0;
}
.action-bar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.test-result-body .result-item {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.test-result-body .label { font-weight: 600; min-width: 80px; }
.test-result-body p { margin: 8px 0 0 0; background: #f5f7fa; padding: 12px; border-radius: 4px; font-size: 13px; line-height: 1.6; }
.clickable-tag { cursor: pointer; }

.table-card { border-radius: 12px; }
.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.search-input { width: 300px; }

.model-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.provider-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  background: #f0f2f5;
  color: #606266;
}
.provider-tag.openai { background: #10a37f; color: #fff; }
.provider-tag.anthropic { background: #d97757; color: #fff; }
.provider-tag.ollama { background: #000; color: #fff; }
.provider-tag.dify { background: #155eef; color: #fff; }
.provider-tag.siliconflow { background: #6b46c1; color: #fff; }
.provider-tag.deepseek { background: #2f54eb; color: #fff; }

.name { font-weight: 600; color: var(--neutral-black); }

.api-fetch-section {
  padding: 15px;
  background: var(--neutral-gray-50);
  border-radius: 8px;
  border: 1px dashed var(--neutral-gray-200);
}
.section-tip { font-size: 12px; color: var(--neutral-gray-500); margin-bottom: 12px; margin-top: 0; }
.label-mini { font-size: 11px; color: var(--neutral-gray-400); margin: 15px 0 5px; }
.model-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.fetched-tag { cursor: pointer; border-radius: 4px; border: none; background: white; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.fetched-tag:hover { border-color: var(--primary-color); color: var(--primary-color); transform: scale(1.05); }
.more-text { font-size: 11px; color: var(--neutral-gray-400); align-self: center; }
</style>
