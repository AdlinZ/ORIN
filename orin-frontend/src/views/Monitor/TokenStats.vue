<template>
  <div class="metrics-dashboard">
    <div class="header-section">
      <div class="page-title">
        <el-icon class="title-icon"><Cpu /></el-icon>
        <span>令牌统计</span>
        <span class="subtitle">运行时令牌消耗仪表盘</span>
      </div>
      <div class="header-actions">
        <div class="status-badge" style="background: var(--orin-primary-soft); color: var(--orin-primary);">
          <span class="dot"></span> 运行版本 2026.2.22-2
        </div>
        <div class="status-badge" style="background: var(--orin-primary-soft); color: var(--orin-primary);">
          <span class="dot"></span> 健康状况 正常
        </div>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          style="width: 240px; margin-right: 12px;"
          @change="handleDateRangeChange"
        />
        <div class="action-icons">
          <el-icon><Monitor /></el-icon>
          <el-icon><RefreshRight @click="handleGlobalRefresh" /></el-icon>
        </div>
      </div>
    </div>

    <!-- Top Section: Activity by Time -->
    <el-card shadow="never" class="activity-card">
      <div class="card-header-flex">
        <div class="header-left">
          <h3 class="card-title">按时间统计</h3>
          <p class="card-subtitle">根据会话时间跨度估算（首次/最近活动时间）。时区：本地。</p>
        </div>
        <div class="header-right">
          <div class="total-tokens">{{ formatNumber(totalTokens) }} tokens</div>
        </div>
      </div>

      <div class="activity-grid">
        <!-- Day of Week -->
        <div class="day-of-week-section">
          <h4 class="section-title">星期分布</h4>
          <div class="days-grid">
            <div 
              v-for="(item, index) in dayOfWeekData" 
              :key="index"
              class="day-card"
              :style="{ backgroundColor: getHeatmapColor(item.value, maxDayValue) }"
            >
              <div class="day-name" :style="{ color: item.value > maxDayValue * 0.5 ? '#fff' : 'inherit' }">{{ item.day }}</div>
              <div class="day-value" :style="{ color: item.value > maxDayValue * 0.5 ? '#fff' : 'inherit' }">{{ formatToken(item.value) }}</div>
            </div>
          </div>
        </div>

        <!-- Hours Heatmap -->
        <div class="hours-section">
          <div class="hours-header">
            <h4 class="section-title">小时分布</h4>
            <span class="hours-range">0 → 23</span>
          </div>
          <div class="hours-heatmap">
            <div 
              v-for="(val, idx) in hourlyData" 
              :key="idx" 
              class="hour-cell"
              :style="{ backgroundColor: getHeatmapColor(val, maxHourlyValue) }"
            ></div>
          </div>
          <div class="hours-labels">
            <span>午夜</span>
            <span>4点</span>
            <span>8点</span>
            <span>中午</span>
            <span>16点</span>
            <span>20点</span>
          </div>
          <div class="legend">
            <div class="legend-gradient"></div>
            <span>低 → 高 Token 密度</span>
          </div>
        </div>
      </div>
    </el-card>

    <div class="bottom-grid">
      <!-- Left Column -->
      <div class="left-column">
        <el-card shadow="never" class="chart-card">
          <div class="card-header-flex">
            <div class="header-left-tabs">
              <el-button
                size="small"
                :type="chartType === 'total' ? 'primary' : ''"
                :plain="chartType === 'total'"
                @click="chartType = 'total'"
              >总计</el-button>
              <el-button
                size="small"
                :type="chartType === 'type' ? 'primary' : ''"
                :plain="chartType === 'type'"
                @click="chartType = 'type'"
              >按类型</el-button>
            </div>
            <h3 class="card-title centered-title">每日 Token 消耗</h3>
          </div>
          <div class="chart-container" style="height: 250px;">
            <div ref="dailyChartRef" class="echarts-wrapper"></div>
          </div>
        </el-card>

        <el-card shadow="never" class="chart-card type-card">
          <h3 class="card-title">Token 按类型</h3>
          <div class="tokens-type-bar">
            <div class="type-segment output" :style="{ width: tokenTypesPercent.output + '%' }"></div>
            <div class="type-segment input" :style="{ width: tokenTypesPercent.input + '%' }"></div>
            <div class="type-segment cache-read" :style="{ width: tokenTypesPercent.cacheRead + '%' }"></div>
          </div>
          <div class="type-legend">
            <div class="legend-item">
              <span class="dot output"></span> 输出 {{ formatToken(tokenTypes.output) }}
            </div>
            <div class="legend-item">
              <span class="dot input"></span> 输入 {{ formatToken(tokenTypes.input) }}
            </div>
            <div class="legend-item">
              <span class="dot cache-write"></span> 缓存写入 {{ formatToken(tokenTypes.cacheWrite) }}
            </div>
            <div class="legend-item">
              <span class="dot cache-read"></span> 缓存读取 {{ formatToken(tokenTypes.cacheRead) }}
            </div>
          </div>
          <div class="total-type-label">总计: {{ formatToken(totalTokensType) }}</div>
        </el-card>
      </div>

      <!-- Right Column -->
      <div class="right-column">
        <el-card shadow="never" class="sessions-card">
          <div class="card-header-flex">
            <h3 class="card-title">会话列表</h3>
            <span class="sessions-count">显示 8 条</span>
          </div>
          <div class="sessions-toolbar">
            <div class="toolbar-stats">
              <span>平均 {{ formatToken(sessionStats.avgTokens) }}</span>
              <span style="margin-left: 12px; color: #64748b;">{{ sessionStats.errorCount }} 个错误</span>
            </div>
            <div class="toolbar-filters">
              <el-button-group size="small">
                <el-button
                  :type="sessionFilter === 'all' ? 'primary' : ''"
                  :plain="sessionFilter === 'all'"
                  @click="sessionFilter = 'all'"
                >全部</el-button>
                <el-button
                  :type="sessionFilter === 'viewed' ? 'primary' : ''"
                  :plain="sessionFilter === 'viewed'"
                  @click="sessionFilter = 'viewed'"
                >最近查看</el-button>
              </el-button-group>
              <div class="sort-dropdown">
                排序 <el-select v-model="sessionSort" size="small" style="width: 100px; margin-left: 8px;" @change="handleSortChange">
                  <el-option label="最近" value="recent"></el-option>
                  <el-option label="最多Token" value="tokens"></el-option>
                  <el-option label="最多消息" value="msgs"></el-option>
                  <el-option label="最少错误" value="errors"></el-option>
                </el-select>
              </div>
              <el-button size="small" :icon="Download" circle style="margin-left: 12px;" @click="handleExport"></el-button>
            </div>
          </div>

          <div class="sessions-list">
            <div
              v-for="(session, idx) in filteredSessions"
              :key="idx"
              class="session-item"
              :class="{ selected: selectedSession === session }"
              @click="selectedSession = session"
            >
              <div class="session-info">
                <div class="session-title">{{ session.name }}</div>
                <div class="session-meta">
                  <span v-if="session.channel">channel:{{ session.channel }}</span>
                  <span v-if="session.agent">agent:{{ session.agent }}</span>
                  <span v-if="session.provider">provider:{{ session.provider }}</span>
                  <span v-if="session.model">model:{{ session.model }}</span>
                </div>
                <div class="session-stats">
                  <span>agent:main</span>
                  <span>msgs:{{ session.msgs }}</span>
                  <span v-if="session.tools">tools:{{ session.tools }}</span>
                  <span>errors:{{ session.errors }}</span>
                  <span>dur:{{ session.dur }}</span>
                </div>
              </div>
              <div class="session-actions">
                <el-button size="small" round @click.stop="handleCopySession(session)">复制</el-button>
                <span class="session-tokens">{{ formatToken(session.tokens) }}</span>
              </div>
            </div>
            <el-empty v-if="filteredSessions.length === 0" description="暂无会话数据" :image-size="60" />
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick, onUnmounted, watch } from 'vue';
import { Cpu, Monitor, RefreshRight, Download } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { getTokenByDayOfWeek, getTokenByHour, getTokenByType, getSessions, getTokenStats, getDailyTokenTrend } from '@/api/monitor';
import { ElMessage } from 'element-plus';

