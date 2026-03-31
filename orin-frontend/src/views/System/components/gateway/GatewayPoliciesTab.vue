<template>
  <div class="gateway-policies-tab">
    <el-tabs v-model="activePolicyTab">
      <!-- Rate Limit Policies -->
      <el-tab-pane label="限流策略" name="rate-limit">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>限流策略</span>
              <el-button type="primary" size="small" @click="openPolicyDialog('rate-limit', null)">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="rateLimitPolicies" stripe>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="dimension" label="维度" width="100" />
            <el-table-column prop="capacity" label="容量" width="80" />
            <el-table-column prop="windowSeconds" label="窗口(秒)" width="100" />
            <el-table-column prop="burst" label="突发" width="80" />
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
        </el-card>
      </el-tab-pane>

      <!-- Circuit Breaker Policies -->
      <el-tab-pane label="熔断策略" name="circuit-breaker">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>熔断策略</span>
              <el-button type="primary" size="small" @click="openPolicyDialog('circuit-breaker', null)">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="circuitBreakerPolicies" stripe>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="failureThreshold" label="失败阈值" width="100" />
            <el-table-column prop="successThreshold" label="成功阈值" width="100" />
            <el-table-column prop="timeoutSeconds" label="超时(秒)" width="100" />
            <el-table-column prop="halfOpenMaxRequests" label="半开请求数" width="120" />
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
        </el-card>
      </el-tab-pane>

      <!-- Retry Policies -->
      <el-tab-pane label="重试策略" name="retry">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>重试策略</span>
              <el-button type="primary" size="small" @click="openPolicyDialog('retry', null)">
                <el-icon><Plus /></el-icon>
                添加
              </el-button>
            </div>
          </template>
          <el-table v-loading="loading" :data="retryPolicies" stripe>
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="maxAttempts" label="最大尝试" width="100" />
            <el-table-column prop="retryOnStatusCodes" label="重试状态码" show-overflow-tooltip />
            <el-table-column prop="backoffMultiplier" label="退避倍数" width="100" />
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
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- Policy Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑策略' : '添加策略'" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="策略名称" />
        </el-form-item>

        <!-- Rate Limit Fields -->
        <template v-if="currentPolicyType === 'rate-limit'">
          <el-form-item label="维度">
            <el-select v-model="form.dimension">
              <el-option label="全局" value="GLOBAL" />
              <el-option label="用户" value="USER" />
              <el-option label="API Key" value="API_KEY" />
              <el-option label="IP" value="IP" />
              <el-option label="路由" value="ROUTE" />
            </el-select>
          </el-form-item>
          <el-form-item label="容量">
            <el-input-number v-model="form.capacity" :min="1" />
          </el-form-item>
          <el-form-item label="窗口(秒)">
            <el-input-number v-model="form.windowSeconds" :min="1" />
          </el-form-item>
          <el-form-item label="突发容量">
            <el-input-number v-model="form.burst" :min="0" />
          </el-form-item>
        </template>

        <!-- Circuit Breaker Fields -->
        <template v-if="currentPolicyType === 'circuit-breaker'">
          <el-form-item label="失败阈值">
            <el-input-number v-model="form.failureThreshold" :min="1" />
          </el-form-item>
          <el-form-item label="成功阈值">
            <el-input-number v-model="form.successThreshold" :min="1" />
          </el-form-item>
          <el-form-item label="超时(秒)">
            <el-input-number v-model="form.timeoutSeconds" :min="1" />
          </el-form-item>
          <el-form-item label="半开最大请求">
            <el-input-number v-model="form.halfOpenMaxRequests" :min="1" />
          </el-form-item>
        </template>

        <!-- Retry Fields -->
        <template v-if="currentPolicyType === 'retry'">
          <el-form-item label="最大尝试次数">
            <el-input-number v-model="form.maxAttempts" :min="1" :max="10" />
          </el-form-item>
          <el-form-item label="重试状态码">
            <el-input v-model="form.retryOnStatusCodes" placeholder="500,502,503,504" />
          </el-form-item>
          <el-form-item label="退避倍数">
            <el-input-number v-model="form.backoffMultiplier" :min="1.0" :step="0.1" />
          </el-form-item>
          <el-form-item label="初始间隔(ms)">
            <el-input-number v-model="form.initialIntervalMs" :min="10" />
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
import { getAllPolicies, createRateLimitPolicy, updateRateLimitPolicy, deleteRateLimitPolicy,
         createCircuitBreakerPolicy, updateCircuitBreakerPolicy, deleteCircuitBreakerPolicy,
         createRetryPolicy, updateRetryPolicy, deleteRetryPolicy } from '@/api/gateway'

const activePolicyTab = ref('rate-limit')
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const currentPolicyType = ref('rate-limit')

const rateLimitPolicies = ref([])
const circuitBreakerPolicies = ref([])
const retryPolicies = ref([])

const form = reactive({
  id: null, name: '', dimension: 'GLOBAL', capacity: 100, windowSeconds: 60, burst: 10,
  failureThreshold: 5, successThreshold: 2, timeoutSeconds: 60, halfOpenMaxRequests: 3,
  maxAttempts: 3, retryOnStatusCodes: '500,502,503,504', backoffMultiplier: 2.0, initialIntervalMs: 100,
  description: ''
})

const loadAllPolicies = async () => {
  loading.value = true
  try {
    const res = await getAllPolicies()
    rateLimitPolicies.value = res.rateLimitPolicies || []
    circuitBreakerPolicies.value = res.circuitBreakerPolicies || []
    retryPolicies.value = res.retryPolicies || []
  } catch (e) {
    ElMessage.error('加载策略失败')
  } finally {
    loading.value = false
  }
}

const openPolicyDialog = (type, row) => {
  currentPolicyType.value = type
  isEdit.value = !!row
  if (row) {
    Object.assign(form, { ...row })
  } else {
    resetForm()
  }
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, {
    id: null, name: '', description: '',
    dimension: 'GLOBAL', capacity: 100, windowSeconds: 60, burst: 10,
    failureThreshold: 5, successThreshold: 2, timeoutSeconds: 60, halfOpenMaxRequests: 3,
    maxAttempts: 3, retryOnStatusCodes: '500,502,503,504', backoffMultiplier: 2.0, initialIntervalMs: 100
  })
}

const savePolicy = async () => {
  submitting.value = true
  try {
    if (isEdit.value) {
      if (currentPolicyType.value === 'rate-limit') await updateRateLimitPolicy(form.id, form)
      else if (currentPolicyType.value === 'circuit-breaker') await updateCircuitBreakerPolicy(form.id, form)
      else await updateRetryPolicy(form.id, form)
      ElMessage.success('策略已更新')
    } else {
      if (currentPolicyType.value === 'rate-limit') await createRateLimitPolicy(form)
      else if (currentPolicyType.value === 'circuit-breaker') await createCircuitBreakerPolicy(form)
      else await createRetryPolicy(form)
      ElMessage.success('策略已添加')
    }
    dialogVisible.value = false
    loadAllPolicies()
  } catch (e) {
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
    ElMessage.success(row.enabled ? '策略已启用' : '策略已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  }
}

loadAllPolicies()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
