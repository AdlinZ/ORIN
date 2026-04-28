<template>
  <div class="cost-dashboard">
    <OrinPageShell
      title="成本与定价"
      description="管理模型调用成本、供应商分布与企业定价策略"
      icon="Money"
      domain="组织治理"
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
    </OrinPageShell>

    <OrinMetricStrip :metrics="costMetrics" class="stats-grid" />

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
            v-for="(item, index) in rankingTopFive"
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
          <el-empty v-if="rankingTopFive.length === 0" description="暂无排行数据" :image-size="72" />
        </div>
      </el-card>
    </div>

    <el-card shadow="never" class="tab-wrapper-card">
      <el-tabs v-model="activeTab" class="content-tabs">
        <el-tab-pane label="成本明细" name="cost-detail">
          <div class="pane-subtitle">当前仅按供应商聚合</div>
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
        </el-tab-pane>

        <el-tab-pane label="定价策略配置" name="pricing-config">
          <div class="filter-bar">
            <el-input
              v-model="filterKeyword"
              placeholder="搜索模型 ID..."
              clearable
              style="width: 220px"
              :prefix-icon="Search"
            />
            <el-select
              v-model="filterGroup"
              placeholder="租户分组"
              clearable
              style="width: 150px"
            >
              <el-option label="全部" value="" />
              <el-option label="Default" value="default" />
              <el-option label="VIP" value="VIP" />
              <el-option label="Internal" value="internal" />
            </el-select>
            <el-button type="primary" :icon="Plus" @click="handleAdd">
              新增规则
            </el-button>
          </div>

          <el-table
            v-loading="pricingLoading"
            border
            :data="filteredData"
            style="width: 100%"
          >
            <el-table-column prop="providerId" label="模型/供应商ID" min-width="150" />

            <el-table-column prop="tenantGroup" label="租户分组" width="120">
              <template #default="{ row }">
                <el-tag :type="row.tenantGroup === 'default' ? 'info' : 'success'">
                  {{ row.tenantGroup }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="billingMode" label="计费模式" width="120">
              <template #default="{ row }">
                <el-tag effect="plain">{{ billingModeLabel(row.billingMode) }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column label="内部成本 (Cost)" align="center">
              <el-table-column prop="inputCostUnit" label="Input / 1k" width="130">
                <template #default="{ row }">
                  {{ formatPrice(row.inputCostUnit, row.currency) }}
                </template>
              </el-table-column>
              <el-table-column prop="outputCostUnit" label="Output / 1k" width="130">
                <template #default="{ row }">
                  {{ formatPrice(row.outputCostUnit, row.currency) }}
                </template>
              </el-table-column>
            </el-table-column>

            <el-table-column label="外部报价 (Price)" align="center">
              <el-table-column prop="inputPriceUnit" label="Input / 1k" width="130">
                <template #default="{ row }">
                  <span class="price-highlight">{{ formatPrice(row.inputPriceUnit, row.currency) }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="outputPriceUnit" label="Output / 1k" width="130">
                <template #default="{ row }">
                  <span class="price-highlight">{{ formatPrice(row.outputPriceUnit, row.currency) }}</span>
                </template>
              </el-table-column>
            </el-table-column>

            <el-table-column prop="currency" label="货币" width="80" align="center" />

            <el-table-column label="利润率 (Est)" width="110" align="center">
              <template #default="{ row }">
                <span :class="calculateMargin(row) > 0 ? 'text-success' : 'text-danger'">
                  {{ calculateMargin(row) }}%
                </span>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
                <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 编辑/新增 Dialog -->
    <PricingEditDialog
      v-model="dialogVisible"
      :initial-data="currentEditRow"
      @saved="fetchPricingData"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { Money, Monitor, RefreshRight, Plus, Search } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { ElMessage, ElMessageBox } from 'element-plus';
import OrinPageShell from '@/components/orin/OrinPageShell.vue';
import OrinMetricStrip from '@/components/orin/OrinMetricStrip.vue';
import PricingEditDialog from '@/components/PricingEditDialog.vue';
import { getCostDistribution, getTokenStats } from '@/api/monitor';
import { getPricingConfig, deletePricingConfig } from '@/api/pricing';

const loading = ref(false);
const pricingLoading = ref(false);
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
const activeTab = ref('cost-detail');

const distributionParams = computed(() => {
  if (!dateRange.value || dateRange.value.length !== 2) return {};
  return {
    startDate: new Date(dateRange.value[0]).getTime(),
    endDate: new Date(dateRange.value[1]).getTime()
  };
});

const distributionTotal = computed(() => distributionData.value.reduce((sum, item) => sum + Number(item.value || 0), 0));
const rankingTopFive = computed(() => distributionData.value.slice(0, 5));
const costMetrics = computed(() => [
  { label: '今日成本', value: formatCurrency(costSummary.value.daily), meta: '日内模型调用消耗' },
  { label: '本周成本', value: formatCurrency(costSummary.value.weekly), meta: '周维度预算观察' },
  { label: '本月成本', value: formatCurrency(costSummary.value.monthly), meta: '月度成本控制口径' },
  { label: '累计成本', value: formatCurrency(costSummary.value.total), meta: '当前统计周期总消耗' }
]);

// 定价配置相关
const tableData = ref([]);
const dialogVisible = ref(false);
const currentEditRow = ref(null);
const filterKeyword = ref('');
const filterGroup = ref('');

const filteredData = computed(() => {
  let list = tableData.value;
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase();
    list = list.filter(r => r.providerId?.toLowerCase().includes(kw));
  }
  if (filterGroup.value) {
    list = list.filter(r => r.tenantGroup === filterGroup.value);
  }
  return list;
});

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

const fetchPricingData = async () => {
  pricingLoading.value = true;
  try {
    const res = await getPricingConfig();
    tableData.value = res.data !== undefined ? res.data : (Array.isArray(res) ? res : []);
  } catch (e) {
    console.error('加载定价规则失败', e);
  } finally {
    pricingLoading.value = false;
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

// 定价配置操作
const handleAdd = () => {
  currentEditRow.value = null;
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  currentEditRow.value = { ...row };
  dialogVisible.value = true;
};

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确认删除模型 "${row.providerId}"（${row.tenantGroup}）的定价规则？`,
    '警告',
    { type: 'warning' }
  ).then(async () => {
    await deletePricingConfig(row.id);
    ElMessage.success('删除成功');
    fetchPricingData();
  }).catch(() => {});
};

// 格式化工具
const CURRENCY_SYMBOLS = { USD: '$', CNY: '¥', EUR: '€' };

const formatPrice = (val, currency = 'USD') => {
  if (val == null) return '-';
  const sym = CURRENCY_SYMBOLS[currency] ?? currency;
  return `${sym}${Number(val).toFixed(6)}`;
};

const calculateMargin = (row) => {
  const cost = (Number(row.inputCostUnit) || 0) + (Number(row.outputCostUnit) || 0);
  const price = (Number(row.inputPriceUnit) || 0) + (Number(row.outputPriceUnit) || 0);
  if (cost === 0 && price === 0) return 0;
  if (cost === 0) return 100;
  return Math.round(((price - cost) / cost) * 100);
};

const BILLING_LABELS = {
  PER_TOKEN: 'Token 计费',
  PER_REQUEST: '按次计费',
  PER_SECOND: '按时计费',
};
const billingModeLabel = (mode) => BILLING_LABELS[mode] ?? mode;

onMounted(async () => {
  await fetchCostData();
  fetchPricingData();
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
  background: transparent;
  min-height: 0;
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
  background: var(--success-light);
  color: var(--success-600);
}

.week-icon {
  background: var(--info-light);
  color: var(--info-600);
}

.month-icon {
  background: var(--warning-light);
  color: var(--warning-600);
}

.total-icon {
  background: var(--primary-light);
  color: var(--orin-primary);
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

.divider-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.price-highlight { color: var(--primary-color); font-weight: bold; }
.text-success { color: var(--success-500); font-weight: 600; }
.text-danger  { color: var(--error-500);   font-weight: 600; }

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .content-grid {
    grid-template-columns: 1fr;
  }
}

.pane-subtitle {
  font-size: 13px;
  color: var(--text-secondary, #64748b);
  margin-bottom: 12px;
}

@media (max-width: 768px) {
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
