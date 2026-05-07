<template>
  <div class="latency-workspace server-workspace">
    <section class="runtime-command-panel">
      <div class="runtime-command-head">
        <div class="header-main">
          <div class="header-icon">
            <el-icon><Timer /></el-icon>
          </div>
          <div>
            <h2 class="header-title">
              时延诊断工作台
            </h2>
            <div class="header-subtitle">
              对齐真实响应链路，定位趋势波动、慢请求分布与异常耗时样本
            </div>
          </div>
        </div>

        <div class="header-actions">
          <div class="period-switch" role="radiogroup" aria-label="趋势时间窗口">
            <button
              v-for="item in trendPeriodOptions"
              :key="item.value"
              type="button"
              class="period-option"
              :class="{ active: trendPeriod === item.value }"
              role="radio"
              :aria-checked="trendPeriod === item.value"
              @click="setTrendPeriod(item.value)"
            >
              {{ item.label }}
            </button>
          </div>
          <el-button
            :icon="Download"
            size="small"
            @click="handleExport"
          >
            导出报告
          </el-button>
          <el-button
            :icon="Refresh"
            :loading="isRefreshing"
            type="primary"
            size="small"
            @click="handleGlobalRefresh"
          >
            刷新数据
          </el-button>
        </div>
      </div>

      <div class="runtime-command-body">
        <div class="runtime-command-copy">
          <span class="section-kicker">Runtime Latency</span>
          <h3>{{ healthLevel.title }}</h3>
          <p class="runtime-command-desc">
            {{ activePeriodLabel }}窗口 · {{ total }} 条历史样本 · 当前页 {{ historyData.length }} 条
          </p>
        </div>

        <div class="runtime-signal-grid latency-signal-grid">
          <div v-for="item in signalCards" :key="item.key" class="runtime-signal">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div class="runtime-health-panel">
          <div class="health-ring" :style="{ '--health': `${healthLevel.score}%`, '--monitor-accent': healthLevel.color }">
            <span>{{ healthLevel.score }}%</span>
          </div>
          <div class="health-copy">
            <span>时延健康</span>
            <strong :style="{ color: healthLevel.color }">{{ healthLevel.label }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="latency-main-grid">
      <el-card shadow="never" class="latency-panel trend-panel">
        <div class="panel-heading">
          <div>
            <span class="section-kicker">Trend</span>
            <h3>平均延迟趋势</h3>
          </div>
          <el-tag effect="plain" size="small">
            {{ activePeriodLabel }}
          </el-tag>
        </div>

        <div v-loading="trendLoading" class="trend-chart-wrap">
          <div v-show="trendData.length" ref="trendChart" class="trend-chart" />
          <el-empty v-if="!trendLoading && !trendData.length" description="暂无趋势数据" :image-size="88" />
        </div>
      </el-card>

      <el-card shadow="never" class="latency-panel diagnosis-panel">
        <div class="panel-heading">
          <div>
            <span class="section-kicker">Diagnosis</span>
            <h3>当前页分布</h3>
          </div>
          <el-tag :type="healthLevel.tagType" effect="plain" size="small">
            {{ healthLevel.label }}
          </el-tag>
        </div>

        <div v-loading="historyLoading" class="distribution-list">
          <div v-for="bucket in latencyBuckets" :key="bucket.name" class="distribution-item">
            <div class="distribution-label">
              <span class="bucket-dot" :style="{ background: bucket.color }" />
              <span>{{ bucket.name }}</span>
              <small>{{ bucket.range }}</small>
            </div>
            <div class="distribution-meter">
              <span :style="{ width: `${bucket.percent}%`, background: bucket.color }" />
            </div>
            <strong>{{ bucket.count }}</strong>
          </div>
        </div>

        <div class="diagnosis-summary">
          <div>
            <span>慢请求占比</span>
            <strong>{{ slowRequestRatio }}%</strong>
          </div>
          <div>
            <span>极慢样本</span>
            <strong>{{ extremeRequestCount }}</strong>
          </div>
        </div>
      </el-card>
    </section>

    <el-card shadow="never" class="latency-panel history-card">
      <div class="panel-heading history-heading">
        <div>
          <span class="section-kicker">Trace Samples</span>
          <h3>慢请求追踪</h3>
        </div>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          size="small"
          @change="handleDateRangeChange"
        />
      </div>

      <el-table
        v-loading="historyLoading"
        border
        :data="historyData"
        stripe
        style="width: 100%"
        empty-text="暂无慢请求样本"
      >
        <el-table-column prop="createdAt" label="时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="providerId"
          label="Agent ID/Name"
          min-width="220"
          show-overflow-tooltip
        >
          <template #default="{ row }">
            {{ row.providerId || row.agentName || '-' }}
          </template>
        </el-table-column>
        <el-table-column
          prop="responseTime"
          label="响应耗时"
          min-width="150"
          align="right"
        >
          <template #default="{ row }">
            <span :class="getLatencyClass(row.responseTime)">{{ formatNumber(row.responseTime) }} ms</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="totalTokens"
          label="Total Tokens"
          min-width="150"
          align="right"
        >
          <template #default="{ row }">
            {{ formatNumber(row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column
          prop="success"
          label="状态"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-tag v-if="row.success" type="success" size="small">
              成功
            </el-tag>
            <el-tag v-else type="danger" size="small">
              失败
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="fetchHistoryData"
          @current-change="fetchHistoryData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { Download, Refresh, Timer } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { getLatencyStats, getLatencyHistory, getLatencyTrend } from '@/api/monitor';

const defaultLatencyStats = {
  avg: 0,
  p50: 0,
  p95: 0,
  p99: 0,
  weekly: 0,
  monthly: 0,
  max: 0
};

const trendPeriodOptions = [
  { label: '5m', value: '5m' },
  { label: '30m', value: '30m' },
  { label: '1h', value: '1h' },
  { label: '6h', value: '6h' },
  { label: '24h', value: '24h' },
  { label: '7d', value: '7d' }
];

const latencyStats = ref({ ...defaultLatencyStats });
const trendPeriod = ref('1h');
const trendLoading = ref(false);
const historyLoading = ref(false);
const statsLoading = ref(false);
const dateRange = ref([]);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const historyData = ref([]);
const trendData = ref([]);
const trendChart = ref(null);

let trendChartInstance = null;
let isUnmounted = false;

const isRefreshing = computed(() => statsLoading.value || trendLoading.value || historyLoading.value);

const activePeriodLabel = computed(() => {
  return trendPeriodOptions.find(item => item.value === trendPeriod.value)?.label || trendPeriod.value;
});

const readMetric = key => Number(latencyStats.value?.[key] || 0);

const signalCards = computed(() => [
  { key: 'avg', label: '平均延迟', value: `${formatNumber(readMetric('avg'))} ms` },
  { key: 'p50', label: 'P50', value: `${formatNumber(readMetric('p50'))} ms` },
  { key: 'p95', label: 'P95', value: `${formatNumber(readMetric('p95'))} ms` },
  { key: 'p99', label: 'P99', value: `${formatNumber(readMetric('p99'))} ms` },
  { key: 'weekly', label: '本周平均', value: `${formatNumber(readMetric('weekly'))} ms` },
  { key: 'max', label: '历史峰值', value: `${formatNumber(readMetric('max'))} ms` }
]);

const healthLevel = computed(() => {
  const avg = readMetric('avg');
  if (avg > 2000) {
    return { title: '时延高风险', label: '高风险', score: 42, color: '#dc2626', tagType: 'danger' };
  }
  if (avg > 800) {
    return { title: '时延需关注', label: '需关注', score: 72, color: '#d97706', tagType: 'warning' };
  }
  return { title: '响应稳定', label: '稳定', score: 92, color: '#0f766e', tagType: 'success' };
});

const latencyBuckets = computed(() => {
  const buckets = [
    { name: '快', range: '< 5s', min: 0, max: 5000, color: '#16a34a', count: 0 },
    { name: '正常', range: '5s-15s', min: 5000, max: 15000, color: '#0ea5e9', count: 0 },
    { name: '偏慢', range: '15s-30s', min: 15000, max: 30000, color: '#8b5cf6', count: 0 },
    { name: '慢', range: '30s-60s', min: 30000, max: 60000, color: '#f59e0b', count: 0 },
    { name: '极慢', range: '> 60s', min: 60000, max: Infinity, color: '#dc2626', count: 0 }
  ];

  historyData.value.forEach(log => {
    const latency = Number(log.responseTime || 0);
    const bucket = buckets.find(item => latency >= item.min && latency < item.max);
    if (bucket) bucket.count += 1;
  });

  const maxCount = Math.max(...buckets.map(item => item.count), 1);
  return buckets.map(item => ({
    ...item,
    percent: Math.round((item.count / maxCount) * 100)
  }));
});

const slowRequestCount = computed(() => {
  return historyData.value.filter(item => Number(item.responseTime || 0) >= 30000).length;
});

const extremeRequestCount = computed(() => {
  return historyData.value.filter(item => Number(item.responseTime || 0) >= 60000).length;
});

const slowRequestRatio = computed(() => {
  if (!historyData.value.length) return 0;
  return Math.round((slowRequestCount.value / historyData.value.length) * 100);
});

const formatNumber = num => {
  const value = Number(num || 0);
  return Math.round(value).toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,');
};

const normalizeDateValue = val => {
  if (!val) return null;
  if (Array.isArray(val)) {
    const [year, month = 1, day = 1, hour = 0, minute = 0, second = 0] = val;
    return new Date(year, month - 1, day, hour, minute, second);
  }
  return new Date(val);
};

const formatDateTime = val => {
  const date = normalizeDateValue(val);
  if (!date || Number.isNaN(date.getTime())) return '-';
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  });
};

const getLatencyClass = val => {
  const latency = Number(val || 0);
  if (latency >= 60000) return 'text-extreme';
  if (latency >= 30000) return 'text-danger';
  if (latency >= 10000) return 'text-warning';
  return 'text-success';
};

const fetchData = async () => {
  statsLoading.value = true;
  try {
    const res = await getLatencyStats();
    if (!isUnmounted && res) {
      latencyStats.value = { ...defaultLatencyStats, ...res };
    }
  } catch (error) {
    console.warn('获取延迟统计失败', error);
    if (!isUnmounted) {
      latencyStats.value = { ...defaultLatencyStats };
    }
  } finally {
    statsLoading.value = false;
  }
};

const fetchTrendData = async () => {
  trendLoading.value = true;
  try {
    const res = await getLatencyTrend(trendPeriod.value);
    if (isUnmounted) return;
    trendData.value = Array.isArray(res) ? res : [];
    renderTrendChart(trendData.value);
  } catch (error) {
    if (!isUnmounted) {
      console.warn('获取延迟趋势失败', error);
      trendData.value = [];
      renderTrendChart([]);
    }
  } finally {
    trendLoading.value = false;
  }
};

const setTrendPeriod = period => {
  if (trendPeriod.value === period) return;
  trendPeriod.value = period;
  fetchTrendData();
};

const renderTrendChart = data => {
  nextTick(() => {
    if (isUnmounted || !trendChart.value || !data.length) {
      trendChartInstance?.dispose();
      trendChartInstance = null;
      return;
    }

    if (!trendChartInstance) {
      trendChartInstance = echarts.init(trendChart.value);
    }

    trendChartInstance.setOption({
      tooltip: {
        trigger: 'axis',
        valueFormatter: value => `${formatNumber(value)} ms`
      },
      grid: { left: 12, right: 18, top: 24, bottom: 12, containLabel: true },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: data.map(item => formatTrendTime(item.timestamp)),
        axisTick: { show: false },
        axisLine: { lineStyle: { color: '#cbd5e1' } },
        axisLabel: { color: '#64748b' }
      },
      yAxis: {
        type: 'value',
        name: 'ms',
        axisLabel: { color: '#64748b' },
        splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.22)' } }
      },
      series: [
        {
          name: '平均延迟',
          type: 'line',
          smooth: true,
          showSymbol: false,
          data: data.map(item => Number(item.latency || 0)),
          lineStyle: { width: 3, color: '#0f766e' },
          itemStyle: { color: '#0f766e' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(15, 118, 110, 0.24)' },
              { offset: 1, color: 'rgba(15, 118, 110, 0.02)' }
            ])
          }
        }
      ]
    });
  });
};

