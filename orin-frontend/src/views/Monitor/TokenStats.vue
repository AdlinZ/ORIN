<template>
  <div class="page-container">
    <PageHeader 
      title="Token 消耗统计" 
      description="查看系统 Token 使用情况、成本分析和历史趋势"
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
              <div class="stat-value">{{ formatNumber(tokenStats[item.key]) }}</div>
              <div class="stat-unit">{{ item.unit }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图表 -->
    <el-row :gutter="20" style="margin-top: 24px;">
      <el-col :span="24">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">Token 消耗趋势</span>
              <el-radio-group v-model="trendPeriod" size="small" @change="fetchTrendData">
                <el-radio-button label="daily">每日</el-radio-button>
                <el-radio-button label="weekly">每周</el-radio-button>
                <el-radio-button label="monthly">每月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div v-loading="trendLoading" style="height: 400px;">
            <div ref="trendChart" style="width: 100%; height: 100%;"></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 成本分析 -->
    <el-row :gutter="20" style="margin-top: 24px;">
      <el-col :span="12">
        <el-card shadow="never" class="cost-card">
          <template #header>
            <span class="card-title">成本估算</span>
          </template>
          <div class="cost-content">
            <p class="cost-note">基于平均价格 $0.002 / 1K tokens</p>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="今日成本">
                <span class="cost-value">${{ calculateCost(tokenStats.daily) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="本周成本">
                <span class="cost-value">${{ calculateCost(tokenStats.weekly) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="本月成本">
                <span class="cost-value">${{ calculateCost(tokenStats.monthly) }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="总成本">
                <span class="cost-value highlight">${{ calculateCost(tokenStats.total) }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never" class="distribution-card">
          <template #header>
            <span class="card-title">Token 分布</span>
          </template>
          <div v-loading="distributionLoading" style="height: 300px;">
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
      <el-table :data="historyData" v-loading="historyLoading" stripe>
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="agentName" label="智能体" width="150" />
        <el-table-column prop="tokens" label="Token 消耗" width="120" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.tokens) }}
          </template>
        </el-table-column>
        <el-table-column prop="requests" label="请求次数" width="100" align="center" />
        <el-table-column prop="avgTokens" label="平均 Token/请求" width="150" align="right">
          <template #default="{ row }">
            {{ row.avgTokens.toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="成本" width="100" align="right">
          <template #default="{ row }">
            ${{ row.cost.toFixed(4) }}
          </template>
        </el-table-column>
        <el-table-column prop="model" label="模型" show-overflow-tooltip />
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
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue';
import { Download, Refresh, Cpu, TrendCharts, Tickets, Connection } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { getTokenStats, getTokenHistory, getTokenTrend } from '@/api/monitor';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';

const tokenStats = ref({
  daily: 0,
  weekly: 0,
  monthly: 0,
  total: 0
});

const trendPeriod = ref('daily');
const trendLoading = ref(false);
const distributionLoading = ref(false);
const historyLoading = ref(false);
const dateRange = ref([]);
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const historyData = ref([]);

const trendChart = ref(null);
const distributionChart = ref(null);
let trendChartInstance = null;
let distributionChartInstance = null;

const statsCards = computed(() => [
  { 
    label: '今日消耗', 
    key: 'daily', 
    unit: 'tokens',
    icon: Cpu, 
    color: '#409EFF', 
    bgColor: 'rgba(64, 158, 255, 0.1)' 
  },
  { 
    label: '本周消耗', 
    key: 'weekly', 
    unit: 'tokens',
    icon: TrendCharts, 
    color: '#67C23A', 
    bgColor: 'rgba(103, 194, 58, 0.1)' 
  },
  { 
    label: '本月消耗', 
    key: 'monthly', 
    unit: 'tokens',
    icon: Tickets, 
    color: '#E6A23C', 
    bgColor: 'rgba(230, 162, 60, 0.1)' 
  },
  { 
    label: '总计消耗', 
    key: 'total', 
    unit: 'tokens',
    icon: Connection, 
    color: '#F56C6C', 
    bgColor: 'rgba(245, 108, 108, 0.1)' 
  }
]);

// 格式化数字
const formatNumber = (num) => {
  if (!num) return '0';
  return num.toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,');
};

// 计算成本
const calculateCost = (tokens) => {
  if (!tokens) return '0.0000';
  return (tokens / 1000 * 0.002).toFixed(4);
};

// 获取统计数据
const fetchData = async () => {
  try {
    const res = await getTokenStats();
    if (res && res.data) {
      tokenStats.value = res.data;
    }
  } catch (error) {
    console.warn('获取 Token 统计失败，使用模拟数据:', error);
    // Mock 数据
    tokenStats.value = {
      daily: 15420,
      weekly: 108500,
      monthly: 452000,
      total: 1250000
    };
    ElMessage.warning({
        message: '获取数据失败，已切换至演示数据',
        duration: 3000
    });
  }
};

// 获取趋势数据
const fetchTrendData = async () => {
  trendLoading.value = true;
  try {
    const res = await getTokenTrend(trendPeriod.value);
    if (res && res.data) {
      renderTrendChart(res.data);
    }
  } catch (error) {
    console.warn('获取趋势数据失败，使用模拟数据:', error);
    // Mock 趋势数据 (过去7天)
    const mockTrend = [];
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        mockTrend.push({
            date: d.toISOString().slice(5, 10),
            tokens: Math.floor(Math.random() * 5000) + 1000
        });
    }
    renderTrendChart(mockTrend);
  } finally {
    trendLoading.value = false;
  }
};

// 渲染趋势图表
const renderTrendChart = (data) => {
  if (!trendChartInstance) {
    trendChartInstance = echarts.init(trendChart.value);
  }

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      axisLabel: {
        rotate: 45
      }
    },
    yAxis: {
      type: 'value',
      name: 'Tokens'
    },
    series: [
      {
        name: 'Token 消耗',
        type: 'bar',
        data: data.map(item => item.tokens),
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409EFF' },
            { offset: 1, color: '#67C23A' }
          ])
        }
      }
    ]
  };

  trendChartInstance.setOption(option);
};

