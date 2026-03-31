<template>
  <div class="gateway-routes-tab">
    <!-- Toolbar -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>API 路由</span>
          <el-button type="primary" @click="openCreateDialog">
            <el-icon><Plus /></el-icon>
            添加路由
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="routes" stripe>
        <el-table-column prop="name" label="名称" width="150" />
        <el-table-column prop="pathPattern" label="路径" min-width="180" show-overflow-tooltip />
        <el-table-column prop="method" label="方法" width="80">
          <template #default="{ row }">
            <el-tag :type="getMethodType(row.method)" size="small">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="targetUrl" label="目标服务" min-width="150" show-overflow-tooltip />
        <el-table-column prop="timeoutMs" label="超时" width="80">
          <template #default="{ row }">{{ row.timeoutMs }}ms</template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="60" />
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
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑路由' : '添加路由'" width="700px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="路由名称" />
        </el-form-item>
        <el-form-item label="路径" required>
          <el-input v-model="form.pathPattern" placeholder="/api/v1/agents/**" />
        </el-form-item>
        <el-form-item label="方法">
          <el-select v-model="form.method" placeholder="请选择">
            <el-option label="ALL" value="ALL" />
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标地址" required>
          <el-input v-model="form.targetUrl" placeholder="http://localhost:8080/api" />
        </el-form-item>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="超时(ms)">
              <el-input-number v-model="form.timeoutMs" :min="100" :max="300000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="重试次数">
              <el-input-number v-model="form.retryCount" :min="0" :max="10" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="负载均衡">
              <el-select v-model="form.loadBalance">
                <el-option label="轮询" value="ROUND_ROBIN" />
                <el-option label="随机" value="RANDOM" />
                <el-option label="加权" value="WEIGHTED" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="1000" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getRoutes, createRoute, updateRoute, deleteRoute as removeRoute, patchRoute } from '@/api/gateway'

const loading = ref(false)
const submitting = ref(false)
const routes = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive({
  id: null,
  name: '',
  pathPattern: '',
  method: 'ALL',
  targetUrl: '',
  timeoutMs: 30000,
  retryCount: 0,
  loadBalance: 'ROUND_ROBIN',
  priority: 0,
  description: ''
})

const getMethodType = (method) => {
  const map = { 'GET': 'success', 'POST': 'primary', 'PUT': 'warning', 'DELETE': 'danger', 'ALL': 'info' }
  return map[method] || 'info'
}

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

const openCreateDialog = () => {
  isEdit.value = false
  Object.assign(form, { id: null, name: '', pathPattern: '', method: 'ALL', targetUrl: '', timeoutMs: 30000, retryCount: 0, loadBalance: 'ROUND_ROBIN', priority: 0, description: '' })
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

const saveRoute = async () => {
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

onMounted(() => {
  loadRoutes()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
