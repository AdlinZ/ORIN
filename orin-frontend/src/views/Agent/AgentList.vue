<template>
  <div class="page-container">
    <PageHeader 
      title="智能体管理" 
      description="Flux-based Intelligent Agency Management"
      icon="UserFilled"
    >
      <template #actions>
        <el-button 
          v-if="selectedRows.length > 0" 
          type="danger" 
          plain 
          :icon="Delete" 
          @click="handleBulkDelete"
        >
          批量注销 ({{ selectedRows.length }})
        </el-button>
        <el-button type="primary" :icon="Plus" @click="$router.push(ROUTES.APPLICATIONS.AGENT_ONBOARD)">接入新智能体</el-button>
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
        @selection-change="handleSelectionChange"
        stripe
      >
        <el-table-column type="selection" width="55" />

        <el-table-column prop="agentName" label="智能体名称" min-width="180" sortable>
          <template #default="{ row }">
            <div class="agent-name-info">
              <span class="name">{{ row.agentName }}</span>
              <el-tag size="small" v-if="row.isNewlyAdded" type="success" effect="plain" class="new-tag">NEW</el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="providerType" label="服务商" width="120" sortable>
          <template #default="{ row }">
            <div class="provider-tag" :class="(row.providerType || 'local').toLowerCase()">
              {{ row.providerType || 'Local' }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="viewType" label="类型" width="150" sortable>
          <template #default="{ row }">
             <el-tag 
               size="small" 
               :type="getViewTypeColor(row.viewType)" 
               effect="plain"
             >
                {{ formatViewType(row) }}
             </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="modelName" label="核心模型" width="220" sortable />
        
        <el-table-column prop="status" label="状态" width="120" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="light">
              {{ row.status === 'RUNNING' ? 'ACTIVE' : 'IDLE' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="lastHeartbeat" label="最后活跃" width="180" align="center" sortable>
          <template #default="{ row }">
            {{ formatTime(row.lastHeartbeat) }}
          </template>
        </el-table-column>

        <el-table-column label="进入" width="80" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="handleRowClick(row)">
              <el-icon><ArrowRight /></el-icon>
            </el-button>
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ROUTES } from '@/router/routes';
import { 
  Plus, Search, Refresh, UserFilled, Delete, ArrowRight 
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { 
  getAgentList as getMonitorAgentList 
} from '@/api/monitor';
import { 
  deleteAgent 
} from '@/api/agent';

const router = useRouter();
const loading = ref(false);
const rawAgentList = ref([]);
const searchQuery = ref('');
const statusFilter = ref('');
const currentPage = ref(1);
const pageSize = ref(10);
const selectedRows = ref([]);

// Data Fetching
const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getMonitorAgentList(); 
    rawAgentList.value = res || [];
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
const handleSelectionChange = (val) => {
  selectedRows.value = val;
};

const handleRowClick = (row) => {
  // 根据类型动态设置 tab 参数
  const viewType = (row.viewType || 'CHAT').toUpperCase();
  let tabName = 'chat'; // 默认
  
  if (viewType === 'TEXT_TO_IMAGE' || viewType === 'IMAGE_TO_IMAGE' || viewType === 'TTI') {
    tabName = 'image';
  } else if (viewType === 'SPEECH_TO_TEXT' || viewType === 'STT') {
    tabName = 'audio';
  } else if (viewType === 'TEXT_TO_VIDEO' || viewType === 'TTV') {
    tabName = 'video';
  } else if (viewType === 'TEXT_TO_SPEECH' || viewType === 'TTS') {
    tabName = 'tts';
  }
  
  router.push({ 
    name: 'AgentConsole', 
    params: { id: row.agentId },
    query: { tab: tabName }
  });
};

const handleBulkDelete = () => {
  const count = selectedRows.value.length;
  ElMessageBox.confirm(
    `确认注销这 ${count} 个智能体吗？此操作不可逆。`, 
    '批量注销警告', 
    { type: 'warning', confirmButtonText: '立即注销', confirmButtonClass: 'el-button--danger' }
  ).then(async () => {
    loading.value = true;
    try {
      await Promise.all(selectedRows.value.map(row => deleteAgent(row.agentId)));
      ElMessage.success(`${count} 个智能体已下线`);
      fetchData();
      selectedRows.value = [];
    } catch (e) {
      ElMessage.error('部分智能体下线失败');
    } finally {
      loading.value = false;
    }
  });
};

const getStatusType = (status) => {
  return status === 'RUNNING' ? 'success' : 'info';
};

const formatTime = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleString();
};

const getViewTypeColor = (viewType) => {
  if (!viewType) return 'info';
  
  // 根据不同的模型类型返回不同的标签颜色
  const typeColorMap = {
    'TEXT_TO_IMAGE': 'warning',
    'TTI': 'warning',
    'IMAGE_TO_IMAGE': 'warning',
    'SPEECH_TO_TEXT': 'success',
    'STT': 'success',
    'TEXT_TO_SPEECH': 'primary',
    'TTS': 'primary',
    'TEXT_TO_VIDEO': 'danger',
    'TTV': 'danger',
    'CHAT': 'info',
    'LLM': 'info',
    'EMBEDDING': 'primary',
    'RERANKER': 'primary'
  };
  
  return typeColorMap[viewType] || 'info';
};

const formatViewType = (row) => {
  const type = (row.viewType || 'CHAT').toUpperCase();
  const typeMap = {
    'TEXT_TO_IMAGE': '文生图',
    'TTI': '文生图',
    'IMAGE_TO_IMAGE': '图生图',
    'SPEECH_TO_TEXT': '语音转文字',
    'STT': '语音转文字',
    'TEXT_TO_SPEECH': '语音合成',
    'TTS': '语音合成',
    'TEXT_TO_VIDEO': '视频生成',
    'TTV': '视频生成',
    'CHAT': '聊天',
    'LLM': '列表'
  };
  return typeMap[type] || type;
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

.provider-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  background: #f0f2f5;
  color: #606266;
  display: inline-block;
}
.provider-tag.openai { background: #10a37f !important; color: #fff !important; }
.provider-tag.anthropic { background: #d97757 !important; color: #fff !important; }
.provider-tag.ollama { background: #000 !important; color: #fff !important; }
.provider-tag.dify { background: #155eef !important; color: #fff !important; }
.provider-tag.siliconflow { background: #6b46c1 !important; color: #fff !important; }
.provider-tag.deepseek { background: #2f54eb !important; color: #fff !important; }

.name { font-weight: 600; color: var(--neutral-black); }

.pagination-container {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-table__row) {
  cursor: pointer;
}

.new-tag {
  font-size: 10px;
  padding: 0 4px;
  height: 18px;
  line-height: 16px;
}
</style>