// 渲染分布图表
const renderDistributionChart = (data) => {
  if (!distributionChartInstance) {
    distributionChartInstance = echarts.init(distributionChart.value);
  }

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{a} <br/>{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      left: 'left'
    },
    series: [
      {
        name: 'Token 分布',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: true,
          formatter: '{b}: {d}%'
        },
        data: data
      }
    ]
  };

  distributionChartInstance.setOption(option);
};

// 获取历史数据
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

    const res = await getTokenHistory(params);
    if (res && res.data) {
      historyData.value = res.data.content || [];
      total.value = res.data.totalElements || 0;

      // 同时获取分布数据
      if (res.data.distribution) {
        renderDistributionChart(res.data.distribution);
      }
    }
  } catch (error) {
    console.warn('获取历史数据失败，使用模拟数据:', error);
    
    // Mock 历史数据
    const mockHistory = [];
    const models = ['gpt-4', 'gpt-3.5-turbo', 'claude-3-opus', 'gemini-pro'];
    const agents = ['客服助手', '代码审核', '翻译专家', '数据分析师'];
    
    for (let i = 0; i < 10; i++) {
        const tokens = Math.floor(Math.random() * 2000) + 100;
        const requests = Math.floor(Math.random() * 10) + 1;
        mockHistory.push({
            date: new Date().toLocaleDateString(),
            agentName: agents[Math.floor(Math.random() * agents.length)],
            tokens: tokens,
            requests: requests,
            avgTokens: tokens / requests,
            cost: (tokens / 1000) * 0.002,
            model: models[Math.floor(Math.random() * models.length)]
        });
    }
    
    historyData.value = mockHistory;
    total.value = 50;
    
    // Mock 分布数据
    renderDistributionChart([
        { value: 45, name: '客服助手' },
        { value: 25, name: '代码审核' },
        { value: 20, name: '翻译专家' },
        { value: 10, name: '数据分析师' }
    ]);
    
  } finally {
    historyLoading.value = false;
  }
};

// 导出报告
const handleExport = () => {
  const headers = ['日期', '智能体', 'Token消耗', '请求次数', '平均Token', '成本', '模型'];
  const rows = historyData.value.map(item => [
    item.date,
    item.agentName,
    item.tokens,
    item.requests,
    item.avgTokens.toFixed(2),
    '$' + item.cost.toFixed(4),
    item.model
  ]);

  const csvContent = "\ufeff" + [headers, ...rows].map(e => e.join(",")).join("\n");
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `Token_Report_${new Date().toISOString().slice(0, 10)}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  
  ElMessage.success('报告导出成功');
};

// 监听全局刷新事件
const handleGlobalRefresh = () => {
  fetchData();
  fetchTrendData();
  fetchHistoryData();
};

onMounted(() => {
  fetchData();
  fetchTrendData();
  fetchHistoryData();
  
  // 监听全局刷新事件
  window.addEventListener('global-refresh', handleGlobalRefresh);
  
  // 监听窗口大小变化
  window.addEventListener('resize', () => {
    trendChartInstance?.resize();
    distributionChartInstance?.resize();
  });
});

onUnmounted(() => {
  window.removeEventListener('global-refresh', handleGlobalRefresh);
  window.removeEventListener('resize', () => {
    trendChartInstance?.resize();
    distributionChartInstance?.resize();
  });
  
  trendChartInstance?.dispose();
  distributionChartInstance?.dispose();
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-5px);
  box-shadow: var(--shadow-premium) !important;
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-content {
  flex: 1;
}

.stat-label {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-bottom: 8px;
  font-weight: 600;
  text-transform: uppercase;
}

.stat-value {
  font-size: 28px;
  font-weight: 800;
  color: var(--neutral-gray-900);
  font-family: var(--font-heading);
  margin-bottom: 4px;
}

.stat-unit {
  font-size: 11px;
  color: var(--neutral-gray-400);
}

.chart-card,
.cost-card,
.distribution-card,
.table-card {
  border-radius: var(--radius-lg) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.cost-content {
  padding: 10px 0;
}

.cost-note {
  font-size: 12px;
  color: var(--neutral-gray-500);
  margin-bottom: 16px;
  text-align: center;
}

.cost-value {
  font-size: 16px;
  font-weight: 700;
  color: var(--primary-color);
}

.cost-value.highlight {
  font-size: 20px;
  background: linear-gradient(135deg, var(--primary-600) 0%, var(--warning-color) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
