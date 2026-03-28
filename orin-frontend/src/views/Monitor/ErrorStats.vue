<template>
  <div class="error-dashboard">
    <PageHeader
      title="错误统计"
      description="查看近期失败调用、错误类型分布与受影响会话"
      icon="Warning"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport">
          导出报告
        </el-button>
        <el-button :icon="RefreshRight" @click="fetchErrorData">
          刷新数据
        </el-button>
      </template>
      <template #filters>
        <div class="filters-row">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="small"
            class="date-range"
            @change="handleDateRangeChange"
          />
          <div class="auto-refresh">
            <el-icon><Monitor /></el-icon>
            <span>自动刷新</span>
            <el-switch
              v-model="autoRefresh"
              active-text=""
              inactive-text=""
              size="small"
              @change="handleAutoRefreshChange"
            />
          </div>
        </div>
      </template>
    </PageHeader>

    <div class="stats-grid">
      <el-card shadow="never" class="stat-card">
        <div class="stat-icon total-icon">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            失败调用数
          </div>
          <div class="stat-value">
            {{ formatNumber(successRate.failedCalls) }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon rate-icon">
          <el-icon><CircleCloseFilled /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            错误率
          </div>
          <div class="stat-value">
            {{ failureRateText }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon category-icon">
          <el-icon><Tickets /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            错误类型数
          </div>
          <div class="stat-value">
            {{ formatNumber(errorDistribution.length) }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon provider-icon">
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            受影响供应商
          </div>
          <div class="stat-value">
            {{ formatNumber(providerDistribution.length) }}
          </div>
        </div>
      </el-card>
    </div>

    <div class="content-grid">
      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                高频错误 Top 8
              </h3>
              <p class="card-subtitle">
                按错误消息聚合，可快速看出最主要的失败来源
              </p>
            </div>
            <div class="header-total">
              {{ formatNumber(totalErrorCount) }} 次失败
            </div>
          </div>
        </template>
        <div v-loading="loading" class="chart-wrap">
          <div v-if="topErrors.length > 0" ref="barChartRef" class="chart" />
          <el-empty v-else description="当前时间范围内暂无错误数据" :image-size="72" />
        </div>
      </el-card>

      <el-card shadow="never" class="chart-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                供应商错误分布
              </h3>
              <p class="card-subtitle">
                帮助判断问题是否集中在某个模型供应商
              </p>
            </div>
          </div>
        </template>
        <div v-loading="loading" class="chart-wrap">
          <div v-if="providerDistribution.length > 0" ref="pieChartRef" class="chart" />
          <el-empty v-else description="暂无供应商错误分布" :image-size="72" />
        </div>
      </el-card>
    </div>

    <div class="table-grid">
      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                错误明细排行
              </h3>
              <p class="card-subtitle">
                当前按供应商 + 错误消息聚合展示
              </p>
            </div>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="errorDistribution"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column
            type="index"
            label="#"
            width="60"
            align="center"
          />
          <el-table-column
            prop="providerId"
            label="供应商"
            min-width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ normalizeProvider(row.providerId) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="errorMessage"
            label="错误消息"
            min-width="320"
            show-overflow-tooltip
          />
          <el-table-column
            prop="count"
            label="次数"
            min-width="100"
            align="right"
          >
            <template #default="{ row }">
              {{ formatNumber(row.count) }}
            </template>
          </el-table-column>
          <el-table-column label="占比" min-width="100" align="right">
            <template #default="{ row }">
              {{ formatPercent(totalErrorCount > 0 ? Number(row.count || 0) / totalErrorCount : 0) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never" class="table-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                最近异常会话
              </h3>
              <p class="card-subtitle">
                基于最近会话列表筛出包含错误的会话
              </p>
            </div>
            <el-tag type="danger" effect="light">
              {{ errorSessions.length }} 个异常会话
            </el-tag>
          </div>
        </template>
        <el-table
          v-loading="loading"
          :data="errorSessions"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column
            prop="name"
            label="会话"
            min-width="220"
            show-overflow-tooltip
          />
          <el-table-column
            prop="provider"
            label="供应商"
            min-width="120"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              {{ normalizeProvider(row.provider) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="model"
            label="模型"
            min-width="160"
            show-overflow-tooltip
          />
          <el-table-column
            prop="msgs"
            label="消息数"
            min-width="90"
            align="right"
          >
            <template #default="{ row }">
              {{ formatNumber(row.msgs) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="errors"
            label="错误数"
            min-width="90"
            align="right"
          >
            <template #default="{ row }">
              <span class="error-count">{{ formatNumber(row.errors) }}</span>
            </template>
          </el-table-column>
          <el-table-column
            prop="tokens"
            label="Token"
            min-width="120"
            align="right"
          >
            <template #default="{ row }">
              {{ formatNumber(row.tokens) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="dur"
            label="持续时间"
            min-width="100"
            align="center"
          />
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { CircleCloseFilled, DataAnalysis, Download, Monitor, RefreshRight, Tickets, Warning } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import PageHeader from '@/components/PageHeader.vue';
import { getCallSuccessRate, getErrorDistribution, getSessions } from '@/api/monitor';

const loading = ref(false);
const autoRefresh = ref(false);
const dateRange = ref([]);
let autoRefreshTimer = null;

const successRate = ref({
  totalCalls: 0,
  successCalls: 0,
  failedCalls: 0,
  successRate: 0
});
const errorDistribution = ref([]);
const sessions = ref([]);

const barChartRef = ref(null);
const pieChartRef = ref(null);
let barChartInstance = null;
let pieChartInstance = null;

const queryParams = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return {};
  return {
    startTime: new Date(dateRange.value[0]).getTime(),
    endTime: new Date(dateRange.value[1]).getTime()
  };
});

const totalErrorCount = computed(() =>
  errorDistribution.value.reduce((sum, item) => sum + Number(item.count || 0), 0)
);

const failureRate = computed(() => {
  const total = Number(successRate.value.totalCalls || 0);
  if (!total) return 0;
  return Number(successRate.value.failedCalls || 0) / total;
});

const failureRateText = computed(() => formatPercent(failureRate.value));

const providerDistribution = computed(() => {
  const grouped = errorDistribution.value.reduce((acc, item) => {
    const provider = normalizeProvider(item.providerId);
    acc[provider] = (acc[provider] || 0) + Number(item.count || 0);
    return acc;
  }, {});

  return Object.entries(grouped)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value);
});

const topErrors = computed(() => errorDistribution.value.slice(0, 8));

const errorSessions = computed(() =>
  sessions.value
    .filter(item => Number(item.errors || 0) > 0)
    .sort((a, b) => Number(b.errors || 0) - Number(a.errors || 0))
);

const formatNumber = value => {
  const number = Number(value || 0);
  return number.toLocaleString('zh-CN');
};

const formatPercent = value => `${(Number(value || 0) * 100).toFixed(1)}%`;

const normalizeProvider = provider => {
  if (!provider) return '未知供应商';
  return String(provider).trim() || '未知供应商';
};

const handleDateRangeChange = () => {
  fetchErrorData();
};

const handleAutoRefreshChange = enabled => {
  if (enabled) {
    autoRefreshTimer = setInterval(fetchErrorData, 30000);
    return;
  }

  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
};

const fetchErrorData = async () => {
  loading.value = true;
  try {
    const [successData, distributionData, sessionData] = await Promise.all([
      getCallSuccessRate(queryParams.value),
      getErrorDistribution(queryParams.value),
      getSessions(50)
    ]);

    successRate.value = {
      totalCalls: Number(successData?.totalCalls || 0),
      successCalls: Number(successData?.successCalls || 0),
      failedCalls: Number(successData?.failedCalls || 0),
      successRate: Number(successData?.successRate || 0)
    };

    errorDistribution.value = (distributionData || [])
      .map(item => ({
        providerId: item.providerId,
        errorMessage: item.errorMessage || '未知错误',
        count: Number(item.count || 0)
      }))
      .sort((a, b) => b.count - a.count);

    sessions.value = Array.isArray(sessionData) ? sessionData : [];

    await nextTick();
    updateCharts();
  } catch (error) {
    console.error('Failed to fetch error stats:', error);
    ElMessage.error(error?.message || '获取错误统计数据失败');
  } finally {
    loading.value = false;
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

const updateBarChart = () => {
  if (!barChartRef.value || topErrors.value.length === 0) {
    barChartInstance?.dispose();
    barChartInstance = null;
    return;
  }

  if (!barChartInstance) {
    barChartInstance = echarts.init(barChartRef.value);
  }

  barChartInstance.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 12,
      right: 16,
      top: 16,
      bottom: 16,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      minInterval: 1
    },
    yAxis: {
      type: 'category',
      data: topErrors.value.map(item => item.errorMessage),
      axisLabel: {
        width: 220,
        overflow: 'truncate'
      }
    },
    series: [
      {
        type: 'bar',
        data: topErrors.value.map(item => item.count),
        barWidth: 18,
        itemStyle: {
          color: '#ef4444',
          borderRadius: [0, 6, 6, 0]
        }
      }
    ]
  });
};

const updatePieChart = () => {
  if (!pieChartRef.value || providerDistribution.value.length === 0) {
    pieChartInstance?.dispose();
    pieChartInstance = null;
    return;
  }

  if (!pieChartInstance) {
    pieChartInstance = echarts.init(pieChartRef.value);
  }

  pieChartInstance.setOption({
    tooltip: {
      trigger: 'item'
    },
    legend: {
      bottom: 0
    },
    series: [
      {
        type: 'pie',
        radius: ['45%', '72%'],
        center: ['50%', '42%'],
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          formatter: '{b}\n{d}%'
        },
        data: providerDistribution.value
      }
    ]
  });
};

const updateCharts = () => {
  updateBarChart();
  updatePieChart();
};

const handleResize = () => {
  barChartInstance?.resize();
  pieChartInstance?.resize();
};

const handleExport = () => {
  const headers = ['供应商', '错误消息', '次数', '占比'];
  const rows = errorDistribution.value.map(item => [
    normalizeProvider(item.providerId),
    (item.errorMessage || '').replace(/\n/g, ' '),
    item.count,
    formatPercent(totalErrorCount.value > 0 ? Number(item.count || 0) / totalErrorCount.value : 0)
  ]);

  const csvContent = '\ufeff' + [headers, ...rows]
    .map(row => row.map(cell => `"${String(cell ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.setAttribute('href', url);
  link.setAttribute('download', `Error_Report_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
  ElMessage.success('错误报告已导出');
};

onMounted(async () => {
  await fetchErrorData();
  window.addEventListener('resize', handleResize);
  window.addEventListener('page-refresh', fetchErrorData);
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('page-refresh', fetchErrorData);

  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }

  barChartInstance?.dispose();
  pieChartInstance?.dispose();
});
</script>

<style scoped>
.error-dashboard {
  padding: 24px;
  background: var(--bg-color, #f8fafc);
  min-height: 100vh;
}

.filters-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.date-range {
  width: 260px;
}

.auto-refresh {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 16px;
  border: 1px solid var(--border-color, #e2e8f0);
  color: var(--text-secondary, #64748b);
  font-size: 13px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card,
.chart-card,
.table-card {
  border-radius: 12px;
  border: 1px solid var(--border-color, #e2e8f0);
  background: var(--card-bg, #fff);
}

.stat-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}

.total-icon {
  background: rgba(239, 68, 68, 0.12);
  color: #dc2626;
}

.rate-icon {
  background: rgba(249, 115, 22, 0.12);
  color: #ea580c;
}

.category-icon {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.provider-icon {
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
}

.stat-content {
  flex: 1;
}

.stat-label,
.card-subtitle {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
}

.stat-value {
  margin-top: 4px;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 24px;
  margin-bottom: 24px;
}

.table-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 24px;
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.header-total {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}

.chart-wrap {
  min-height: 360px;
}

.chart {
  width: 100%;
  height: 360px;
}

.error-count {
  color: #dc2626;
  font-weight: 700;
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .error-dashboard {
    padding: 16px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .date-range {
    width: 100%;
  }
}
</style>
