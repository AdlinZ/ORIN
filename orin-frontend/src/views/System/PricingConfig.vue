<template>
  <div class="page-container">
    <PageHeader 
      title="定价策略配置" 
      description="管理不同模型的进货成本与外部报价逻辑"
      icon="Money"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleAdd">新增规则</el-button>
      </template>
    </PageHeader>

    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" style="width: 100%">
        <el-table-column prop="providerId" label="模型/供应商ID" min-width="150" />
        <el-table-column prop="tenantGroup" label="租户分组" width="120">
          <template #default="{ row }">
            <el-tag :type="row.tenantGroup === 'default' ? 'info' : 'success'">{{ row.tenantGroup }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="billingMode" label="计费模式" width="120">
          <template #default="{ row }">
            <el-tag effect="plain">{{ row.billingMode }}</el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="内部成本 (Cost)" align="center">
          <el-table-column prop="inputCostUnit" label="Input / 1k" width="120">
            <template #default="{ row }">{{ formatPrice(row.inputCostUnit) }}</template>
          </el-table-column>
          <el-table-column prop="outputCostUnit" label="Output / 1k" width="120">
            <template #default="{ row }">{{ formatPrice(row.outputCostUnit) }}</template>
          </el-table-column>
        </el-table-column>

        <el-table-column label="外部报价 (Price)" align="center">
          <el-table-column prop="inputPriceUnit" label="Input / 1k" width="120">
            <template #default="{ row }">
              <span class="price-highlight">{{ formatPrice(row.inputPriceUnit) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="outputPriceUnit" label="Output / 1k" width="120">
            <template #default="{ row }">
              <span class="price-highlight">{{ formatPrice(row.outputPriceUnit) }}</span>
            </template>
          </el-table-column>
        </el-table-column>

        <el-table-column label="利润率 (Est)" width="100" align="center">
          <template #default="{ row }">
             <span :class="calculateMargin(row) > 0 ? 'text-success' : 'text-danger'">
               {{ calculateMargin(row) }}%
             </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑定价规则' : '新增定价规则'"
      width="600px"
    >
      <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="模型ID" prop="providerId">
              <el-input v-model="form.providerId" placeholder="e.g. gpt-4" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户分组" prop="tenantGroup">
              <el-select v-model="form.tenantGroup" allow-create filterable default-first-option>
                <el-option label="Default" value="default" />
                <el-option label="VIP" value="VIP" />
                <el-option label="Internal" value="internal" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-form-item label="计费模式" prop="billingMode">
          <el-radio-group v-model="form.billingMode">
            <el-radio-button label="PER_TOKEN">Token计费</el-radio-button>
            <el-radio-button label="PER_REQUEST">按次计费</el-radio-button>
            <el-radio-button label="PER_SECOND">按时计费</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">定价配置 (单位: USD)</el-divider>

        <!-- Markup Tool -->
        <div class="markup-tool">
          <span>快速定价 (加价率): </span>
          <el-input-number v-model="markupRate" :step="10" size="small" style="width: 100px" /> %
          <el-button type="primary" link size="small" @click="applyMarkup">应用加价</el-button>
        </div>

        <div class="pricing-grid">
          <div class="grid-header">成本 (Internal Cost)</div>
          <div class="grid-header">报价 (External Price)</div>

          <!-- Input Row -->
          <div class="grid-label">Input / Unit</div>
          <el-form-item prop="inputCostUnit" label-width="0">
             <el-input-number v-model="form.inputCostUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
          </el-form-item>
          <el-form-item prop="inputPriceUnit" label-width="0">
             <el-input-number v-model="form.inputPriceUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
          </el-form-item>

          <!-- Output Row -->
          <div class="grid-label">Output / Unit</div>
          <el-form-item prop="outputCostUnit" label-width="0">
             <el-input-number v-model="form.outputCostUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
          </el-form-item>
          <el-form-item prop="outputPriceUnit" label-width="0">
             <el-input-number v-model="form.outputPriceUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitForm">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { Plus, Money } from '@element-plus/icons-vue';
import { getPricingConfig, deletePricingConfig } from '@/api/monitor';
import { ElMessage, ElMessageBox } from 'element-plus';
import PricingEditDialog from '@/components/PricingEditDialog.vue';

const loading = ref(false);
const tableData = ref([]);
const dialogVisible = ref(false);
const currentEditRow = ref(null);

const fetchData = async () => {
  loading.value = true;
  try {
    const res = await getPricingConfig();
    tableData.value = res.data || res;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleAdd = () => {
  currentEditRow.value = null;
  dialogVisible.value = true;
};

const handleEdit = (row) => {
  currentEditRow.value = { ...row }; // Clone
  dialogVisible.value = true;
};

const handleSaved = () => {
  fetchData();
};

const handleDelete = (row) => {
  ElMessageBox.confirm('确认删除该定价规则?', '警告', {
    type: 'warning'
  }).then(async () => {
    await deletePricingConfig(row.id);
    ElMessage.success('删除成功');
    fetchData();
  });
};

const formatPrice = (val) => {
  if (!val) return '-';
  return '¥' + Number(val).toFixed(6);
};

const calculateMargin = (row) => {
  const cost = (row.inputCostUnit || 0) + (row.outputCostUnit || 0);
  const price = (row.inputPriceUnit || 0) + (row.outputPriceUnit || 0);
  if (cost === 0 && price === 0) return 0;
  if (cost === 0) return 100;
  return Math.round(((price - cost) / cost) * 100);
};

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.page-container { padding: 0; }
.price-highlight { color: var(--primary-color); font-weight: bold; }
.text-success { color: var(--success-color); }
.text-danger { color: var(--danger-color); }

.markup-tool {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--neutral-gray-50);
  padding: 8px;
  border-radius: 4px;
  margin-bottom: 16px;
  font-size: 13px;
}

.pricing-grid {
  display: grid;
  grid-template-columns: 80px 1fr 1fr;
  gap: 12px;
  align-items: center;
}

.grid-header {
  font-weight: bold;
  text-align: center;
  color: var(--neutral-gray-600);
}

.grid-label {
  text-align: right;
  font-size: 12px;
  color: var(--neutral-gray-500);
  padding-right: 8px;
}

/* Dark Mode Adaptation */
html.dark .markup-tool {
  background: var(--neutral-gray-800);
  color: var(--neutral-gray-300);
}

html.dark .grid-header {
  color: var(--neutral-gray-300);
}

html.dark .grid-label {
  color: var(--neutral-gray-400);
}
</style>
