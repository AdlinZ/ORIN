<template>
  <div class="page-container">
    <PageHeader 
      title="资源消耗分析" 
      description="查看全平台（文本、图形、视频等）资源消耗明细、成本分布和增长趋势"
      icon="Cpu"

    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport">导出报告</el-button>
        <el-button :icon="Refresh" @click="fetchData">刷新数据</el-button>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="(item, index) in statsCards" :key="index">
        <el-card shadow="hover" class="stat-card" :body-style="{ padding: '24px' }">
          <div class="stat-card-inner">
            <div class="stat-icon" :style="{ backgroundColor: item.bgColor }">
              <el-icon :style="{ color: item.color }"><component :is="item.icon" /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">{{ item.label }}</div>
              <div class="stat-value">
                <span class="cost-currency">{{ currency }}</span>
                {{ (tokenStats[item.costKey] || 0).toFixed(2) }}
              </div>
              <div class="stat-sub">
                <span class="tokens-label">Tokens:</span>
                <span class="tokens-value">{{ formatNumber(tokenStats[item.key]) }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势与分布图表 -->
    <el-row :gutter="20" style="margin-top: 24px;">
      <el-col :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">资源消耗趋势</span>
              <el-radio-group v-model="trendPeriod" size="small" @change="fetchTrendData">
                <el-radio-button value="daily">每日</el-radio-button>
                <el-radio-button value="weekly">每周</el-radio-button>
                <el-radio-button value="monthly">每月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-loading="trendLoading" style="height: 400px;">
            <div ref="trendChart" style="width: 100%; height: 100%;"></div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never" class="distribution-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">渠道分布</span>
              <el-select v-model="distType" size="small" style="width: 100px;" @change="fetchDistributionData">
                <el-option label="按成本" value="cost" />
                <el-option label="按 Token" value="token" />
              </el-select>
            </div>
          </template>
          <div v-loading="distributionLoading" style="height: 400px;">
            <div ref="distributionChart" style="width: 100%; height: 100%;"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 历史记录表格 -->
    <el-card shadow="never" class="table-card" style="margin-top: 24px;">
      <template #header>
        <div class="card-header">
          <span class="card-title">Token 使用历史</span>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            size="small"
            @change="fetchHistoryData"
          />
        </div>
      </template>
      <el-table border :data="historyData" v-loading="historyLoading" stripe>
        <el-table-column prop="createdAt" label="时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="providerId" label="智能体" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getAgentName(row.providerId) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="Token 消耗" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column prop="responseTime" label="耗时 (ms)" width="100" align="center" />
        <el-table-column prop="model" label="模型" min-width="200" show-overflow-tooltip />
        <el-table-column prop="estimatedCost" label="费用 (Cost)" min-width="100" align="right">
          <template #default="{ row }">
            ${{ (row.estimatedCost || 0).toFixed(6) }}
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
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { Download, Refresh, Cpu, TrendCharts, Tickets, Connection } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { getTokenStats, getTokenHistory, getTokenTrend, getAgentList, getTokenDistribution, getCostDistribution } from '@/api/monitor';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';

const tokenStats = ref({
  daily: 0,
  weekly: 0,
  monthly: 0,
  total: 0,
  daily_cost: 0,
  weekly_cost: 0,
  monthly_cost: 0,
  total_cost: 0
});

const trendPeriod = ref('daily');
const distType = ref('cost'); // 'token' or 'cost'
const trendLoading = ref(false);
const distributionLoading = ref(false);
const historyLoading = ref(false);
const dateRange = ref([]);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const historyData = ref([]);
const agentMap = ref({});
const currency = ref('¥');

const trendChart = ref(null);
const distributionChart = ref(null);
let trendChartInstance = null;
let distributionChartInstance = null;

const statsCards = computed(() => [
  { 
    label: '今日消耗', 
    key: 'daily', 
    costKey: 'daily_cost',
    icon: Cpu, 
    color: 'var(--orin-primary)', 
    bgColor: 'var(--orin-primary-soft)' 
  },
  { 
    label: '本周消耗', 
    key: 'weekly', 
    costKey: 'weekly_cost',
    icon: TrendCharts, 
    color: '#26FFDF', 
    bgColor: 'rgba(38, 255, 223, 0.1)' 
  },
  { 
    label: '本月消耗', 
    key: 'monthly', 
    costKey: 'monthly_cost',
    icon: Tickets, 
    color: '#14B8A6', 
    bgColor: 'rgba(20, 184, 166, 0.1)' 
  },
  { 
    label: '年度总计', 
    key: 'total', 
    costKey: 'total_cost',
    icon: Connection, 
    color: '#0D9488', 
    bgColor: 'rgba(13, 148, 136, 0.1)' 
  }
]);

// 格式化数字
const formatNumber = (num) => {
  if (!num) return '0';
  return num.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,');
};

const formatDateTime = (val) => {
  if (!val) return '-';
  return new Date(val).toLocaleString();
};

const getAgentName = (id) => {
  if (!id) return '-';
  return agentMap.value[id] || id;
};

// 获取 Agent 列表映射
const fetchAgents = async () => {
    try {
        const list = await getAgentList();
        if (list) {
            list.forEach(agent => {
                agentMap.value[agent.agentId] = agent.agentName;
            });
        }
    } catch (e) { console.warn(e); }
};

// 获取统计数据
const fetchData = async () => {
  try {
    const res = await getTokenStats();
    if (res) {
      tokenStats.value = res;
    }
  } catch (error) {
    console.warn('获取统计数据失败');
  }
};

// 获取趋势数据
const fetchTrendData = async () => {
  trendLoading.value = true;
  try {
    const res = await getTokenTrend(trendPeriod.value);
    if (res && res.length > 0) {
      renderTrendChart(res);
    } else {
      throw new Error('No trend data');
    }
  } catch (error) {
    console.warn('Trend data fetch error', error);
  } finally {
    trendLoading.value = false;
  }
};

// 渲染趋势图表
const renderTrendChart = (data) => {
  nextTick(() => {
    if (!trendChart.value) return;
    if (trendChartInstance) {
      trendChartInstance.dispose();
      trendChartInstance = null;
    }
    trendChartInstance = echarts.init(trendChart.value);

    const option = {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' }
      },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { 
        type: 'category', 
        data: data.map(item => item.date), 
        axisLabel: { rotate: 0 } 
      },
      yAxis: { type: 'value', name: 'Tokens' },
      series: [
        {
          name: 'Token 消耗',
          type: 'bar',
          data: data.map(item => item.tokens),
          barMaxWidth: 30,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00BFA5' },
              { offset: 1, color: '#64FFDA' }
            ]),
            borderRadius: [4, 4, 0, 0]
          },
          animationDuration: 1000
        }
      ]
    };
    trendChartInstance.setOption(option);
  });
};

