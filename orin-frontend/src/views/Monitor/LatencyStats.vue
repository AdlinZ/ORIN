<template>
  <div class="page-container">
    <PageHeader 
      title="延迟统计分析" 
      description="分析系统响应速度、延迟趋势及性能瓶颈"
      icon="Connection"
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
              <div class="stat-value">{{ formatNumber(latencyStats[item.key]) }}</div>
              <div class="stat-unit">{{ item.unit }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图表 -->
    <el-row :gutter="20" style="margin-top: 24px;">
      <el-col :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span class="card-title">平均延迟趋势</span>
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
      <el-col :span="8">
        <el-card shadow="never" class="distribution-card">
          <template #header>
            <span class="card-title">延迟分布 (当前页)</span>
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
          <span class="card-title">响应延迟历史</span>
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
        <el-table-column prop="createdAt" label="时间" width="180">
            <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
            </template>
        </el-table-column>
        <el-table-column prop="providerId" label="Agent ID/Name" width="200" show-overflow-tooltip />
        <el-table-column prop="responseTime" label="响应耗时" width="150" align="right">
          <template #default="{ row }">
            <span :class="getLatencyClass(row.responseTime)">{{ formatNumber(row.responseTime) }} ms</span>
          </template>
        </el-table-column>
        <el-table-column prop="totalTokens" label="Total Tokens" width="150" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalTokens) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
             <template #default="{ row }">
                <el-tag v-if="row.status === 'success'" type="success" size="small">成功</el-tag>
                <el-tag v-else type="danger" size="small">失败</el-tag>
             </template>
        </el-table-column>
        <el-table-column prop="error" label="错误信息" show-overflow-tooltip />
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
import { Download, Refresh, Timer, TrendCharts, WarningFilled, Connection } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import { getLatencyStats, getLatencyHistory, getLatencyTrend } from '@/api/monitor';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';

const latencyStats = ref({
  daily: 0,
  weekly: 0,
  monthly: 0,
  max: 0
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
    label: '今日平均延迟', 
    key: 'daily', 
    unit: 'ms',
    icon: Timer, 
    color: 'var(--orin-primary)', 
    bgColor: 'var(--orin-primary-fade)' 
  },
  { 
    label: '本周平均延迟', 
    key: 'weekly', 
    unit: 'ms',
    icon: TrendCharts, 
    color: '#67C23A', 
    bgColor: 'rgba(103, 194, 58, 0.1)' 
  },
  { 
    label: '本月平均延迟', 
    key: 'monthly', 
    unit: 'ms',
    icon: Connection, 
    color: '#E6A23C', 
    bgColor: 'rgba(230, 162, 60, 0.1)' 
  },
  { 
    label: '历史峰值延迟', 
    key: 'max', 
    unit: 'ms',
    icon: WarningFilled, 
    color: '#F56C6C', 
    bgColor: 'rgba(245, 108, 108, 0.1)' 
  }
]);

const formatNumber = (num) => {
  if (num === null || num === undefined) return '0';
  return Math.round(num).toString().replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1,');
};

const formatDateTime = (val) => {
    if(!val) return '-';
    // Back-end returns array [yyyy, MM, dd, HH, mm, ss] or timestamp?
    // AuditLog usually serializes timestamps as arrays or ISO strings depending on Jackson config.
    // Let's assume ISO string or timestamp number simply.
    return new Date(val).toLocaleString();
}

const getLatencyClass = (val) => {
    if (!val) return '';
    if (val > 5000) return 'text-danger';
    if (val > 2000) return 'text-warning';
    return 'text-success';
}

// 获取统计数据
const fetchData = async () => {
  try {
    const res = await getLatencyStats();
    if (res && res.data) {
      latencyStats.value = res.data;
    }
  } catch (error) {
    console.warn('获取延迟统计失败，使用模拟数据:', error);
    latencyStats.value = {
      daily: 450,
      weekly: 420,
      monthly: 480,
      max: 5200
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
    const res = await getLatencyTrend(trendPeriod.value);
    if (res && res.data) {
      renderTrendChart(res.data);
    }
  } catch (error) {
    console.warn('获取趋势数据失败，使用模拟数据:', error);
    const mockTrend = [];
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        mockTrend.push({
            date: d.toISOString().slice(5, 10),
            latency: Math.floor(Math.random() * 800) + 200
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
      trigger: 'axis'
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      data: data.map(item => item.date),
      axisLabel: { rotate: 45 }
    },
    yAxis: { type: 'value', name: 'Latency (ms)' },
    series: [
      {
        name: '平均延迟',
        type: 'line',
        smooth: true,
        data: data.map(item => item.latency),
        itemStyle: { color: '#67C23A' },
        areaStyle: {
             color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103, 194, 58, 0.5)' },
            { offset: 1, color: 'rgba(103, 194, 58, 0.1)' }
          ])
        }
      }
    ]
  };
  trendChartInstance.setOption(option);
};

