<template>
  <div class="gateway-policies-tab">
    <!-- Inner Segmented Nav -->
    <div class="policy-nav">
      <button
        v-for="p in policyTypes"
        :key="p.name"
        class="policy-nav-item"
        :class="{ active: activePolicyTab === p.name }"
        @click="activePolicyTab = p.name"
      >
        <el-icon><component :is="p.icon" /></el-icon>
        {{ p.label }}
        <span v-if="policyCount(p.name)" class="policy-badge">{{ policyCount(p.name) }}</span>
      </button>
    </div>

    <!-- Rate Limit Policies -->
    <div v-show="activePolicyTab === 'rate-limit'" class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#d97706"><Lightning /></el-icon>
          限流策略
        </div>
        <el-button type="primary" size="small" @click="openPolicyDialog('rate-limit', null)">
          <el-icon><Plus /></el-icon>添加
        </el-button>
      </div>
      <el-table v-loading="loading" :data="rateLimitPolicies" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="dimension" label="维度" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ dimensionLabel(row.dimension) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规则" min-width="140">
          <template #default="{ row }">
            <span class="rule-text">{{ row.capacity }} 次 / {{ row.windowSeconds }}s</span>
            <span v-if="row.burst" class="rule-burst">突发 {{ row.burst }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="togglePolicy('rate-limit', row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openPolicyDialog('rate-limit', row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deletePolicy('rate-limit', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Circuit Breaker Policies -->
    <div v-show="activePolicyTab === 'circuit-breaker'" class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#dc2626"><Warning /></el-icon>
          熔断策略
        </div>
        <el-button type="primary" size="small" @click="openPolicyDialog('circuit-breaker', null)">
          <el-icon><Plus /></el-icon>添加
        </el-button>
      </div>
      <el-table v-loading="loading" :data="circuitBreakerPolicies" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column label="触发阈值" width="100" align="center">
          <template #default="{ row }">失败 {{ row.failureThreshold }} 次</template>
        </el-table-column>
        <el-table-column label="恢复阈值" width="100" align="center">
          <template #default="{ row }">成功 {{ row.successThreshold }} 次</template>
        </el-table-column>
        <el-table-column prop="timeoutSeconds" label="超时(秒)" width="100" align="center" />
        <el-table-column prop="halfOpenMaxRequests" label="半开请求数" width="110" align="center" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="togglePolicy('circuit-breaker', row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openPolicyDialog('circuit-breaker', row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deletePolicy('circuit-breaker', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Retry Policies -->
    <div v-show="activePolicyTab === 'retry'" class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#2563eb"><RefreshRight /></el-icon>
          重试策略
        </div>
        <el-button type="primary" size="small" @click="openPolicyDialog('retry', null)">
          <el-icon><Plus /></el-icon>添加
        </el-button>
      </div>
      <el-table v-loading="loading" :data="retryPolicies" stripe>
        <el-table-column prop="name" label="名称" />
        <el-table-column prop="maxAttempts" label="最大尝试" width="100" align="center" />
        <el-table-column prop="retryOnStatusCodes" label="重试状态码" show-overflow-tooltip />
        <el-table-column label="退避策略" width="130">
          <template #default="{ row }">
            × {{ row.backoffMultiplier }}，初始 {{ row.initialIntervalMs }}ms
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="togglePolicy('retry', row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openPolicyDialog('retry', row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deletePolicy('retry', row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Policy Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑策略' : '添加策略'" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="策略名称" />
        </el-form-item>

        <template v-if="currentPolicyType === 'rate-limit'">
          <el-form-item label="维度">
            <el-select v-model="form.dimension" style="width:100%">
              <el-option v-for="d in dimensionOptions" :key="d.value" :label="d.label" :value="d.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="容量">
            <el-input-number v-model="form.capacity" :min="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="窗口(秒)">
            <el-input-number v-model="form.windowSeconds" :min="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="突发容量">
            <el-input-number v-model="form.burst" :min="0" style="width:100%" />
          </el-form-item>
        </template>

        <template v-if="currentPolicyType === 'circuit-breaker'">
          <el-form-item label="失败阈值">
            <el-input-number v-model="form.failureThreshold" :min="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="成功阈值">
            <el-input-number v-model="form.successThreshold" :min="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="超时(秒)">
            <el-input-number v-model="form.timeoutSeconds" :min="1" style="width:100%" />
          </el-form-item>
          <el-form-item label="半开最大请求">
            <el-input-number v-model="form.halfOpenMaxRequests" :min="1" style="width:100%" />
          </el-form-item>
        </template>

        <template v-if="currentPolicyType === 'retry'">
          <el-form-item label="最大尝试次数">
            <el-input-number v-model="form.maxAttempts" :min="1" :max="10" style="width:100%" />
          </el-form-item>
          <el-form-item label="重试状态码">
            <el-input v-model="form.retryOnStatusCodes" placeholder="500,502,503,504" />
          </el-form-item>
          <el-form-item label="退避倍数">
            <el-input-number v-model="form.backoffMultiplier" :min="1.0" :step="0.1" style="width:100%" />
          </el-form-item>
          <el-form-item label="初始间隔(ms)">
            <el-input-number v-model="form.initialIntervalMs" :min="10" style="width:100%" />
          </el-form-item>
        </template>

        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePolicy" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Lightning, Warning, RefreshRight, Plus } from '@element-plus/icons-vue'
import {
  getAllPolicies,
  createRateLimitPolicy, updateRateLimitPolicy, deleteRateLimitPolicy,
  createCircuitBreakerPolicy, updateCircuitBreakerPolicy, deleteCircuitBreakerPolicy,
  createRetryPolicy, updateRetryPolicy, deleteRetryPolicy
} from '@/api/gateway'

const activePolicyTab = ref('rate-limit')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentPolicyType = ref('rate-limit')

const rateLimitPolicies = ref([])
const circuitBreakerPolicies = ref([])
const retryPolicies = ref([])

const policyTypes = [
  { name: 'rate-limit',      label: '限流策略', icon: Lightning },
  { name: 'circuit-breaker', label: '熔断策略', icon: Warning },
  { name: 'retry',           label: '重试策略', icon: RefreshRight },
]

const dimensionOptions = [
  { label: '全局',    value: 'GLOBAL' },
  { label: '用户',    value: 'USER' },
  { label: 'API Key', value: 'API_KEY' },
  { label: 'IP',      value: 'IP' },
  { label: '路由',    value: 'ROUTE' },
]

const dimensionLabel = (v) => dimensionOptions.find(d => d.value === v)?.label || v

const policyCount = (type) => {
  if (type === 'rate-limit') return rateLimitPolicies.value.length
  if (type === 'circuit-breaker') return circuitBreakerPolicies.value.length
  return retryPolicies.value.length
}

const form = reactive({
  id: null, name: '', description: '',
  dimension: 'GLOBAL', capacity: 100, windowSeconds: 60, burst: 10,
  failureThreshold: 5, successThreshold: 2, timeoutSeconds: 60, halfOpenMaxRequests: 3,
  maxAttempts: 3, retryOnStatusCodes: '500,502,503,504', backoffMultiplier: 2.0, initialIntervalMs: 100
})

const loadAllPolicies = async () => {
  loading.value = true
  try {
    const res = await getAllPolicies()
    rateLimitPolicies.value = res.rateLimitPolicies || []
    circuitBreakerPolicies.value = res.circuitBreakerPolicies || []
    retryPolicies.value = res.retryPolicies || []
  } catch {
    ElMessage.error('加载策略失败')
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  Object.assign(form, {
    id: null, name: '', description: '',
    dimension: 'GLOBAL', capacity: 100, windowSeconds: 60, burst: 10,
    failureThreshold: 5, successThreshold: 2, timeoutSeconds: 60, halfOpenMaxRequests: 3,
    maxAttempts: 3, retryOnStatusCodes: '500,502,503,504', backoffMultiplier: 2.0, initialIntervalMs: 100
  })
}

const openPolicyDialog = (type, row) => {
  currentPolicyType.value = type
  isEdit.value = !!row
  row ? Object.assign(form, { ...row }) : resetForm()
  dialogVisible.value = true
}

const savePolicy = async () => {
  if (!form.name) { ElMessage.warning('名称为必填项'); return }
  submitting.value = true
  try {
    const t = currentPolicyType.value
    if (isEdit.value) {
      if (t === 'rate-limit') await updateRateLimitPolicy(form.id, form)
      else if (t === 'circuit-breaker') await updateCircuitBreakerPolicy(form.id, form)
      else await updateRetryPolicy(form.id, form)
      ElMessage.success('策略已更新')
    } else {
      if (t === 'rate-limit') await createRateLimitPolicy(form)
      else if (t === 'circuit-breaker') await createCircuitBreakerPolicy(form)
      else await createRetryPolicy(form)
      ElMessage.success('策略已添加')
    }
    dialogVisible.value = false
    loadAllPolicies()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const deletePolicy = async (type, row) => {
  try {
    await ElMessageBox.confirm(`确定要删除策略 "${row.name}" 吗?`, '提示', { type: 'warning' })
    if (type === 'rate-limit') await deleteRateLimitPolicy(row.id)
    else if (type === 'circuit-breaker') await deleteCircuitBreakerPolicy(row.id)
    else await deleteRetryPolicy(row.id)
    ElMessage.success('策略已删除')
    loadAllPolicies()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const togglePolicy = async (type, row) => {
  try {
    if (type === 'rate-limit') await updateRateLimitPolicy(row.id, { enabled: row.enabled })
    else if (type === 'circuit-breaker') await updateCircuitBreakerPolicy(row.id, { enabled: row.enabled })
    else await updateRetryPolicy(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '已启用' : '已禁用')
  } catch {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  }
}

loadAllPolicies()
</script>

<style scoped>
.gateway-policies-tab {
  padding: 0;
}

/* Policy inner segmented nav */
.policy-nav {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.policy-nav-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border: 1px solid var(--neutral-gray-200, #e5e7eb);
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--neutral-gray-500, #6b7280);
  cursor: pointer;
  transition: all 0.15s ease;
}

.policy-nav-item .el-icon {
  font-size: 14px;
}

.policy-nav-item:hover:not(.active) {
  border-color: var(--el-color-primary, #2563eb);
  color: var(--el-color-primary, #2563eb);
}

.policy-nav-item.active {
  background: var(--el-color-primary-light-9, #eff6ff);
  border-color: var(--el-color-primary, #2563eb);
  color: var(--el-color-primary, #2563eb);
}

.policy-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  background: var(--el-color-primary, #2563eb);
  color: #fff;
  border-radius: 9px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
}

.policy-nav-item:not(.active) .policy-badge {
  background: var(--neutral-gray-300, #d1d5db);
}

/* Section card */
.section-card {
  background: #fff;
  border: 1px solid var(--neutral-gray-100, #f0f0f0);
  border-radius: 10px;
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid var(--neutral-gray-100, #f0f0f0);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-700, #374151);
}

.rule-text {
  font-size: 13px;
  color: var(--neutral-gray-700, #374151);
}

.rule-burst {
  margin-left: 6px;
  font-size: 12px;
  color: var(--neutral-gray-400, #9ca3af);
}
</style>