// 获取分布数据 (Support both Token and Cost)
const fetchDistributionData = async () => {
    distributionLoading.value = true;
    try {
        const params = {};
        if (dateRange.value && dateRange.value.length === 2) {
            params.startDate = dateRange.value[0].getTime();
            params.endDate = dateRange.value[1].getTime();
        }
        
        const res = distType.value === 'cost' 
            ? await getCostDistribution(params)
            : await getTokenDistribution(params);
            
        renderDistributionChart(res || []);
    } catch (e) {
        console.warn('Fetch distribution failed', e);
        renderDistributionChart([]);
    } finally {
        distributionLoading.value = false;
    }
};

// 渲染分布图表
const renderDistributionChart = (data) => {
  nextTick(() => {
    if (!distributionChart.value) return;
    if (distributionChartInstance) {
       distributionChartInstance.dispose();
       distributionChartInstance = null;
    }
    distributionChartInstance = echarts.init(distributionChart.value);

    const unit = distType.value === 'cost' ? currency.value : 'Tokens';

    const option = {
      tooltip: { trigger: 'item', formatter: `{a} <br/>{b}: {c} (${unit}) ({d}%)` },
      legend: { orient: 'horizontal', bottom: '0', icon: 'circle' },
      series: [
        {
          name: distType.value === 'cost' ? '成本预览' : '消耗预览',
          type: 'pie',
          radius: ['45%', '70%'],
          center: ['50%', '45%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: {
            label: { show: true, fontSize: 14, fontWeight: 'bold' }
          },
          data: data
        }
      ]
    };

    distributionChartInstance.setOption(option);
  });
};

