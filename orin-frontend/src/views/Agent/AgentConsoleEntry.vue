<template>
  <div class="console-entry-page">
    <PageHeader
      title="应用控制台"
      description="选择一个智能体进行实时调试和配置"
      icon="Monitor"
    />

    <div class="entry-container">
      <!-- Quick Access - Recent Agents -->
      <div v-if="recentAgents.length > 0" class="recent-section">
        <div class="section-header">
          <el-icon><Clock /></el-icon>
          <span>最近访问</span>
        </div>
        <div class="agent-grid">
          <div 
            v-for="agent in recentAgents" 
            :key="agent.id"
            class="agent-card recent-card"
            @click="openConsole(agent.id)"
          >
            <div class="card-icon">
              <el-icon><Setting /></el-icon>
            </div>
            <div class="card-content">
              <div class="agent-name">{{ agent.name }}</div>
              <div class="agent-type">{{ formatAgentType(agent.viewType) }}</div>
              <div class="last-access">{{ formatTime(agent.lastAccess) }}</div>
            </div>
            <el-icon class="card-arrow"><ArrowRight /></el-icon>
          </div>
        </div>
      </div>

      <!-- All Agents Section -->
      <div class="all-agents-section">
        <div class="section-header">
          <el-icon><Menu /></el-icon>
          <span>全部智能体</span>
        </div>

        <!-- Search & Filter -->
        <div class="search-bar">
          <el-input
            v-model="searchQuery"
            placeholder="搜索智能体..."
            :prefix-icon="Search"
            clearable
            @input="filterAgents"
          />
          <el-select
            v-model="typeFilter"
            placeholder="筛选类型"
            clearable
            @change="filterAgents"
            style="width: 150px"
          >
            <el-option label="全部类型" value="" />
            <el-option label="对话" value="CHAT" />
            <el-option label="工作流" value="WORKFLOW" />
            <el-option label="文生图" value="TEXT_TO_IMAGE" />
            <el-option label="图生图" value="IMAGE_TO_IMAGE" />
            <el-option label="语音合成" value="TEXT_TO_SPEECH" />
            <el-option label="转写文字" value="SPEECH_TO_TEXT" />
            <el-option label="视频生成" value="TEXT_TO_VIDEO" />
          </el-select>
        </div>

        <!-- Agent List -->
        <div v-if="filteredAgents.length === 0" class="empty-state">
          <el-icon><Picture /></el-icon>
          <span>暂无匹配的智能体</span>
        </div>

        <div v-else class="agent-grid">
          <div 
            v-for="agent in filteredAgents" 
            :key="agent.id"
            class="agent-card"
            @click="openConsole(agent.id)"
          >
            <div class="card-header">
              <div class="card-name">{{ agent.name }}</div>
              <el-tag size="small">{{ formatAgentType(agent.viewType) }}</el-tag>
            </div>
            <div class="card-description">
              {{ agent.description || '暂无描述' }}
            </div>
            <div class="card-footer">
              <span class="card-model">{{ agent.model }}</span>
              <el-icon class="card-enter"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getAgentList } from '@/api/agent';
