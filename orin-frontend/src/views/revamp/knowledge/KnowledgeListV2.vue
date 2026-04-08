<template>
  <div class="page-container">
    <OrinPageShell
      title="知识与工作流"
      description="统一管理知识库资产，支撑智能体检索、推理与工作流执行"
      icon="Reading"
      domain="知识与工作流"
      maturity="available"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="createKnowledge">
          创建知识库
        </el-button>
        <el-button :icon="Refresh" @click="loadData">
          刷新
        </el-button>
      </template>
      <template #filters>
        <OrinFilterBar>
          <el-input
            v-model="search"
            placeholder="搜索知识库名称或描述"
            clearable
            :prefix-icon="Search"
            style="width: 280px"
          />
          <el-select
            v-model="typeFilter"
            clearable
            placeholder="类型筛选"
            style="width: 180px"
          >
            <el-option label="非结构化" value="UNSTRUCTURED" />
            <el-option label="结构化" value="STRUCTURED" />
            <el-option label="流程型" value="PROCEDURAL" />
            <el-option label="记忆型" value="META_MEMORY" />
          </el-select>
        </OrinFilterBar>
      </template>
    </OrinPageShell>

    <el-row :gutter="16" class="summary-row">
      <el-col :xs="12" :md="6">
        <StatCard label="知识库总数" :value="summary.total" icon="Collection" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="非结构化" :value="summary.unstructured" icon="Document" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="结构化" :value="summary.structured" icon="Grid" />
      </el-col>
      <el-col :xs="12" :md="6">
        <StatCard label="流程型" :value="summary.procedural" icon="Connection" />
      </el-col>
    </el-row>

    <el-card shadow="never">
      <OrinAsyncState :status="state.status" empty-text="暂无知识库数据" @retry="loadData">
        <el-table
          :data="filteredRows"
          stripe
          border
          @row-click="openDetail"
        >
          <el-table-column prop="name" label="知识库名称" min-width="220" />
          <el-table-column prop="type" label="类型" width="140">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">
                {{ typeLabel(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="资产规模" width="140">
            <template #default="{ row }">
              {{ statLabel(row) }}
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
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openDetail(row)">
                详情
              </el-button>
              <el-button link type="danger" @click.stop="removeKnowledge(row)">
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
import StatCard from '@/components/StatCard.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import { deleteKnowledge, getKnowledgeList } from '@/api/knowledge'
import {
  createAsyncState,
  markEmpty,
  markError,
  markLoading,
  markSuccess,
  toKnowledgeListViewModel,
  toKnowledgeSummaryViewModel
} from '@/viewmodels'

const router = useRouter()
const state = reactive(createAsyncState())
const rows = ref([])
const summary = reactive(toKnowledgeSummaryViewModel([]))
const search = ref('')
const typeFilter = ref('')

const filteredRows = computed(() => {
  const q = search.value.trim().toLowerCase()
  return rows.value.filter((item) => {
    const byType = !typeFilter.value || item.type === typeFilter.value
    const byQuery = !q ||
      item.name.toLowerCase().includes(q) ||
      item.description.toLowerCase().includes(q)
    return byType && byQuery
  })
})

const loadData = async () => {
  markLoading(state)
  try {
    const response = await getKnowledgeList()
    rows.value = toKnowledgeListViewModel(response)
    Object.assign(summary, toKnowledgeSummaryViewModel(rows.value))
    if (rows.value.length === 0) {
      markEmpty(state)
    } else {
      markSuccess(state)
    }
  } catch (error) {
    markError(state, error)
  }
}

const createKnowledge = () => {
  router.push(ROUTES.KNOWLEDGE.CREATE)
}

const openDetail = (row) => {
  if (!row?.id) return
  router.push(ROUTES.KNOWLEDGE.DETAIL.replace(':id', row.id))
}

const removeKnowledge = async (row) => {
  if (!row?.id) return
  await ElMessageBox.confirm(`确认删除知识库「${row.name}」吗？`, '删除确认', { type: 'warning' })
  await deleteKnowledge(row.id)
  ElMessage.success('删除成功')
  loadData()
}

const typeLabel = (type) => {
  switch (type) {
    case 'UNSTRUCTURED': return '非结构化'
    case 'STRUCTURED': return '结构化'
    case 'PROCEDURAL': return '流程型'
    case 'META_MEMORY': return '记忆型'
    default: return '未知'
  }
}

const statLabel = (row) => {
  if (row.type === 'STRUCTURED') return `${row.stats.tableCount} 表`
  if (row.type === 'PROCEDURAL') return `${row.stats.skillCount} 技能`
  if (row.type === 'META_MEMORY') return `${row.stats.memoryEntryCount} 条记忆`
  return `${row.stats.documentCount} 文档`
}

const formatTime = (value) => value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'

onMounted(loadData)
</script>

<style scoped>
.summary-row {
  margin-bottom: 16px;
}
</style>
