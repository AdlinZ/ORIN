<template>
  <div class="page-container alert-rule-builder-page">
    <OrinPageShell
      :title="isEditMode ? '编辑告警规则' : '创建告警规则'"
      description="像快捷指令一样选择告警场景、调整参数，再保存为后端可执行规则"
      icon="Bell"
      domain="运行监控"
    >
      <template #actions>
        <el-button @click="goBack">
          返回列表
        </el-button>
      </template>
    </OrinPageShell>

    <el-card shadow="never" class="builder-card">
      <OrinStepFlow :steps="guideSteps" :active="currentStepIndex" />

      <section class="builder-section">
        <div class="builder-section-head">
          <span class="builder-step">1</span>
          <div>
            <h3>选择场景</h3>
            <p>从包装好的规则开始，避免直接输入后端表达式。</p>
          </div>
        </div>
        <div class="preset-grid">
          <button
            v-for="preset in rulePresets"
            :key="preset.key"
            class="preset-card"
            :class="{ active: selectedPresetKey === preset.key }"
            type="button"
            @click="selectPreset(preset.key)"
          >
            <span class="preset-type">{{ getRuleTypeText(preset.ruleType) }}</span>
            <strong>{{ preset.name }}</strong>
            <small>{{ preset.description }}</small>
          </button>
        </div>
      </section>

      <section class="builder-section">
        <div class="builder-section-head">
          <span class="builder-step">2</span>
          <div>
            <h3>配置条件</h3>
            <p>{{ selectedPreset?.sentence || '选择场景后配置触发条件。' }}</p>
          </div>
        </div>

        <el-form
          ref="formRef"
          :model="ruleForm"
          :rules="formRules"
          label-position="top"
          class="builder-form"
        >
          <el-form-item label="规则名称" prop="ruleName">
            <el-input
              v-model="ruleForm.ruleName"
              size="large"
              placeholder="例如：协作健康变红"
            />
          </el-form-item>

          <div class="condition-grid">
            <el-form-item label="规则类型">
              <el-tag size="large">
                {{ getRuleTypeText(ruleForm.ruleType) }}
              </el-tag>
            </el-form-item>
            <el-form-item
              v-for="field in selectedPresetFields"
              :key="field.key"
              :label="field.label"
            >
              <el-input-number
                v-if="field.type === 'number'"
                v-model="presetParams[field.key]"
                :min="field.min"
                :max="field.max"
                :step="field.step"
                :precision="field.precision"
                size="large"
                @change="handlePresetParamChange"
              />
              <el-select
                v-else-if="field.type === 'select'"
                v-model="presetParams[field.key]"
                size="large"
                :placeholder="field.placeholder"
                @change="handlePresetParamChange"
              >
                <el-option
                  v-for="option in field.options"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-input
                v-else-if="field.type === 'text'"
                v-model="presetParams[field.key]"
                size="large"
                :placeholder="field.placeholder"
                @input="handlePresetParamChange"
              />
              <span v-if="field.unit" class="unit-text">{{ field.unit }}</span>
              <template #extra>
                <span v-if="field.help">{{ field.help }}</span>
              </template>
            </el-form-item>
          </div>

          <div class="condition-preview">
            <span>触发预览</span>
            <strong>{{ getPresetConditionSummary(ruleForm) }}</strong>
          </div>
        </el-form>
      </section>

      <section class="builder-section">
        <div class="builder-section-head">
          <span class="builder-step">3</span>
          <div>
            <h3>通知与抑制</h3>
            <p>配置等级、冷却时间和外部通知方式。</p>
          </div>
        </div>

        <el-form :model="ruleForm" label-position="top" class="builder-form">
          <div class="condition-grid">
            <el-form-item label="严重程度">
              <el-select v-model="ruleForm.severity" size="large">
                <el-option label="信息" value="INFO" />
                <el-option label="警告" value="WARNING" />
                <el-option label="错误" value="ERROR" />
                <el-option label="严重" value="CRITICAL" />
              </el-select>
            </el-form-item>
            <el-form-item label="冷却时间">
              <el-input-number
                v-model="ruleForm.cooldownMinutes"
                :min="1"
                :max="120"
                size="large"
              />
              <span class="unit-text">分钟</span>
            </el-form-item>
          </div>

          <el-form-item label="通知渠道">
            <el-checkbox-group v-model="selectedChannels">
              <el-checkbox label="EMAIL">
                邮件
              </el-checkbox>
              <el-checkbox label="DINGTALK">
                钉钉
              </el-checkbox>
              <el-checkbox label="WECHAT">
                企业微信
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item label="接收人列表">
            <el-input
              v-model="ruleForm.recipientList"
              type="textarea"
              :rows="2"
              placeholder="多个接收人用逗号分隔"
            />
          </el-form-item>

          <el-form-item label="启用规则">
            <el-switch v-model="ruleForm.enabled" />
          </el-form-item>
        </el-form>
      </section>

      <el-collapse v-model="advancedPanels" class="advanced-rule-panel">
        <el-collapse-item name="expression">
          <template #title>
            <span>高级设置</span>
            <el-tag
              v-if="advancedExpressionEdited"
              size="small"
              type="warning"
              class="advanced-tag"
            >
              自定义条件
            </el-tag>
          </template>
          <el-form :model="ruleForm" label-position="top">
            <el-form-item label="条件表达式">
              <el-input
                v-model="ruleForm.conditionExpr"
                size="large"
                placeholder="例如：overallLevel == RED"
                @input="markAdvancedExpressionEdited"
              />
              <template #extra>
                普通规则不需要编辑这里。当前支持 overallLevel、successRate、p95LatencyMs、dlqBacklog、biddingPostSuccessRate、avgCritiqueRounds。
              </template>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>

      <div class="action-bar">
        <el-button size="large" @click="goBack">
          取消
        </el-button>
        <el-button
          type="primary"
          size="large"
          :loading="saving"
          @click="saveRule"
        >
          保存规则
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinStepFlow from '@/components/orin/OrinStepFlow.vue'

