<template>
  <div class="cost-dashboard">
    <PageHeader
      title="成本分析"
      description="查看模型调用成本、供应商分布与成本占比"
      icon="Money"
    >
      <template #actions>
        <el-button :icon="RefreshRight" @click="fetchCostData">
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
        <div class="stat-icon today-icon">
          <el-icon><Calendar /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            今日成本
          </div>
          <div class="stat-value">
            {{ formatCurrency(costSummary.daily) }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon week-icon">
          <el-icon><Histogram /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            本周成本
          </div>
          <div class="stat-value">
            {{ formatCurrency(costSummary.weekly) }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon month-icon">
          <el-icon><DataAnalysis /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            本月成本
          </div>
          <div class="stat-value">
            {{ formatCurrency(costSummary.monthly) }}
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="stat-card">
        <div class="stat-icon total-icon">
          <el-icon><Money /></el-icon>
        </div>
        <div class="stat-content">
          <div class="stat-label">
            累计成本
          </div>
          <div class="stat-value">
            {{ formatCurrency(costSummary.total) }}
          </div>
        </div>
      </el-card>
    </div>

    <div class="content-grid">
      <el-card shadow="never" class="distribution-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                供应商成本分布
              </h3>
              <p class="card-subtitle">
                按模型供应商汇总最近时间范围内的成本消耗
              </p>
            </div>
            <div class="header-total">
              {{ formatCurrency(distributionTotal) }}
            </div>
          </div>
        </template>
        <div v-loading="loading" class="chart-wrap">
          <div v-if="distributionData.length > 0" ref="pieChartRef" class="chart" />
          <el-empty v-else description="暂无成本数据" :image-size="72" />
        </div>
      </el-card>

      <el-card shadow="never" class="ranking-card">
        <template #header>
          <div class="card-header">
            <div>
              <h3 class="card-title">
                成本排行
              </h3>
              <p class="card-subtitle">
                便于快速看出主要成本来源
              </p>
            </div>
          </div>
        </template>
        <div v-loading="loading" class="ranking-list">
          <div
            v-for="(item, index) in distributionData"
            :key="`${item.name}-${index}`"
            class="ranking-item"
          >
            <div class="ranking-main">
              <div class="ranking-index">
                {{ index + 1 }}
              </div>
              <div class="ranking-info">
                <div class="ranking-name">
                  {{ item.name || '未知供应商' }}
                </div>
                <div class="ranking-share">
                  占比 {{ formatPercent(item.share) }}
                </div>
              </div>
            </div>
            <div class="ranking-value">
              {{ formatCurrency(item.value) }}
            </div>
          </div>
          <el-empty v-if="distributionData.length === 0" description="暂无排行数据" :image-size="72" />
        </div>
      </el-card>
    </div>

    <el-card shadow="never" class="table-card">
      <template #header>
        <div class="card-header">
          <div>
            <h3 class="card-title">
              成本明细
            </h3>
            <p class="card-subtitle">
              当前仅按供应商聚合，后续如需要我也可以再帮你补模型级明细
            </p>
          </div>
        </div>
      </template>
      <el-table
        v-loading="loading"
        :data="distributionData"
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
          prop="name"
          label="供应商"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="成本" min-width="160" align="right">
          <template #default="{ row }">
            {{ formatCurrency(row.value) }}
          </template>
        </el-table-column>
        <el-table-column label="占比" min-width="120" align="right">
          <template #default="{ row }">
            {{ formatPercent(row.share) }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { Calendar, DataAnalysis, Histogram, Money, Monitor, RefreshRight } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import PageHeader from '@/components/PageHeader.vue';
import { getCostDistribution, getTokenStats } from '@/api/monitor';

const loading = ref(false);
const autoRefresh = ref(false);
const dateRange = ref([]);
let autoRefreshTimer = null;

const pieChartRef = ref(null);
let pieChartInstance = null;

const costSummary = ref({
  daily: 0,
  weekly: 0,
  monthly: 0,
  total: 0
});

const distributionData = ref([]);

const distributionParams = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return {};
  return {
    startDate: new Date(dateRange.value[0]).getTime(),
    endDate: new Date(dateRange.value[1]).getTime()
  };
});

const distributionTotal = computed(() => distributionData.value.reduce((sum, item) => sum + Number(item.value || 0), 0));

const fetchCostData = async () => {
  loading.value = true;
  try {
    const [statsData, costData] = await Promise.all([
      getTokenStats(),
      getCostDistribution(distributionParams.value)
    ]);

    costSummary.value = {
      daily: Number(statsData?.daily_cost || 0),
      weekly: Number(statsData?.weekly_cost || 0),
      monthly: Number(statsData?.monthly_cost || 0),
      total: Number(statsData?.total_cost || 0)
    };

    const total = (costData || []).reduce((sum, item) => sum + Number(item.value || 0), 0);
    distributionData.value = (costData || [])
      .map(item => ({
        ...item,
        value: Number(item.value || 0),
        share: total > 0 ? Number(item.value || 0) / total : 0
      }))
      .sort((a, b) => b.value - a.value);

    await nextTick();
    updatePieChart();
  } catch (error) {
    console.error('Failed to fetch cost data:', error);
    ElMessage.error(error?.message || '获取成本数据失败');
  } finally {
    loading.value = false;
    window.dispatchEvent(new Event('page-refresh-done'));
  }
};

const initPieChart = () => {
  if (!pieChartRef.value) return;
  pieChartInstance = echarts.init(pieChartRef.value);
  updatePieChart();
};

const updatePieChart = () => {
  if (!pieChartInstance) return;

  const isDark = document.documentElement.classList.contains('dark');
  const textColor = isDark ? '#e2e8f0' : '#1e293b';
  const subTextColor = isDark ? '#94a3b8' : '#64748b';

  pieChartInstance.setOption({
    tooltip: {
      trigger: 'item',
      formatter: params => `${params.name}<br/>${formatCurrency(params.value)} (${params.percent}%)`
    },
    legend: {
      bottom: 0,
      textStyle: { color: subTextColor }
    },
    series: [
      {
        name: '成本分布',
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '44%'],
        avoidLabelOverlap: true,
        label: {
          color: textColor,
          formatter: ({ name, percent }) => `${name}\n${percent}%`
        },
        itemStyle: {
          borderRadius: 8,
          borderColor: isDark ? '#0f172a' : '#ffffff',
          borderWidth: 2
        },
        data: distributionData.value.map(item => ({
          name: item.name,
          value: item.value
        }))
      }
    ]
  });
};

