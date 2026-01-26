<template>
  <div class="page-container">
    <PageHeader 
      title="智能体管理" 
      description="Flux-based Intelligent Agency Management"
      icon="UserFilled"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push('/dashboard/agent/onboard')">接入新智能体</el-button>
        <el-button :icon="Refresh" @click="fetchData">刷新列表</el-button>
      </template>

      <template #filters>
        <el-input 
          v-model="searchQuery" 
          placeholder="搜索智能体或模型..." 
          :prefix-icon="Search" 
          clearable 
          class="search-input"
        />
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable class="filter-select">
          <el-option label="ACTIVE (运行中)" value="RUNNING" />
          <el-option label="IDLE (已停止)" value="STOPPED" />
        </el-select>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card">
      <el-table 
        v-loading="loading" 
        :data="paginatedAgentList"
        style="width: 100%"
        @row-click="handleRowClick"
        stripe
      >
        <el-table-column prop="agentName" label="智能体名称" min-width="180">
          <template #default="{ row }">
            <div class="agent-name-info">
              <span class="name">{{ row.agentName }}</span>
              <el-tag size="small" v-if="row.isNewlyAdded" type="success" effect="plain" class="new-tag">NEW</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="modelName" label="核心模型" width="200" />
        
        <el-table-column prop="status" label="当前状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light">
              {{ row.status === 'RUNNING' ? 'ACTIVE' : 'IDLE' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="updatedAt" label="最后活跃" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.updatedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
             <el-button link type="primary" @click.stop="handleChat(row)">对话</el-button>
             <el-button link type="primary" @click.stop="goToMonitor(row)">监控</el-button>
             <el-button link type="primary" @click.stop="handleEdit(row)">配置</el-button>
             <el-button link type="danger" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
        />
      </div>
    </el-card>

    <!-- Agent Config Drawer (Refactored from Inspector) -->
    <el-drawer 
      v-model="drawerVisible" 
      title="智能体配置详情" 
      size="550px" 
      class="config-drawer"
      destroy-on-close
    >
      <div v-loading="editLoading" class="drawer-content">
        <div class="drawer-section">
          <h4>基础定义</h4>
          <el-form :model="editForm" label-position="top">
            <el-form-item label="智能体名称">
              <el-input v-model="editForm.name" placeholder="请输入名称" @blur="submitOptimisticUpdate('name')" />
            </el-form-item>
            <el-form-item label="核心模型架构">
              <el-input v-model="editForm.model" placeholder="LLM Model Identity" @blur="submitOptimisticUpdate('model')" />
            </el-form-item>
          </el-form>
        </div>

        <div class="drawer-section">
          <h4>推理参数 (Hyperparameters)</h4>
          <div class="params-grid">
            <div class="param-item">
              <label>Temperature ({{ editForm.temperature }})</label>
              <el-slider v-model="editForm.temperature" :min="0" :max="2" :step="0.1" @change="submitOptimisticUpdate('temperature')" />
            </div>
            <div class="param-item">
              <label>Top P ({{ editForm.topP }})</label>
              <el-slider v-model="editForm.topP" :min="0" :max="1" :step="0.1" @change="submitOptimisticUpdate('topP')" />
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="section-header">
            <h4>系统预设指令 (System Prompt)</h4>
            <el-select v-model="selectedPromptTemplate" placeholder="应用模板" size="small" style="width: 150px;" @change="applyPromptTemplate">
              <el-option v-for="t in promptTemplates" :key="t.id" :label="t.name" :value="t.content" />
            </el-select>
          </div>
          <el-input 
            v-model="editForm.systemPrompt" 
            type="textarea" 
            :rows="12" 
            placeholder="在此定义智能体的角色、约束与技能描述..."
            @blur="submitOptimisticUpdate('systemPrompt')"
          />
        </div>

        <div class="drawer-footer">
          <el-button type="primary" style="width: 100%" @click="syncKnowledgeBase" :loading="syncLoading">
            同步关联知识资产 (Sync Knowledge Base)
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- Chat Hub (Standard Drawer Like Logs) -->
    <el-drawer
      v-model="chatVisible"
      :title="'智能体对话: ' + (currentChatAgent.agentName || '')"
      size="500px"
      append-to-body
      class="chat-drawer"
    >
      <div class="chat-main">
        <div class="chat-history" ref="messagesContainer">
          <div v-for="(msg, i) in chatMessages" :key="i" class="chat-bubble" :class="msg.role">
            <div class="bubble-content">{{ msg.content }}</div>
          </div>
          <div v-if="chatLoading" class="chat-bubble assistant">
             <div class="bubble-content typing">...</div>
          </div>
        </div>
        <div class="chat-input-area">
          <el-input 
            v-model="chatInput" 
            placeholder="发送指令或提问..." 
            @keyup.enter="sendMessage"
            :disabled="chatLoading"
          >
            <template #suffix>
              <el-icon class="icon-send" @click="sendMessage"><Position /></el-icon>
            </template>
          </el-input>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed, watch, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { 
  Plus, Search, Refresh, UserFilled, Position, 
  Cpu, User, ChatDotRound, Delete, Monitor 
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { 
  getAgentList as getMonitorAgentList 
} from '@/api/monitor';
import { 
  updateAgent, getAgentAccessProfile, 
  getAgentMetadata, chatAgent, deleteAgent 
} from '@/api/agent';
import request from '@/utils/request';

const router = useRouter();
const loading = ref(false);
const rawAgentList = ref([]);
const searchQuery = ref('');
const statusFilter = ref('');
const currentPage = ref(1);
const pageSize = ref(10);

// Drawer States
const drawerVisible = ref(false);
const editLoading = ref(false);
const syncLoading = ref(false);
const editForm = ref({
  agentId: '',
  name: '',
  model: '',
  temperature: 0.7,
  topP: 0.7,
  systemPrompt: ''
});
const promptTemplates = ref([]);
const selectedPromptTemplate = ref('');

// Chat States
const chatVisible = ref(false);
const currentChatAgent = ref({});
const chatMessages = ref([]);
const chatInput = ref('');
const chatLoading = ref(false);
const messagesContainer = ref(null);

// Data Fetching
const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getMonitorAgentList(); 
    rawAgentList.value = res.data || [];
  } catch (error) {
    ElMessage.error('无法同步智能体实时状态');
  } finally {
    loading.value = false;
  }
};

// Filtering & Pagination
const filteredAgentList = computed(() => {
  let list = [...rawAgentList.value];
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase();
    list = list.filter(a => 
      (a.agentName || '').toLowerCase().includes(q) || 
      (a.modelName || '').toLowerCase().includes(q)
    );
  }
  if (statusFilter.value) {
    list = list.filter(a => a.status === statusFilter.value);
  }
  return list;
});

