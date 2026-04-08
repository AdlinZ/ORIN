<template>
  <div class="page-container">
    <OrinPageShell
      title="多智能体协作"
      description="任务包、子任务与事件流的统一协作控制台"
      icon="DataAnalysis"
      domain="协作协调"
      maturity="beta"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          创建任务包
        </el-button>
        <el-button :icon="Refresh" @click="loadAll">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选"
            style="width: 180px"
          >
            <el-option
              v-for="status in statuses"
              :key="status"
              :value="status"
              :label="status"
            />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :md="6">
        <StatCard label="任务总数" :value="stats.total" icon="Tickets" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="执行中" :value="stats.running" icon="Loading" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="已完成" :value="stats.completed" icon="CircleCheck" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="成功率" :value="`${stats.successRate}%`" icon="DataLine" />
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card shadow="never">
          <OrinAsyncState :status="packagesState.status" empty-text="暂无协作任务包" @retry="loadPackages">
            <el-table :data="filteredPackages" border stripe>
              <el-table-column
                prop="packageId"
                label="任务包ID"
                min-width="180"
                show-overflow-tooltip
              />
              <el-table-column
                prop="intent"
                label="任务意图"
                min-width="260"
                show-overflow-tooltip
              />
              <el-table-column prop="status" label="状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="statusTag(row.status)" size="small">
                    {{ row.status }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="priority" label="优先级" width="110" />
              <el-table-column prop="createdAt" label="创建时间" width="180">
                <template #default="{ row }">
                  {{ formatTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="160" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="showTimeline(row)">
                    事件流
                  </el-button>
                  <el-button link type="info" @click="activePackage = row">
                    详情
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </OrinAsyncState>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="9">
        <el-card shadow="never">
          <template #header>
            <div class="side-header">
              事件时间线
            </div>
          </template>
          <OrinAsyncState :status="timelineState.status" empty-text="请选择任务包查看事件">
            <OrinTaskTimeline :items="timeline" />
          </OrinAsyncState>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="showCreate" title="创建协作任务包" width="620px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="任务意图">
          <el-input
            v-model="createForm.intent"
            type="textarea"
            :rows="3"
            placeholder="描述你的目标任务"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="createForm.priority" style="width: 100%">
            <el-option label="LOW" value="LOW" />
            <el-option label="NORMAL" value="NORMAL" />
            <el-option label="HIGH" value="HIGH" />
            <el-option label="URGENT" value="URGENT" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">
          取消
        </el-button>
        <el-button type="primary" :loading="creating" @click="createPackage">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import StatCard from '@/components/StatCard.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinTaskTimeline from '@/components/orin/OrinTaskTimeline.vue'
import {
  createCollaborationPackage,
  getAllPackages,
  getCollaborationStats,
  getEventHistory
} from '@/api/collaboration'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toCollaborationPackagesViewModel,
  toCollaborationStatsViewModel,
  toTimelineViewModel
} from '@/viewmodels'

const statuses = ['PLANNING', 'DECOMPOSING', 'EXECUTING', 'CONSENSUS', 'COMPLETED', 'FAILED', 'FALLBACK']

const packagesState = reactive(createAsyncState())
const timelineState = reactive(createAsyncState({ status: 'empty' }))
const stats = reactive(toCollaborationStatsViewModel({}))

const statusFilter = ref('')
const packages = ref([])
const timeline = ref([])
const activePackage = ref(null)

const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({
  intent: '',
  priority: 'NORMAL'
})

const filteredPackages = computed(() => {
  if (!statusFilter.value) return packages.value
  return packages.value.filter((item) => item.status === statusFilter.value)
})

const loadStats = async () => {
  try {
    const response = await getCollaborationStats()
    Object.assign(stats, toCollaborationStatsViewModel(response))
  } catch (error) {
    ElMessage.warning('协作统计加载失败')
  }
}

const loadPackages = async () => {
  markLoading(packagesState)
  try {
    const response = await getAllPackages()
    packages.value = toCollaborationPackagesViewModel(response)
    if (packages.value.length === 0) {
      markEmpty(packagesState)
    } else {
      markSuccess(packagesState)
    }
  } catch (error) {
    markError(packagesState, error)
  }
}

const loadAll = async () => {
  await Promise.all([loadStats(), loadPackages()])
}

const showTimeline = async (row) => {
  if (!row?.packageId) return
  activePackage.value = row
  markLoading(timelineState)
  try {
    const response = await getEventHistory(row.packageId)
    timeline.value = toTimelineViewModel(response)
    if (timeline.value.length === 0) {
      markEmpty(timelineState)
    } else {
      markSuccess(timelineState)
    }
  } catch (error) {
    markError(timelineState, error)
  }
}

const createPackage = async () => {
  if (!createForm.intent.trim()) {
    ElMessage.warning('请先填写任务意图')
    return
  }
  creating.value = true
  try {
    await createCollaborationPackage({
      intent: createForm.intent,
      priority: createForm.priority
    })
    ElMessage.success('任务包创建成功')
    showCreate.value = false
    createForm.intent = ''
    await loadAll()
  } finally {
    creating.value = false
  }
}

const formatTime = (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'

const statusTag = (status) => {
  switch (status) {
    case 'COMPLETED': return 'success'
    case 'FAILED':
    case 'FALLBACK': return 'danger'
    case 'EXECUTING':
    case 'CONSENSUS': return 'warning'
    default: return 'info'
  }
}

onMounted(loadAll)
</script>

<style scoped>
.stats-row {
  margin-bottom: 16px;
}

.side-header {
  font-weight: 600;
}

@media (max-width: 992px) {
  .side-header {
    margin-top: 8px;
  }
}
</style>