const router = useRouter()
const route = useRoute()
const formRef = ref(null)
const saving = ref(false)
const selectedChannels = ref([])
const selectedPresetKey = ref('collab-overall-red')
const presetParams = ref({})
const advancedPanels = ref([])
const advancedExpressionEdited = ref(false)

const isEditMode = computed(() => Boolean(route.params.id))
const currentStepIndex = computed(() => {
  if (advancedExpressionEdited.value) return 1
  if (selectedChannels.value.length || ruleForm.value.recipientList) return 2
  return selectedPresetKey.value ? 1 : 0
})

const guideSteps = [
  { key: 'preset', title: '选择场景', description: '从包装好的规则开始' },
  { key: 'condition', title: '配置条件', description: '调整阈值和规则名称' },
  { key: 'notification', title: '通知抑制', description: '配置等级、冷却和渠道' }
]

const formRules = {
  ruleName: [{ required: true, message: '请填写规则名称', trigger: 'blur' }]
}

const ruleForm = ref({
  ruleName: '',
  ruleType: 'COLLAB_HEALTH',
  conditionExpr: 'overallLevel == RED',
  thresholdValue: 1,
  targetScope: 'ALL',
  targetId: '',
  metricWindowMinutes: 5,
  minSampleCount: 1,
  severity: 'WARNING',
  notificationChannels: '',
  recipientList: '',
  cooldownMinutes: 5,
  enabled: true
})

const formatRatio = value => Number(value || 0).toFixed(2)
const formatNumber = value => Number(value || 0).toString()
const percentText = value => `${Math.round(Number(value || 0) * 100)}%`
const normalizeTarget = value => String(value || '').trim().toUpperCase()

const dependencyOptions = [
  { label: '全部依赖', value: '' },
  { label: 'MySQL', value: 'MYSQL' },
  { label: 'Redis', value: 'REDIS' },
  { label: 'Milvus', value: 'MILVUS' }
]

const dependencyText = value => {
  const option = dependencyOptions.find(item => item.value === normalizeTarget(value))
  return option?.label || value || '全部依赖'
}

const providerText = value => value?.trim() || '全部 Provider'

