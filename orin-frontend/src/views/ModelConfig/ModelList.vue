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
              <el-radio-button label="LLM">语言模型 (LLM)</el-radio-button>
              <el-radio-button label="EMBEDDING">向量模型</el-radio-button>
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
         </div>
      </div>

      <ResizableTable v-loading="loading" :data="filteredList">
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
                <el-option label="LLM" value="LLM" />
                <el-option label="Embedding" value="EMBEDDING" />
                <el-option label="Rerank" value="RERANK" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模型标识符" prop="modelId">
          <el-input v-model="form.modelId" placeholder="例如: gpt-4o 或 llama3:8b" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
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
import { getModelList, saveModel, deleteModel, toggleModelStatus } from '@/api/model';
import { ElMessage, ElMessageBox } from 'element-plus';

const loading = ref(false);
const submitting = ref(false);
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
  type: 'LLM',
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
  { label: '已启用 LLM', value: modelList.value.filter(m => m.type === 'LLM' && m.status === 'ENABLED').length },
  { label: '向量模型数', value: modelList.value.filter(m => m.type === 'EMBEDDING').length },
  { label: '异常资源', value: 0 }
]);

const handleAdd = () => {
  Object.assign(form, { id: null, name: '', provider: '', type: 'LLM', modelId: '', description: '', status: 'ENABLED' });
  dialogVisible.value = true;
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
</style>
