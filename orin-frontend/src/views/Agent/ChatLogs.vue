<template>
  <div class="page-container">
    <PageHeader 
      title="会话记录" 
      description="审计并追溯所有智能体的历史交互记录"
      icon="ChatLineRound"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport">
          导出报告
        </el-button>
      </template>
    </PageHeader>

    <section class="conversation-summary-grid">
      <article
        v-for="card in summaryCards"
        :key="card.key"
        class="summary-card"
        :class="`tone-${card.tone}`"
      >
        <div class="summary-icon">
          <el-icon><component :is="card.icon" /></el-icon>
        </div>
        <div class="summary-content">
          <span>{{ card.label }}</span>
          <strong>{{ card.value }}</strong>
          <small>{{ card.meta }}</small>
        </div>
      </article>
    </section>

    <section class="conversation-insights">
      <article class="insight-card accent-card">
        <div class="insight-card-header">
          <div>
            <span class="eyebrow">会话分布</span>
            <strong>高频智能体</strong>
          </div>
          <el-tag size="small" effect="plain">
            TOP {{ topAgentCards.length || 0 }}
          </el-tag>
        </div>
        <div v-if="topAgentCards.length" class="agent-rank-list">
          <div v-for="agent in topAgentCards" :key="agent.name" class="agent-rank-item">
            <div class="rank-avatar">
              {{ agent.name.slice(0, 1) }}
            </div>
            <div class="rank-body">
              <div class="rank-title">
                <span>{{ agent.name }}</span>
                <strong>{{ agent.count }} 次</strong>
              </div>
              <el-progress
                :percentage="agent.percent"
                :show-text="false"
                :stroke-width="6"
              />
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无会话分布" :image-size="56" />
      </article>

      <article class="insight-card recent-card">
        <div class="insight-card-header">
          <div>
            <span class="eyebrow">追溯入口</span>
            <strong>最近活跃会话</strong>
          </div>
          <span class="muted-text">{{ filteredChatLogs.length }} 条记录</span>
        </div>
        <div v-if="recentLogCards.length" class="recent-list">
          <button
            v-for="log in recentLogCards"
            :key="log.sessionId"
            type="button"
            class="recent-item"
            @click="viewDetail(log)"
          >
            <div>
              <span class="recent-title">{{ log.lastQuery || '空会话' }}</span>
              <small>{{ log.agentName }} · {{ log.time }}</small>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <el-empty v-else description="暂无活跃会话" :image-size="56" />
      </article>
    </section>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="table-card-header">
          <div>
            <strong>会话流水</strong>
            <span>按最近活跃时间排序，支持筛选、导出与上下文回放</span>
          </div>
          <el-tag effect="plain" round>
            {{ pagedLogs.length }} / {{ filteredChatLogs.length }}
          </el-tag>
        </div>
      </template>
      <div class="table-filter-bar">
        <el-input
          v-model="searchQuery"
          class="filter-search"
          placeholder="搜索会话 ID / 内容 / 智能体 / 模型"
          :prefix-icon="Search"
          clearable
        />
        <el-select
          v-model="filterAgent"
          class="filter-select"
          placeholder="智能体"
          clearable
        >
          <el-option
            v-for="agent in agents"
            :key="agent.agentId || agent.id"
            :label="agent.agentName || agent.name"
            :value="agent.agentId || agent.id"
          />
        </el-select>
        <el-select
          v-model="filterResult"
          class="filter-select filter-select-small"
          placeholder="结果"
          clearable
        >
          <el-option label="成功" value="success" />
          <el-option label="失败" value="failed" />
        </el-select>
        <el-popover
          placement="bottom-end"
          trigger="click"
          width="420"
          popper-class="conversation-filter-popover"
        >
          <template #reference>
            <el-button class="advanced-filter-button">
              高级筛选
              <el-tag
                v-if="advancedFilterCount"
                size="small"
                round
                effect="plain"
              >
                {{ advancedFilterCount }}
              </el-tag>
            </el-button>
          </template>
          <div class="advanced-filter-panel">
            <span class="advanced-filter-title">时间范围</span>
            <el-date-picker
              v-model="filterDateRange"
              class="advanced-date-range"
              type="datetimerange"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              range-separator="至"
              value-format="YYYY-MM-DD HH:mm:ss"
              clearable
            />
            <span class="advanced-filter-title">Tokens 区间</span>
            <div class="token-filter">
              <el-input-number
                v-model="minTokens"
                :min="0"
                :controls="false"
                placeholder="最小"
              />
              <span class="token-separator">-</span>
              <el-input-number
                v-model="maxTokens"
                :min="0"
                :controls="false"
                placeholder="最大"
              />
            </div>
          </div>
        </el-popover>
        <el-button class="reset-filter-button" @click="resetFilters">
          重置
        </el-button>
      </div>
      <el-table
        v-loading="loading"
        border
        :data="pagedLogs"
        style="width: 100%"
        stripe
      >
        <el-table-column
          prop="sessionId"
          label="会话 ID"
          width="140"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <code class="session-id">{{ row.sessionId }}</code>
          </template>
        </el-table-column>
        <el-table-column
          prop="agentName"
          label="智能体"
          width="140"
          sortable
        >
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="info">
              {{ row.agentName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="lastQuery"
          label="最近对话"
          min-width="240"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            <div class="last-msg-container">
              <span class="text-main">{{ row.lastQuery }}</span>
              <el-badge
                :value="row.messageCount"
                :max="99"
                class="msg-count-badge"
                type="info"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column
          prop="tokens"
          label="累计 Tokens"
          width="120"
          align="center"
          sortable
        />
        <el-table-column
          prop="time"
          label="最后活跃"
          width="170"
          align="center"
          sortable
        />
        <el-table-column
          label="操作"
          width="100"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">
              查看会话
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
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
    <el-drawer
      v-model="drawerVisible"
      title="会话上下文详情"
      size="500px"
      class="chat-drawer"
      destroy-on-close
    >
      <div v-if="selectedRow" class="chat-detail-header" style="padding: 0 20px 15px; border-bottom: 1px solid var(--neutral-gray-100); margin-bottom: 10px;">
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
        <div
          v-for="(msg, i) in currentChat"
          :key="i"
          class="chat-bubble"
          :class="msg.role"
        >
          <div class="role-icon">
            <el-icon v-if="msg.role === 'user'">
              <User />
            </el-icon>
            <el-icon v-else>
              <Cpu />
            </el-icon>
          </div>
          <div class="content">
            <div v-if="msg.text && msg.text.includes('file_id=')" class="msg-text">
              <div v-for="(part, idx) in formatFileLinks(msg.text)" :key="idx">
                <span v-if="part.isLink" class="file-link" @click="downloadFile(part.url)">
                  <el-icon><Download /></el-icon> 点击下载{{ part.fileType }}
                </span>
                <span v-else class="markdown-body inline-markdown" v-html="renderMarkdown(part.text)" />
              </div>
            </div>
            <div v-else class="msg-text markdown-body" v-html="renderMarkdown(msg.text)" />
            <div class="msg-meta">
              {{ msg.time }}
            </div>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue';
import {
  ArrowRight,
  ChatDotRound,
  Cpu,
  DataLine,
  Download,
  Search,
  Timer,
  User
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { getAgentList, getGroupedConversationLogs, getConversationHistory } from '@/api/agent';
import { ElMessage } from 'element-plus';
import { marked } from 'marked';

const loading = ref(false);
const filterAgent = ref('');
const searchQuery = ref('');
const filterDateRange = ref([]);
const filterResult = ref('');
const minTokens = ref(null);
const maxTokens = ref(null);
const drawerVisible = ref(false);
const agents = ref([]);
const rawLogs = ref([]);
const selectedRow = ref(null);

// Pagination
const currentPage = ref(1);
const pageSize = ref(10);

const currentChat = ref([]);

const resolveAgentName = (log) => {
  const agent = agents.value.find(a => a.agentId === log.agentId || a.id === log.agentId);
  return agent?.agentName || agent?.name || (log.modelName ? `未知智能体 (${log.modelName})` : '未知智能体');
};

const formatNumber = (value) => Number(value || 0).toLocaleString('zh-CN');

const formatPercent = (value) => `${Math.round(value || 0)}%`;

const includesKeyword = (value, keyword) => String(value || '').toLowerCase().includes(keyword);

const hasTokenBoundary = (value) => value !== null && value !== undefined && value !== '';

const advancedFilterCount = computed(() => {
  const tokenCount = [minTokens.value, maxTokens.value].filter(hasTokenBoundary).length;
  const timeCount = Array.isArray(filterDateRange.value) && filterDateRange.value.length === 2 ? 1 : 0;
  return tokenCount + timeCount;
});

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
      tokens: log.cumulativeTokens,
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
    window.dispatchEvent(new Event('page-refresh-done'));
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
  const keyword = searchQuery.value.trim().toLowerCase();
  if (keyword) {
    list = list.filter(l =>
      includesKeyword(l.fullContent, keyword) ||
      includesKeyword(l.response, keyword) ||
      includesKeyword(l.lastQuery, keyword) ||
      includesKeyword(l.sessionId, keyword) ||
      includesKeyword(l.modelName, keyword) ||
      includesKeyword(resolveAgentName(l), keyword)
    );
  }

  // 3. Time Range Filter
  if (Array.isArray(filterDateRange.value) && filterDateRange.value.length === 2) {
    const [start, end] = filterDateRange.value;
    const startTime = new Date(String(start).replace(' ', 'T')).getTime();
    const endTime = new Date(String(end).replace(' ', 'T')).getTime();
    if (Number.isFinite(startTime) && Number.isFinite(endTime)) {
      list = list.filter(l => Number.isFinite(l.timestamp) && l.timestamp >= startTime && l.timestamp <= endTime);
    }
  }

  // 4. Result Filter
  if (filterResult.value === 'success') {
    list = list.filter(l => l.success !== false);
  } else if (filterResult.value === 'failed') {
    list = list.filter(l => l.success === false);
  }

  // 5. Token Range Filter
  if (hasTokenBoundary(minTokens.value)) {
    list = list.filter(l => Number(l.tokens || 0) >= Number(minTokens.value));
  }
  if (hasTokenBoundary(maxTokens.value)) {
    list = list.filter(l => Number(l.tokens || 0) <= Number(maxTokens.value));
  }
  
  // Sorting (Timestamp Descending)
  return list.sort((a, b) => b.timestamp - a.timestamp);
});

const pagedLogs = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value;
  const paged = filteredChatLogs.value.slice(start, start + pageSize.value);
  
  // Resolve agent names dynamically with multiple fallbacks
  return paged.map(log => {
    return {
      ...log,
      agentName: resolveAgentName(log)
    };
  });
});

