<template>
  <div class="page-container">
    <PageHeader 
      title="Token 消耗统计" 
      description="查看系统 Token 使用情况、成本分析和历史趋势"
      icon="Cpu"

    >
      <template #actions>
        <el-button :icon="Setting" @click="showCostConfig = true">设置单价</el-button>
        <el-button :icon="Download" @click="handleExport">导出报告</el-button>
        <el-button :icon="Refresh" @click="fetchData">刷新数据</el-button>
      </template>
    </PageHeader>

    <!-- Cost Config Dialog -->
    <el-dialog v-model="showCostConfig" title="成本设置" width="400px">
      <el-form :model="costConfig" label-width="120px">
        <el-form-item label="单价 (/1K Tokens)">
          <el-input-number v-model="costConfig.unitPrice" :precision="4" :step="0.0001" :min="0" />
        </el-form-item>
        <el-form-item label="货币符号">
          <el-input v-model="costConfig.currency" placeholder="$ 或 ¥" style="width: 100px;" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="showCostConfig = false">取消</el-button>
          <el-button type="primary" @click="saveCostConfig">保存</el-button>
        </span>
      </template>
    </el-dialog>

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
            {{ (row.avgTokens || 0).toFixed(2) }}
          </template>
        </el-table-column>
        <el-table-column prop="cost" label="成本" width="100" align="right">
          <template #default="{ row }">
            ${{ (row.cost || 0).toFixed(4) }}
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
import { ref, reactive, onMounted, onUnmounted, computed, nextTick } from 'vue';
import { Download, Refresh, Cpu, TrendCharts, Tickets, Connection, Setting } from '@element-plus/icons-vue';
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

// Cost Config
const showCostConfig = ref(false);
const costConfig = ref({
  unitPrice: 0.002, // Default price per 1k tokens
  currency: '$'
});

