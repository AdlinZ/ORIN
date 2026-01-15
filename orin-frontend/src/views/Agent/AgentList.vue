<template>
  <div class="page-container">
    <PageHeader 
      title="智能体管理" 
      description="管理纳管的所有智能体实例，支持搜索、筛选和动态操作"
      icon="UserFilled"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push('/dashboard/agent/onboard')" class="action-button">接入新 Agent</el-button>
        
        <!-- Batch Management Dropdown -->
        <el-dropdown 
          trigger="click" 
          @command="handleBatchCommand" 
          :disabled="!selectedRows.length"
        >
          <el-button :icon="Setting" class="action-button">
            批量管理 <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="VideoPlay" command="start">批量启动</el-dropdown-item>
              <el-dropdown-item :icon="VideoPause" command="stop">批量停止</el-dropdown-item>
              <el-dropdown-item divided :icon="Delete" command="delete" style="color: #f56c6c;">批量删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>

      <template #filters>
        <el-input 
          v-model="searchQuery" 
          placeholder="关键词搜索..." 
          :prefix-icon="Search" 
          clearable 
          class="search-input"
          @input="handleSearch"
        />
        <el-select v-model="modeFilter" placeholder="类型筛选" clearable @change="handleSearch" class="filter-select">
          <el-option label="对话型" value="chat" />
          <el-option label="工作流" value="workflow" />
        </el-select>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable @change="handleSearch" class="filter-select">
          <el-option label="运行中" value="RUNNING" />
          <el-option label="高负载" value="HIGH_LOAD" />
          <el-option label="已停止" value="STOPPED" />
        </el-select>
      </template>
    </PageHeader>


    <!-- Table -->
    <el-card class="table-card">
      <el-table 
        v-loading="loading" 
        :data="paginatedAgentList" 
        style="width: 100%"
        row-key="agentId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" reserve-selection />
        <el-table-column type="index" label="序号" width="60" align="center">
          <template #default="scope">
            {{ (currentPage - 1) * pageSize + scope.$index + 1 }}
          </template>
        </el-table-column>
        
        <el-table-column prop="agentName" label="智能体名称" min-width="150">
          <template #default="{ row }">
            <div class="agent-name-cell">
              <span class="name">{{ row.agentName }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="modelName" label="模型名称" width="200" align="center">
          <template #default="{ row }">
            <el-tag type="info" effect="plain" class="category-tag">
              {{ row.modelName || 'N/A' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="mode" label="类型" width="90" align="center">
          <template #default="{ row }">
             <el-tag :type="row.mode === 'chat' ? 'success' : 'warning'" class="category-tag">
               {{ row.mode === 'chat' ? '对话型' : '工作流' }}
             </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="providerType" label="来源" width="100" align="center">
          <template #default="{ row }">
             <el-tag effect="plain" class="category-tag">
               {{ row.providerType || 'Dify' }}
             </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="接入状态" width="120" align="center">
          <template #default="{ row }">
            <div class="status-cell" style="display: flex; align-items: center; justify-content: center; gap: 8px;">
              <span class="status-dot" :class="{ 'online': row.status === 'RUNNING' }"></span>
              <el-tag :type="getStatusType(row.status)" effect="plain">
                {{ row.status === 'RUNNING' ? '已上线' : '运行中' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>



        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons-wrapper">
             <el-button class="action-btn" size="small" :icon="Monitor" @click="goToMonitor(row)">监控</el-button>
             <el-button class="action-btn" size="small" :icon="ChatLineSquare" @click="handleChat(row)">会话</el-button>
             <el-button class="action-btn" size="small" :icon="Edit" @click="handleEdit(row)">编辑</el-button>
             <el-button class="action-btn danger-btn" size="small" :icon="Delete" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>

        <template #empty>
           <el-empty description="暂无受监控的智能体">
             <el-button type="primary" @click="$router.push('/dashboard/agent/onboard')">立刻接入新 Agent</el-button>
           </el-empty>
        </template>
      </el-table>

      <!-- Pagination -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="fetchData"
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- Edit Dialog -->
    <el-dialog v-model="dialogVisible" title="编辑智能体配置" width="600px" destroy-on-close>
      <el-form :model="editForm" label-width="120px" v-loading="editLoading">
        <el-form-item label="Agent ID">
          <el-input v-model="editForm.agentId" disabled />
        </el-form-item>
        <el-form-item label="智能体名称">
           <el-input v-model="editForm.name" placeholder="自定义智能体名称"></el-input>
        </el-form-item>
        <el-form-item label="模型名称">
           <el-input v-model="editForm.model" placeholder="例如: Qwen/Qwen2-7B-Instruct"></el-input>
        </el-form-item>

        <el-form-item label="System Prompt">
           <el-input 
             v-model="editForm.systemPrompt" 
             type="textarea" 
             :rows="3" 
             placeholder="输入系统提示词 (Instruction)..." 
           />
        </el-form-item>
        
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="Temperature">
              <el-input-number v-model="editForm.temperature" :precision="1" :step="0.1" :min="0" :max="2.0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Top P">
              <el-input-number v-model="editForm.topP" :precision="1" :step="0.1" :min="0" :max="1.0" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="Max Tokens">
           <el-input-number v-model="editForm.maxTokens" :step="100" :min="1" />
        </el-form-item>
        <el-form-item label="Endpoint URL">
           <el-input v-model="editForm.endpointUrl" placeholder="API 请求地址"></el-input>
        </el-form-item>
        <el-form-item label="API Key">
           <el-input 
             v-model="editForm.apiKey" 
             type="password" 
             show-password 
             :placeholder="editForm.apiKeyPlaceholder || '若不修改请留空'" 
           />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitEdit" :loading="editLoading"> 确认修改 </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Chat Dialog -->
    <el-drawer
      v-model="chatVisible"
      :title="'与 ' + (currentChatAgent.agentName || '智能体') + ' 对话'"
      size="500px"
      direction="rtl"
      destroy-on-close
    >
      <div class="chat-container">
        <div class="messages" ref="messagesContainer">
          <div v-if="chatMessages.length === 0" class="empty-chat">
             <el-empty description="开始新的对话..." :image-size="100" />
          </div>
          <div 
             v-for="(msg, index) in chatMessages" 
             :key="index" 
             class="message-item"
             :class="msg.role"
          >
             <div class="role-avatar">
               <el-icon v-if="msg.role === 'user'"><User /></el-icon>
               <component :is="msg.role === 'assistant' ? 'Cpu' : 'User'" v-else />
             </div>
             <div class="message-content">
               {{ msg.content }}
             </div>
          </div>
          <div v-if="chatLoading" class="message-item assistant">
             <div class="role-avatar"><el-icon><Cpu /></el-icon></div>
             <div class="message-content loading"> . . . </div>
          </div>
        </div>
        <div class="input-area">
          <div v-if="selectedFile" class="file-preview">
            <el-tag closable @close="removeFile" type="info" class="file-tag">
              <el-icon><Paperclip /></el-icon>
              {{ selectedFile.name }}
            </el-tag>
          </div>
          <div class="input-actions-wrapper">
             <input type="file" ref="fileInput" @change="handleFileChange" style="display: none" />
             <el-button 
                class="upload-btn" 
                :icon="Paperclip" 
                circle 
                plain 
                @click="triggerFileUpload"
                :disabled="chatLoading"
             />
             <el-input
                v-model="chatInput"
                placeholder="输入消息..."
                @keyup.enter="sendMessage"
                :disabled="chatLoading"
                class="chat-input-field"
             >
                <template #append>
                  <el-button :icon="Position" @click="sendMessage" :loading="chatLoading" />
                </template>
             </el-input>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { getAgentList } from '@/api/monitor'; 
import { updateAgent, getAgentAccessProfile, getAgentMetadata, chatAgent, deleteAgent, controlAgent, uploadFile } from '@/api/agent';
import { useRefreshStore } from '@/stores/refresh';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Plus, Edit, Delete, Monitor, Search, ChatLineSquare, 
  User, Cpu, Position, UserFilled, Setting,
  VideoPlay, VideoPause, ArrowDown, Paperclip, Close
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const router = useRouter();
const refreshStore = useRefreshStore();
const loading = ref(false);
const rawAgentList = ref([]);
const searchQuery = ref('');
const modeFilter = ref('');
const statusFilter = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const selectedRows = ref([]);

// Edit
const dialogVisible = ref(false);
const editLoading = ref(false);
const editForm = ref({
  agentId: '',
  name: '',
  endpointUrl: '',
  apiKey: '',
  apiKeyPlaceholder: '',
  model: '',
  temperature: 0.7,
  topP: 0.7,
  maxTokens: 512,
  systemPrompt: ''
});

// Chat
const chatVisible = ref(false);
const currentChatAgent = ref({});
const chatMessages = ref([]);
const chatInput = ref('');
const chatLoading = ref(false);
const messagesContainer = ref(null);

const handleSelectionChange = (val) => {
  selectedRows.value = val;
};

const handleBatchCommand = (command) => {
  if (command === 'delete') {
    handleBatchDelete();
  } else if (command === 'start' || command === 'stop') {
    handleBatchControl(command);
  }
};

const handleBatchDelete = () => {
  ElMessageBox.confirm(
    `确认批量删除选中的 ${selectedRows.value.length} 个智能体吗？此操作不可恢复。`,
    '警告',
    { type: 'warning', confirmButtonText: '批量删除', cancelButtonText: '取消' }
  ).then(async () => {
    loading.value = true;
    try {
      const ids = selectedRows.value.map(row => row.agentId);
      let successCount = 0;
      for (const id of ids) {
        try {
          await deleteAgent(id);
          successCount++;
        } catch (e) {
          console.error(`Failed to delete agent ${id}:`, e);
        }
      }
      ElMessage.success(`成功删除 ${successCount} 个智能体`);
      fetchData();
    } catch (e) {
      ElMessage.error('批量删除任务启动失败');
    } finally {
      loading.value = false;
    }
  });
};

const handleBatchControl = (action) => {
  const label = action === 'start' ? '启动' : '停止';
  ElMessageBox.confirm(
    `确认批量${label}选中的 ${selectedRows.value.length} 个智能体吗？`,
    '批量控制',
    { type: 'info', confirmButtonText: `批量${label}`, cancelButtonText: '取消' }
  ).then(async () => {
    loading.value = true;
    try {
      const ids = selectedRows.value.map(row => row.agentId);
      let successCount = 0;
      for (const id of ids) {
        try {
          await controlAgent(id, action);
          successCount++;
        } catch (e) {
          console.error(`Failed to ${action} agent ${id}:`, e);
        }
      }
      ElMessage.success(`成功${label} ${successCount} 个智能体`);
      fetchData();
    } catch (e) {
      ElMessage.error(`批量${label}任务启动失败`);
    } finally {
      loading.value = false;
    }
  });
};

const filteredAgentList = computed(() => {
  let list = rawAgentList.value;
  if (searchQuery.value) {
    list = list.filter(a => a.agentName.toLowerCase().includes(searchQuery.value.toLowerCase()));
  }
  if (modeFilter.value) {
    list = list.filter(a => a.mode === modeFilter.value);
  }
  if (statusFilter.value) {
    list = list.filter(a => a.status === statusFilter.value);
  }
  return list;
});

const total = computed(() => filteredAgentList.value.length);

const paginatedAgentList = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const end = start + pageSize.value;
  return filteredAgentList.value.slice(start, end);
});

const fetchData = async () => {
  // 注册刷新操作并获取 AbortController
  const controller = refreshStore.registerRefresh('agent-list');
  
  loading.value = true;
  try {
    const res = await getAgentList({ signal: controller.signal });
    if (res && res.data) {
      rawAgentList.value = res.data;
      
      // 显示刷新成功提示
      ElMessage({
        message: '智能体列表已刷新',
        type: 'success',
        duration: 2000,
        showClose: true
      });
    }
  } catch (error) {
    // 如果是取消错误，不显示错误消息
    if (error.name === 'CanceledError' || error.name === 'AbortError') {
      console.log('智能体列表刷新已取消');
      return;
    }
    
    console.error('获取智能体列表失败:', error);
    
    // 显示具体错误信息
    let errorMsg = '获取智能体列表失败';
    if (error.response) {
      if (error.response.status === 403) {
        errorMsg = '权限不足，请重新登录';
      } else if (error.response.status === 401) {
        errorMsg = '登录已过期，请重新登录';
      } else if (error.response.data && error.response.data.message) {
        errorMsg = error.response.data.message;
      }
    } else if (error.message) {
      errorMsg = error.message;
    }
    
    ElMessage.error(errorMsg);
  } finally {
    loading.value = false;
    // 注销刷新操作
    refreshStore.unregisterRefresh('agent-list');
  }
};

const handleSearch = () => {
  currentPage.value = 1;
};

const formatTime = (timestamp) => {
  if (!timestamp) return '-';
  return new Date(timestamp).toLocaleString();
};

const getStatusType = (status) => {
  switch (status) {
    case 'RUNNING': return 'success';
    case 'STOPPED': return 'info';
    case 'HIGH_LOAD': return 'warning';
    case 'ERROR': return 'danger';
    default: return 'info';
  }
};

const goToMonitor = (row) => {
  router.push({ path: '/dashboard/monitor', query: { agentId: row.agentId } });
};

// Edit Logic
const handleEdit = async (row) => {
  dialogVisible.value = true;
  editLoading.value = true;
  
  // Initialize form with basic info
  // Prioritize row.modelName as that matches the backend entity field
  editForm.value = {
    agentId: row.agentId,
    name: '',
    endpointUrl: '', 
    apiKey: '',
    model: row.modelName || '',
    temperature: row.temperature !== undefined ? row.temperature : 0.7,
    topP: row.topP !== undefined ? row.topP : 0.7,
    maxTokens: row.maxTokens !== undefined ? row.maxTokens : 512,
    systemPrompt: row.systemPrompt || ''
  };

  try {
    const [profileRes, metadataRes] = await Promise.all([
      getAgentAccessProfile(row.agentId),
      getAgentMetadata(row.agentId)
    ]);

    if (profileRes.data) {
      editForm.value.endpointUrl = profileRes.data.endpointUrl || '';
      
      if (profileRes.data.apiKey) {
          const key = profileRes.data.apiKey;
          let masked = '';
          if (key.length > 8) {
              masked = key.substring(0, 3) + '****' + key.substring(key.length - 4);
          } else {
              masked = '********';
          }
          editForm.value.apiKeyPlaceholder = masked;
      }
    }

    if (metadataRes.data) {
       editForm.value.name = metadataRes.data.name || '';
       editForm.value.model = metadataRes.data.modelName || '';
       editForm.value.systemPrompt = metadataRes.data.systemPrompt || '';
       editForm.value.temperature = metadataRes.data.temperature !== undefined ? metadataRes.data.temperature : 0.7;
       editForm.value.topP = metadataRes.data.topP !== undefined ? metadataRes.data.topP : 0.7;
       editForm.value.maxTokens = metadataRes.data.maxTokens !== undefined ? metadataRes.data.maxTokens : 512;
    }
  } catch (e) {
    ElMessage.error('获取配置详情失败');
  } finally {
    editLoading.value = false;
  }
};

const submitEdit = async () => {
    editLoading.value = true;
    try {
        await updateAgent(editForm.value.agentId, editForm.value);
        ElMessage.success('更新成功');
        dialogVisible.value = false;
        fetchData();
    } catch(e) {
        ElMessage.error('更新失败');
    } finally {
        editLoading.value = false;
    }
};

// Chat Logic
const handleChat = (row) => {
    currentChatAgent.value = row;
    chatVisible.value = true;
    chatMessages.value = [];
    chatInput.value = '';
    // Optional: Add greeting
    chatMessages.value.push({
        role: 'assistant',
        content: `你好！我是 ${row.agentName}，有什么可以帮你的吗？`
    });
};

const selectedFile = ref(null);
const fileInput = ref(null);

const triggerFileUpload = () => {
    fileInput.value.click();
};

const handleFileChange = (event) => {
    const file = event.target.files[0];
    if (file) {
        // Simple validation if needed
        selectedFile.value = file;
    }
    // Reset input so same file can be selected again if needed
    event.target.value = '';
};

const removeFile = () => {
    selectedFile.value = null;
};

const sendMessage = async () => {
    if (!chatInput.value.trim() && !selectedFile.value) return;
    
    const userMsg = chatInput.value;
    const currentFile = selectedFile.value;

    let displayMsg = userMsg;
    if (currentFile) {
        displayMsg += (displayMsg ? '\n' : '') + `[文件: ${currentFile.name}]`;
    }

    chatMessages.value.push({ role: 'user', content: displayMsg });
    
    // Reset inputs immediately
    chatInput.value = '';
    selectedFile.value = null;
    
    chatLoading.value = true;
    scrollToBottom();
    
    try {
        let fileId = null;
        if (currentFile) {
            try {
                // 1. Upload file first
                chatMessages.value.push({ role: 'assistant', content: '正在上传文件...', isSystem: true });
                const uploadRes = await uploadFile(currentFile, currentChatAgent.value.agentId);
                // Adjust this based on actual response structure. 
                // Documentation says: { code, message, data: { id: "...", ... } }
                if (uploadRes.data && uploadRes.data.data && uploadRes.data.data.id) {
                    fileId = uploadRes.data.data.id;
                    // Remove the "Uploading..." message or update it
                    chatMessages.value.pop(); 
                } else {
                    throw new Error('File upload failed: No ID returned');
                }
            } catch (e) {
                console.error('Upload error', e);
                chatMessages.value.pop();
                chatMessages.value.push({ role: 'assistant', content: '文件上传失败，将仅发送文本。' });
            }
        }

        // 2. Send chat message
        const res = await chatAgent(currentChatAgent.value.agentId, userMsg, fileId);
        
        // Assuming backend returns result in specific format. Currently chat() returns Object. 
        // Need to know structure. Dify returns a complex object usually.
        // But let's assume standard response for now or string.
        // Dify: 'answer' field.
        let reply = '收到响应';
        if (res.data && res.data.answer) {
            reply = res.data.answer;
        } else if (res.data && res.data.choices && res.data.choices[0]) {
             reply = res.data.choices[0].message.content; // OpenAI format
        } else if (typeof res.data === 'string') {
            reply = res.data;
        } else {
             reply = JSON.stringify(res.data);
        }
        
        chatMessages.value.push({ role: 'assistant', content: reply });
    } catch (e) {
        chatMessages.value.push({ role: 'assistant', content: '错误: 无法获取响应' });
    } finally {
        chatLoading.value = false;
        scrollToBottom();
    }
};

const scrollToBottom = () => {
    nextTick(() => {
        if (messagesContainer.value) {
            messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
        }
    });
};

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确认删除智能体 "${row.agentName}" 吗？此操作不可恢复。`,
    '警告',
    {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(async () => {
    loading.value = true;
    try {
      await deleteAgent(row.agentId);
      ElMessage.success('删除成功');
      fetchData();
    } catch (e) {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'));
    } finally {
      loading.value = false;
    }
  });
};

onMounted(() => {
  fetchData();
  
  // 监听全局刷新事件（来自Navbar的刷新按钮）
  window.addEventListener('global-refresh', fetchData);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', fetchData);
  
  // 注销刷新操作（如果还在进行中）
  refreshStore.unregisterRefresh('agent-list');
});
</script>

<style scoped>
/* Action buttons styling */
.action-buttons-wrapper {
  display: flex;
  gap: 0px !important;
  justify-content: center;
  align-items: center;
}
.page-container {
  padding: 0;
}

.action-bar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.agent-name-cell {
  display: flex;
  align-items: center;
}

.agent-name-cell .icon {
  font-size: 20px;
  margin-right: 8px;
}

.agent-name-cell .name {
  font-weight: 500;
  color: var(--neutral-black);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.table-card {
  border-radius: var(--radius-lg) !important;
  border: 1px solid var(--neutral-gray-100) !important;
  background: var(--neutral-white) !important;
  box-shadow: var(--shadow-sm) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.table-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-lg) !important;
  border-color: var(--primary-light) !important;
}

.el-table {
  --el-table-header-bg-color: var(--neutral-gray-50);
  border-radius: var(--radius-base);
}

.el-table th.el-table__cell {
  font-weight: 700;
  color: var(--neutral-gray-900);
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.05em;
}

.action-button {
  border-radius: var(--radius-base) !important;
  font-weight: 600;
}

.search-input {
  width: 240px;
  margin-right: 12px;
}
.filter-select {
  width: 150px;
  margin-right: 12px;
}

/* Dark mode */
html.dark .table-card {
  background: var(--neutral-white) !important;
  border: 1px solid var(--neutral-gray-500) !important;
}

html.dark .el-table {
  --el-table-header-bg-color: var(--neutral-gray-200);
}

html.dark .el-table th.el-table__cell {
  color: var(--neutral-gray-900);
}

html.dark .agent-name-cell .name {
  color: var(--neutral-gray-900);
}

/* Chat UI */
.chat-container {
  height: calc(100vh - 120px); /* Adjust height */
  display: flex;
  flex-direction: column;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--neutral-gray-50);
}
.message-item {
  display: flex;
  margin-bottom: 20px;
  gap: 12px;
}
.message-item.user {
  flex-direction: row-reverse;
}
.role-avatar {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.message-item.assistant .role-avatar {
  background: #fff;
  border: 1px solid var(--neutral-gray-200);
  color: var(--primary-color);
}
.message-item.user .role-avatar {
  background: var(--primary-color);
  color: white;
}
.message-content {
  background: white;
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 80%;
  line-height: 1.6;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  font-size: 14px;
}
.message-item.user .message-content {
  background: var(--primary-color);
  color: white;
  border-bottom-right-radius: 4px;
}
.message-item.assistant .message-content {
  border-top-left-radius: 4px;
}
.input-area {
  padding: 16px;
  background: white;
  border-top: 1px solid var(--neutral-gray-200);
}
.input-actions-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
}
.chat-input-field {
  flex: 1;
}
.file-preview {
  margin-bottom: 8px;
  display: flex;
  align-items: center;
}
.file-tag {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