// Date range
const dateRange = ref([]);
const dateRangeParams = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return {};
  return {
    startDate: dateRange.value[0].toISOString().split('T')[0],
    endDate: dateRange.value[1].toISOString().split('T')[0]
  };
});

// Chart type toggle
const chartType = ref('total');

// Session filter and stats
const sessionFilter = ref('all');
const selectedSession = ref(null);
const recentlyViewed = ref(new Set());
const sessionStats = ref({
  avgTokens: 0,
  errorCount: 0
});

// Filtered and sorted sessions
const filteredSessions = computed(() => {
  let sessions = [...sessionData.value];

  // Apply filter
  if (sessionFilter.value === 'viewed') {
    sessions = sessions.filter(s => recentlyViewed.value.has(s.id || s.name));
  }

  // Apply sort
  switch (sessionSort.value) {
    case 'tokens':
      sessions.sort((a, b) => (b.tokens || 0) - (a.tokens || 0));
      break;
    case 'msgs':
      sessions.sort((a, b) => (b.msgs || 0) - (a.msgs || 0));
      break;
    case 'errors':
      sessions.sort((a, b) => (a.errors || 0) - (b.errors || 0));
      break;
    default:
      sessions.sort((a, b) => new Date(b.startTime || 0) - new Date(a.startTime || 0));
  }

  return sessions;
});