// 获取历史数据
const fetchHistoryData = async () => {
  historyLoading.value = true;
  try {
    const params = { page: currentPage.value - 1, size: pageSize.value };
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0].getTime();
      params.endDate = dateRange.value[1].getTime();
    }

    const res = await getTokenHistory(params);
    if (res && res.content) {
      historyData.value = res.content || [];
      total.value = res.totalElements || 0;
    }
  } catch (error) {
    console.warn('获取历史数据失败:', error);
    historyData.value = [];
    total.value = 0;
  } finally {
    historyLoading.value = false;
  }
  fetchDistributionData();
};

// 导出报告
const handleExport = () => {
  const headers = ['日期', '智能体', 'Token消耗', '费用', '模型'];
  const rows = historyData.value.map(item => [
    formatDateTime(item.createdAt), getAgentName(item.providerId), item.totalTokens,
    currency.value + (item.estimatedCost || 0).toFixed(6), item.model
  ]);

  const csvContent = "\ufeff" + [headers, ...rows].map(e => e.join(",")).join("\n");
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `Consumption_Report_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  ElMessage.success('报告导出成功');
};

const handleGlobalRefresh = () => {
  fetchAgents();
  fetchData();
  fetchTrendData();
  fetchHistoryData();
};

onMounted(() => {
  fetchAgents();
  fetchData();
  fetchTrendData();
  fetchHistoryData();
  window.addEventListener('global-refresh', handleGlobalRefresh);
  window.addEventListener('resize', () => {
    trendChartInstance?.resize();
    distributionChartInstance?.resize();
  });
});
</script>

<style scoped>
.page-container { padding: 0; }
.stats-row { margin-bottom: 24px; }
.stat-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
  transition: all 0.3s ease;
}
.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-premium) !important;
}
.stat-card-inner { display: flex; align-items: center; gap: 16px; }
.stat-icon {
  width: 48px; height: 48px;
  border-radius: var(--radius-lg);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.stat-content { flex: 1; }
.stat-label {
  font-size: 11px; color: var(--neutral-gray-500);
  margin-bottom: 4px; font-weight: 600; text-transform: uppercase;
}
.stat-value {
  font-size: 22px; font-weight: 800; color: var(--neutral-gray-900);
  font-family: var(--font-heading); line-height: 1.2;
}
.cost-currency { font-size: 14px; margin-right: 2px; color: var(--neutral-gray-500); }
.stat-sub {
  margin-top: 4px; font-size: 11px; color: var(--neutral-gray-400);
  display: flex; gap: 4px; border-top: 1px solid var(--neutral-gray-50); padding-top: 4px;
}
.tokens-value { color: var(--neutral-gray-600); font-weight: 500; }

.chart-card, .distribution-card, .table-card {
  border-radius: var(--radius-lg) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 15px; font-weight: 700; color: var(--neutral-gray-900); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }

/* Dark Mode Adaptation */
html.dark .chart-card,
html.dark .distribution-card,
html.dark .table-card,
html.dark .stat-card {
  background-color: var(--neutral-gray-900);
  border-color: var(--neutral-gray-800) !important;
}

html.dark .card-title {
  color: var(--neutral-white);
}

html.dark .stat-label {
  color: var(--neutral-gray-400);
}

html.dark .stat-value {
  color: var(--neutral-white);
}

html.dark .stat-sub {
  border-top-color: var(--neutral-gray-800);
}

html.dark .tokens-value {
  color: var(--neutral-gray-400);
}

html.dark .cost-currency {
  color: var(--neutral-gray-500);
}
</style>