// 渲染分布图表（基于当前数据简单分类）
const updateDistribution = (logs) => {
    let fast = 0, medium = 0, slow = 0, verySlow = 0;
    logs.forEach(log => {
        const l = log.responseTime || 0;
        if (l < 500) fast++;
        else if (l < 2000) medium++;
        else if (l < 5000) slow++;
        else verySlow++;
    });

    const data = [
        { value: fast, name: '< 500ms' },
        { value: medium, name: '500-2s' },
        { value: slow, name: '2s-5s' },
        { value: verySlow, name: '> 5s' }
    ];

    if (!distributionChartInstance) {
        distributionChartInstance = echarts.init(distributionChart.value);
    }
    distributionChartInstance.setOption({
        tooltip: { trigger: 'item' },
        legend: { top: '5%', left: 'center' },
        series: [{
            name: '延迟分布',
            type: 'pie',
            radius: ['40%', '70%'],
            avoidLabelOverlap: false,
            itemStyle: { borderRadius: 10, borderColor: '#fff', borderWidth: 2 },
            label: { show: false, position: 'center' },
            emphasis: { label: { show: true, fontSize: 20, fontWeight: 'bold' } },
            data: data
        }]
    });
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

    const res = await getLatencyHistory(params);
    if (res && res.data) {
      historyData.value = res.data.content || [];
      total.value = res.data.totalElements || 0;
      updateDistribution(historyData.value);
    }
  } catch (error) {
    console.warn('获取历史数据失败，使用模拟数据:', error);
    
    // Mock History
    const mockHistory = [];
    for (let i = 0; i < 10; i++) {
        const latency = Math.floor(Math.random() * 3000) + 100;
        mockHistory.push({
            createdAt: new Date().toISOString(),
            providerId: 'demo-agent-' + Math.floor(Math.random()*5),
            responseTime: latency,
            totalTokens: Math.floor(Math.random() * 2000),
            status: 'success'
        });
    }
    historyData.value = mockHistory;
    total.value = 50;
    updateDistribution(mockHistory);

  } finally {
    historyLoading.value = false;
  }
};

// 导出
const handleExport = () => {
  const headers = ['时间', 'Agent', '响应耗时(ms)', 'Tokens', '状态'];
  const rows = historyData.value.map(item => [
    new Date(item.createdAt).toLocaleString(),
    item.providerId,
    item.responseTime,
    item.totalTokens,
    item.status
  ]);
  const csvContent = "\ufeff" + [headers, ...rows].map(e => e.join(",")).join("\n");
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `Latency_Report_${new Date().toISOString().slice(0, 10)}.csv`);
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

onMounted(() => {
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
  window.removeEventListener('resize', () => {
    trendChartInstance?.resize();
    distributionChartInstance?.resize();
  });
  trendChartInstance?.dispose();
  distributionChartInstance?.dispose();
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
  width: 56px; height: 56px; border-radius: var(--radius-lg);
  display: flex; align-items: center; justify-content: center; font-size: 24px;
}
.stat-content { flex: 1; }
.stat-label {
  font-size: 12px; color: var(--neutral-gray-500); margin-bottom: 8px;
  font-weight: 600; text-transform: uppercase;
}
.stat-value {
  font-size: 28px; font-weight: 800; color: var(--neutral-gray-900);
  font-family: var(--font-heading); margin-bottom: 4px;
}
.stat-unit { font-size: 11px; color: var(--neutral-gray-400); }
.chart-card, .distribution-card, .table-card {
  border-radius: var(--radius-lg) !important; border: 1px solid var(--neutral-gray-100) !important;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 16px; font-weight: 700; color: var(--neutral-gray-900); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
.text-danger { color: var(--error-color); font-weight: bold; }
.text-warning { color: var(--warning-color); font-weight: bold; }
.text-success { color: var(--success-color); }
</style>