const enrichedLogs = computed(() => filteredChatLogs.value.map(log => ({
  ...log,
  agentName: resolveAgentName(log)
})));

const successRate = computed(() => {
  if (!filteredChatLogs.value.length) return 0;
  const successful = filteredChatLogs.value.filter(log => log.success !== false).length;
  return (successful / filteredChatLogs.value.length) * 100;
});

const averageLatency = computed(() => {
  const latencyLogs = filteredChatLogs.value.filter(log => Number(log.responseTime) > 0);
  if (!latencyLogs.length) return 0;
  const total = latencyLogs.reduce((sum, log) => sum + Number(log.responseTime || 0), 0);
  return Math.round(total / latencyLogs.length);
});

const totalTokens = computed(() => filteredChatLogs.value.reduce((sum, log) => sum + Number(log.tokens || 0), 0));

const activeAgentCount = computed(() => {
  const unique = new Set(filteredChatLogs.value.map(log => log.agentId).filter(Boolean));
  return unique.size;
});

const summaryCards = computed(() => [
  {
    key: 'sessions',
    label: '会话总量',
    value: formatNumber(filteredChatLogs.value.length),
    meta: `当前筛选下 ${pagedLogs.value.length} 条显示中`,
    icon: ChatDotRound,
    tone: 'primary'
  },
  {
    key: 'agents',
    label: '活跃智能体',
    value: formatNumber(activeAgentCount.value),
    meta: filterAgent.value ? '已按智能体聚焦' : '覆盖全部会话来源',
    icon: Cpu,
    tone: 'teal'
  },
  {
    key: 'tokens',
    label: '累计 Tokens',
    value: formatNumber(totalTokens.value),
    meta: '用于判断上下文消耗规模',
    icon: DataLine,
    tone: 'amber'
  },
  {
    key: 'latency',
    label: '平均响应',
    value: averageLatency.value ? `${formatNumber(averageLatency.value)}ms` : '-',
    meta: `成功率 ${formatPercent(successRate.value)}`,
    icon: Timer,
    tone: averageLatency.value > 3000 ? 'danger' : 'green'
  }
]);

