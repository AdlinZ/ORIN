<template>
  <div class="page-container">
    <PageHeader 
      title="全域会话记录" 
      description="审计并追溯所有接入智能体的历史交互明细"
      icon="ChatLineRound"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport">导出报告</el-button>
      </template>
      <template #filters>
        <el-input 
          v-model="searchQuery" 
          placeholder="搜索会话内容..." 
          :prefix-icon="Search" 
          clearable 
          style="width: 250px;"
        />
        <el-select v-model="filterAgent" placeholder="筛选智能体" style="width: 200px;" clearable>
          <el-option 
            v-for="agent in agents" 
            :key="agent.agentId" 
            :label="agent.agentName || agent.name" 
            :value="agent.agentId" />
        </el-select>
      </template>
    </PageHeader>


    <el-card shadow="never" class="table-card">
      <el-table border :data="pagedLogs" style="width: 100%" v-loading="loading" stripe>
        <el-table-column prop="sessionId" label="会话 ID" width="160" show-overflow-tooltip>
           <template #default="{ row }">
             <code class="session-id">{{ row.sessionId }}</code>
           </template>
        </el-table-column>
        <el-table-column prop="agentName" label="智能体" width="150" sortable>
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="info">{{ row.agentName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastQuery" label="最近对话" min-width="300" show-overflow-tooltip>
           <template #default="{ row }">
             <div class="last-msg-container">
               <span class="text-main">{{ row.lastQuery }}</span>
               <el-badge :value="row.messageCount" :max="99" class="msg-count-badge" type="info" />
             </div>
           </template>
        </el-table-column>
        <el-table-column prop="tokens" label="累计 Tokens" width="120" align="center" sortable />
        <el-table-column prop="time" label="最后活跃" width="180" align="center" sortable />
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">查看全景</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container" style="margin-top: 20px; text-align: right;">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="filteredChatLogs.length"
        />
      </div>
    </el-card>

    <!-- Chat Replay Drawer -->
    <el-drawer v-model="drawerVisible" title="会话上下文详情" size="500px" class="chat-drawer" destroy-on-close>
       <div class="chat-detail-header" v-if="selectedRow" style="padding: 0 20px 15px; border-bottom: 1px solid var(--neutral-gray-100); margin-bottom: 10px;">
          <div style="font-size: 13px; color: var(--neutral-gray-600); margin-bottom: 4px;">
            <b style="color: var(--neutral-gray-900);">会话ID:</b> {{ selectedRow.sessionId }}
          </div>
          <div style="display: flex; gap: 15px; font-size: 12px; color: var(--neutral-gray-500);">
            <span>消耗: <b style="color: var(--success-color);">{{ selectedRow.tokens }}</b> tokens</span>
            <span>响应: <b style="color: var(--warning-color);">{{ selectedRow.responseTime }}ms</b></span>
            <span>时间: {{ selectedRow.time }}</span>
          </div>
       </div>
       <div class="chat-history">
          <div v-for="(msg, i) in currentChat" :key="i" class="chat-bubble" :class="msg.role">
             <div class="role-icon">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <el-icon v-else><Cpu /></el-icon>
             </div>
             <div class="content">
                <div class="msg-text">{{ msg.text }}</div>
                <div class="msg-meta">{{ msg.time }}</div>
             </div>
          </div>
       </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import { Download, User, Cpu, ChatLineRound, Search } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { getAgentList, getGroupedConversationLogs, getConversationHistory } from '@/api/agent';
import { getAgentList as getStatusAgentList } from '@/api/monitor';
import { ElMessage } from 'element-plus';

const loading = ref(false);
const filterAgent = ref('');
const searchQuery = ref('');
const drawerVisible = ref(false);
const agents = ref([]);
const rawLogs = ref([]);
const selectedRow = ref(null);

// Pagination
const currentPage = ref(1);
const pageSize = ref(10);

const currentChat = ref([]);

const loadAgents = async () => {
  try {
    const response = await getAgentList(); // Using Metadata API
    agents.value = response || [];
  } catch (error) {
    ElMessage.error('获取智能体列表失败: ' + error.message);
  }
};

const loadChatLogs = async () => {
  loading.value = true;
  try {
    const res = await getGroupedConversationLogs(currentPage.value - 1, pageSize.value);
    rawLogs.value = res.content.map(log => ({
      sessionId: log.conversationId,
      agentId: log.agentId,
      agentName: '', // Will be resolved by computed
      modelName: log.model,
      lastQuery: log.query,
      tokens: log.totalTokens,
      responseTime: log.responseTime,
      time: log.createdAt,
      timestamp: new Date(String(log.createdAt).replace(' ', 'T')).getTime(),
      success: log.success
    }));
    // total is used for pagination
    // Since we are using grouped logs, we should probably handle totalElements
    // But local filtering is used here, so I'll just keep it simple for now or update total
  } catch (error) {
    ElMessage.error('获取日志流水线失败');
  } finally {
    loading.value = false;
  }
};

// Computed for filtering and sorting
const filteredChatLogs = computed(() => {
  let list = [...rawLogs.value];
  
  // 1. Agent Filter
  if (filterAgent.value) {
    list = list.filter(l => l.agentId === filterAgent.value);
  }
  
  // 2. Keyword Filter
  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase();
    list = list.filter(l => 
      (l.fullContent && l.fullContent.toLowerCase().includes(query)) || 
      (l.response && l.response.toLowerCase().includes(query)) ||
      (l.sessionId && l.sessionId.toLowerCase().includes(query))
    );
  }
  
  // 3. Sorting (Timestamp Descending)
  return list.sort((a, b) => b.timestamp - a.timestamp);
});

const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const paged = filteredChatLogs.value.slice(start, start + pageSize.value);
  
  // Resolve agent names dynamically with multiple fallbacks
  return paged.map(log => {
    const agentId = log.agentId;
    const agent = agents.value.find(a => a.agentId === agentId || a.id === agentId);
    
    let resolvedName = agent?.agentName || agent?.name;
    
    // If still unknown, try to use model name as a clue
    if (!resolvedName) {
      resolvedName = log.modelName ? `未知智能体 (${log.modelName})` : '未知智能体';
    }
    
    return {
      ...log,
      agentName: resolvedName
    };
  });
});

