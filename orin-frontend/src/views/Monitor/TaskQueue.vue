<template>
  <div class="page-container">
    <PageHeader title="任务队列管理" icon="Tickets">
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">
          刷新
        </el-button>
      </template>
    </PageHeader>

    <!-- 任务统计概览 -->
    <el-row :gutter="20" class="stats-row">
      <el-col v-for="(stat, index) in taskStats" :key="index" :span="6">
        <el-card shadow="hover" class="stat-card" :class="stat.class">
          <div class="stat-card-inner">
            <div class="stat-icon" :style="{ backgroundColor: stat.bgColor }">
              <el-icon :style="{ color: stat.color }">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-label">
                {{ stat.label }}
              </div>
              <div class="stat-value">
                {{ stat.value }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 优先级统计 -->
    <el-card class="priority-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>优先级分布</span>
        </div>
      </template>
      <el-row :gutter="20">
        <el-col v-for="(pstat, index) in priorityStats" :key="index" :span="8">
          <div class="priority-item">
            <el-tag :type="pstat.type" size="large">
              {{ pstat.label }}
            </el-tag>
            <span class="count">{{ pstat.count }}</span>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 任务列表 -->
    <el-card class="task-list-card" shadow="hover">
      <template #header>
        <div class="card-header">
          <span>任务列表</span>
          <el-radio-group v-model="activeTab" size="small">
            <el-radio-button label="queued">
              排队中
            </el-radio-button>
            <el-radio-button label="running">
              执行中
            </el-radio-button>
            <el-radio-button label="failed">
              失败
            </el-radio-button>
            <el-radio-button label="dead">
              死信
            </el-radio-button>
          </el-radio-group>
        </div>
      </template>

      <el-table v-loading="loading" :data="taskList" stripe>
        <el-table-column
          prop="taskId"
          label="任务ID"
          width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="workflowId" label="工作流ID" width="100" />
        <el-table-column prop="priority" label="优先级" width="100">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small">
              {{ row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="100" />
        <el-table-column prop="queuedAt" label="入队时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.queuedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="startedAt" label="开始时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.startedAt) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="completedAt" label="完成时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.completedAt) || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时(ms)" width="100">
          <template #default="{ row }">
            {{ row.durationMs || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              type="info"
              size="small"
              text
              @click="handleViewDetail(row)"
            >
              详情
            </el-button>
            <el-tooltip v-if="row.status === 'FAILED' || row.status === 'DEAD'" content="重新执行该任务" placement="top">
              <el-button
                type="primary"
                size="small"
                text
                @click="handleReplay(row)"
              >
                重放
              </el-button>
            </el-tooltip>
            <el-tooltip v-if="row.status === 'QUEUED'" content="取消该排队中的任务" placement="top">
              <el-button
                type="danger"
                size="small"
                text
                @click="handleCancel(row)"
              >
                取消
              </el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 任务详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="任务详情" width="700px">
      <el-descriptions v-if="currentTask" :column="2" border>
        <el-descriptions-item label="任务ID">
          {{ currentTask.taskId }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentTask.status)">
            {{ currentTask.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工作流ID">
          {{ currentTask.workflowId }}
        </el-descriptions-item>
        <el-descriptions-item label="工作流实例ID">
          {{ currentTask.workflowInstanceId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="getPriorityType(currentTask.priority)">
            {{ currentTask.priority }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="触发来源">
          {{ currentTask.triggerSource || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="触发者">
          {{ currentTask.triggeredBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="重试次数">
          {{ currentTask.retryCount || 0 }} / {{ currentTask.maxRetries || 0 }}
        </el-descriptions-item>
        <el-descriptions-item label="下次重试时间">
          {{ formatTime(currentTask.nextRetryAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="耗时">
          {{ currentTask.durationMs ? currentTask.durationMs + 'ms' : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="入队时间">
          {{ formatTime(currentTask.queuedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ formatTime(currentTask.startedAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">
          {{ formatTime(currentTask.completedAt) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatTime(currentTask.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="错误信息" :span="2">
          {{ currentTask.errorMessage || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="死信原因" :span="2">
          {{ currentTask.deadLetterReason || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="输入数据" :span="2">
          <pre class="json-content">{{ JSON.stringify(currentTask.inputData, null, 2) || '-' }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="输出数据" :span="2">
          <pre class="json-content">{{ JSON.stringify(currentTask.outputData, null, 2) || '-' }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Tickets, CircleCheck, Loading, Warning, CircleClose } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import {
  getTaskStatistics,
  getPendingPriorityStatistics,
  getQueuedTasks,
  getRunningTasks,
  getFailedTasks,
  getDeadTasks,
  getTaskById,
  replayTask,
  cancelTask
} from '@/api/task';

const loading = ref(false);
const activeTab = ref('queued');
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const taskList = ref([]);
const taskStats = ref({});
const priorityStats = ref([]);
const showDetailDialog = ref(false);
const currentTask = ref(null);

const statusMap = {
  QUEUED: { label: '排队中', icon: Tickets, color: '#909399', bgColor: 'rgba(144, 147, 153, 0.1)', class: 'stat-queued' },
  RUNNING: { label: '执行中', icon: Loading, color: '#E6A23C', bgColor: 'rgba(230, 162, 60, 0.1)', class: 'stat-running' },
  RETRYING: { label: '重试中', icon: Refresh, color: '#409EFF', bgColor: 'rgba(64, 158, 255, 0.1)', class: 'stat-retrying' },
  COMPLETED: { label: '已完成', icon: CircleCheck, color: '#67C23A', bgColor: 'rgba(103, 194, 58, 0.1)', class: 'stat-completed' },
  FAILED: { label: '失败', icon: Warning, color: '#F56C6C', bgColor: 'rgba(245, 108, 108, 0.1)', class: 'stat-failed' },
  DEAD: { label: '死信', icon: CircleClose, color: '#C0C4CC', bgColor: 'rgba(192, 196, 204, 0.1)', class: 'stat-dead' }
};

const priorityMap = {
  HIGH: { label: '高优', type: 'danger' },
  NORMAL: { label: '普通', type: 'warning' },
  LOW: { label: '低优', type: 'info' }
};

const getPriorityType = (priority) => {
  return priorityMap[priority]?.type || 'info';
};

const getStatusType = (status) => {
  const map = {
    QUEUED: 'info',
    RUNNING: 'warning',
    RETRYING: 'primary',
    COMPLETED: 'success',
    FAILED: 'danger',
    DEAD: 'info'
  };
  return map[status] || 'info';
};

const formatTime = (time) => {
  if (!time) return null;
  if (typeof time === 'string') {
    return time.replace('T', ' ').substring(0, 19);
  }
  return time;
};

const fetchStatistics = async () => {
  try {
    const statsRes = await getTaskStatistics();
    const stats = statsRes.data || statsRes;
    taskStats.value = stats;

    taskStats.value = [
      { label: '排队中', value: stats.QUEUED || 0, ...statusMap.QUEUED },
      { label: '执行中', value: stats.RUNNING || 0, ...statusMap.RUNNING },
      { label: '重试中', value: stats.RETRYING || 0, ...statusMap.RETRYING },
      { label: '已完成', value: stats.COMPLETED || 0, ...statusMap.COMPLETED },
      { label: '失败', value: stats.FAILED || 0, ...statusMap.FAILED },
      { label: '死信', value: stats.DEAD || 0, ...statusMap.DEAD }
    ];
  } catch (e) {
    console.error('获取任务统计失败:', e);
  }
};

const fetchPriorityStatistics = async () => {
  try {
    const pStatsRes = await getPendingPriorityStatistics();
    const pStats = pStatsRes.data || pStatsRes;

    priorityStats.value = [
      { label: 'HIGH - 高优', count: pStats.HIGH || 0, type: 'danger' },
      { label: 'NORMAL - 普通', count: pStats.NORMAL || 0, type: 'warning' },
      { label: 'LOW - 低优', count: pStats.LOW || 0, type: 'info' }
    ];
  } catch (e) {
    console.error('获取优先级统计失败:', e);
  }
};

const fetchTasks = async () => {
  loading.value = true;
  try {
    let res;
    const params = { page: currentPage.value - 1, size: pageSize.value };

    switch (activeTab.value) {
      case 'queued':
        res = await getQueuedTasks(params);
        break;
      case 'running':
        res = await getRunningTasks(params);
        break;
      case 'failed':
        res = await getFailedTasks(params);
        break;
      case 'dead':
        res = await getDeadTasks(params);
        break;
    }

    const data = res.data || res;
    taskList.value = data.content || data.records || [];
    total.value = data.totalElements || data.total || taskList.value.length;
  } catch (e) {
    ElMessage.error('获取任务列表失败: ' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
};

const fetchData = async () => {
  await Promise.all([fetchStatistics(), fetchPriorityStatistics(), fetchTasks()]);
};

const handleReplay = async (task) => {
  try {
    await ElMessageBox.confirm(`确定要重放任务 ${task.taskId} 吗?`, '确认重放', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    await replayTask(task.taskId);
    ElMessage.success('任务已重放');
    fetchData();
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('重放失败: ' + (e.message || '未知错误'));
    }
  }
};

const handleViewDetail = async (task) => {
  // 获取任务详情
  try {
    const res = await getTaskById(task.taskId);
    currentTask.value = res.data || res;
    showDetailDialog.value = true;
  } catch (e) {
    ElMessage.error('获取任务详情失败');
  }
};

const handleCancel = async (task) => {
  try {
    await ElMessageBox.confirm(`确定要取消任务 ${task.taskId} 吗?`, '确认取消', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    await cancelTask(task.taskId);
    ElMessage.success('任务已取消');
    fetchData();
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('取消失败: ' + (e.message || '未知错误'));
    }
  }
};

const handleSizeChange = () => {
  currentPage.value = 1;
  fetchTasks();
};

const handlePageChange = () => {
  fetchTasks();
};

watch(activeTab, () => {
  currentPage.value = 1;
  fetchTasks();
});

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.page-container {
  padding: 0;
  animation: fadeIn 0.5s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.stats-row {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: var(--radius-xl) !important;
}

.stat-card-inner {
  display: flex;
  align-items: center;
  gap: 16px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.priority-card, .task-list-card {
  margin-bottom: 24px;
  border-radius: var(--radius-xl) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.priority-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.priority-item .count {
  font-size: 20px;
  font-weight: 700;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.json-content {
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