const rulePresets = [
  {
    key: 'collab-overall-red',
    ruleType: 'COLLAB_HEALTH',
    name: '协作整体变红',
    description: '当协作健康总状态进入 RED 时触发。',
    sentence: '当协作整体状态为红色时触发。',
    defaultName: '多智能体协作健康告警',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [],
    conditionBuilder: () => 'overallLevel == RED',
    thresholdValueBuilder: () => 1,
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: () => '协作整体状态为红色'
  },
  {
    key: 'collab-success-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '成功率低于阈值',
    description: '适合发现协作任务连续失败或空转。',
    sentence: '当协作成功率低于设置阈值时触发。',
    defaultName: '协作成功率过低',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [{
      key: 'threshold',
      label: '成功率低于',
      type: 'number',
      unit: '比例',
      min: 0,
      max: 1,
      step: 0.05,
      precision: 2,
      defaultValue: 0.7,
      help: '0.80 表示 80%。空样本窗口不会按 0% 触发。'
    }],
    conditionBuilder: params => `successRate <= ${formatRatio(params.threshold)}`,
    thresholdValueBuilder: params => Number(params.threshold || 0),
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `协作成功率 <= ${percentText(params.threshold)}`
  },
  {
    key: 'collab-p95-critical',
    ruleType: 'COLLAB_HEALTH',
    name: 'P95 延迟过高',
    description: '适合监控协作链路是否明显变慢。',
    sentence: '当协作 P95 延迟超过设置秒数时触发。',
    defaultName: '协作延迟过高',
    defaultSeverity: 'ERROR',
    defaultCooldown: 15,
    fields: [{
      key: 'seconds',
      label: 'P95 延迟超过',
      type: 'number',
      unit: '秒',
      min: 1,
      max: 600,
      step: 5,
      precision: 0,
      defaultValue: 60
    }],
    conditionBuilder: params => `p95LatencyMs >= ${formatNumber(Number(params.seconds || 0) * 1000)}`,
    thresholdValueBuilder: params => Number(params.seconds || 0) * 1000,
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `协作 P95 延迟 >= ${formatNumber(params.seconds)} 秒`
  },
  {
    key: 'collab-dlq-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '死信队列积压',
    description: '适合发现协作消息无法被正常消费。',
    sentence: '当协作死信队列积压达到设置数量时触发。',
    defaultName: '协作队列积压',
    defaultSeverity: 'CRITICAL',
    defaultCooldown: 15,
    fields: [{
      key: 'count',
      label: '积压达到',
      type: 'number',
      unit: '条',
      min: 1,
      max: 1000,
      step: 1,
      precision: 0,
      defaultValue: 20
    }],
    conditionBuilder: params => `dlqBacklog >= ${formatNumber(params.count)}`,
    thresholdValueBuilder: params => Number(params.count || 0),
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `死信队列积压 >= ${formatNumber(params.count)} 条`
  },
  {
    key: 'collab-critique-high',
    ruleType: 'COLLAB_HEALTH',
    name: '反复修正过多',
    description: '适合发现多智能体协作质量下降。',
    sentence: '当平均 Critique 轮次超过设置阈值时触发。',
    defaultName: '协作反复修正过多',
    defaultSeverity: 'WARNING',
    defaultCooldown: 15,
    fields: [{
      key: 'rounds',
      label: '平均轮次超过',
      type: 'number',
      unit: '轮',
      min: 0,
      max: 20,
      step: 0.5,
      precision: 1,
      defaultValue: 3.5
    }],
    conditionBuilder: params => `avgCritiqueRounds >= ${formatNumber(params.rounds)}`,
    thresholdValueBuilder: params => Number(params.rounds || 0),
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `平均 Critique 轮次 >= ${formatNumber(params.rounds)}`
  },
  {
    key: 'collab-bidding-success-critical',
    ruleType: 'COLLAB_HEALTH',
    name: '竞标后成功率过低',
    description: '适合检查调度竞标后的实际完成质量。',
    sentence: '当竞标后协作成功率低于设置阈值时触发。',
    defaultName: '竞标后协作成功率过低',
    defaultSeverity: 'WARNING',
    defaultCooldown: 15,
    fields: [{
      key: 'threshold',
      label: '成功率低于',
      type: 'number',
      unit: '比例',
      min: 0,
      max: 1,
      step: 0.05,
      precision: 2,
      defaultValue: 0.75,
      help: '没有触发竞标的窗口不会参与异常判定。'
    }],
    conditionBuilder: params => `biddingPostSuccessRate <= ${formatRatio(params.threshold)}`,
    thresholdValueBuilder: params => Number(params.threshold || 0),
    targetScopeBuilder: () => 'ALL',
    targetIdBuilder: () => '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `竞标后成功率 <= ${percentText(params.threshold)}`
  },
  {
    key: 'system-health-down',
    ruleType: 'SYSTEM_HEALTH',
    name: '系统依赖异常',
    description: '当任一外部依赖探测失败时触发。',
    sentence: '当选定依赖组件状态为 DOWN 时触发。',
    defaultName: '系统依赖健康告警',
    defaultSeverity: 'ERROR',
    defaultCooldown: 5,
    fields: [{
      key: 'dependency',
      label: '依赖组件',
      type: 'select',
      defaultValue: '',
      placeholder: '选择依赖组件',
      options: dependencyOptions
    }],
    conditionBuilder: () => 'status == DOWN',
    thresholdValueBuilder: () => 1,
    targetScopeBuilder: params => params.dependency ? 'DEPENDENCY' : 'ALL',
    targetIdBuilder: params => params.dependency || '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `${dependencyText(params.dependency)} 状态为 DOWN`
  },
  {
    key: 'api-single-failure',
    ruleType: 'ERROR_RATE',
    name: 'API 单次失败',
    description: '任一目标 Provider 出现失败调用时触发。',
    sentence: '当选定 Provider 出现一次失败调用时触发。',
    defaultName: 'API 单次失败告警',
    defaultSeverity: 'ERROR',
    defaultCooldown: 5,
    fields: [{
      key: 'providerId',
      label: '目标 Provider',
      type: 'text',
      defaultValue: '',
      placeholder: '留空表示全部 Provider',
      help: '填写 providerId 时仅匹配该 Provider。'
    }],
    conditionBuilder: () => 'lastFailure == TRUE',
    thresholdValueBuilder: () => 1,
    targetScopeBuilder: params => params.providerId?.trim() ? 'PROVIDER' : 'ALL',
    targetIdBuilder: params => params.providerId?.trim() || '',
    metricWindowBuilder: () => 5,
    minSampleBuilder: () => 1,
    summaryBuilder: params => `${providerText(params.providerId)} 出现单次 API 失败`
  },
  {
    key: 'api-failure',
    ruleType: 'ERROR_RATE',
    name: 'API 窗口失败次数',
    description: '统计窗口内同一 Provider 失败次数达到阈值。',
    sentence: '当选定 Provider 在统计窗口内失败次数达到阈值时触发。',
    defaultName: 'API 失败次数过高',
    defaultSeverity: 'ERROR',
    defaultCooldown: 5,
    fields: [
      {
        key: 'providerId',
        label: '目标 Provider',
        type: 'text',
        defaultValue: '',
        placeholder: '留空表示全部 Provider'
      },
      {
        key: 'window',
        label: '统计窗口',
        type: 'number',
        unit: '分钟',
        min: 1,
        max: 1440,
        step: 1,
        precision: 0,
        defaultValue: 5
      },
      {
        key: 'count',
        label: '错误次数达到',
        type: 'number',
        unit: '次',
        min: 1,
        max: 1000,
        step: 1,
        precision: 0,
        defaultValue: 3
      }
    ],
    conditionBuilder: params => `errorCount >= ${formatNumber(params.count)}`,
    thresholdValueBuilder: params => Number(params.count || 0),
    targetScopeBuilder: params => params.providerId?.trim() ? 'PROVIDER' : 'ALL',
    targetIdBuilder: params => params.providerId?.trim() || '',
    metricWindowBuilder: params => Number(params.window || 5),
    minSampleBuilder: () => 1,
    summaryBuilder: params => `${providerText(params.providerId)} ${formatNumber(params.window)} 分钟内 API 错误次数 >= ${formatNumber(params.count)}`
  },
  {
    key: 'api-error-rate',
    ruleType: 'ERROR_RATE',
    name: 'API 窗口失败率',
    description: '统计窗口内失败率达到阈值，且满足最小样本数。',
    sentence: '当选定 Provider 在统计窗口内失败率达到阈值时触发。',
    defaultName: 'API 失败率过高',
    defaultSeverity: 'WARNING',
    defaultCooldown: 5,
    fields: [
      {
        key: 'providerId',
        label: '目标 Provider',
        type: 'text',
        defaultValue: '',
        placeholder: '留空表示全部 Provider'
      },
      {
        key: 'window',
        label: '统计窗口',
        type: 'number',
        unit: '分钟',
        min: 1,
        max: 1440,
        step: 1,
        precision: 0,
        defaultValue: 5
      },
      {
        key: 'threshold',
        label: '失败率达到',
        type: 'number',
        unit: '比例',
        min: 0,
        max: 1,
        step: 0.05,
        precision: 2,
        defaultValue: 0.3,
        help: '0.30 表示 30%。'
      },
      {
        key: 'minSamples',
        label: '最小样本数',
        type: 'number',
        unit: '次',
        min: 1,
        max: 10000,
        step: 1,
        precision: 0,
        defaultValue: 10
      }
    ],
    conditionBuilder: params => `errorRate >= ${formatRatio(params.threshold)}`,
    thresholdValueBuilder: params => Number(params.threshold || 0),
    targetScopeBuilder: params => params.providerId?.trim() ? 'PROVIDER' : 'ALL',
    targetIdBuilder: params => params.providerId?.trim() || '',
    metricWindowBuilder: params => Number(params.window || 5),
    minSampleBuilder: params => Number(params.minSamples || 1),
    summaryBuilder: params => `${providerText(params.providerId)} ${formatNumber(params.window)} 分钟内 API 失败率 >= ${percentText(params.threshold)}，样本 >= ${formatNumber(params.minSamples)}`
  }
]

