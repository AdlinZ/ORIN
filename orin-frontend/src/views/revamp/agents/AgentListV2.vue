<template>
  <div class="page-container">
    <OrinPageShell
      title="智能体中枢"
      description="统一管理智能体、模型绑定、工作流入口和运行状态"
      icon="UserFilled"
      domain="智能体中枢"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="$router.push(ROUTES.AGENTS.ONBOARD)">
          接入新智能体
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="search"
            placeholder="搜索智能体、模型、服务商"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选"
            style="width: 160px"
          >
            <el-option label="运行中" value="RUNNING" />
            <el-option label="已停止" value="STOPPED" />
            <el-option label="高负载" value="HIGH_LOAD" />
            <el-option label="异常" value="ERROR" />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <el-card shadow="never">
      <OrinAsyncState :status="state.status" empty-text="暂无智能体数据" @retry="loadData">
        <el-table
          :data="viewRows"
          stripe
          border
          @row-click="goConsole"
        >
          <el-table-column prop="agentName" label="智能体名称" min-width="220" />
          <el-table-column prop="providerType" label="服务商" width="130" />
          <el-table-column prop="viewType" label="类型" width="120" />
          <el-table-column prop="modelName" label="核心模型" min-width="220" />
          <el-table-column
            prop="status"
            label="状态"
            width="120"
            align="center"
          >
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" effect="light">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastHeartbeat" label="最后活跃" width="180">
            <template #default="{ row }">
              {{ formatTime(row.lastHeartbeat) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="goConsole(row)">
                控制台
              </el-button>
              <el-button link type="info" @click.stop="openWorkspace">
                工作台
              </el-button>
              <el-button link type="danger" @click.stop="handleDelete(row)">
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
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { ROUTES } from '@/router/routes'
import { getAgentList } from '@/api/monitor'
import { deleteAgent } from '@/api/agent'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toAgentListViewModel
} from '@/viewmodels'

const router = useRouter()
const state = reactive(createAsyncState())

const rows = ref([])
const search = ref('')
const statusFilter = ref('')

const viewRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchQuery = !q ||
      row.agentName.toLowerCase().includes(q) ||
      row.modelName.toLowerCase().includes(q) ||
      row.providerType.toLowerCase().includes(q)
    const matchStatus = !statusFilter.value || row.status === statusFilter.value
    return matchQuery && matchStatus
  })
})

const loadData = async () => {
  markLoading(state)
  try {
    const response = await getAgentList()
    rows.value = toAgentListViewModel(response)
    if (rows.value.length === 0) {
      markEmpty(state)
    } else {
      markSuccess(state)
    }
  } catch (error) {
    markError(state, error)
  }
}

const goConsole = (row) => {
  const id = row?.id || row?.raw?.id
  if (!id) return
  router.push(ROUTES.AGENTS.CONSOLE.replace(':id', id))
}

const openWorkspace = () => {
  router.push(ROUTES.AGENTS.WORKSPACE)
}

const handleDelete = async (row) => {
  if (!row?.id) {
    ElMessage.warning('该记录缺少 ID，无法删除')
    return
  }
  await ElMessageBox.confirm(`确认删除智能体「${row.agentName}」吗？`, '删除确认', { type: 'warning' })
  await deleteAgent(row.id)
  ElMessage.success('删除成功')
  loadData()
}

const getStatusType = (status) => {
  switch (status) {
    case 'RUNNING': return 'success'
    case 'HIGH_LOAD': return 'warning'
    case 'ERROR': return 'danger'
    case 'STOPPED': return 'info'
    default: return ''
  }
}

const formatTime = (val) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-')

onMounted(loadData)
</script>