// Real data from API
const totalTokens = ref(0);
const totalTokensType = ref(0);

const dayOfWeekData = ref([
  { day: '周日', value: 0 },
  { day: '周一', value: 0 },
  { day: '周二', value: 0 },
  { day: '周三', value: 0 },
  { day: '周四', value: 0 },
  { day: '周五', value: 0 },
  { day: '周六', value: 0 }
]);

const maxDayValue = computed(() => Math.max(...dayOfWeekData.value.map(d => d.value), 1));

const hourlyData = ref(Array(24).fill(0));
const maxHourlyValue = computed(() => Math.max(...hourlyData.value, 1));

const tokenTypes = ref({
  output: 0,
  input: 0,
  cacheWrite: 0,
  cacheRead: 0
});

const tokenTypesPercent = computed(() => {
  const total = tokenTypes.value.output + tokenTypes.value.input + tokenTypes.value.cacheRead;
  if (total === 0) return { output: 0, input: 0, cacheRead: 0 };
  return {
    output: (tokenTypes.value.output / total) * 100,
    input: (tokenTypes.value.input / total) * 100,
    cacheRead: (tokenTypes.value.cacheRead / total) * 100
  };
});

const sessionSort = ref('recent');

const sessionData = ref([]);

const loading = ref(false);

const dailyChartData = ref([]);

const fetchAllData = async () => {
  loading.value = true;
  try {
    // Build params with date range
    const params = { ...dateRangeParams.value };

    // Fetch all data in parallel
    const [dayData, hourData, typeData, sessionsData, statsData, trendData] = await Promise.all([
      getTokenByDayOfWeek({ params }),
      getTokenByHour({ params }),
      getTokenByType({ params }),
      getSessions(20, { params }),
      getTokenStats({ params }),
      getDailyTokenTrend('daily', { params })
    ]);

    // Update day of week data
    if (dayData && dayData.length > 0) {
      dayOfWeekData.value = dayData;
    }

    // Update hourly data
    if (hourData && hourData.length > 0) {
      hourlyData.value = hourData.map(h => h.value || 0);
    }

    // Update token types
    if (typeData) {
      tokenTypes.value = {
        output: typeData.output || 0,
        input: typeData.input || 0,
        cacheWrite: typeData.cacheWrite || 0,
        cacheRead: typeData.cacheRead || 0
      };
    }

    // Update sessions
    if (sessionsData) {
      sessionData.value = sessionsData;

      // Calculate session stats
      if (sessionsData.length > 0) {
        const totalTokens = sessionsData.reduce((sum, s) => sum + (s.tokens || 0), 0);
        const totalErrors = sessionsData.reduce((sum, s) => sum + (s.errors || 0), 0);
        sessionStats.value = {
          avgTokens: Math.round(totalTokens / sessionsData.length),
          errorCount: totalErrors
        };
      }
    }

    // Update total tokens
    if (statsData) {
      totalTokens.value = statsData.total || 0;
      totalTokensType.value = statsData.total || 0;
    }

    // Update daily chart data
    if (trendData && trendData.length > 0) {
      dailyChartData.value = trendData.map(t => t.tokens || 0);
    }

    // Update chart after data is loaded
    nextTick(() => {
      updateDailyChart();
    });
  } catch (error) {
    console.error('Failed to fetch metrics data:', error);
  } finally {
    loading.value = false;
  }
};