const getLatencyClass = (ms) => {
  if (ms > 3000) return 'text-danger';
  if (ms > 1000) return 'text-warning';
  return 'text-success';
};

const handleExport = () => {
  if (filteredChatLogs.value.length === 0) return;
  
  // Define CSV headers
  const headers = ['会话ID', '智能体', '提问内容', '响应内容', '消耗Tokens', '响应延迟(ms)', '会话时间'];
  
  // Map data to rows
  const rows = filteredChatLogs.value.map(log => [
    log.sessionId,
    log.agentName,
    `"${(log.fullContent || '').replace(/"/g, '""')}"`,
    `"${(log.response || '').replace(/"/g, '""')}"`,
    log.tokens,
    log.responseTime,
    log.time
  ]);

  // Create CSV content
  const csvContent = "\ufeff" + [headers, ...rows].map(e => e.join(",")).join("\n");
  
  // Create blob and download
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const link = document.createElement("a");
  const url = URL.createObjectURL(blob);
  
  const timestamp = new Date().toISOString().slice(0, 10);
  link.setAttribute("href", url);
  link.setAttribute("download", `ORIN_Chat_Report_${timestamp}.csv`);
  link.style.visibility = 'hidden';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  ElMessage.success('报告导出成功');
};

const viewDetail = async (row) => {
  selectedRow.value = row;
  loading.value = true;
  try {
    const history = await getConversationHistory(row.sessionId);
    currentChat.value = history.flatMap(log => [
      { role: 'user', text: log.query, time: log.createdAt.includes(' ') ? log.createdAt.split(' ')[1] : log.createdAt },
      { role: 'assistant', text: log.response || (log.success ? '（内容为空）' : `错误: ${log.errorMessage}`), time: log.createdAt.includes(' ') ? log.createdAt.split(' ')[1] : log.createdAt }
    ]);
    drawerVisible.value = true;
  } catch (e) {
    ElMessage.error('加载详情失败');
  } finally {
    loading.value = false;
  }
};

// Reset pagination when filter changes
watch([filterAgent, searchQuery], () => {
  currentPage.value = 1;
});

onMounted(async () => {
  await loadAgents();
  loadChatLogs();
  
  // 监听全局刷新事件
  window.addEventListener('global-refresh', loadChatLogs);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', loadChatLogs);
});
</script>

<style scoped>
.page-container {
  padding: 0;
}
.chat-history {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.chat-bubble {
  display: flex;
  gap: 12px;
  max-width: 90%;
}

.chat-bubble.assistant { align-self: flex-start; }
.chat-bubble.user { align-self: flex-end; flex-direction: row-reverse; }

.role-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--neutral-gray-100);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-bubble.user .role-icon { background: var(--primary-light); color: var(--primary-color); }

.content {
  background: var(--neutral-gray-100);
  padding: 12px 16px;
  border-radius: 12px;
}

.chat-bubble.user .content {
  background: var(--primary-color);
  color: #fff;
}

.msg-text { font-size: 14px; line-height: 1.6; }
.msg-meta { font-size: 11px; margin-top: 6px; opacity: 0.6; }

.session-id {
  background: var(--neutral-gray-50);
  color: var(--primary-color);
  padding: 2px 8px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', Courier, monospace;
  font-size: 12px;
}

.last-msg-container {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.msg-count-badge {
  flex-shrink: 0;
}
</style>