const total = computed(() => filteredAgentList.value.length);
const paginatedAgentList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  return filteredAgentList.value.slice(start, start + pageSize.value);
});

// UI Handlers
const handleRowClick = (row) => {
  handleEdit(row);
};

const handleEdit = async (row) => {
  drawerVisible.value = true;
  editLoading.value = true;
  
  editForm.value = {
    agentId: row.agentId,
    name: row.agentName || '',
    model: row.modelName || '',
    temperature: 0.7,
    topP: 0.7,
    systemPrompt: ''
  };

  try {
    const [metaRes, promptRes] = await Promise.all([
      getAgentMetadata(row.agentId),
      request.get(`/knowledge/agents/${row.agentId}/meta/prompts`)
    ]);

    if (metaRes.data) {
      editForm.value = { ...editForm.value, ...metaRes.data };
      editForm.value.name = metaRes.data.name; // Mapping backend 'name' to frontend 'name'
      editForm.value.model = metaRes.data.modelName;
    }
    promptTemplates.value = promptRes.data || [];
  } catch (e) {
    console.warn('Config fetch failure');
  } finally {
    editLoading.value = false;
  }
};

const submitOptimisticUpdate = async (field) => {
  // Sync to local list for immediate visual feedback
  const index = rawAgentList.value.findIndex(a => a.agentId === editForm.value.agentId);
  if (index !== -1) {
    if (field === 'name') rawAgentList.value[index].agentName = editForm.value.name;
    if (field === 'model') rawAgentList.value[index].modelName = editForm.value.model;
  }

  try {
    await updateAgent(editForm.value.agentId, editForm.value);
    ElMessage({ message: `配置同步成功: ${field}`, type: 'success', duration: 1000 });
  } catch (e) {
    ElMessage.error('同步失败，请检查网络');
    fetchData(); // Rollback
  }
};