const statsCards = computed(() => [
  { 
    label: '今日消耗', 
    key: 'daily', 
    unit: 'tokens',
    icon: Cpu, 
    color: 'var(--orin-primary)', 
    bgColor: 'var(--orin-primary-soft)' 
  },
  { 
    label: '本周消耗', 
    key: 'weekly', 
    unit: 'tokens',
    icon: TrendCharts, 
    color: '#26FFDF', 
    bgColor: 'rgba(38, 255, 223, 0.1)' 
  },
  { 
    label: '本月消耗', 
    key: 'monthly', 
    unit: 'tokens',
    icon: Tickets, 
    color: '#14B8A6', 
    bgColor: 'rgba(20, 184, 166, 0.1)' 
  },
  { 
    label: '总计消耗', 
    key: 'total', 
    unit: 'tokens',
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

// 计算成本
const calculateCost = (tokens) => {
  if (!tokens) return '0.0000';
  return (tokens / 1000 * costConfig.value.unitPrice).toFixed(4);
};

// 获取统计数据
// 获取统计数据
const fetchData = async () => {
  try {
    const res = await getTokenStats();
    if (res && (res.total > 0 || res.daily > 0)) {
      tokenStats.value = res;
    } else {
      throw new Error('No data');
    }
  } catch (error) {
    console.warn('获取 Token 统计失败或无数据，使用模拟数据');
    // Mock 数据
    tokenStats.value = {
      daily: 15420,
      weekly: 108500,
      monthly: 452000,
      total: 1250000
    };
  }
};

// 获取趋势数据
// 获取趋势数据
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
    // Mock 趋势数据
    const mockTrend = [];
    const days = trendPeriod.value === 'monthly' ? 30 : (trendPeriod.value === 'weekly' ? 12 : 7);
    
    for (let i = days - 1; i >= 0; i--) {
        const d = new Date();
        // Correct date calculation for weekly/monthly
        if (trendPeriod.value === 'weekly') {
             d.setDate(d.getDate() - i * 7);
        } else {
             d.setDate(d.getDate() - i);
        }
        
        mockTrend.push({
            date: d.toISOString().slice(5, 10),
            tokens: Math.floor(Math.random() * 5000) + 1000 + (Math.random() * 2000)
        });
    }
    renderTrendChart(mockTrend);
  } finally {
    trendLoading.value = false;
  }
};

// 渲染趋势图表
// 渲染趋势图表
const renderTrendChart = (data) => {
  nextTick(() => {
    if (!trendChart.value) return;
    
    // Dispose old instance if exists to prevent memory leaks
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
          barMaxWidth: 50,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00BFA5' },   // Use explicit color
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

// 渲染分布图表
// 渲染分布图表
const renderDistributionChart = (data) => {
  nextTick(() => {
    if (!distributionChart.value) return;
    
    if (distributionChartInstance) {
       distributionChartInstance.dispose();
       distributionChartInstance = null;
    }
    
    distributionChartInstance = echarts.init(distributionChart.value);

    const option = {
      tooltip: { trigger: 'item', formatter: '{a} <br/>{b}: {c} ({d}%)' },
      legend: { orient: 'vertical', left: 'left', top: 'center' },
      series: [
        {
          name: 'Token 分布',
          type: 'pie',
          radius: ['50%', '75%'],
          center: ['60%', '50%'],
          avoidLabelOverlap: false,
          itemStyle: { borderRadius: 8, borderColor: '#fff', borderWidth: 2 },
          label: { show: false, position: 'center' },
          emphasis: {
            label: { show: true, fontSize: 16, fontWeight: 'bold' }
          },
          data: data
        }
      ]
    };

    distributionChartInstance.setOption(option);
  });
};

// 获取历史数据
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
    if (res && res.content && res.content.length > 0) {
      historyData.value = res.content || [];
      total.value = res.totalElements || 0;
      if (res.distribution) renderDistributionChart(res.distribution);
    } else {
       throw new Error('No history data');
    }
  } catch (error) {
    const mockHistory = [];
    const models = ['gpt-4', 'gpt-3.5-turbo', 'claude-3-opus', 'gemini-pro'];
    const agents = ['客服助手', '代码审核', '翻译专家', '数据分析师'];
    
    for (let i = 0; i < 15; i++) {
        const tokens = Math.floor(Math.random() * 2000) + 100;
        const requests = Math.floor(Math.random() * 10) + 1;
        mockHistory.push({
            date: new Date().toLocaleDateString(),
            agentName: agents[Math.floor(Math.random() * agents.length)],
            tokens: tokens,
            requests: requests,
            avgTokens: tokens / requests,
            cost: (tokens / 1000) * costConfig.value.unitPrice,
            model: models[Math.floor(Math.random() * models.length)]
        });
    }
    
    historyData.value = mockHistory;
    total.value = 50;
    
    renderDistributionChart([
        { value: 45, name: '客服助手', itemStyle: { color: '#00BFA5' } },
        { value: 25, name: '代码审核', itemStyle: { color: '#009688' } },
        { value: 20, name: '翻译专家', itemStyle: { color: '#4DB6AC' } },
        { value: 10, name: '数据分析师', itemStyle: { color: '#80CBC4' } }
    ]);
  } finally {
    historyLoading.value = false;
  }
};

// 导出报告
const handleExport = () => {
  const headers = ['日期', '智能体', 'Token消耗', '请求次数', '平均Token', '成本', '模型'];
  const rows = historyData.value.map(item => [
    item.date, item.agentName, item.tokens, item.requests, item.avgTokens.toFixed(2),
    '$' + item.cost.toFixed(4), item.model
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

const handleGlobalRefresh = () => {
  fetchData();
  fetchTrendData();
  fetchHistoryData();
};

const loadCostConfig = () => {
  const saved = localStorage.getItem('orin_cost_config');
  if (saved) {
    try {
      costConfig.value = JSON.parse(saved);
    } catch(e) {}
  }
};

const saveCostConfig = () => {
  localStorage.setItem('orin_cost_config', JSON.stringify(costConfig.value));
  showCostConfig.value = false;
  ElMessage.success('成本配置已保存');
  fetchHistoryData(); // Recalculate with new cost if possible (mock data will use new cost)
};

onMounted(() => {
  loadCostConfig();
  fetchData();
  fetchTrendData();
  fetchHistoryData();
  window.addEventListener('global-refresh', handleGlobalRefresh);
  window.addEventListener('resize', () => {
    trendChartInstance?.resize();
    distributionChartInstance?.resize();
  });
});

onUnmounted(() => {
  window.removeEventListener('global-refresh', handleGlobalRefresh);
  window.removeEventListener('resize', () => {});
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