const formatCurrency = value => {
  const amount = Number(value || 0);
  return `¥${amount.toFixed(2)}`;
};

const formatPercent = value => `${(Number(value || 0) * 100).toFixed(1)}%`;

const handleDateRangeChange = () => {
  fetchCostData();
};

const handleAutoRefreshChange = enabled => {
  if (enabled) {
    autoRefreshTimer = setInterval(fetchCostData, 30000);
    return;
  }

  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
};

const handleResize = () => {
  pieChartInstance?.resize();
};

let themeObserver = null;

onMounted(async () => {
  await fetchCostData();
  nextTick(() => {
    initPieChart();
  });

  window.addEventListener('resize', handleResize);
  window.addEventListener('page-refresh', fetchCostData);

  themeObserver = new MutationObserver(() => {
    updatePieChart();
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  });
});

onUnmounted(() => {
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('page-refresh', fetchCostData);
  if (autoRefreshTimer) {
    clearInterval(autoRefreshTimer);
    autoRefreshTimer = null;
  }
  themeObserver?.disconnect();
  pieChartInstance?.dispose();
});
</script>

<style scoped>
.cost-dashboard {
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
.distribution-card,
.ranking-card,
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

.today-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.week-icon {
  background: rgba(59, 130, 246, 0.12);
  color: #2563eb;
}

.month-icon {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

.total-icon {
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

.ranking-list {
  min-height: 360px;
}

.ranking-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
}

.ranking-item:last-child {
  border-bottom: none;
}

.ranking-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.ranking-index {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--orin-primary-soft);
  color: var(--orin-primary);
  font-weight: 700;
}

.ranking-name {
  color: var(--text-primary, #1e293b);
  font-weight: 600;
}

.ranking-share {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary, #64748b);
}

.ranking-value {
  font-weight: 700;
  color: var(--text-primary, #1e293b);
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
  .cost-dashboard {
    padding: 16px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .chart,
  .chart-wrap,
  .ranking-list {
    min-height: 300px;
  }
}
</style>
