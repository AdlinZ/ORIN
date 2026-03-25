<template>
  <div class="api-gateway-container">
    <PageHeader
      title="统一网关"
      description="管理 API 网关配置、路由规则和访问控制"
      icon="Router"
    />

    <el-tabs v-model="activeTab" class="gateway-tabs">
      <!-- 网关概览 -->
      <el-tab-pane label="网关概览" name="overview">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">{{ gatewayStats.totalRequests }}</div>
              <div class="stat-label">总请求数</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">{{ gatewayStats.qps }}</div>
              <div class="stat-label">QPS</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">{{ gatewayStats.avgLatency }}ms</div>
              <div class="stat-label">平均延迟</div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value" :class="{ 'text-success': gatewayStats.errorRate < 1, 'text-danger': gatewayStats.errorRate >= 1 }">
                {{ gatewayStats.errorRate }}%
              </div>
              <div class="stat-label">错误率</div>
            </el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>在线服务</span>
              <el-tag type="success">正常运行</el-tag>
            </div>
          </template>
          <el-table :data="onlineServices">
            <el-table-column prop="name" label="服务名称" />
            <el-table-column prop="host" label="地址" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag type="success">在线</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="qps" label="QPS" width="100" />
            <el-table-column prop="latency" label="延迟" width="100" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 路由管理 -->
      <el-tab-pane label="路由管理" name="routes" :lazy="true">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>API 路由</span>
              <el-button type="primary" size="small" @click="openRouteDialog()">
                <el-icon><Plus /></el-icon>
                添加路由
              </el-button>
            </div>
          </template>

          <el-table :data="routes" v-loading="routesLoading" stripe>
            <el-table-column prop="path" label="路由路径" min-width="180" />
            <el-table-column prop="method" label="方法" width="80">
              <template #default="{ row }">
                <el-tag :type="getMethodType(row.method)" size="small">{{ row.method }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="service" label="目标服务" min-width="150" />
            <el-table-column prop="timeout" label="超时" width="80" />
            <el-table-column prop="rateLimit" label="限流" width="100" />
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="toggleRoute(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="editRoute(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="deleteRoute(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 访问控制 -->
      <el-tab-pane label="访问控制" name="acl" :lazy="true">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>IP 白名单/黑名单</span>
              <el-button type="primary" size="small" @click="openAclDialog()">
                <el-icon><Plus /></el-icon>
                添加规则
              </el-button>
            </div>
          </template>

          <el-table :data="aclRules" v-loading="aclLoading" stripe>
            <el-table-column prop="name" label="规则名称" width="150" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag :type="row.type === 'whitelist' ? 'success' : 'danger'" size="small">
                  {{ row.type === 'whitelist' ? '白名单' : '黑名单' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ips" label="IP 地址" min-width="200" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" min-width="150" />
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="toggleAcl(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="danger" link size="small" @click="deleteAcl(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 路由编辑对话框 -->
    <el-dialog v-model="routeDialogVisible" :title="isEdit ? '编辑路由' : '添加路由'" width="600px">
      <el-form :model="routeForm" label-width="100px">
        <el-form-item label="路由路径" required>
          <el-input v-model="routeForm.path" placeholder="/api/v1/agents" />
        </el-form-item>
        <el-form-item label="请求方法">
          <el-select v-model="routeForm.method" placeholder="请选择">
            <el-option label="ALL" value="ALL" />
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标服务" required>
          <el-input v-model="routeForm.service" placeholder="agent-service" />
        </el-form-item>
        <el-form-item label="超时时间">
          <el-input-number v-model="routeForm.timeout" :min="1" :max="300" /> 秒
        </el-form-item>
        <el-form-item label="限流配置">
          <el-input v-model="routeForm.rateLimit" placeholder="100/s" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="routeForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRoute">保存</el-button>
      </template>
    </el-dialog>

    <!-- ACL 编辑对话框 -->
    <el-dialog v-model="aclDialogVisible" title="添加访问规则" width="500px">
      <el-form :model="aclForm" label-width="100px">
        <el-form-item label="规则名称" required>
          <el-input v-model="aclForm.name" placeholder="生产环境 IP" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-radio-group v-model="aclForm.type">
            <el-radio value="whitelist">白名单</el-radio>
            <el-radio value="blacklist">黑名单</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="IP 地址" required>
          <el-input v-model="aclForm.ips" type="textarea" :rows="3" placeholder="每行一个 IP，支持 CIDR" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="aclForm.description" placeholder="规则描述" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="aclForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="aclDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveAcl">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

const activeTab = ref('overview')

// 网关统计
const gatewayStats = ref({
  totalRequests: 1256789,
  qps: 156,
  avgLatency: 45,
  errorRate: 0.3
})

// 在线服务
const onlineServices = ref([
  { name: 'agent-service', host: 'localhost:8080', status: 'online', qps: 45, latency: '32ms' },
  { name: 'knowledge-service', host: 'localhost:8081', status: 'online', qps: 23, latency: '28ms' },
  { name: 'workflow-service', host: 'localhost:8082', status: 'online', qps: 12, latency: '45ms' }
])

// 路由相关
const routesLoading = ref(false)
const routes = ref([])
const routeDialogVisible = ref(false)
const isEdit = ref(false)
const routeForm = reactive({
  id: null,
  path: '',
  method: 'ALL',
  service: '',
  timeout: 30,
  rateLimit: '',
  enabled: true
})

// ACL 相关
const aclLoading = ref(false)
const aclRules = ref([])
const aclDialogVisible = ref(false)
const aclForm = reactive({
  name: '',
  type: 'whitelist',
  ips: '',
  description: '',
  enabled: true
})

// 加载路由
const loadRoutes = async () => {
  routesLoading.value = true
  try {
    const res = await request.get('/system/gateway/routes')
    routes.value = res || []
  } catch (e) {
    console.error('加载路由失败:', e)
  } finally {
    routesLoading.value = false
  }
}

// 加载 ACL
const loadAcl = async () => {
  aclLoading.value = true
  try {
    const res = await request.get('/system/gateway/acl')
    aclRules.value = res || []
  } catch (e) {
    console.error('加载 ACL 失败:', e)
  } finally {
    aclLoading.value = false
  }
}

// 打开路由对话框
const openRouteDialog = () => {
  isEdit.value = false
  Object.assign(routeForm, { id: null, path: '', method: 'ALL', service: '', timeout: 30, rateLimit: '', enabled: true })
  routeDialogVisible.value = true
}

// 编辑路由
const editRoute = (row) => {
  isEdit.value = true
  Object.assign(routeForm, row)
  routeDialogVisible.value = true
}

// 保存路由
const saveRoute = async () => {
  try {
    if (isEdit.value) {
      await request.put(`/system/gateway/routes/${routeForm.id}`, routeForm)
      ElMessage.success('路由已更新')
    } else {
      await request.post('/system/gateway/routes', routeForm)
      ElMessage.success('路由已添加')
    }
    routeDialogVisible.value = false
    loadRoutes()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  }
}

// 删除路由
const deleteRoute = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除路由 "${row.path}" 吗?`, '提示', { type: 'warning' })
    await request.delete(`/system/gateway/routes/${row.id}`)
    ElMessage.success('路由已删除')
    loadRoutes()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || e))
  }
}

// 切换路由状态
const toggleRoute = async (row) => {
  try {
    await request.patch(`/system/gateway/routes/${row.id}`, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '路由已启用' : '路由已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

// 打开 ACL 对话框
const openAclDialog = () => {
  Object.assign(aclForm, { name: '', type: 'whitelist', ips: '', description: '', enabled: true })
  aclDialogVisible.value = true
}

// 保存 ACL
const saveAcl = async () => {
  try {
    await request.post('/system/gateway/acl', aclForm)
    ElMessage.success('规则已添加')
    aclDialogVisible.value = false
    loadAcl()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  }
}

// 删除 ACL
const deleteAcl = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除规则 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await request.delete(`/system/gateway/acl/${row.id}`)
    ElMessage.success('规则已删除')
    loadAcl()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || e))
  }
}

// 切换 ACL 状态
const toggleAcl = async (row) => {
  try {
    await request.patch(`/system/gateway/acl/${row.id}`, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '规则已启用' : '规则已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

const getMethodType = (method) => {
  const map = { 'GET': 'success', 'POST': 'primary', 'PUT': 'warning', 'DELETE': 'danger', 'ALL': 'info' }
  return map[method] || 'info'
}

onMounted(() => {
  loadRoutes()
  loadAcl()
})
</script>

<style scoped>
.api-gateway-container {
  padding: 20px;
}

.gateway-tabs {
  margin-top: 20px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 8px;
}

.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
