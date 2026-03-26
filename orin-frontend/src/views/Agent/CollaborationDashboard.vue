<template>
  <div class="collaboration-dashboard">
    <!-- 顶部统计 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">进行中任务</span>
              <span class="value">{{ stats.executing }}</span>
            </div>
            <el-icon class="icon warning"><Loading /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">已完成</span>
              <span class="value">{{ stats.completed }}</span>
            </div>
            <el-icon class="icon success"><CircleCheck /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">活跃 Agent</span>
              <span class="value">{{ stats.activeAgents }}</span>
            </div>
            <el-icon class="icon primary"><User /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">今日 Token</span>
              <span class="value">{{ formatNumber(stats.todayTokens) }}</span>
            </div>
            <el-icon class="icon info"><Coin /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">平均延迟</span>
              <span class="value">{{ stats.avgLatency }}ms</span>
            </div>
            <el-icon class="icon"><Timer /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="label">成功率</span>
              <span class="value">{{ stats.successRate }}%</span>
            </div>
            <el-icon class="icon success"><DataLine /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 任务包列表 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card class="task-list-card">
          <template #header>
            <div class="card-header">
              <span>协作任务包</span>
              <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
                创建任务
              </el-button>
            </div>
          </template>

          <!-- 筛选器 -->
          <div class="filter-bar">
            <el-select v-model="filters.status" placeholder="状态" clearable style="width: 120px" @change="loadPackages">
              <el-option value="PLANNING" label="规划中" />
              <el-option value="DECOMPOSING" label="分解中" />
              <el-option value="EXECUTING" label="执行中" />
              <el-option value="CONSENSUS" label="共识中" />
              <el-option value="COMPLETED" label="已完成" />
              <el-option value="FAILED" label="失败" />
              <el-option value="FALLBACK" label="回退中" />
            </el-select>
            <el-select v-model="filters.priority" placeholder="优先级" clearable style="width: 120px" @change="loadPackages">
              <el-option value="LOW" label="低" />
              <el-option value="NORMAL" label="普通" />
              <el-option value="HIGH" label="高" />
              <el-option value="URGENT" label="紧急" />
            </el-select>
            <el-select v-model="filters.category" placeholder="类别" clearable style="width: 120px" @change="loadPackages">
              <el-option value="ANALYSIS" label="分析" />
              <el-option value="GENERATION" label="生成" />
              <el-option value="REVIEW" label="审查" />
              <el-option value="RESEARCH" label="研究" />
              <el-option value="CODING" label="编码" />
              <el-option value="TESTING" label="测试" />
            </el-select>
            <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
          </div>

          <el-table :data="packages" v-loading="loading" stripe>
            <el-table-column prop="packageId" label="任务包ID" width="200">
              <template #default="{ row }">
                <el-link type="primary" @click="showDetail(row)">
                  {{ row.packageId?.substring(0, 12) }}...
                </el-link>
              </template>
            </el-table-column>
            <el-table-column prop="intent" label="任务意图" min-width="200" show-overflow-tooltip />
            <el-table-column prop="status" label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusName(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="collaborationMode" label="协作模式" width="120">
              <template #default="{ row }">
                <el-tag type="info" size="small">{{ row.collaborationMode }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="intentCategory" label="类别" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.intentCategory || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" @click="viewTopology(row)">
                  拓扑
                </el-button>
                <el-button type="info" size="small" @click="viewTimeline(row)">
                  时间线
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 创建任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建协作任务包" width="600px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="任务意图">
          <el-input v-model="createForm.intent" type="textarea" :rows="3"
            placeholder="请描述您希望完成的任务" />
        </el-form-item>
        <el-form-item label="任务类别">
          <el-select v-model="createForm.category" style="width: 100%">
            <el-option value="ANALYSIS" label="分析" />
            <el-option value="GENERATION" label="生成" />
            <el-option value="REVIEW" label="审查" />
            <el-option value="RESEARCH" label="研究" />
            <el-option value="CODING" label="编码" />
            <el-option value="TESTING" label="测试" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option value="LOW" label="低" />
            <el-option value="NORMAL" label="普通" />
            <el-option value="HIGH" label="高" />
            <el-option value="URGENT" label="紧急" />
          </el-select>
        </el-form-item>
        <el-form-item label="复杂度">
          <el-select v-model="createForm.complexity" style="width: 100%">
            <el-option value="SIMPLE" label="简单" />
            <el-option value="MEDIUM" label="中等" />
            <el-option value="COMPLEX" label="复杂" />
            <el-option value="VERY_COMPLEX" label="非常复杂" />
          </el-select>
        </el-form-item>
        <el-form-item label="协作模式">
          <el-select v-model="createForm.collaborationMode" style="width: 100%">
            <el-option value="SEQUENTIAL" label="顺序执行" />
            <el-option value="PARALLEL" label="并行执行" />
            <el-option value="CONSENSUS" label="共识模式" />
            <el-option value="HIERARCHICAL" label="层级模式" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="createPackage" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 任务拓扑图对话框 -->
    <el-dialog v-model="showTopologyDialog" title="协作任务拓扑" width="800px">
      <div class="topology-container">
        <div class="topology-header">
          <el-tag type="info">任务包: {{ currentPackage?.packageId?.substring(0, 16) }}</el-tag>
          <el-tag :type="getStatusType(currentPackage?.status)">
            {{ getStatusName(currentPackage?.status) }}
          </el-tag>
        </div>
        <div class="topology-content">
          <el-timeline>
            <el-timeline-item
              v-for="(subtask, index) in subtasks"
              :key="subtask.subTaskId"
              :timestamp="subtask.status"
              :type="getSubtaskTimelineType(subtask.status)"
              placement="top"
            >
              <el-card class="subtask-card">
                <template #header>
                  <div class="subtask-header">
                    <span class="subtask-id">#{{ index + 1 }} {{ subtask.subTaskId?.substring(0, 8) }}</span>
                    <el-tag size="small" :type="getStatusType(subtask.status)">
                      {{ getStatusName(subtask.status) }}
                    </el-tag>
                  </div>
                </template>
                <div class="subtask-content">
                  <p class="subtask-desc">{{ subtask.description }}</p>
                  <div class="subtask-meta">
                    <span>角色: {{ subtask.expectedRole }}</span>
                    <span v-if="subtask.executedBy">执行者: {{ subtask.executedBy }}</span>
                  </div>
                  <div class="subtask-result" v-if="subtask.result">
                    <span class="result-label">结果:</span>
                    <el-input v-model="subtask.result" type="textarea" :rows="2" size="small" placeholder="执行结果" />
                  </div>
                  <div class="subtask-actions">
                    <el-button
                      v-if="subtask.status === 'FAILED'"
                      type="warning"
                      size="small"
                      @click="handleRetry(subtask)"
                    >
                      重试
                    </el-button>
                    <el-button
                      v-if="subtask.status === 'PENDING' || subtask.status === 'FAILED'"
                      type="info"
                      size="small"
                      @click="handleSkip(subtask)"
                    >
                      跳过
                    </el-button>
                    <el-button
                      v-if="subtask.status === 'PENDING' || subtask.status === 'RUNNING'"
                      type="success"
                      size="small"
                      @click="handleManualComplete(subtask)"
                    >
                      手动完成
                    </el-button>
                  </div>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-dialog>

    <!-- 事件时间线对话框 -->
    <el-dialog v-model="showTimelineDialog" title="协作事件时间线" width="800px">
      <div class="timeline-container">
        <el-timeline>
          <el-timeline-item
            v-for="event in events"
            :key="event.eventId"
            :timestamp="formatDateTime(event.timestamp)"
            :type="getEventTimelineType(event.eventType)"
          >
            <el-card class="event-card">
              <div class="event-header">
                <el-tag size="small">{{ event.eventType }}</el-tag>
                <span class="event-agent" v-if="event.agentId">Agent: {{ event.agentId }}</span>
              </div>
              <div class="event-data" v-if="event.eventData">
                {{ JSON.stringify(event.eventData, null, 2) }}
              </div>
            </el-card>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Loading, CircleCheck, User, Coin, Timer, DataLine, Refresh } from '@element-plus/icons-vue'
import {
  getAllPackages, createCollaborationPackage, decomposePackage,
  getExecutableSubtasks, updateSubtaskStatus, getSubtasks, getEventHistory,
  filterPackages, retrySubtask, skipSubtask, manualCompleteSubtask
} from '@/api/collaboration'

// 状态
const loading = ref(false)
const creating = ref(false)
const packages = ref([])
const subtasks = ref([])
const events = ref([])
const showCreateDialog = ref(false)
const showTopologyDialog = ref(false)
const showTimelineDialog = ref(false)
const currentPackage = ref(null)

// 筛选条件
const filters = reactive({
  status: '',
  priority: '',
  category: ''
})

// 创建表单
const createForm = reactive({
  intent: '',
  category: 'GENERATION',
  priority: 'NORMAL',
  complexity: 'MEDIUM',
  collaborationMode: 'SEQUENTIAL'
})

// 统计数据
const stats = reactive({
  executing: 0,
  completed: 0,
  activeAgents: 0,
  todayTokens: 0,
  avgLatency: 0,
  successRate: 0
})

// 加载任务包
const loadPackages = async () => {
  loading.value = true
  try {
    let res
    // 如果有筛选条件
    if (filters.status || filters.priority || filters.category) {
      res = await filterPackages({
        status: filters.status || undefined,
        priority: filters.priority || undefined,
        category: filters.category || undefined
      })
    } else {
      res = await getAllPackages()
    }
    packages.value = res.data || []

    // 更新统计
    stats.executing = packages.value.filter(p => p.status === 'EXECUTING').length
    stats.completed = packages.value.filter(p => p.status === 'COMPLETED').length
  } catch (error) {
    console.error('加载任务包失败:', error)
  } finally {
    loading.value = false
  }
}

// 重置筛选
const resetFilters = () => {
  filters.status = ''
  filters.priority = ''
  filters.category = ''
  loadPackages()
}

// 创建任务包
const createPackage = async () => {
  if (!createForm.intent) {
    ElMessage.warning('请输入任务意图')
    return
  }

  creating.value = true
  try {
    const res = await createCollaborationPackage(createForm)
    ElMessage.success('创建成功')

    // 自动分解任务
    await decomposePackage(res.data.packageId, ['analysis', 'generation', 'review'])

    showCreateDialog.value = false
    loadPackages()

    // 重置表单
    createForm.intent = ''
    createForm.category = 'GENERATION'
    createForm.priority = 'NORMAL'
    createForm.complexity = 'MEDIUM'
    createForm.collaborationMode = 'SEQUENTIAL'
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

// 查看拓扑
const viewTopology = async (pkg) => {
  currentPackage.value = pkg
  showTopologyDialog.value = true

  // 加载子任务
  try {
    const res = await getSubtasks(pkg.packageId)
    subtasks.value = res.data || []
  } catch (error) {
    console.error('加载子任务失败:', error)
    subtasks.value = []
  }
}

// 查看时间线
const viewTimeline = async (pkg) => {
  currentPackage.value = pkg
  showTimelineDialog.value = true

  // 加载事件历史
  try {
    const res = await getEventHistory(pkg.packageId)
    events.value = res.data || []
  } catch (error) {
    console.error('加载事件历史失败:', error)
    events.value = []
  }
}

// 辅助函数
const getStatusType = (status) => {
  const map = { PLANNING: 'info', DECOMPOSING: 'warning', EXECUTING: 'warning', CONSENSUS: 'warning', COMPLETED: 'success', FAILED: 'danger', FALLBACK: 'warning' }
  return map[status] || 'info'
}

const getStatusName = (status) => {
  const map = { PLANNING: '规划中', DECOMPOSING: '分解中', EXECUTING: '执行中', CONSENSUS: '共识中', COMPLETED: '已完成', FAILED: '失败', FALLBACK: '回退中' }
  return map[status] || status
}

const getSubtaskTimelineType = (status) => {
  const map = { PENDING: 'info', ASSIGNED: 'primary', RUNNING: 'warning', COMPLETED: 'success', FAILED: 'danger', SKIPPED: 'info' }
  return map[status] || 'info'
}

const getEventTimelineType = (eventType) => {
  if (eventType.includes('COMPLETED') || eventType.includes('REACHED')) return 'success'
  if (eventType.includes('FAILED') || eventType.includes('ERROR')) return 'danger'
  if (eventType.includes('STARTED') || eventType.includes('ASSIGNED')) return 'primary'
  return 'info'
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

const formatNumber = (num) => {
  if (!num) return '0'
  if (num > 1000000) return (num / 1000000).toFixed(1) + 'M'
  if (num > 1000) return (num / 1000).toFixed(1) + 'K'
  return num.toString()
}

// 人工干预操作
const handleRetry = async (subtask) => {
  try {
    await retrySubtask(currentPackage.value.packageId, subtask.subTaskId)
    ElMessage.success('重试成功')
    viewTopology(currentPackage.value)
  } catch (error) {
    ElMessage.error('重试失败')
  }
}

const handleSkip = async (subtask) => {
  try {
    await ElMessageBox.confirm('确定要跳过此子任务吗？', '确认跳过', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await skipSubtask(currentPackage.value.packageId, subtask.subTaskId)
    ElMessage.success('已跳过')
    viewTopology(currentPackage.value)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('跳过失败')
    }
  }
}

const handleManualComplete = async (subtask) => {
  const { value } = await ElMessageBox.prompt('请输入完成结果', '手动完成', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: subtask.result || ''
  })
  try {
    await manualCompleteSubtask(currentPackage.value.packageId, subtask.subTaskId, value)
    ElMessage.success('已完成')
    viewTopology(currentPackage.value)
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

onMounted(() => {
  loadPackages()
})
</script>

<style scoped>
.collaboration-dashboard {
  padding: 20px;
}

.stats-row {
  margin-bottom: 20px;
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
  font-size: 28px;
}

.icon.primary { color: var(--el-color-primary); }
.icon.warning { color: var(--el-color-warning); }
.icon.success { color: var(--el-color-success); }
.icon.info { color: var(--el-color-info); }

.task-list-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.topology-container, .timeline-container {
  padding: 10px;
}

.topology-header {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.subtask-card {
  margin-bottom: 10px;
}

.subtask-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.subtask-id {
  font-weight: 600;
}

.subtask-meta {
  display: flex;
  flex-direction: column;
  gap: 5px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.event-card {
  margin-bottom: 10px;
}

.event-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.event-data {
  font-family: monospace;
  font-size: 12px;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
}

.subtask-result {
  margin-top: 10px;
}

.subtask-result .result-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 5px;
  display: block;
}

.subtask-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
</style>