<template>
  <div class="workflow-history">
    <el-table v-loading="loading" :data="instances" border>
      <el-table-column prop="id" label="实例ID" width="120">
        <template #default="{ row }">
          <el-tag size="small">
            {{ row.id?.substring(0, 8) }}...
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="inputs" label="输入" min-width="150">
        <template #default="{ row }">
          <el-tooltip :content="JSON.stringify(row.inputs)" placement="top">
            <span class="truncate">{{ JSON.stringify(row.inputs) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="outputs" label="输出" min-width="150">
        <template #default="{ row }">
          <el-tooltip v-if="row.outputs" :content="JSON.stringify(row.outputs)" placement="top">
            <span class="truncate">{{ JSON.stringify(row.outputs) }}</span>
          </el-tooltip>
          <span v-else class="text-gray">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="duration" label="耗时" width="80">
        <template #default="{ row }">
          {{ row.duration ? `${row.duration}ms` : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button type="primary" link @click="viewDetail(row)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="loadInstances"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getWorkflowInstances } from '@/api/workflow'

const props = defineProps({
  workflowId: {
    type: String,
    required: true
  }
})

const loading = ref(false)
const instances = ref([])
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const loadInstances = async () => {
  loading.value = true
  try {
    const res = await getWorkflowInstances(props.workflowId)
    instances.value = res.data || res || []
    total.value = instances.value.length
  } catch (e) {
    console.error('Failed to load workflow instances', e)
  } finally {
    loading.value = false
  }
}

const getStatusType = (status) => {
  const map = {
    'running': 'warning',
    'completed': 'success',
    'failed': 'danger',
    'pending': 'info'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'running': '运行中',
    'completed': '已完成',
    'failed': '失败',
    'pending': '等待中'
  }
  return map[status] || status
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

const viewDetail = (row) => {
  console.log('View detail:', row)
}

onMounted(() => {
  loadInstances()
})
</script>

<style scoped>
.workflow-history {
  padding: 16px;
}

.truncate {
  display: inline-block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-gray {
  color: #909399;
}
</style>
