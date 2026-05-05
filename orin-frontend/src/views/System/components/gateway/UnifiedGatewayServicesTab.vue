<template>
  <div class="gateway-services-tab">
    <div class="section-card">
      <div class="section-header">
        <div class="section-title">
          <el-icon style="color:#059669"><SetUp /></el-icon>
          上游服务
        </div>
        <el-button type="primary" size="small" @click="openServiceDialog(null)">
          <el-icon><Plus /></el-icon>
          添加服务
        </el-button>
      </div>

      <el-table v-loading="loading" :data="services" stripe style="border-radius:0">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 10px 50px;">
              <el-table :data="row.instances || []" size="small" border>
                <el-table-column prop="host" label="主机" />
                <el-table-column prop="port" label="端口" width="80" />
                <el-table-column prop="weight" label="权重" width="80" />
                <el-table-column prop="status" label="状态" width="100">
                  <template #default="{ row: instance }">
                    <el-tag :type="instance.status === 'UP' ? 'success' : 'danger'" size="small">
                      {{ instance.status }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="lastHeartbeat" label="心跳" width="160">
                  <template #default="{ row: col }">{{ formatTime(col.lastHeartbeat) }}</template>
                </el-table-column>
                <el-table-column label="操作" width="200">
                  <template #default="{ row: col }">
                    <el-button type="primary" link size="small" @click="openInstanceDialog(row, col)">编辑</el-button>
                    <el-button type="warning" link size="small" @click="healthCheck(row.id, col.id)">健康检查</el-button>
                    <el-button type="danger" link size="small" @click="deleteInstance(row.id, col.id)">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <el-button type="primary" size="small" style="margin-top: 10px;" @click="openInstanceDialog(row, null)">
                添加实例
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务名称" />
        <el-table-column prop="serviceKey" label="服务Key" />
        <el-table-column prop="protocol" label="协议" width="80" />
        <el-table-column prop="basePath" label="基础路径" show-overflow-tooltip />
        <el-table-column prop="instanceCount" label="实例数" width="80" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'HEALTHY' ? 'success' : row.status === 'DEGRADED' ? 'warning' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openServiceDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="deleteService(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Service Dialog -->
    <el-dialog v-model="serviceDialogVisible" :title="isServiceEdit ? '编辑服务' : '添加服务'" width="500px">
      <el-form :model="serviceForm" label-width="100px">
        <el-form-item label="服务Key" required>
          <el-input v-model="serviceForm.serviceKey" placeholder="agent-service" />
        </el-form-item>
        <el-form-item label="服务名称" required>
          <el-input v-model="serviceForm.serviceName" placeholder="智能体服务" />
        </el-form-item>
        <el-form-item label="协议">
          <el-select v-model="serviceForm.protocol">
            <el-option label="HTTP" value="HTTP" />
            <el-option label="HTTPS" value="HTTPS" />
            <el-option label="WS" value="WS" />
            <el-option label="WSS" value="WSS" />
          </el-select>
        </el-form-item>
        <el-form-item label="基础路径">
          <el-input v-model="serviceForm.basePath" placeholder="/api/v1" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="serviceForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveService" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>

    <!-- Instance Dialog -->
    <el-dialog v-model="instanceDialogVisible" :title="isInstanceEdit ? '编辑实例' : '添加实例'" width="500px">
      <el-form :model="instanceForm" label-width="100px">
        <el-form-item label="主机" required>
          <el-input v-model="instanceForm.host" placeholder="localhost" />
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input-number v-model="instanceForm.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="权重">
          <el-input-number v-model="instanceForm.weight" :min="1" :max="1000" />
        </el-form-item>
        <el-form-item label="健康检查路径">
          <el-input v-model="instanceForm.healthCheckPath" placeholder="/health" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="instanceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveInstance" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { SetUp, Plus } from '@element-plus/icons-vue'
import { getServices, createService, updateService, deleteService as deleteServiceApi,
         getServiceInstances, createServiceInstance, updateServiceInstance,
         deleteServiceInstance, triggerHealthCheck } from '@/api/gateway'

const loading = ref(false)
const submitting = ref(false)
const services = ref([])
const serviceDialogVisible = ref(false)
const instanceDialogVisible = ref(false)
const isServiceEdit = ref(false)
const isInstanceEdit = ref(false)
const currentServiceId = ref(null)

const serviceForm = reactive({
  id: null, serviceKey: '', serviceName: '', protocol: 'HTTP', basePath: '', description: ''
})

const instanceForm = reactive({
  id: null, host: '', port: 8080, weight: 100, healthCheckPath: '/health'
})

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

const loadServices = async () => {
  loading.value = true
  try {
    const data = await getServices()
    services.value = data || []
    for (const svc of services.value) {
      try {
        const instances = await getServiceInstances(svc.id)
        svc.instances = instances || []
      } catch {
        svc.instances = []
      }
    }
  } catch (e) {
    ElMessage.error('加载服务失败')
  } finally {
    loading.value = false
  }
}

const openServiceDialog = (row) => {
  isServiceEdit.value = !!row
  Object.assign(serviceForm, row ? { ...row } : { id: null, serviceKey: '', serviceName: '', protocol: 'HTTP', basePath: '', description: '' })
  serviceDialogVisible.value = true
}

const saveService = async () => {
  submitting.value = true
  try {
    if (isServiceEdit.value) {
      await updateService(serviceForm.id, serviceForm)
      ElMessage.success('服务已更新')
    } else {
      await createService(serviceForm)
      ElMessage.success('服务已添加')
    }
    serviceDialogVisible.value = false
    loadServices()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const deleteService = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除服务 "${row.serviceName}" 吗?`, '提示', { type: 'warning' })
    await deleteServiceApi(row.id)
    ElMessage.success('服务已删除')
    loadServices()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const openInstanceDialog = (service, instance) => {
  currentServiceId.value = service.id
  isInstanceEdit.value = !!instance
  Object.assign(instanceForm, instance ? { ...instance } : { id: null, host: '', port: 8080, weight: 100, healthCheckPath: '/health' })
  instanceDialogVisible.value = true
}

const saveInstance = async () => {
  submitting.value = true
  try {
    if (isInstanceEdit.value) {
      await updateServiceInstance(currentServiceId.value, instanceForm.id, instanceForm)
      ElMessage.success('实例已更新')
    } else {
      await createServiceInstance(currentServiceId.value, instanceForm)
      ElMessage.success('实例已添加')
    }
    instanceDialogVisible.value = false
    loadServices()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}

const deleteInstance = async (serviceId, instanceId) => {
  try {
    await ElMessageBox.confirm('确定要删除此实例吗?', '提示', { type: 'warning' })
    await deleteServiceInstance(serviceId, instanceId)
    ElMessage.success('实例已删除')
    loadServices()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const healthCheck = async (serviceId, instanceId) => {
  try {
    const res = await triggerHealthCheck(serviceId, instanceId)
    if (res.success) {
      ElMessage.success(`健康检查成功，延迟 ${res.latencyMs}ms`)
    } else {
      ElMessage.warning('健康检查失败: ' + (res.error || '未知错误'))
    }
    loadServices()
  } catch (e) {
    ElMessage.error('健康检查请求失败')
  }
}

onMounted(() => {
  loadServices()
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
</style>