const formatNumber = (num) => {
  if (num === null || num === undefined) return '0';
  if (num >= 1000000) {
    return (num / 1000000).toFixed(1) + 'M';
  }
  return num.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
};

const formatToken = (num) => {
  if (!num) return '0';
  if (num >= 1000000) return (num / 1000000).toFixed(1) + 'M';
  if (num >= 1000) return (num / 1000).toFixed(1) + 'K';
  return num.toString();
};

const getHeatmapColor = (value, max) => {
  if (value === 0) return 'var(--orin-primary-soft)';
  const intensity = (value / max);
  // Teal scale: from light teal to strong teal (var(--orin-primary) = #00BFA5)
  const r = Math.round(240 - (215 * intensity));
  const g = Math.round(253 - (99 * intensity));
  const b = Math.round(250 - (141 * intensity));
  return `rgb(${r}, ${g}, ${b})`;
};

const dailyChartRef = ref(null);
let dailyChartInstance = null;

const initCharts = () => {
  if (dailyChartRef.value) {
    dailyChartInstance = echarts.init(dailyChartRef.value);
    updateDailyChart();
  }
};

const updateDailyChart = () => {
  if (!dailyChartInstance) return;

  // Use real data or fallback to empty
  const chartData = dailyChartData.value.length > 0 ? dailyChartData.value : [];
  const maxValue = Math.max(...chartData, 1);

  const option = {
    grid: { left: '3%', right: '3%', bottom: '5%', top: '10%', containLabel: true },
    xAxis: {
      type: 'category',
      data: chartData.map((_, i) => i % 2 === 0 ? `${i + 1}日` : ''),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 12, rotate: 45 }
    },
    yAxis: {
      type: 'value',
      show: false,
      max: maxValue * 1.1
    },
    series: [
      {
        data: chartData,
        type: 'bar',
        barWidth: '15%',
        itemStyle: { color: 'var(--orin-primary)', borderRadius: [2, 2, 0, 0] },
        label: {
          show: true,
          position: 'top',
          formatter: (params) => {
            if(params.value === 0) return '';
            return formatToken(params.value);
          },
          color: '#94a3b8',
          fontSize: 10
        }
      }
    ]
  };
  dailyChartInstance.setOption(option);
};

const handleGlobalRefresh = () => {
  fetchAllData();
};

// Handle date range change
const handleDateRangeChange = () => {
  fetchAllData();
};

// Handle session filter change
watch(sessionFilter, (newVal) => {
  if (newVal === 'viewed') {
    // Already tracking viewed sessions
  }
});

// Handle sort change
const handleSortChange = () => {
  // Sorting is handled by computed property
};

// Copy session info
const handleCopySession = (session) => {
  const info = {
    name: session.name,
    tokens: session.tokens,
    msgs: session.msgs,
    errors: session.errors,
    duration: session.dur,
    channel: session.channel,
    agent: session.agent,
    model: session.model
  };
  navigator.clipboard.writeText(JSON.stringify(info, null, 2)).then(() => {
    ElMessage.success('会话信息已复制');
    // Add to recently viewed
    recentlyViewed.value.add(session.id || session.name);
  }).catch(() => {
    ElMessage.error('复制失败');
  });
};

// Export sessions
const handleExport = () => {
  const data = filteredSessions.value.map(s => ({
    name: s.name,
    tokens: s.tokens,
    msgs: s.msgs,
    errors: s.errors,
    duration: s.dur,
    channel: s.channel,
    agent: s.agent,
    model: s.model,
    startTime: s.startTime
  }));

  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `sessions_${new Date().toISOString().split('T')[0]}.json`;
  a.click();
  URL.revokeObjectURL(url);
  ElMessage.success('导出成功');
};

// Watch for chart type changes
watch(chartType, () => {
  updateDailyChart();
});

onMounted(() => {
  fetchAllData();
  nextTick(() => {
    initCharts();
  });
  window.addEventListener('resize', handleResize);
});

const handleResize = () => {
  dailyChartInstance?.resize();
};

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  dailyChartInstance?.dispose();
});
</script>

