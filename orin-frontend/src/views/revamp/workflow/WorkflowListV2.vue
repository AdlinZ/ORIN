<template>
  <div class="page-container">
    <OrinPageShell
      title="工作流中枢"
      description="统一管理工作流编排、发布状态与执行入口"
      icon="Connection"
      domain="知识与工作流"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="createWorkflow">
          新建工作流
        </el-button>
        <el-button type="success" :icon="Edit" @click="createVisual">
          可视化编辑器
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="search"
            placeholder="搜索工作流名称或描述"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选"
            style="width: 180px"
          >
            <el-option label="已发布" value="PUBLISHED" />
            <el-option label="草稿" value="DRAFT" />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="24" :sm="8">
        <StatCard label="总工作流" :value="stats.total" icon="Connection" />
      </el-col>
      <el-col :xs="12" :sm="8">
        <StatCard label="已发布" :value="stats.published" icon="VideoPlay" />
      </el-col>
      <el-col :xs="12" :sm="8">
        <StatCard label="草稿" :value="stats.draft" icon="Edit" />
      </el-col>
    </el-row>

    <el-card shadow="never">
      <OrinAsyncState :status="state.status" empty-text="暂无工作流数据" @retry="loadData">
        <el-table :data="filteredRows" border stripe>
          <el-table-column prop="workflowName" label="工作流名称" min-width="220" />
          <el-table-column prop="status" label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ row.status === 'PUBLISHED' ? '已发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="description"
            label="描述"
            min-width="260"
            show-overflow-tooltip
          />
          <el-table-column prop="updatedAt" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="editWorkflow(row)">
                编排
              </el-button>
              <el-button link type="info" @click="runWorkflow(row)">
                测试
              </el-button>
              <el-button link type="success" @click="exportDsl(row)">
                导出
              </el-button>
              <el-button link type="danger" @click="removeWorkflow(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </OrinAsyncState>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ROUTES } from '@/router/routes'
import StatCard from '@/components/StatCard.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import { deleteWorkflow, exportWorkflow, getWorkflows } from '@/api/workflow'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toWorkflowListViewModel,
  toWorkflowStatsViewModel
} from '@/viewmodels'

const router = useRouter()
const state = reactive(createAsyncState())
const rows = ref([])
const stats = reactive(toWorkflowStatsViewModel([]))

const search = ref('')
const statusFilter = ref('')

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const byStatus = !statusFilter.value || row.status === statusFilter.value
    const byQuery = !q ||
      row.workflowName.toLowerCase().includes(q) ||
      row.description.toLowerCase().includes(q)
    return byStatus && byQuery
  })
})

const loadData = async () => {
  markLoading(state)
  try {
    const response = await getWorkflows()
    rows.value = toWorkflowListViewModel(response)
    Object.assign(stats, toWorkflowStatsViewModel(rows.value))
    if (rows.value.length === 0) {
      markEmpty(state)
    } else {
      markSuccess(state)
    }
  } catch (error) {
    markError(state, error)
  }
}

const createWorkflow = () => router.push(ROUTES.AGENTS.WORKFLOW_CREATE)
const createVisual = () => router.push(ROUTES.AGENTS.WORKFLOW_VISUAL)

const editWorkflow = (row) => {
  if (!row?.id) return
  router.push(ROUTES.AGENTS.WORKFLOW_EDIT.replace(':id', row.id))
}

const runWorkflow = (row) => {
  if (!row?.id) return
  router.push(`/dashboard/applications/workflows/edit/${row.id}`)
}

const exportDsl = async (row) => {
  if (!row?.id) return
  try {
    await exportWorkflow(row.id)
    ElMessage.success('导出任务已触发')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const removeWorkflow = async (row) => {
  if (!row?.id) return
  await ElMessageBox.confirm(`确认删除工作流「${row.workflowName}」吗？`, '删除确认', { type: 'warning' })
  await deleteWorkflow(row.id)
  ElMessage.success('删除成功')
  loadData()
}

const formatTime = (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'

onMounted(loadData)
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}
</style>