const selectedPreset = computed(() => rulePresets.find(preset => preset.key === selectedPresetKey.value))
const selectedPresetFields = computed(() => selectedPreset.value?.fields || [])

const createPresetParams = (preset) => {
  return (preset?.fields || []).reduce((params, field) => {
    params[field.key] = field.defaultValue
    return params
  }, {})
}

const applyPresetToRuleForm = (options = {}) => {
  const { preserveName = false, preserveSeverity = false, preserveCooldown = false, forceExpression = false } = options
  const preset = selectedPreset.value
  if (!preset) return

  ruleForm.value.ruleType = preset.ruleType
  ruleForm.value.thresholdValue = preset.thresholdValueBuilder(presetParams.value)
  ruleForm.value.targetScope = preset.targetScopeBuilder(presetParams.value)
  ruleForm.value.targetId = preset.targetIdBuilder(presetParams.value)
  ruleForm.value.metricWindowMinutes = preset.metricWindowBuilder(presetParams.value)
  ruleForm.value.minSampleCount = preset.minSampleBuilder(presetParams.value)
  if (!preserveSeverity) ruleForm.value.severity = preset.defaultSeverity
  if (!preserveCooldown) ruleForm.value.cooldownMinutes = preset.defaultCooldown
  if (!preserveName && !ruleForm.value.ruleName) ruleForm.value.ruleName = preset.defaultName
  if (forceExpression || !advancedExpressionEdited.value) {
    ruleForm.value.conditionExpr = preset.conditionBuilder(presetParams.value)
  }
}

