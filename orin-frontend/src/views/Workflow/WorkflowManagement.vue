<template>
  <div class="workflow-management">
    <el-card class="header-card">
      <div class="header-content">
        <h2>工作流管理</h2>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          创建工作流
        </el-button>
      </div>
    </el-card>

    <!-- 工作流列表 -->
    <el-card class="table-card">
      <el-table border :data="workflows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="workflowName" label="工作流名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="workflowType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag>{{ getTypeLabel(row.workflowType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="350" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewInstances(row)">执行记录</el-button>
            <el-button size="small" type="success" @click="executeWorkflow(row)">执行</el-button>
            <el-button size="small" type="primary" @click="editWorkflow(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteWorkflow(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建工作流对话框 -->
    <el-dialog v-model="dialogVisible" title="创建工作流" width="600px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="工作流名称" required>
          <el-input v-model="form.workflowName" placeholder="请输入工作流名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入工作流描述"
          />
        </el-form-item>
        <el-form-item label="工作流类型">
          <el-select v-model="form.workflowType">
            <el-option label="顺序执行" value="SEQUENTIAL" />
            <el-option label="并行执行" value="PARALLEL" />
            <el-option label="DAG 执行" value="DAG" />
          </el-select>
        </el-form-item>
        <el-form-item label="超时时间(秒)">
          <el-input-number v-model="form.timeoutSeconds" :min="10" :max="3600" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">创建</el-button>
      </template>
    </el-dialog>

    <!-- 执行工作流对话框 -->
    <el-dialog v-model="executeDialogVisible" title="执行工作流" width="600px">
      <el-form label-width="120px">
        <el-form-item label="输入数据 (JSON)">
          <el-input
            v-model="executeInputs"
            type="textarea"
            :rows="10"
            placeholder='{"key": "value"}'
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="executeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmExecute">执行</el-button>
      </template>
    </el-dialog>

    <!-- 执行实例列表对话框 -->
    <el-dialog v-model="instancesDialogVisible" title="执行记录" width="900px">
      <el-table border :data="instances" v-loading="instancesLoading">
        <el-table-column prop="id" label="实例 ID" width="100" />
        <el-table-column prop="traceId" label="Trace ID" width="200" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getInstanceStatusTagType(row.status)">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="120" />
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="viewTrace(row)">查看追踪</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import axios from 'axios'
import { useRouter } from 'vue-router'

const router = useRouter()
const workflows = ref([])
const instances = ref([])
const loading = ref(false)
const instancesLoading = ref(false)
const dialogVisible = ref(false)
const executeDialogVisible = ref(false)
const instancesDialogVisible = ref(false)
const executeInputs = ref('{}')
const currentWorkflow = ref(null)

const form = ref({
  workflowName: '',
  description: '',
  workflowType: 'SEQUENTIAL',
  timeoutSeconds: 300,
  workflowDefinition: {}
})

onMounted(() => {
  loadWorkflows()
})

const loadWorkflows = async () => {
  loading.value = true
  try {
    const response = await axios.get('/api/workflows')
    workflows.value = response.data
  } catch (error) {
    ElMessage.error('加载工作流列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  form.value = {
    workflowName: '',
    description: '',
    workflowType: 'SEQUENTIAL',
    timeoutSeconds: 300,
    workflowDefinition: {}
  }
  dialogVisible.value = true
}

const submitForm = async () => {
  try {
    await axios.post('/api/workflows', form.value)
    ElMessage.success('工作流创建成功')
    dialogVisible.value = false
    loadWorkflows()
  } catch (error) {
    ElMessage.error('创建失败: ' + error.message)
  }
}

const executeWorkflow = (workflow) => {
  currentWorkflow.value = workflow
  executeInputs.value = '{}'
  executeDialogVisible.value = true
}

const confirmExecute = async () => {
  try {
    const inputs = JSON.parse(executeInputs.value)
    const response = await axios.post(
      `/api/workflows/${currentWorkflow.value.id}/execute`,
      inputs
    )
    ElMessage.success(`工作流执行成功,实例 ID: ${response.data.instanceId}`)
    executeDialogVisible.value = false
  } catch (error) {
    ElMessage.error('执行失败: ' + error.message)
  }
}

const viewInstances = async (workflow) => {
  currentWorkflow.value = workflow
  instancesLoading.value = true
  instancesDialogVisible.value = true
  
  try {
    const response = await axios.get(`/api/workflows/${workflow.id}/instances`)
    instances.value = response.data
  } catch (error) {
    ElMessage.error('加载执行记录失败: ' + error.message)
  } finally {
    instancesLoading.value = false
  }
}

const viewTrace = (instance) => {
  router.push(`/trace/${instance.traceId}`)
}

const editWorkflow = (workflow) => {
  ElMessage.info('编辑功能开发中')
}

const deleteWorkflow = async (workflow) => {
  try {
    await ElMessageBox.confirm(`确定要删除工作流 "${workflow.workflowName}" 吗?`, '确认删除', {
      type: 'warning'
    })
    await axios.delete(`/api/workflows/${workflow.id}`)
    ElMessage.success('删除成功')
    loadWorkflows()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + error.message)
    }
  }
}

const getTypeLabel = (type) => {
  const labels = { SEQUENTIAL: '顺序', PARALLEL: '并行', DAG: 'DAG' }
  return labels[type] || type
}

const getStatusTagType = (status) => {
  const types = { DRAFT: 'info', ACTIVE: 'success', ARCHIVED: 'warning' }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = { DRAFT: '草稿', ACTIVE: '活跃', ARCHIVED: '已归档' }
  return labels[status] || status
}

const getInstanceStatusTagType = (status) => {
  const types = {
    RUNNING: 'primary',
    SUCCESS: 'success',
    FAILED: 'danger',
    TIMEOUT: 'warning',
    CANCELLED: 'info'
  }
  return types[status] || 'info'
}
</script>

<style scoped>
.workflow-management {
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.table-card {
  margin-bottom: 20px;
}
</style>
