<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑定价规则' : '配置定价规则'"
    width="600px"
    append-to-body
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="form"
      label-width="100px"
      :rules="rules"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="模型ID" prop="providerId">
            <el-tooltip
              content="模型 ID 是定价规则的标识符，创建后不可修改。如需变更，请删除并新建。"
              placement="top"
              :disabled="!isEdit && !fixedProviderId"
            >
              <el-input
                v-model="form.providerId"
                placeholder="e.g. gpt-4"
                :disabled="isEdit || !!fixedProviderId"
              />
            </el-tooltip>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="租户分组" prop="tenantGroup">
            <el-tooltip
              content="租户分组是定价规则的标识符，创建后不可修改。"
              placement="top"
              :disabled="!isEdit"
            >
              <el-select
                v-model="form.tenantGroup"
                allow-create
                filterable
                default-first-option
                :disabled="isEdit"
                style="width: 100%"
              >
                <el-option label="Default" value="default" />
                <el-option label="VIP" value="VIP" />
                <el-option label="Internal" value="internal" />
              </el-select>
            </el-tooltip>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="计费模式" prop="billingMode">
        <el-radio-group v-model="form.billingMode">
          <el-radio-button label="PER_TOKEN">Token 计费</el-radio-button>
          <el-radio-button label="PER_REQUEST">按次计费</el-radio-button>
          <el-radio-button label="PER_SECOND">按时计费</el-radio-button>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="货币单位" prop="currency">
        <el-select v-model="form.currency" style="width: 120px">
          <el-option label="USD ($)" value="USD" />
          <el-option label="CNY (¥)" value="CNY" />
          <el-option label="EUR (€)" value="EUR" />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">
        定价配置（单位: {{ form.currency }}）
      </el-divider>

      <!-- 快速加价工具 -->
      <div class="markup-tool">
        <span>快速定价（加价率）：</span>
        <el-input-number
          v-model="markupRate"
          :step="10"
          :min="0"
          size="small"
          style="width: 100px"
        />
        <span>%</span>
        <el-button type="primary" link size="small" @click="applyMarkup">
          应用加价
        </el-button>
      </div>

      <div class="pricing-grid">
        <div></div>
        <div class="grid-header">成本 (Internal Cost)</div>
        <div class="grid-header">报价 (External Price)</div>

        <!-- Input Row -->
        <div class="grid-label">{{ billingLabelInput }}</div>
        <el-form-item prop="inputCostUnit" label-width="0">
          <el-input-number
            v-model="form.inputCostUnit"
            :precision="6"
            :step="0.001"
            :min="0"
            style="width: 100%"
            placeholder="0.000000"
          />
        </el-form-item>
        <el-form-item prop="inputPriceUnit" label-width="0">
          <el-input-number
            v-model="form.inputPriceUnit"
            :precision="6"
            :step="0.001"
            :min="0"
            style="width: 100%"
            placeholder="0.000000"
          />
        </el-form-item>

        <!-- Output Row -->
        <div class="grid-label">{{ billingLabelOutput }}</div>
        <el-form-item prop="outputCostUnit" label-width="0">
          <el-input-number
            v-model="form.outputCostUnit"
            :precision="6"
            :step="0.001"
            :min="0"
            style="width: 100%"
            placeholder="0.000000"
          />
        </el-form-item>
        <el-form-item prop="outputPriceUnit" label-width="0">
          <el-input-number
            v-model="form.outputPriceUnit"
            :precision="6"
            :step="0.001"
            :min="0"
            style="width: 100%"
            placeholder="0.000000"
          />
        </el-form-item>
      </div>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { savePricingConfig, getPricingConfig } from '@/api/pricing';

const props = defineProps({
  modelValue: Boolean,
  /**
   * 预填数据：
   * - 来自 PricingConfig（编辑行）：直接传入完整行数据
   * - 来自 ModelList（fixedProviderId 场景）：不传，由组件自行精确查询
   */
  initialData: { type: Object, default: null },
  /**
   * 若设置，锁定 providerId 字段（来自 ModelList.vue）
   */
  fixedProviderId: { type: String, default: null },
});