const selectPreset = (key) => {
  if (key === selectedPresetKey.value) return
  selectedPresetKey.value = key
  presetParams.value = createPresetParams(selectedPreset.value)
  advancedExpressionEdited.value = false
  advancedPanels.value = []
  ruleForm.value.ruleName = selectedPreset.value?.defaultName || ruleForm.value.ruleName
  applyPresetToRuleForm({ forceExpression: true })
}

const handlePresetParamChange = () => {
  applyPresetToRuleForm({ preserveName: true, preserveSeverity: true, preserveCooldown: true })
}

const markAdvancedExpressionEdited = () => {
  advancedExpressionEdited.value = true
}

const parseConditionExpr = (expr) => {
  const match = String(expr || '').trim().match(/^([A-Za-z][\w]*)\s*(==|!=|>=|<=|>|<)\s*([A-Za-z0-9_.-]+)$/)
  if (!match) return null
  return { field: match[1], operator: match[2], value: match[3] }
}

const compareConditionExpr = (left, right) => {
  const leftExpr = parseConditionExpr(left)
  const rightExpr = parseConditionExpr(right)
  if (!leftExpr || !rightExpr) {
    return String(left || '').trim() === String(right || '').trim()
  }
  if (leftExpr.field !== rightExpr.field || leftExpr.operator !== rightExpr.operator) return false

  const leftNumber = Number(leftExpr.value)
  const rightNumber = Number(rightExpr.value)
  if (Number.isFinite(leftNumber) && Number.isFinite(rightNumber)) {
    return Math.abs(leftNumber - rightNumber) < 0.000001
  }
  return leftExpr.value === rightExpr.value
}