import { 
  Clock, Setting, Menu, Search, Picture, ArrowRight
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';

const router = useRouter();

// Data
const agents = ref([]);
const recentAgents = ref([]);
const loading = ref(false);
const searchQuery = ref('');
const typeFilter = ref('');

// Computed
const filteredAgents = computed(() => {
  return agents.value.filter(agent => {
    const matchesSearch = !searchQuery.value || 
      agent.name.toLowerCase().includes(searchQuery.value.toLowerCase()) ||
      agent.description?.toLowerCase().includes(searchQuery.value.toLowerCase());
    
    const matchesType = !typeFilter.value || 
      agent.viewType?.toUpperCase() === typeFilter.value;
    
    return matchesSearch && matchesType;
  });
});

// Methods
const fetchAgents = async () => {
  try {
    loading.value = true;
    const response = await getAgentList();
    agents.value = response.data || [];
    loadRecentAgents();
  } catch (error) {
    ElMessage.error('加载智能体列表失败');
  } finally {
    loading.value = false;
  }
};

const loadRecentAgents = () => {
  const recentIds = JSON.parse(localStorage.getItem('recent-agents') || '[]');
  recentAgents.value = agents.value
    .filter(a => recentIds.includes(a.id))
    .slice(0, 4);
};

const openConsole = (agentId) => {
  // Save to recent
  const recentIds = JSON.parse(localStorage.getItem('recent-agents') || '[]');
  const newRecent = [agentId, ...recentIds.filter(id => id !== agentId)].slice(0, 10);
  localStorage.setItem('recent-agents', JSON.stringify(newRecent));
  
  // Navigate
  router.push(`/dashboard/agents/console/${agentId}`);
};

const filterAgents = () => {
  // Auto-filter is handled by computed property
};

const formatAgentType = (type) => {
  if (!type) return '未知类型';
  const typeMap = {
    'CHAT': '对话',
    'WORKFLOW': '工作流',
    'TEXT_TO_IMAGE': '文生图',
    'IMAGE_TO_IMAGE': '图生图',
    'TEXT_TO_SPEECH': '语音合成',
    'SPEECH_TO_TEXT': '转写文字',
    'TEXT_TO_VIDEO': '视频生成',
    'TTI': '文生图',
    'TTS': '语音合成',
    'STT': '转写文字',
    'TTV': '视频生成',
  };
  return typeMap[type.toUpperCase()] || type;
};

const formatTime = (timestamp) => {
  if (!timestamp) return '未使用';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now - date;
  
  if (diff < 60000) return '刚刚';
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前';
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前';
  return Math.floor(diff / 86400000) + '天前';
};

onMounted(() => {
  fetchAgents();
});
</script>

<style scoped>
.console-entry-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f6f8fa;
}

.entry-container {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

/* Section Header */
.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 24px;
  padding-bottom: 12px;
  border-bottom: 2px solid #e5e7eb;
}

.section-header :deep(.el-icon) {
  font-size: 18px;
  color: #6366f1;
}

/* Recent Section */
.recent-section {
  margin-bottom: 48px;
}

/* Search Bar */
.search-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.search-bar :deep(.el-input) {
  flex: 1;
}

/* Agent Grid */
.agent-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.agent-card {
  background: white;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.agent-card:hover {
  border-color: #6366f1;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.15);
  transform: translateY(-2px);
}

/* Recent Card Style */
.recent-card {
  flex-direction: row;
  align-items: center;
  gap: 16px;
}

.recent-card .card-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
  border-radius: 8px;
  font-size: 24px;
  color: #6366f1;
  flex-shrink: 0;
}

.recent-card .card-content {
  flex: 1;
}

.recent-card .agent-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.recent-card .agent-type {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
}

.recent-card .last-access {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
}

.recent-card .card-arrow {
  font-size: 18px;
  color: #d1d5db;
  transition: all 0.3s;
}

.recent-card:hover .card-arrow {
  color: #6366f1;
  transform: translateX(4px);
}

/* Regular Card Style */
.agent-card .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.card-name + :deep(.el-tag) {
  background: #f0f4ff;
  border: 1px solid #d9e1f7;
  color: #6366f1;
}

.card-description {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  margin-bottom: 12px;
  flex: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}

.card-model {
  font-size: 12px;
  color: #9ca3af;
  background: #f9fafb;
  padding: 4px 8px;
  border-radius: 4px;
}

.card-enter {
  font-size: 16px;
  color: #d1d5db;
  transition: all 0.3s;
}

.agent-card:hover .card-enter {
  color: #6366f1;
  transform: translateX(4px);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: #9ca3af;
}

.empty-state :deep(.el-icon) {
  font-size: 48px;
  margin-bottom: 16px;
  opacity: 0.5;
}

/* Scrollbar */
.entry-container::-webkit-scrollbar {
  width: 6px;
}

.entry-container::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 3px;
}

.entry-container::-webkit-scrollbar-thumb:hover {
  background: #9ca3af;
}
</style>