const topAgentCards = computed(() => {
  const counts = enrichedLogs.value.reduce((acc, log) => {
    const name = log.agentName || '未知智能体';
    acc.set(name, (acc.get(name) || 0) + 1);
    return acc;
  }, new Map());

  const maxCount = Math.max(...counts.values(), 0);
  return [...counts.entries()]
    .map(([name, count]) => ({
      name,
      count,
      percent: maxCount ? Math.round((count / maxCount) * 100) : 0
    }))
    .sort((a, b) => b.count - a.count)
    .slice(0, 4);
});

const recentLogCards = computed(() => enrichedLogs.value.slice(0, 4));

const handleExport = () => {
  if (filteredChatLogs.value.length === 0) return;
  
  // Define CSV headers
  const headers = ['会话ID', '智能体', '提问内容', '响应内容', '消耗Tokens', '响应延迟(ms)', '会话时间'];
  
  // Map data to rows
  const rows = enrichedLogs.value.map(log => [
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

const renderMarkdown = (text) => {
  if (!text) return '';
  const normalized = String(text)
    .replace(/\\r\\n/g, '\n')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t');
  const escaped = normalized
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
  try {
    return String(marked.parse(escaped)).trim();
  } catch (error) {
    return escaped.replace(/\n/g, '<br>');
  }
};

// 解析文件链接
const formatFileLinks = (text) => {
  if (!text || !text.includes('file_id=')) {
    // 检查是否包含外部URL（如 [图片文件] http://example.com/image.png）
    if (!text || (!text.includes('[图片文件]') && !text.includes('[视频文件]') && !text.includes('[音频文件]'))) {
      return [{ text, isLink: false }];
    }
    // 处理外部URL的情况
    const result = [];
    let fileType = '音频';
    if (text.includes('[图片文件]')) {
      fileType = '图片';
    } else if (text.includes('[视频文件]')) {
      fileType = '视频';
    }
    // 匹配 [文件类型] URL 格式（匹配到行尾或遇到换行）
    const urlRegex = /\[(?:图片|视频|音频)文件\]\s*(.+?)(?=\n|$)/g;
    let lastIndex = 0;
    let match;
    while ((match = urlRegex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        result.push({ text: text.substring(lastIndex, match.index), isLink: false });
      }
      result.push({ url: match[1], isLink: true, fileType });
      lastIndex = urlRegex.lastIndex;
    }
    if (lastIndex < text.length) {
      result.push({ text: text.substring(lastIndex), isLink: false });
    }
    return result.length > 0 ? result : [{ text, isLink: false }];
  }
  const result = [];
  // 判断文件类型
  let fileType = '音频';
  if (text.includes('[图片文件]')) {
    fileType = '图片';
  } else if (text.includes('[视频文件]')) {
    fileType = '视频';
  }
  // 精确匹配 file_id= 后面跟着的 UUID 格式
  const regex = /file_id=([a-f0-9-]{36})/g;
  let lastIndex = 0;
  let match;
  while ((match = regex.exec(text)) !== null) {
    // 添加匹配之前的文本
    if (match.index > lastIndex) {
      result.push({ text: text.substring(lastIndex, match.index), isLink: false });
    }
    // 添加文件链接
    const fileId = match[1];
    const downloadUrl = `/api/v1/multimodal/files/${fileId}/download`;
    result.push({ url: downloadUrl, isLink: true, fileType });
    lastIndex = regex.lastIndex;
  }
  // 添加剩余文本
  if (lastIndex < text.length) {
    result.push({ text: text.substring(lastIndex), isLink: false });
  }
  return result;
};

// 下载文件
const downloadFile = (url) => {
  window.open(url, '_blank');
};

const resetFilters = () => {
  searchQuery.value = '';
  filterAgent.value = '';
  filterDateRange.value = [];
  filterResult.value = '';
  minTokens.value = null;
  maxTokens.value = null;
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
watch([filterAgent, searchQuery, filterDateRange, filterResult, minTokens, maxTokens], () => {
  currentPage.value = 1;
});

onMounted(async () => {
  await loadAgents();
  loadChatLogs();

  // 监听全局刷新事件
  window.addEventListener('global-refresh', loadChatLogs);
  window.addEventListener('page-refresh', loadChatLogs);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', loadChatLogs);
  window.removeEventListener('page-refresh', loadChatLogs);
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.conversation-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

.summary-card,
.insight-card,
.table-card {
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, #ffffff);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.08));
}

.summary-card {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  padding: 16px;
  overflow: hidden;
}

.summary-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto;
  height: 3px;
  background: var(--card-accent, var(--orin-primary, #0d9488));
}

.summary-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--card-accent, var(--orin-primary, #0d9488));
  background: var(--card-soft, rgba(13, 148, 136, 0.1));
  font-size: 18px;
}

.summary-content {
  min-width: 0;
}

.summary-content span,
.summary-content small,
.eyebrow,
.muted-text {
  color: var(--neutral-gray-500);
  font-size: 12px;
  line-height: 1.4;
}

.summary-content span,
.eyebrow {
  display: block;
  font-weight: 700;
}

.summary-content strong {
  display: block;
  margin: 6px 0 4px;
  color: var(--neutral-gray-900);
  font-size: 24px;
  line-height: 1;
}

.tone-primary {
  --card-accent: var(--primary-color, #0d9488);
  --card-soft: var(--primary-light, rgba(13, 148, 136, 0.12));
}

.tone-teal {
  --card-accent: #0891b2;
  --card-soft: rgba(8, 145, 178, 0.12);
}

.tone-amber {
  --card-accent: #d97706;
  --card-soft: rgba(217, 119, 6, 0.12);
}

.tone-green {
  --card-accent: var(--success-color, #16a34a);
  --card-soft: rgba(22, 163, 74, 0.12);
}

.tone-danger {
  --card-accent: var(--danger-color, #dc2626);
  --card-soft: rgba(220, 38, 38, 0.12);
}

.conversation-insights {
  display: grid;
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1.05fr);
  gap: 16px;
  margin-bottom: 16px;
}

.insight-card {
  padding: 16px;
  min-width: 0;
}

.accent-card {
  background:
    linear-gradient(135deg, rgba(13, 148, 136, 0.08), transparent 46%),
    var(--orin-surface, #ffffff);
}

.insight-card-header,
.table-card-header,
.rank-title,
.recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.insight-card-header {
  margin-bottom: 14px;
}

.insight-card-header strong,
.table-card-header strong {
  display: block;
  color: var(--neutral-gray-900);
  font-size: 16px;
}

.agent-rank-list,
.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.agent-rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--neutral-gray-100);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.rank-avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--primary-color);
  background: var(--primary-light);
  font-weight: 700;
}

.rank-body {
  min-width: 0;
  flex: 1;
}

.rank-title {
  margin-bottom: 6px;
  color: var(--neutral-gray-700);
  font-size: 13px;
}

.rank-title span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rank-title strong {
  flex-shrink: 0;
  color: var(--neutral-gray-900);
  font-size: 13px;
}

.recent-item {
  width: 100%;
  padding: 12px;
  border: 1px solid var(--neutral-gray-100);
  border-radius: 8px;
  color: inherit;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--transition-base), box-shadow var(--transition-base), transform var(--transition-base);
}

.recent-item:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.08));
  transform: translateY(-1px);
}

.recent-item > div {
  min-width: 0;
}

.recent-title {
  display: block;
  max-width: 100%;
  overflow: hidden;
  color: var(--neutral-gray-900);
  font-size: 14px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-item small,
.table-card-header span {
  display: block;
  margin-top: 4px;
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.table-card {
  overflow: hidden;
  max-width: 100%;
  min-width: 0;
}

.table-card :deep(.el-card__header) {
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
}

.table-card :deep(.el-card__body) {
  padding: 0;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
}

.table-filter-bar {
  display: grid;
  grid-template-columns:
    minmax(260px, 1fr)
    minmax(140px, 0.36fr)
    minmax(112px, 0.24fr)
    auto
    auto;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
  background:
    linear-gradient(135deg, rgba(13, 148, 136, 0.06), transparent 52%),
    #fff;
}

.filter-search {
  min-width: 0;
}

.filter-select {
  width: 100%;
  min-width: 0;
}

.filter-select-small {
  width: 100%;
}

.advanced-filter-button,
.reset-filter-button {
  justify-content: center;
  width: auto;
  min-width: 72px;
  padding-inline: 12px;
}

.advanced-filter-button :deep(.el-tag) {
  margin-left: 4px;
}

.advanced-filter-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.advanced-date-range,
.advanced-filter-panel :deep(.el-date-editor--datetimerange) {
  width: 100%;
}

.advanced-filter-title {
  color: var(--neutral-gray-700);
  font-size: 13px;
  font-weight: 700;
}

.token-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  width: 100%;
  min-height: 36px;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: rgba(255, 255, 255, 0.78);
  color: var(--neutral-gray-500);
  font-size: 12px;
  font-weight: 700;
  min-width: 0;
}

.token-filter :deep(.el-input-number) {
  width: 100%;
  flex: 1 1 0;
  min-width: 0;
}

.token-filter :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
}

.token-separator {
  color: var(--neutral-gray-400);
}

.table-card :deep(.el-table th.el-table__cell) {
  color: var(--neutral-gray-600);
  font-size: 12px;
  font-weight: 700;
  background: var(--neutral-gray-50);
}

.table-card :deep(.el-table) {
  max-width: 100%;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  background: #fff;
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
.msg-text :deep(p) {
  margin: 0 0 8px;
}
.msg-text :deep(p:last-child) {
  margin-bottom: 0;
}
.msg-text :deep(ul),
.msg-text :deep(ol) {
  margin: 0 0 8px;
  padding-left: 18px;
}
.msg-text :deep(pre) {
  margin: 8px 0;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  background: rgba(15, 23, 42, 0.08);
}
.chat-bubble.user .msg-text :deep(pre) {
  background: rgba(255, 255, 255, 0.2);
}
.msg-text :deep(code) {
  padding: 2px 4px;
  border-radius: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
  background: rgba(15, 23, 42, 0.08);
}
.chat-bubble.user .msg-text :deep(code) {
  background: rgba(255, 255, 255, 0.2);
}
.msg-text :deep(pre code) {
  padding: 0;
  background: transparent;
}
.msg-text :deep(a) {
  color: inherit;
  text-decoration: underline;
}
.inline-markdown :deep(p) {
  display: inline;
  margin: 0;
}
.msg-meta { font-size: 11px; margin-top: 6px; opacity: 0.6; }
.file-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--primary-color);
  text-decoration: none;
  font-weight: 500;
}
.file-link:hover {
  text-decoration: underline;
}

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

html.dark .summary-card,
html.dark .insight-card,
html.dark .table-card,
html.dark .table-card :deep(.el-card__header),
html.dark .table-filter-bar,
html.dark .pagination-container {
  background: rgba(15, 23, 42, 0.82);
  border-color: rgba(148, 163, 184, 0.22);
  box-shadow: none;
}

html.dark .agent-rank-item,
html.dark .recent-item,
html.dark .token-filter {
  background: rgba(15, 23, 42, 0.66);
  border-color: rgba(148, 163, 184, 0.18);
}

html.dark .summary-content strong,
html.dark .insight-card-header strong,
html.dark .table-card-header strong,
html.dark .rank-title strong,
html.dark .recent-title {
  color: #f8fafc;
}

@media (max-width: 1200px) {
  .conversation-summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .table-filter-bar {
    grid-template-columns: minmax(220px, 1fr) minmax(130px, 0.5fr) minmax(112px, 0.35fr);
  }
}

@media (max-width: 900px) {
  .conversation-insights {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .conversation-summary-grid {
    grid-template-columns: 1fr;
  }

  .table-card-header,
  .table-filter-bar,
  .pagination-container {
    align-items: flex-start;
  }

  .table-filter-bar {
    grid-template-columns: 1fr;
  }

  .advanced-filter-button,
  .reset-filter-button {
    width: 100%;
  }

  .token-filter {
    justify-content: space-between;
    width: 100%;
  }
}
</style>
