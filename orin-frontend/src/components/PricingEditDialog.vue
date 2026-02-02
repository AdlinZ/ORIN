<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑定价规则' : '配置定价规则'"
    width="600px"
    @closed="handleClosed"
    append-to-body
  >
    <el-form :model="form" label-width="100px" ref="formRef" :rules="rules">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="模型ID" prop="providerId">
            <el-input v-model="form.providerId" placeholder="e.g. gpt-4" :disabled="!!fixedProviderId" />
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

      <el-divider content-position="left">定价配置 (单位: CNY)</el-divider>

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
        <div class="grid-label">{{ billingLabelInput }}</div>
        <el-form-item prop="inputCostUnit" label-width="0">
           <el-input-number v-model="form.inputCostUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
        </el-form-item>
        <el-form-item prop="inputPriceUnit" label-width="0">
           <el-input-number v-model="form.inputPriceUnit" :precision="6" :step="0.001" style="width: 100%" placeholder="0.000000" />
        </el-form-item>

        <!-- Output Row (Only for Token mode usually, but keep simple) -->
        <div class="grid-label">{{ billingLabelOutput }}</div>
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
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { savePricingConfig, getPricingConfig } from '@/api/monitor';
import { ElMessage } from 'element-plus';

const props = defineProps({
  modelValue: Boolean, // v-model for visibility
  initialData: Object,
  fixedProviderId: String, // If set, disables editing providerId field
});

const emit = defineEmits(['update:modelValue', 'saved']);

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
});

const formRef = ref(null);
const saving = ref(false);
const markupRate = ref(50);
const rawPricingList = ref([]); // To verify if we are editing or creating

const form = reactive({
  id: null,
  providerId: '',
  tenantGroup: 'default',
  billingMode: 'PER_TOKEN',
  inputCostUnit: 0,
  outputCostUnit: 0,
  inputPriceUnit: 0,
  outputPriceUnit: 0,
  currency: 'USD'
});

const isEdit = computed(() => !!form.id);

const rules = {
  providerId: [{ required: true, message: '请输入模型ID', trigger: 'blur' }],
  tenantGroup: [{ required: true, message: '请选择或输入分组', trigger: 'change' }]
};

const billingLabelInput = computed(() => {
    if (form.billingMode === 'PER_TOKEN') return 'Input / 1k Tokens';
    if (form.billingMode === 'PER_REQUEST') return 'Request Base';
    return 'Unit Cost';
});

const billingLabelOutput = computed(() => {
    if (form.billingMode === 'PER_TOKEN') return 'Output / 1k Tokens';
    if (form.billingMode === 'PER_REQUEST') return 'Request Extra';
    return 'Unit Extra';
});

// Watch for opening to populate data
watch(() => props.modelValue, async (val) => {
  if (val) {
    if (props.initialData) {
      // Direct edit mode
      Object.assign(form, props.initialData);
    } else if (props.fixedProviderId) {
      // New or lookup mode from ModelList
      resetForm();
      form.providerId = props.fixedProviderId;
      await tryFetchExisting(props.fixedProviderId);
    } else {
      // Pure add mode
      resetForm();
    }
  }
});

const resetForm = () => {
  Object.assign(form, {
    id: null,
    providerId: '',
    tenantGroup: 'default',
    billingMode: 'PER_TOKEN',
    inputCostUnit: 0,
    outputCostUnit: 0,
    inputPriceUnit: 0,
    outputPriceUnit: 0,
    currency: 'CNY'
  });
};

// Try to find if a pricing rule already exists for this providerId (default group)
const tryFetchExisting = async (pid) => {
    try {
        const res = await getPricingConfig();
        const list = res.data || res;
        // Simple client side lookup for now to pre-fill
        const match = list.find(p => p.providerId === pid && p.tenantGroup === form.tenantGroup);
        if (match) {
            Object.assign(form, match);
        }
    } catch(e) { /* ignore */ }
};

const applyMarkup = () => {
  const rate = 1 + markupRate.value / 100;
  if (form.inputCostUnit) form.inputPriceUnit = Number((form.inputCostUnit * rate).toFixed(6));
  if (form.outputCostUnit) form.outputPriceUnit = Number((form.outputCostUnit * rate).toFixed(6));
  ElMessage.success(`已应用 ${markupRate.value}% 加价`);
};

const submitForm = async () => {
  if (!formRef.value) return;
  await formRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true;
      try {
        await savePricingConfig(form);
        ElMessage.success('保存成功');
        visible.value = false;
        emit('saved');
      } catch (e) {
        // Handled by global interceptor usually
      } finally {
        saving.value = false;
      }
    }
  });
};

const handleClosed = () => {
  formRef.value?.resetFields();
};
</script>

<style scoped>
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