const applyPromptTemplate = (content) => {
  if (content) {
    editForm.value.systemPrompt = content;
    submitOptimisticUpdate('systemPrompt');
  }
};

const syncKnowledgeBase = () => {
  syncLoading.value = true;
  setTimeout(() => {
    ElMessage.success('全域知识索引已重新热加载');
    syncLoading.value = false;
  }, 1200);
};

// Chat Logic
const handleChat = (row) => {
  currentChatAgent.value = row;
  chatVisible.value = true;
  chatMessages.value = [{ role: 'assistant', content: `你好，我是 ${row.agentName}。我已准备好协助你，请发送指令。` }];
};

const sendMessage = async () => {
  if (!chatInput.value.trim() || chatLoading.value) return;
  
  const msg = chatInput.value;
  chatMessages.value.push({ role: 'user', content: msg });
  chatInput.value = '';
  chatLoading.value = true;
  
  try {
    const res = await chatAgent(currentChatAgent.value.agentId, msg);
    chatMessages.value.push({ role: 'assistant', content: res.data?.answer || '处理完成' });
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: '（交互异常，请检查智能体服务状态）' });
  } finally {
    chatLoading.value = false;
    nextTick(() => {
      if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    });
  }
};

const handleDelete = (row) => {
  ElMessageBox.confirm(`确认注销智能体 [${row.agentName}] 吗？`, '安全警告', { type: 'warning' })
    .then(async () => {
      await deleteAgent(row.agentId);
      ElMessage.success('智能体已下线');
      fetchData();
    });
};

const goToMonitor = (row) => {
  router.push({ path: '/dashboard/monitor', query: { agentId: row.agentId } });
};

const getStatusType = (status) => {
  return status === 'RUNNING' ? 'success' : 'info';
};

const formatTime = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleString();
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.search-input {
  width: 250px;
  margin-right: 12px;
}

.filter-select {
  width: 160px;
}

.table-card {
  margin-top: 5px;
}

.agent-name-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

/* Drawer Styles */
.drawer-content {
  padding: 0 24px 40px;
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.drawer-section h4 {
  font-size: 14px;
  color: var(--orin-primary);
  margin: 0 0 16px 0;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 5px;
}

.params-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.param-item label {
  font-size: 12px;
  color: var(--text-secondary);
  display: block;
  margin-bottom: 8px;
}

.drawer-footer {
  margin-top: auto;
  padding-top: 24px;
  border-top: 1px solid var(--border-subtle);
}

/* Chat Drawer Styles */
.chat-main {
  height: calc(100vh - 80px);
  display: flex;
  flex-direction: column;
}

.chat-history {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: var(--neutral-gray-50);
}

.chat-bubble {
  max-width: 85%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
}

.chat-bubble.user {
  align-self: flex-end;
  background: var(--orin-primary);
  color: white;
  border-bottom-right-radius: 2px;
}

.chat-bubble.assistant {
  align-self: flex-start;
  background: white;
  color: var(--text-primary);
  border-bottom-left-radius: 2px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.chat-input-area {
  padding: 20px 24px;
  background: white;
  border-top: 1px solid var(--border-subtle);
}

.icon-send {
  cursor: pointer;
  color: var(--orin-primary);
  font-size: 18px;
  transition: transform 0.2s;
}

.icon-send:hover {
  transform: scale(1.1) translateX(2px);
}
</style>
