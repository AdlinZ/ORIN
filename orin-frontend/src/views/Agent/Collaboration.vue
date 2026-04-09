<template>
  <div class="page-container">
    <PageHeader
      title="多智能体协作"
      description="管理多智能体协作任务，实现顺序执行、并行执行和轮询调度"
      icon="Connection"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
          创建协作任务
        </el-button>
      </template>
    </PageHeader>

    <!-- 统计卡片 -->
    <el-row :gutter="24" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">总任务数</span>
              <span class="value">{{ stats.total }}</span>
            </div>
            <el-icon class="icon primary">
              <Connection />
            </el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">进行中</span>
              <span class="value">{{ stats.running }}</span>
            </div>
            <el-icon class="icon warning">
              <Loading />
            </el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">已完成</span>
              <span class="value">{{ stats.completed }}</span>
            </div>
            <el-icon class="icon success">
              <CircleCheck />
            </el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">已失败</span>
              <span class="value">{{ stats.failed }}</span>
            </div>
            <el-icon class="icon danger">
              <CircleClose />
            </el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="chat-entry-card">
      <template #header>
        <div class="chat-entry-title">
          协作对话入口
        </div>
      </template>
      <el-input
        v-model="chatIntent"
        type="textarea"
        :rows="3"
        placeholder="输入任务目标，跳转到智能体工作台继续对话"
      />
      <div class="chat-entry-actions">
        <el-button type="primary" @click="goToWorkspace">
          去工作台对话
        </el-button>
      </div>
    </el-card>

    <!-- 任务列表 -->
    <el-card class="task-card" shadow="never">
      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column
          prop="name"
          label="任务名称"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="description"
          label="描述"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="taskType" label="任务类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTaskTypeTag(row.taskType)" size="small">
              {{ getTaskTypeName(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="agentIds" label="参与 Agent" min-width="150">
          <template #default="{ row }">
            <el-tag
              v-for="agent in row.agentIds"
              :key="agent"
              size="small"
              style="margin-right: 4px"
            >
              {{ agent }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              type="primary"
              size="small"
              @click="startTask(row.id)"
            >
              开始
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              type="warning"
              size="small"
              @click="executeNext(row.id)"
            >
              执行下一步
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              type="success"
              size="small"
              @click="completeTask(row.id)"
            >
              完成
            </el-button>
            <el-button type="danger" size="small" @click="deleteTask(row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建协作任务" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="form.name" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="任务描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入任务描述"
          />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option value="SEQUENTIAL" label="顺序执行" />
            <el-option value="PARALLEL" label="并行执行" />
            <el-option value="ROUND_ROBIN" label="轮询调度" />
          </el-select>
        </el-form-item>
        <el-form-item label="参与 Agent">
          <el-select
            v-model="form.agentIds"
            multiple
            filterable
            placeholder="选择参与协作的 Agent"
            style="width: 100%"
          >
            <el-option
              v-for="agent in agentList"
              :key="agent.agentId"
              :label="agent.name"
              :value="agent.agentId"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">
          取消
        </el-button>
        <el-button type="primary" @click="createTask">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Connection, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { ROUTES } from '@/router/routes'
import { getAgentList } from '@/api/agent'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const tasks = ref([])
const agentList = ref([])
const showCreateDialog = ref(false)
const chatIntent = ref('')

const form = ref({
  name: '',
  description: '',
  taskType: 'SEQUENTIAL',
  agentIds: []
})

const stats = computed(() => {
  return {
    total: tasks.value.length,
    running: tasks.value.filter(t => t.status === 'RUNNING').length,
    completed: tasks.value.filter(t => t.status === 'COMPLETED').length,
    failed: tasks.value.filter(t => t.status === 'FAILED').length
  }
})

// 加载任务列表
const loadTasks = async () => {
  loading.value = true
  try {
    const res = await request.get('/collaboration/tasks')
    tasks.value = res.data || []
  } catch (error) {
    console.error('加载任务列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 加载 Agent 列表
const loadAgentList = async () => {
  try {
    const res = await getAgentList()
    agentList.value = res.data || []
  } catch (error) {
    console.error('加载 Agent 列表失败:', error)
  }
}

// 创建任务
const createTask = async () => {
  if (!form.value.name || form.value.agentIds.length === 0) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await request.post('/collaboration/tasks', form.value)
    ElMessage.success('创建成功')
    showCreateDialog.value = false
    form.value = { name: '', description: '', taskType: 'SEQUENTIAL', agentIds: [] }
    loadTasks()
  } catch (error) {
    console.error('创建任务失败:', error)
    ElMessage.error('创建失败')
  }
}

// 开始任务
const startTask = async (id) => {
  try {
    await request.post(`/collaboration/tasks/${id}/start`)
    ElMessage.success('任务已开始')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 执行下一步
const executeNext = async (id) => {
  try {
    await request.post(`/collaboration/tasks/${id}/next`)
    ElMessage.success('已执行下一步')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 完成任务
const completeTask = async (id) => {
  try {
    await request.post(`/collaboration/tasks/${id}/complete`, { result: 'Task completed' })
    ElMessage.success('任务已完成')
    loadTasks()
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

// 删除任务
const deleteTask = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该任务?', '警告', { type: 'warning' })
    await request.delete(`/collaboration/tasks/${id}`)
    ElMessage.success('删除成功')
    loadTasks()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const goToWorkspace = () => {
  const prompt = chatIntent.value.trim()
  router.push({
    path: ROUTES.AGENTS.WORKSPACE,
    query: prompt ? { prompt } : {}
  })
}

// 辅助函数
const getStatusTag = (status) => {
  const map = { PENDING: 'info', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger' }
  return map[status] || 'info'
}

const getStatusName = (status) => {
  const map = { PENDING: '待执行', RUNNING: '进行中', COMPLETED: '已完成', FAILED: '已失败' }
  return map[status] || status
}

const getTaskTypeTag = (type) => {
  const map = { SEQUENTIAL: '', PARALLEL: 'success', ROUND_ROBIN: 'warning' }
  return map[type] || ''
}

const getTaskTypeName = (type) => {
  const map = { SEQUENTIAL: '顺序执行', PARALLEL: '并行执行', ROUND_ROBIN: '轮询调度' }
  return map[type] || type
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadTasks()
  loadAgentList()
})
</script>

<style scoped>
.page-container {
  padding: 24px;
}

.stats-row {
  margin-bottom: 24px;
}

.chat-entry-card {
  margin-bottom: 24px;
}

.chat-entry-title {
  font-weight: 600;
}

.chat-entry-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

.stat-card {
  border-radius: 8px;
}

.stat-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-info .label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-info .value {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.stat-content .icon {
  font-size: 32px;
}

.stat-content .icon.primary { color: var(--el-color-primary); }
.stat-content .icon.warning { color: var(--el-color-warning); }
.stat-content .icon.success { color: var(--el-color-success); }
.stat-content .icon.danger { color: var(--el-color-danger); }

.task-card {
  border-radius: 8px;
}
</style>
