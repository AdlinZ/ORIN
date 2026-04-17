<template>
  <div class="gateway-routes-tab">
    <div class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#2563eb"><Share /></el-icon>
          API 路由
        </div>
        <div class="header-actions">
          <el-button size="small" @click="openTestDialog">
            <el-icon><Connection /></el-icon>
            测试路由
          </el-button>
          <el-button type="primary" size="small" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            添加路由
          </el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="routes" stripe style="border-radius:0">
        <el-table-column prop="name" label="名称" width="140" />
        <el-table-column prop="pathPattern" label="路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="80">
          <template #default="{ row }">
            <el-tag :type="getMethodType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型 / 目标" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap">
              <el-tag v-if="isLocalTableRoute(row)" type="primary" size="small" effect="dark">LOCAL</el-tag>
              <el-tag v-else type="success" size="small" effect="dark">PROXY</el-tag>
              <span v-if="row.targetUrl" class="target-text">{{ row.targetUrl }}</span>
              <span v-else-if="row.serviceId" class="target-text">{{ getServiceName(row.serviceId) }}</span>
              <span v-else class="text-muted">本地处理</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="160">
          <template #default="{ row }">
            <el-space wrap>
              <el-tag v-if="row.rateLimitPolicyId" type="warning" size="small" effect="plain">限流</el-tag>
              <el-tag v-if="row.circuitBreakerPolicyId" type="danger" size="small" effect="plain">熔断</el-tag>
              <el-tag v-if="row.retryPolicyId" type="success" size="small" effect="plain">重试</el-tag>
              <el-tag v-if="row.authRequired" type="info" size="small" effect="plain">认证</el-tag>
            </el-space>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="80" />
        <el-table-column prop="enabled" label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" @change="toggleRoute(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deleteRoute(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑路由' : '添加路由'" width="760px">
      <el-form :model="form" label-width="110px">
        <!-- 基础配置 -->
        <el-divider content-position="left">基础配置</el-divider>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="名称" required>
              <el-input v-model="form.name" placeholder="路由名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="方法">
              <el-select v-model="form.method" style="width:100%">
                <el-option label="ALL" value="ALL" />
                <el-option label="GET" value="GET" />
                <el-option label="POST" value="POST" />
                <el-option label="PUT" value="PUT" />
                <el-option label="PATCH" value="PATCH" />
                <el-option label="DELETE" value="DELETE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="路径" required>
          <el-input v-model="form.pathPattern" placeholder="/api/v1/agents/**" />
        </el-form-item>

        <!-- 目标配置 -->
        <el-divider content-position="left">目标配置</el-divider>

        <el-alert type="info" :closable="false" style="margin-bottom:14px" v-if="isLocalFormRoute">
          <template #title>
            <strong>本地策略路由 (LOCAL)</strong>
          </template>
          目标地址和关联服务均为空时，此路由为本地策略路由：请求不会被代理转发，
          而是经过 ACL → 认证 → 路由级限流 → 审计日志 后，直接交给 ORIN 本地 Controller 处理。
          适用于对 <code>/api/v1/**</code>、<code>/v1/**</code> 等 ORIN 自身端点施加统一策略。
        </el-alert>

        <el-form-item label="关联服务">
          <el-select v-model="form.serviceId" clearable placeholder="选择后端服务（优先级高于直连地址）" style="width:100%">
            <el-option v-for="svc in services" :key="svc.id" :label="svc.serviceName" :value="svc.id" />
          </el-select>
          <div class="form-tip">选择服务后将使用服务实例做负载均衡，无需填写直连地址</div>
        </el-form-item>
        <el-form-item label="直连地址">
          <el-input v-model="form.targetUrl" placeholder="http://localhost:8080/api（均不填则为本地策略路由）" :disabled="!!form.serviceId" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="去除前缀">
              <el-switch v-model="form.stripPrefix" />
              <span class="form-tip">匹配路径去掉前缀后再转发</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重写路径">
              <el-input v-model="form.rewritePath" placeholder="/api/v2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="负载均衡">
              <el-select v-model="form.loadBalance" style="width:100%">
                <el-option label="轮询 (ROUND_ROBIN)" value="ROUND_ROBIN" />
                <el-option label="随机 (RANDOM)" value="RANDOM" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="超时(ms)">
              <el-input-number v-model="form.timeoutMs" :min="100" :max="300000" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 策略绑定 -->
        <el-divider content-position="left">策略绑定</el-divider>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="限流策略">
              <el-select v-model="form.rateLimitPolicyId" clearable placeholder="不限流" style="width:100%">
                <el-option v-for="p in rateLimitPolicies" :key="p.id" :label="`${p.name}（${p.capacity}/${p.windowSeconds}s）`" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="熔断策略">
              <el-select v-model="form.circuitBreakerPolicyId" clearable placeholder="不熔断" style="width:100%">
                <el-option v-for="p in circuitBreakerPolicies" :key="p.id" :label="`${p.name}（失败阈值 ${p.failureThreshold}）`" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="重试策略">
              <el-select v-model="form.retryPolicyId" clearable placeholder="不重试（或使用下方次数）" style="width:100%">
                <el-option v-for="p in retryPolicies" :key="p.id" :label="`${p.name}（最多 ${p.maxAttempts} 次）`" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试次数">
              <el-input-number v-model="form.retryCount" :min="0" :max="10" style="width:100%" />
              <div class="form-tip">未绑定重试策略时生效，额外重试次数</div>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 安全 & 其他 -->
        <el-divider content-position="left">安全 & 其他</el-divider>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="要求认证">
              <el-switch v-model="form.authRequired" />
              <span class="form-tip">开启后须携带 JWT 或 API Key</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="1000" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoute" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- Test Route Dialog -->
    <el-dialog v-model="testDialogVisible" title="测试路由匹配" width="500px">
      <el-form :model="testForm" label-width="80px">
        <el-form-item label="请求路径">
          <el-input v-model="testForm.path" placeholder="/api/v1/example" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="testForm.method" style="width:100%">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="testResult" :type="testResult.success ? 'success' : 'warning'" :closable="false" style="margin-top:12px">
        <template #title>
          <span v-if="testResult.success">
            匹配路由：<strong>{{ testResult.matchedRoute }}</strong>
            → {{ testResult.targetUrl }}
          </span>
          <span v-else>无匹配路由</span>
        </template>
      </el-alert>
      <template #footer>
        <el-button @click="testDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="runTest" :loading="testing">测试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Plus, Share } from '@element-plus/icons-vue'
import {
  getRoutes, createRoute, updateRoute, deleteRoute as removeRoute, patchRoute, testRoute,
  getAllPolicies, getServices
} from '@/api/gateway'

const loading = ref(false)
const submitting = ref(false)
const testing = ref(false)
const routes = ref([])
const services = ref([])
const rateLimitPolicies = ref([])
const circuitBreakerPolicies = ref([])
const retryPolicies = ref([])
const dialogVisible = ref(false)
const testDialogVisible = ref(false)
const isEdit = ref(false)
const testResult = ref(null)

const defaultForm = () => ({
  id: null,
  name: '',
  pathPattern: '',
  method: 'ALL',
  serviceId: null,
  targetUrl: '',
  stripPrefix: false,
  rewritePath: '',
  timeoutMs: 30000,
  loadBalance: 'ROUND_ROBIN',
  retryCount: 0,
  rateLimitPolicyId: null,
  circuitBreakerPolicyId: null,
  retryPolicyId: null,
  authRequired: true,
  priority: 0,
  description: ''
})

const form = reactive(defaultForm())

const testForm = reactive({ path: '', method: 'GET' })

const getMethodType = (method) => {
  const map = { GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', ALL: 'info', PATCH: 'warning' }
  return map[method] || 'info'
}

const getServiceName = (id) => {
  const svc = services.value.find(s => s.id === id)
  return svc ? svc.serviceName : `Service#${id}`
}

/** 表格行判断：本地路由（无 targetUrl 且无 serviceId） */
const isLocalTableRoute = (row) => !row.targetUrl && !row.serviceId

/** 表单实时判断：当前编辑的是否为本地路由 */
const isLocalFormRoute = computed(() => !form.targetUrl && !form.serviceId)

const loadRoutes = async () => {
  loading.value = true
  try {
    routes.value = await getRoutes()
  } catch (e) {
    ElMessage.error('加载路由失败')
  } finally {
    loading.value = false
  }
}

const loadMeta = async () => {
  try {
    const [policiesRes, servicesRes] = await Promise.all([getAllPolicies(), getServices()])
    rateLimitPolicies.value = policiesRes.rateLimitPolicies || []
    circuitBreakerPolicies.value = policiesRes.circuitBreakerPolicies || []
    retryPolicies.value = policiesRes.retryPolicies || []
    services.value = servicesRes || []
  } catch (e) {
    // 策略/服务加载失败不阻断主流程
  }
}

const openCreateDialog = () => {
  isEdit.value = false
  Object.assign(form, defaultForm())
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(form, defaultForm(), { ...row })
  dialogVisible.value = true
}

const saveRoute = async () => {
  if (!form.name || !form.pathPattern) {
    ElMessage.warning('名称和路径为必填项')
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateRoute(form.id, form)
      ElMessage.success('路由已更新')
    } else {
      await createRoute(form)
      ElMessage.success('路由已添加')
    }
    dialogVisible.value = false
    loadRoutes()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    submitting.value = false
  }
}

const deleteRoute = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除路由 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await removeRoute(row.id)
    ElMessage.success('路由已删除')
    loadRoutes()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const toggleRoute = async (row) => {
  try {
    await patchRoute(row.id, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '路由已启用' : '路由已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败')
  }
}

const openTestDialog = () => {
  testResult.value = null
  testDialogVisible.value = true
}

const runTest = async () => {
  if (!testForm.path) {
    ElMessage.warning('请输入请求路径')
    return
  }
  testing.value = true
  try {
    testResult.value = await testRoute(testForm)
  } catch (e) {
    ElMessage.error('测试失败')
  } finally {
    testing.value = false
  }
}

onMounted(() => {
  loadRoutes()
  loadMeta()
})
</script>

<style scoped>
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

.header-actions {
  display: flex;
  gap: 8px;
}
.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}
.text-muted {
  color: var(--el-text-color-placeholder);
}
.target-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