const inferPresetParams = (rule, preset) => {
  const params = createPresetParams(preset)
  const expr = rule?.conditionExpr || ''
  const numericMatch = expr.match(/(-?\d+(?:\.\d+)?)\s*$/)
  const value = numericMatch ? Number(numericMatch[1]) : Number(rule?.thresholdValue)

  if (preset.key === 'collab-success-critical' || preset.key === 'collab-bidding-success-critical') {
    params.threshold = Number.isFinite(value) ? value : params.threshold
  } else if (preset.key === 'collab-p95-critical') {
    params.seconds = Number.isFinite(value) ? value / 1000 : params.seconds
  } else if (preset.key === 'collab-dlq-critical' || preset.key === 'api-failure') {
    params.count = Number.isFinite(value) ? value : params.count
  } else if (preset.key === 'collab-critique-high') {
    params.rounds = Number.isFinite(value) ? value : params.rounds
  } else if (preset.key === 'system-health-down') {
    params.dependency = rule?.targetScope === 'DEPENDENCY' ? normalizeTarget(rule.targetId) : ''
  } else if (preset.key === 'api-single-failure') {
    params.providerId = rule?.targetScope === 'PROVIDER' ? rule.targetId : ''
  } else if (preset.key === 'api-error-rate') {
    params.threshold = Number.isFinite(value) ? value : params.threshold
    params.window = rule?.metricWindowMinutes || params.window
    params.minSamples = rule?.minSampleCount || params.minSamples
    params.providerId = rule?.targetScope === 'PROVIDER' ? rule.targetId : ''
  }

  if (preset.key === 'api-failure') {
    params.window = rule?.metricWindowMinutes || params.window
    params.providerId = rule?.targetScope === 'PROVIDER' ? rule.targetId : ''
  }
  return params
}

const matchesPresetExpression = (rule, preset) => {
  const params = inferPresetParams(rule, preset)
  const expectedTargetScope = preset.targetScopeBuilder(params)
  const expectedTargetId = preset.targetIdBuilder(params)
  const actualTargetScope = normalizeTarget(rule.targetScope || 'ALL')
  const actualTargetId = rule.targetId || ''
  return compareConditionExpr(rule.conditionExpr, preset.conditionBuilder(params)) &&
    normalizeTarget(expectedTargetScope) === actualTargetScope &&
    normalizeTarget(expectedTargetId) === normalizeTarget(actualTargetId)
}

const findRulePreset = (rule) => {
  return rulePresets.find(preset => preset.ruleType === rule.ruleType && matchesPresetExpression(rule, preset))
}

const getPresetConditionSummary = (rule) => {
  const preset = selectedPreset.value
  if (!preset || advancedExpressionEdited.value) {
    return rule?.conditionExpr ? `自定义条件：${rule.conditionExpr}` : '自定义条件'
  }
  return preset.summaryBuilder(presetParams.value)
}

const getRuleTypeText = (type) => {
  const map = {
    ERROR_RATE: '错误率',
    SYSTEM_HEALTH: '系统健康',
    COLLAB_HEALTH: '协作健康'
  }
  return map[type] || type
}

const loadRule = async () => {
  if (!isEditMode.value) {
    selectedPresetKey.value = 'collab-overall-red'
    presetParams.value = createPresetParams(selectedPreset.value)
    applyPresetToRuleForm({ forceExpression: true })
    return
  }

  const rules = await request.get('/alerts/rules')
  const rule = (rules || []).find(item => item.id === route.params.id)
  if (!rule) {
    ElMessage.error('告警规则不存在')
    goBack()
    return
  }

  ruleForm.value = { ...rule }
  const preset = findRulePreset(rule)
  selectedPresetKey.value = preset?.key || 'custom'
  presetParams.value = preset ? inferPresetParams(rule, preset) : {}
  advancedExpressionEdited.value = !preset || !compareConditionExpr(rule.conditionExpr, preset.conditionBuilder(presetParams.value))
  advancedPanels.value = advancedExpressionEdited.value ? ['expression'] : []
  selectedChannels.value = rule.notificationChannels
    ?.split(',')
    .map(channel => channel.trim())
    .filter(Boolean) || []
}