const emit = defineEmits(['update:modelValue', 'saved']);

// ─────────────────── v-model 桥接 ───────────────────
const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
});

// ─────────────────── 表单状态 ───────────────────
const formRef = ref(null);
const saving = ref(false);
const markupRate = ref(50);

const EMPTY_FORM = {
  id: null,
  providerId: '',
  tenantGroup: 'default',
  billingMode: 'PER_TOKEN',
  inputCostUnit: 0,
  outputCostUnit: 0,
  inputPriceUnit: 0,
  outputPriceUnit: 0,
  currency: 'USD',
};

const form = reactive({ ...EMPTY_FORM });

const isEdit = computed(() => !!form.id);

// ─────────────────── 校验规则 ───────────────────
const rules = {
  providerId: [{ required: true, message: '请输入模型 ID', trigger: 'blur' }],
  tenantGroup: [{ required: true, message: '请选择或输入分组', trigger: 'change' }],
  billingMode: [{ required: true, message: '请选择计费模式', trigger: 'change' }],
};

// ─────────────────── 标签计算 ───────────────────
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

// ─────────────────── Dialog 打开时数据填充 ───────────────────
watch(() => props.modelValue, async (open) => {
  if (!open) return;

  if (props.initialData) {
    // 1. 来自父组件直接传入（PricingConfig 编辑行 / ModelList 预查询后结果）
    Object.assign(form, EMPTY_FORM, props.initialData);
  } else if (props.fixedProviderId) {
    // 2. fixedProviderId 模式：调后端精确接口，而非全量拉取再 find
    resetForm();
    form.providerId = props.fixedProviderId;
    await tryFetchByProvider(props.fixedProviderId, form.tenantGroup);
  } else {
    // 3. 纯新增模式
    resetForm();
  }
});

const resetForm = () => {
  Object.assign(form, EMPTY_FORM);
};

/**
 * 精确查询已有规则并预填（仅在 fixedProviderId 场景使用）
 */
const tryFetchByProvider = async (providerId, tenantGroup) => {
  try {
    // Inline filtering to avoid slash route issues with path variables
    const res = await getPricingConfig();
    const list = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
    const pid = props.fixedProviderId || form.providerId;
    const data = Array.isArray(list) ? list.find(p => p.providerId === pid && p.tenantGroup === (form.tenantGroup || 'default')) : null;
    
    if (data) {
      Object.assign(form, data);
    }
  } catch {
    // 允许静默处理
  }
};

// ─────────────────── 加价工具 ───────────────────
const applyMarkup = () => {
  const rate = 1 + markupRate.value / 100;
  if (form.inputCostUnit) {
    form.inputPriceUnit = Number((form.inputCostUnit * rate).toFixed(6));
  }
  if (form.outputCostUnit) {
    form.outputPriceUnit = Number((form.outputCostUnit * rate).toFixed(6));
  }
  ElMessage.success(`已应用 ${markupRate.value}% 加价`);
};

// ─────────────────── 提交 ───────────────────
const submitForm = async () => {
  if (!formRef.value) return;
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  saving.value = true;
  try {
    await savePricingConfig(form);
    ElMessage.success('保存成功');
    visible.value = false;
    emit('saved');
  } catch {
    // 全局拦截器已处理错误提示
  } finally {
    saving.value = false;
  }
};

// ─────────────────── Dialog 关闭清理 ───────────────────
const handleClosed = () => {
  formRef.value?.resetFields();
  resetForm();
};
</script>

<style scoped>
.markup-tool {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--neutral-gray-50);
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 16px;
  font-size: 13px;
}

.pricing-grid {
  display: grid;
  grid-template-columns: 120px 1fr 1fr;
  gap: 10px 12px;
  align-items: center;
}

.grid-header {
  font-weight: 600;
  text-align: center;
  font-size: 13px;
  color: var(--neutral-gray-600);
}

.grid-label {
  text-align: right;
  font-size: 12px;
  color: var(--neutral-gray-500);
  padding-right: 8px;
}

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