const formatTrendTime = timestamp => {
  const date = normalizeDateValue(timestamp);
  if (!date || Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

const fetchHistoryData = async () => {
  historyLoading.value = true;
  try {
    const params = {
      page: currentPage.value - 1,
      size: pageSize.value
    };

    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0].getTime();
      params.endDate = dateRange.value[1].getTime();
    }

    const res = await getLatencyHistory(params);
    if (!isUnmounted && res) {
      historyData.value = Array.isArray(res.content) ? res.content : [];
      total.value = Number(res.totalElements || 0);
    }
  } catch (error) {
    if (!isUnmounted) {
      console.warn('获取延迟历史失败', error);
      historyData.value = [];
      total.value = 0;
      ElMessage.error('获取延迟历史数据失败');
    }
  } finally {
    historyLoading.value = false;
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

const handleDateRangeChange = () => {
  currentPage.value = 1;
  fetchHistoryData();
};

const escapeCsvValue = value => {
  const text = String(value ?? '');
  if (/[",\n]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
};

const handleExport = () => {
  const headers = ['时间', 'Agent', '响应耗时(ms)', 'Tokens', '状态'];
  const rows = historyData.value.map(item => [
    formatDateTime(item.createdAt),
    item.providerId || item.agentName || '',
    item.responseTime || 0,
    item.totalTokens || 0,
    item.success ? '成功' : '失败'
  ]);
  const csvContent = `\ufeff${[headers, ...rows].map(row => row.map(escapeCsvValue).join(',')).join('\n')}`;
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `Latency_Report_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  ElMessage.success('报告导出成功');
};

const handleGlobalRefresh = () => {
  fetchData();
  fetchTrendData();
  fetchHistoryData();
};

const handleResize = () => {
  trendChartInstance?.resize();
};

onMounted(() => {
  isUnmounted = false;
  fetchData();
  fetchTrendData();
  fetchHistoryData();
  window.addEventListener('page-refresh', handleGlobalRefresh);
  window.addEventListener('resize', handleResize);
});

onUnmounted(() => {
  isUnmounted = true;
  window.removeEventListener('page-refresh', handleGlobalRefresh);
  window.removeEventListener('resize', handleResize);
  trendChartInstance?.dispose();
  trendChartInstance = null;
});
</script>

<style scoped>
.latency-workspace {
  --monitor-accent: #0f766e;
  --monitor-accent-soft: rgba(15, 118, 110, 0.08);
  --monitor-text-main: var(--text-primary, #0f172a);
  --monitor-text-sub: var(--text-secondary, #475569);
  --monitor-text-muted: #94a3b8;
  --monitor-radius: 16px;
  --monitor-radius-sm: 12px;
  --monitor-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
  min-height: 100%;
  padding: 20px;
  background:
    radial-gradient(circle at top right, rgba(20, 184, 166, 0.08), transparent 34%),
    linear-gradient(180deg, rgba(248, 250, 252, 0.56), transparent 260px);
}

.runtime-command-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-bottom: 16px;
  padding: 16px 18px;
  border-radius: var(--monitor-radius);
  border: 1px solid rgba(203, 213, 225, 0.72);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.9)),
    linear-gradient(135deg, rgba(15, 118, 110, 0.08), rgba(59, 130, 246, 0.06));
  box-shadow: var(--monitor-shadow);
}

.runtime-command-head,
.runtime-command-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.runtime-command-body {
  display: grid;
  grid-template-columns: minmax(220px, 0.72fr) minmax(360px, 1.3fr) minmax(132px, 0.38fr);
  align-items: center;
  padding-top: 14px;
  border-top: 1px solid rgba(203, 213, 225, 0.62);
}

.header-main,
.header-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-main {
  flex: 1 1 auto;
}

.header-actions {
  flex: 0 0 auto;
  justify-content: flex-end;
  flex-wrap: wrap;
}

.period-switch {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 3px;
  border: 1px solid rgba(148, 163, 184, 0.36);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.period-option {
  min-width: 38px;
  height: 26px;
  padding: 0 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--monitor-text-sub);
  font-size: 13px;
  font-weight: 700;
  line-height: 26px;
  letter-spacing: 0;
  cursor: pointer;
  transition:
    background 0.16s ease,
    color 0.16s ease,
    box-shadow 0.16s ease;
}

.period-option:hover {
  background: rgba(15, 118, 110, 0.08);
  color: var(--monitor-accent);
}

.period-option.active {
  background: #0f766e;
  color: #ffffff;
  box-shadow: 0 4px 10px rgba(15, 118, 110, 0.18);
}

.header-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 10px;
  border: 1px solid rgba(203, 213, 225, 0.78);
  background: rgba(255, 255, 255, 0.72);
  color: var(--monitor-accent);
  font-size: 18px;
}

.header-title {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  letter-spacing: 0;
  font-weight: 750;
  color: var(--monitor-text-main);
}

.header-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--monitor-text-sub);
}

.runtime-command-copy {
  min-width: 0;
  overflow-wrap: normal;
}

.section-kicker {
  display: inline-block;
  margin-bottom: 4px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--monitor-accent);
}

.runtime-command-copy h3 {
  margin: 0;
  font-size: 22px;
  line-height: 1.12;
  font-weight: 750;
  letter-spacing: 0;
  color: var(--monitor-text-main);
  word-break: keep-all;
}

.runtime-command-desc {
  max-width: 260px;
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--monitor-text-sub);
  word-break: normal;
}

.runtime-signal-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(96px, 1fr));
  gap: 8px;
  min-width: 0;
}

.runtime-signal {
  min-width: 0;
  padding: 9px 10px;
  border-radius: 9px;
  border: 1px solid rgba(203, 213, 225, 0.72);
  background: rgba(255, 255, 255, 0.72);
}

.runtime-signal span {
  display: block;
  margin-bottom: 3px;
  font-size: 11px;
  color: var(--monitor-text-muted);
}

.runtime-signal strong {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  color: var(--monitor-text-main);
}

.runtime-health-panel {
  min-width: 0;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding-left: 18px;
  border-left: 1px solid rgba(203, 213, 225, 0.76);
}

.health-ring {
  --health: 0%;
  width: 56px;
  height: 56px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background:
    radial-gradient(circle at center, #ffffff 0 58%, transparent 60%),
    conic-gradient(var(--monitor-accent) var(--health), #e2e8f0 0);
}

.health-ring span {
  font-size: 14px;
  font-weight: 750;
  color: var(--monitor-text-main);
}

.health-copy {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  text-align: center;
}

.health-copy span {
  font-size: 12px;
  color: var(--monitor-text-sub);
}

.health-copy strong {
  font-size: 15px;
}

.latency-main-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(320px, 0.8fr);
  gap: 16px;
  margin-bottom: 16px;
}

.latency-panel {
  border-radius: var(--monitor-radius) !important;
  border: 1px solid rgba(203, 213, 225, 0.72) !important;
  box-shadow: var(--monitor-shadow) !important;
}

.latency-panel :deep(.el-card__body) {
  padding: 16px;
}

.panel-heading,
.history-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.panel-heading h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.2;
  letter-spacing: 0;
  color: var(--monitor-text-main);
}

.trend-chart-wrap {
  height: 384px;
  display: grid;
  place-items: stretch;
}

.trend-chart {
  width: 100%;
  height: 100%;
}

.distribution-list {
  display: flex;
  flex-direction: column;
  gap: 13px;
  min-height: 294px;
}

.distribution-item {
  display: grid;
  grid-template-columns: minmax(104px, 0.7fr) minmax(90px, 1fr) 34px;
  align-items: center;
  gap: 10px;
}

.distribution-label {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--monitor-text-main);
  font-size: 13px;
  font-weight: 650;
}

.distribution-label small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--monitor-text-muted);
  font-size: 11px;
  font-weight: 500;
}

.bucket-dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
}

.distribution-meter {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #e2e8f0;
}

.distribution-meter span {
  display: block;
  min-width: 4px;
  height: 100%;
  border-radius: inherit;
}

.distribution-item strong {
  text-align: right;
  font-size: 13px;
  color: var(--monitor-text-main);
}

.diagnosis-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid rgba(203, 213, 225, 0.72);
}

.diagnosis-summary div {
  min-width: 0;
  padding: 10px;
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.9);
}

.diagnosis-summary span,
.diagnosis-summary strong {
  display: block;
}

.diagnosis-summary span {
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--monitor-text-muted);
}

.diagnosis-summary strong {
  font-size: 20px;
  color: var(--monitor-text-main);
}

.history-card :deep(.el-table) {
  --el-table-header-bg-color: #f8fafc;
  --el-table-border-color: #e2e8f0;
  --el-table-row-hover-bg-color: rgba(15, 118, 110, 0.05);
  --el-table-text-color: var(--monitor-text-main);
}

.history-card :deep(.el-table__header th) {
  color: var(--monitor-text-sub);
  font-weight: 700;
}

.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-danger {
  color: #dc2626;
  font-weight: 750;
}

.text-warning {
  color: #d97706;
  font-weight: 750;
}

.text-success {
  color: #16a34a;
}

.text-extreme {
  color: #991b1b;
  font-weight: 800;
  text-decoration: underline;
}

@media (max-width: 1360px) {
  .runtime-command-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .runtime-command-body,
  .latency-main-grid {
    grid-template-columns: 1fr;
  }

  .runtime-command-desc {
    max-width: 720px;
  }

  .runtime-health-panel {
    flex-direction: row;
    justify-content: flex-start;
    padding-left: 0;
    padding-top: 14px;
    border-left: 0;
    border-top: 1px solid rgba(203, 213, 225, 0.76);
  }
}

@media (max-width: 720px) {
  .latency-workspace {
    padding: 12px;
  }

  .runtime-command-head,
  .panel-heading,
  .history-heading {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .period-switch {
    width: 100%;
    height: auto;
    flex-wrap: wrap;
  }

  .period-option {
    flex: 1 1 48px;
  }

  .runtime-signal-grid {
    grid-template-columns: 1fr;
  }

  .trend-chart-wrap {
    height: 320px;
  }

  .distribution-item {
    grid-template-columns: 1fr;
  }

  .distribution-item strong {
    text-align: left;
  }

  .diagnosis-summary {
    grid-template-columns: 1fr;
  }
}
</style>