const saveRule = async () => {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  applyPresetToRuleForm({ preserveName: true, preserveSeverity: true, preserveCooldown: true })

  if (!ruleForm.value.conditionExpr?.trim()) {
    ElMessage.error('请选择规则场景或填写高级条件表达式')
    return
  }

  const payload = {
    ...ruleForm.value,
    notificationChannels: selectedChannels.value.join(',')
  }

  saving.value = true
  try {
    if (isEditMode.value) {
      await request.put(`/alerts/rules/${route.params.id}`, payload)
      ElMessage.success('规则更新成功')
    } else {
      await request.post('/alerts/rules', payload)
      ElMessage.success('规则创建成功')
    }
    goBack()
  } catch (error) {
    ElMessage.error('保存规则失败')
  } finally {
    saving.value = false
  }
}

const goBack = () => {
  router.push('/dashboard/runtime/alerts')
}

onMounted(loadRule)
</script>

<style scoped>
.alert-rule-builder-page {
  padding: 20px;
}

.builder-card {
  border-radius: 12px;
  border: 1px solid rgba(203, 213, 225, 0.78);
}

.builder-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.builder-section {
  padding: 16px;
  border: 1px solid rgba(226, 232, 240, 0.9);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.72);
}

.builder-section-head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.builder-step {
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  background: var(--orin-primary, #0d9488);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}

.builder-section-head h3 {
  margin: 0;
  color: var(--text-primary, #0f172a);
  font-size: 16px;
  line-height: 1.3;
}

.builder-section-head p {
  margin: 4px 0 0;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  line-height: 1.5;
}

.preset-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.preset-card {
  min-width: 0;
  min-height: 132px;
  padding: 14px;
  text-align: left;
  border: 1px solid rgba(203, 213, 225, 0.92);
  border-radius: 8px;
  background: #fff;
  color: inherit;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, transform 0.16s ease;
}

.preset-card:hover {
  border-color: rgba(13, 148, 136, 0.56);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

.preset-card.active {
  border-color: var(--orin-primary, #0d9488);
  background: rgba(240, 253, 250, 0.86);
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.12);
}

.preset-card strong,
.preset-card small {
  display: block;
}

.preset-card strong {
  margin-top: 8px;
  font-size: 15px;
  line-height: 1.35;
  color: var(--text-primary, #0f172a);
}

.preset-card small {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-secondary, #64748b);
}

.preset-type {
  display: inline-flex;
  align-items: center;
  min-height: 20px;
  padding: 0 7px;
  border-radius: 999px;
  background: rgba(15, 118, 110, 0.1);
  color: var(--orin-primary, #0d9488);
  font-size: 11px;
  font-weight: 700;
}

.builder-form {
  max-width: 760px;
}

.condition-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.unit-text {
  margin-left: 8px;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
}

.condition-preview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 4px;
  padding: 12px 14px;
  border-radius: 8px;
  border: 1px dashed rgba(13, 148, 136, 0.42);
  background: rgba(240, 253, 250, 0.66);
}

.condition-preview span {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.condition-preview strong {
  min-width: 0;
  color: #0f766e;
  font-size: 14px;
  line-height: 1.45;
  text-align: right;
}

.advanced-rule-panel {
  border-top: 1px solid rgba(226, 232, 240, 0.9);
  border-bottom: 1px solid rgba(226, 232, 240, 0.9);
}

.advanced-tag {
  margin-left: 8px;
}

.action-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

html.dark .builder-card,
html.dark .builder-section {
  border-color: rgba(100, 116, 139, 0.46);
  background: rgba(15, 23, 42, 0.38);
}

html.dark .preset-card {
  border-color: rgba(100, 116, 139, 0.52);
  background: rgba(15, 23, 42, 0.46);
}

html.dark .preset-card.active {
  border-color: rgba(45, 212, 191, 0.78);
  background: rgba(20, 184, 166, 0.14);
}

html.dark .builder-section-head h3,
html.dark .preset-card strong {
  color: #f1f5f9;
}

html.dark .builder-section-head p,
html.dark .preset-card small {
  color: #cbd5e1;
}

html.dark .condition-preview {
  border-color: rgba(45, 212, 191, 0.38);
  background: rgba(20, 184, 166, 0.12);
}

html.dark .condition-preview span,
html.dark .condition-preview strong {
  color: #99f6e4;
}

@media (max-width: 1100px) {
  .preset-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .alert-rule-builder-page {
    padding: 12px;
  }

  .preset-grid,
  .condition-grid {
    grid-template-columns: 1fr;
  }

  .condition-preview {
    align-items: flex-start;
    flex-direction: column;
  }

  .condition-preview strong {
    text-align: left;
  }
}
</style>
