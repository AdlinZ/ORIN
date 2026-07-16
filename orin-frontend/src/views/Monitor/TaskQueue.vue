<template>
  <div class="task-queue fade-in">
    <OrinPageShell
      title="任务队列管理"
      description="查看排队、执行、失败、死信和取消任务，并执行重放或取消操作"
      icon="Tickets"
      domain="运行监控"
    >
      <template #actions>
        <el-button :icon="Refresh" :loading="loading" @click="fetchData">
          刷新
        </el-button>
      </template>
      <template #filters>
        <div class="task-toolbar">
          <div class="task-toolbar-heading">
            <h2>队列任务</h2>
            <span>{{ total }} 个结果</span>
          </div>
          <el-radio-group v-model="activeTab" size="small" class="task-status-filter">
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
            <el-radio-button label="cancelled">
              已取消
            </el-radio-button>
          </el-radio-group>
        </div>
      </template>
    </OrinPageShell>

    <section class="task-overview" aria-label="任务运行概览">
      <div class="task-stat-grid">
        <div
          v-for="stat in taskStats"
          :key="stat.label"
          class="task-stat"
          :class="stat.class"
        >
          <div class="task-stat-icon" :style="{ backgroundColor: stat.bgColor }">
            <el-icon :style="{ color: stat.color }">
              <component :is="stat.icon" />
            </el-icon>
          </div>
          <div>
            <span>{{ stat.label }}</span>
            <strong>{{ stat.value }}</strong>
          </div>
        </div>
      </div>
      <div class="priority-summary">
        <span>待处理优先级</span>
        <div class="priority-list">
          <div v-for="pstat in priorityStats" :key="pstat.label" class="priority-item">
            <el-tag :type="pstat.type" effect="plain" size="small">
              {{ pstat.label }}
            </el-tag>
            <strong>{{ pstat.count }}</strong>
          </div>
        </div>
      </div>
    </section>

    <OrinAsyncState
      :status="loading ? 'loading' : (taskList.length ? 'success' : 'empty')"
      empty-text="当前状态下暂无任务"
    >
      <OrinDataTable compact>
        <el-table v-loading="loading" :data="taskList" :row-class-name="taskRowClassName">
          <el-table-column
            prop="taskId"
            label="任务ID"
            min-width="180"
            show-overflow-tooltip
          />
          <el-table-column
            prop="workflowId"
            label="工作流"
            min-width="120"
            show-overflow-tooltip
          />
          <el-table-column prop="priority" label="优先级" width="92">
            <template #default="{ row }">
              <el-tag :type="getPriorityType(row.priority)" size="small">
                {{ row.priority }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="96">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="retryCount"
            label="重试"
            width="72"
            align="center"
          />
          <el-table-column prop="queuedAt" label="入队时间" width="164">
            <template #default="{ row }">
              {{ formatTime(row.queuedAt) }}
            </template>
          </el-table-column>
          <el-table-column
            prop="durationMs"
            label="耗时"
            width="86"
            align="right"
          >
            <template #default="{ row }">
              {{ row.durationMs ? `${row.durationMs}ms` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="156" fixed="right">
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
        <template #footer>
          <div class="task-table-footer">
            <span>共 {{ total }} 个任务</span>
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[10, 20, 50, 100]"
              :layout="total > pageSize ? 'total, ->, sizes, prev, pager, next' : 'total, ->, sizes'"
              background
              size="small"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </template>
      </OrinDataTable>
    </OrinAsyncState>

    <!-- 任务详情对话框 -->
    <el-dialog
      v-model="showDetailDialog"
      title="任务详情"
      width="min(720px, calc(100vw - 32px))"
      align-center
    >
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
        <el-descriptions-item label="输入摘要" :span="2">
          <el-collapse class="json-collapse">
            <el-collapse-item :title="jsonSummary(currentTask.inputData)" name="input">
              <pre class="json-content">{{ formatJson(currentTask.inputData) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-descriptions-item>
        <el-descriptions-item label="输出摘要" :span="2">
          <el-collapse class="json-collapse">
            <el-collapse-item :title="jsonSummary(currentTask.outputData)" name="output">
              <pre class="json-content">{{ formatJson(currentTask.outputData) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Tickets, CircleCheck, Loading, Warning, CircleClose } from '@element-plus/icons-vue';
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue';
import OrinDataTable from '@/components/orin/OrinDataTable.vue';
import OrinPageShell from '@/components/orin/OrinPageShell.vue';
import {
  getTaskStatistics,
  getPendingPriorityStatistics,
  getQueuedTasks,
  getRunningTasks,
  getFailedTasks,
  getDeadTasks,
  getCancelledTasks,
  getTaskById,
  replayTask,
  cancelTask
} from '@/api/task';

const loading = ref(false);
const route = useRoute();
const activeTab = ref('queued');
const currentPage = ref(1);
const pageSize = ref(20);
const total = ref(0);
const taskList = ref([]);
const taskStats = ref([]);
const priorityStats = ref([]);
const showDetailDialog = ref(false);
const currentTask = ref(null);
const highlightedTaskId = ref('');

const statusMap = {
  QUEUED:    { label: '排队中', icon: Tickets,      color: 'var(--neutral-gray-400)',  bgColor: 'var(--neutral-gray-100)',          class: 'stat-queued'    },
  RUNNING:   { label: '执行中', icon: Loading,      color: 'var(--warning-500)',       bgColor: 'var(--warning-light)',             class: 'stat-running'   },
  RETRYING:  { label: '重试中', icon: Refresh,      color: 'var(--info-500)',          bgColor: 'var(--info-light)',                class: 'stat-retrying'  },
  COMPLETED: { label: '已完成', icon: CircleCheck,  color: 'var(--success-500)',       bgColor: 'var(--success-light)',             class: 'stat-completed' },
  FAILED:    { label: '失败',   icon: Warning,      color: 'var(--error-500)',         bgColor: 'var(--error-light)',               class: 'stat-failed'    },
  DEAD: { label: '死信', icon: CircleClose, color: '#C0C4CC', bgColor: 'rgba(192, 196, 204, 0.1)', class: 'stat-dead' },
  CANCELLED: { label: '已取消', icon: CircleClose, color: 'var(--neutral-gray-500)', bgColor: 'var(--neutral-gray-100)', class: 'stat-cancelled' }
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
    DEAD: 'info',
    CANCELLED: 'info'
  };
  return map[status] || 'info';
};

const formatJson = (value) => {
  if (value === null || value === undefined || value === '') return '-';
  return JSON.stringify(value, null, 2);
};

const jsonSummary = (value) => {
  if (value === null || value === undefined || value === '') return '暂无数据';
  const text = JSON.stringify(value);
  return text.length > 120 ? `${text.slice(0, 120)}...` : text;
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
    const payload = statsRes.data || statsRes;
    const stats = payload.statusStatistics || payload;

    taskStats.value = [
      { label: '排队中', value: stats.QUEUED || 0, ...statusMap.QUEUED },
      { label: '执行中', value: stats.RUNNING || 0, ...statusMap.RUNNING },
      { label: '重试中', value: stats.RETRYING || 0, ...statusMap.RETRYING },
      { label: '已完成', value: stats.COMPLETED || 0, ...statusMap.COMPLETED },
      { label: '失败', value: stats.FAILED || 0, ...statusMap.FAILED },
      { label: '死信', value: stats.DEAD || 0, ...statusMap.DEAD },
      { label: '已取消', value: stats.CANCELLED || 0, ...statusMap.CANCELLED }
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
      case 'cancelled':
        res = await getCancelledTasks(params);
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
    await ElMessageBox.confirm(
      [
        `原任务: ${task.taskId}`,
        `状态: ${task.status || '-'}`,
        `失败原因: ${task.errorMessage || '-'}`,
        `死信原因: ${task.deadLetterReason || '-'}`,
        `重试次数: ${task.retryCount || 0} / ${task.maxRetries || 0}`,
        `Trace ID: ${task.traceId || '-'}`,
        '确认后将创建一个新的排队任务，原任务终态保持不变。'
      ].join('\n'),
      '确认重放失败任务',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    const res = await replayTask(task.taskId);
    const payload = res.data || res;
    highlightedTaskId.value = payload.newTaskId || payload.taskId || '';
    if (highlightedTaskId.value) {
      activeTab.value = 'queued';
    }
    ElMessage.success(`任务已重放，原任务 ${payload.originalTaskId || task.taskId}，新任务 ${highlightedTaskId.value || '-'}`);
    await fetchData();
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
    await ElMessageBox.confirm(
      [
        `任务: ${task.taskId}`,
        `状态: ${task.status || '-'}`,
        `Trace ID: ${task.traceId || '-'}`,
        '只允许取消仍在排队中的任务，确认后任务将进入 CANCELLED 终态。'
      ].join('\n'),
      '确认取消排队任务',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );

    const res = await cancelTask(task.taskId);
    const payload = res.data || res;
    highlightedTaskId.value = task.taskId;
    ElMessage.success(`任务已取消，当前状态 ${payload.status || 'CANCELLED'}`);
    await fetchData();
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

const taskRowClassName = ({ row }) => {
  return row?.taskId && row.taskId === highlightedTaskId.value ? 'highlight-task-row' : '';
};

watch(activeTab, () => {
  currentPage.value = 1;
  fetchTasks();
});

onMounted(async () => {
  await fetchData();
  const taskId = route.query.taskId;
  if (typeof taskId === 'string' && taskId) {
    highlightedTaskId.value = taskId;
    await handleViewDetail({ taskId });
  }
});
</script>

<style scoped>
.task-queue {
  min-width: 0;
}

.fade-in {
  animation: fadeIn 0.35s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.task-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  width: 100%;
  min-width: 0;
}

.task-toolbar-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.task-toolbar-heading h2 {
  margin: 0;
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-size: 16px;
  font-weight: var(--font-semibold, 600);
}

.task-toolbar-heading span {
  padding: 3px 8px;
  border-radius: var(--radius-full, 999px);
  background: var(--neutral-gray-100, var(--el-fill-color-light));
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  font-size: 12px;
  white-space: nowrap;
}

.task-status-filter {
  flex: none;
  max-width: 100%;
  overflow-x: auto;
}

.task-overview {
  display: grid;
  gap: 12px;
  margin: 0 0 var(--orin-page-gap, 18px);
}

.task-stat-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(108px, 1fr));
  gap: 10px;
}

.task-stat {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--orin-border-strong, var(--el-border-color));
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, var(--el-bg-color));
}

.task-stat-icon {
  display: grid;
  flex: none;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  place-items: center;
  font-size: 16px;
}

.task-stat span,
.priority-summary > span {
  display: block;
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  font-size: 12px;
}

.task-stat strong {
  display: block;
  margin-top: 2px;
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-size: 19px;
  line-height: 1.1;
}

.priority-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 12px;
  border: 1px solid var(--orin-border-strong, var(--el-border-color));
  border-radius: var(--radius-base, 8px);
  background: color-mix(in srgb, var(--orin-surface, var(--el-bg-color)) 86%, var(--orin-primary-soft, transparent));
}

.priority-list {
  display: flex;
  align-items: center;
  gap: 18px;
}

.priority-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.priority-item strong {
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-size: 14px;
}

.task-table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 16px;
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  font-size: 13px;
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

.json-collapse {
  width: 100%;
}

.json-collapse :deep(.el-collapse-item__header) {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.highlight-task-row) {
  --el-table-tr-bg-color: rgba(20, 184, 166, 0.12);
}

@media (max-width: 1280px) {
  .task-stat-grid {
    grid-template-columns: repeat(4, minmax(120px, 1fr));
  }
}

@media (max-width: 860px) {
  .task-toolbar,
  .priority-summary,
  .task-table-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .task-status-filter {
    width: 100%;
  }

  .priority-list {
    flex-wrap: wrap;
    gap: 10px 16px;
  }
}

@media (max-width: 560px) {
  .task-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .task-stat {
    padding: 10px;
  }
}
</style>