<style scoped>
.metrics-dashboard {
  padding: 24px;
  background-color: #f8fafc;
  min-height: 100vh;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
}

/* Header */
.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  background: white;
  padding: 16px 24px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
}

.page-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 18px;
  color: #1e293b;
}

.title-icon {
  color: var(--orin-primary);
  font-size: 24px;
}

.subtitle {
  color: #94a3b8;
  font-size: 12px;
  font-weight: 500;
  margin-left: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 13px;
  font-weight: 500;
}

.status-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: currentColor;
}

.action-icons {
  display: flex;
  gap: 12px;
  color: #64748b;
  font-size: 18px;
  cursor: pointer;
}

/* Cards General */
.el-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.card-header-flex {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0;
}

.centered-title {
  flex-grow: 1;
  text-align: center;
  margin-right: 120px; /* offset the left tabs */
}

.card-subtitle {
  font-size: 12px;
  color: #64748b;
  margin: 4px 0 0 0;
}

/* Top Activity Section */
.activity-card {
  margin-bottom: 24px;
}

.total-tokens {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.activity-grid {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: 32px;
  margin-top: 24px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin: 0 0 12px 0;
}

.days-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.day-card {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(0,0,0,0.05);
  display: flex;
  flex-direction: column;
}

.day-name {
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 4px;
  color: #1e293b;
}

.day-value {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.hours-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.hours-range {
  font-size: 12px;
  color: #64748b;
}

.hours-heatmap {
  display: grid;
  grid-template-columns: repeat(24, 1fr);
  gap: 4px;
  height: 48px;
}

.hour-cell {
  border-radius: 4px;
  border: 1px solid rgba(0,0,0,0.05);
}

.hours-labels {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 11px;
  color: #94a3b8;
}

.legend {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  font-size: 11px;
  color: #94a3b8;
}

.legend-gradient {
  width: 24px;
  height: 8px;
  border-radius: 4px;
  background: linear-gradient(to right, var(--orin-primary-soft), var(--orin-primary));
}

/* Bottom Grid */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

/* Tokens By Type Bar */
.tokens-type-bar {
  display: flex;
  height: 24px;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 16px;
}

.type-segment {
  height: 100%;
}
.type-segment.output { background-color: var(--orin-primary); }
.type-segment.input { background-color: #14B8A6; }
.type-segment.cache-read { background-color: #26FFDF; }

.type-legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #475569;
  margin-bottom: 16px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}

.dot {
  width: 8px;
  height: 8px;
}
.dot.output { background-color: var(--orin-primary); }
.dot.input { background-color: #14B8A6; }
.dot.cache-write { background-color: #10b981; }
.dot.cache-read { background-color: #26FFDF; }

.total-type-label {
  font-size: 13px;
  color: #64748b;
}

.echarts-wrapper {
  width: 100%;
  height: 100%;
}

/* Sessions List */
.sessions-card {
  height: 100%;
}

.sessions-count {
  font-size: 13px;
  color: #64748b;
}

.sessions-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.toolbar-stats {
  font-size: 13px;
  color: #1e293b;
  font-weight: 500;
}

.toolbar-filters {
  display: flex;
  align-items: center;
}

.sort-dropdown {
  font-size: 13px;
  color: #64748b;
}

.session-item {
  display: flex;
  justify-content: space-between;
  padding: 16px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: all 0.2s ease;
  border-radius: 8px;
  margin-bottom: 4px;
  padding: 12px 16px;
}
.session-item:hover {
  background-color: var(--orin-primary-soft);
}
.session-item.selected {
  background-color: var(--orin-primary-soft);
  border-left: 3px solid var(--orin-primary);
}
.session-item:last-child {
  border-bottom: none;
}

.session-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 6px;
}

.session-meta {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
  display: flex;
  gap: 8px;
}
.session-meta span::after {
  content: '·';
  margin-left: 8px;
}
.session-meta span:last-child::after {
  content: '';
}

.session-stats {
  font-size: 12px;
  color: #94a3b8;
  display: flex;
  gap: 8px;
}
.session-stats span::after {
  content: '·';
  margin-left: 8px;
}
.session-stats span:last-child::after {
  content: '';
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 32px;
}

.session-tokens {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  min-width: 60px;
  text-align: right;
}
</style>
