<template>
  <div class="page-container">
    <PageHeader
      title="定价策略配置"
      description="管理不同模型的进货成本与外部报价逻辑"
      icon="Money"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          新增规则
        </el-button>
      </template>
    </PageHeader>

    <!-- 搜索过滤栏 -->
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
    </div>

    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
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
    </el-card>

    <!-- 编辑/新增 Dialog（统一使用组件） -->
    <PricingEditDialog
      v-model="dialogVisible"
      :initial-data="currentEditRow"
      @saved="fetchData"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { Plus, Search } from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import PageHeader from '@/components/PageHeader.vue';
import PricingEditDialog from '@/components/PricingEditDialog.vue';
import { getPricingConfig, deletePricingConfig } from '@/api/pricing';

const loading = ref(false);
const tableData = ref([]);
const dialogVisible = ref(false);
const currentEditRow = ref(null);
const filterKeyword = ref('');
const filterGroup = ref('');

// ─────────────────── 数据过滤 ───────────────────
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

// ─────────────────── 数据拉取 ───────────────────
const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getPricingConfig();
    // Defensive check: extract data if Result<T> wrapped, else use raw array
    tableData.value = res.data !== undefined ? res.data : (Array.isArray(res) ? res : []);
  } catch (e) {
    console.error('加载定价规则失败', e);
  } finally {
    loading.value = false;
  }
};

// ─────────────────── 操作处理 ───────────────────
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
    fetchData();
  }).catch(() => {});
};

// ─────────────────── 格式化工具 ───────────────────
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

onMounted(fetchData);
</script>

<style scoped>
.page-container { padding: 0; }

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.price-highlight { color: var(--primary-color); font-weight: bold; }
.text-success { color: var(--success-500); font-weight: 600; }
.text-danger  { color: var(--error-500);   font-weight: 600; }
</style>
