<template>
  <div class="runner-list-page">
    <div class="page-header">
      <div>
        <h2>Runner 服务器</h2>
        <p class="subtitle">接入并监控你的自有服务器</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showEnrollDialog = true">
        接入服务器
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6" v-for="stat in stats" :key="stat.label">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-value" :class="stat.color">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Runner 列表 -->
    <el-card shadow="never">
      <OrinDataTable>
      <el-table
        :data="runners"
        v-loading="loading"
        stripe
        @row-click="goDetail"
        style="cursor: pointer; width: 100%"
        empty-text="暂无接入的服务器，点击「接入服务器」开始"
      >
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small" effect="dark">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="hostname" label="主机名" min-width="140" />
        <el-table-column label="CPU" width="80">
          <template #default="{ row }">{{ row.cpuCores ?? '-' }}核</template>
        </el-table-column>
        <el-table-column label="内存" width="110">
          <template #default="{ row }">{{ formatBytes(row.memoryTotal) }}</template>
        </el-table-column>
        <el-table-column label="磁盘" width="110">
          <template #default="{ row }">{{ formatBytes(row.diskTotal) }}</template>
        </el-table-column>
        <el-table-column label="心跳" width="120">
          <template #default="{ row }">
            <span v-if="row.lastHeartbeatAgeSec != null" :class="heartbeatClass(row)">
              {{ formatAge(row.lastHeartbeatAgeSec) }}
            </span>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="80" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click.stop="goDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'ONLINE' || row.status === 'DEGRADED'"
              link type="warning" size="small" @click.stop="handleDrain(row)"
            >Drain</el-button>
            <el-button
              v-if="row.status === 'DRAINING' || row.drainRequested"
              link type="success" size="small" @click.stop="handleRestore(row)"
            >恢复</el-button>
          </template>
        </el-table-column>
      </el-table>
      </OrinDataTable>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 接入向导对话框 -->
    <EnrollmentWizard
      v-model:visible="showEnrollDialog"
      @enrolled="fetchList"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { listRunners, drainRunner, restoreRunner } from '@/api/runner';
import { ROUTES } from '@/router/routes';
import EnrollmentWizard from './EnrollmentWizard.vue';
import OrinDataTable from '@/components/orin/OrinDataTable.vue';

const router = useRouter();

const loading = ref(false);
const runners = ref([]);
const page = ref(1);
const size = ref(20);
const total = ref(0);
const showEnrollDialog = ref(false);

const stats = computed(() => {
  const online = runners.value.filter(r => r.status === 'ONLINE').length;
  const degraded = runners.value.filter(r => r.status === 'DEGRADED').length;
  const offline = runners.value.filter(r => r.status === 'OFFLINE').length;
  const draining = runners.value.filter(r => r.status === 'DRAINING').length;
  return [
    { label: '在线', value: online, color: 'green' },
    { label: '降级', value: degraded, color: 'orange' },
    { label: '离线', value: offline, color: 'red' },
    { label: '维护中', value: draining, color: 'blue' },
  ];
});

function statusTagType(status) {
  const map = {
    ONLINE: 'success', ENROLLING: 'warning', DRAINING: 'info',
    DEGRADED: 'danger', OFFLINE: 'info', REVOKED: '', NEW: 'warning',
  };
  return map[status] || 'info';
}

function heartbeatClass(row) {
  if (!row.lastHeartbeatAgeSec) return '';
  if (row.status === 'OFFLINE') return 'text-danger';
  if (row.lastHeartbeatAgeSec > 30) return 'text-warning';
  return 'text-success';
}

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '-';
  if (bytes < 1024) return bytes + ' B';
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = bytes / 1024, i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return v.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

function formatAge(sec) {
  if (sec == null) return '-';
  if (sec < 60) return Math.floor(sec) + 's';
  if (sec < 3600) return Math.floor(sec / 60) + 'm';
  return Math.floor(sec / 3600) + 'h';
}

async function fetchList() {
  loading.value = true;
  try {
    const res = await listRunners({ page: page.value - 1, size: size.value });
    const data = res?.data ?? res;
    runners.value = data.content ?? data ?? [];
    total.value = data.totalElements ?? runners.value.length;
  } catch {
    ElMessage.error('获取 Runner 列表失败');
  } finally {
    loading.value = false;
  }
}

function goDetail(row) {
  router.push(ROUTES.WORKSPACE.RUNNER_DETAIL.replace(':runnerId', row.id));
}

async function handleDrain(row) {
  try {
    await ElMessageBox.confirm(
      `确认将 "${row.name}" 设为 DRAINING（停止领取新 Run，等待当前 Run 结束）？`,
      '确认 Drain', { type: 'warning', confirmButtonText: '确认 Drain', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  try {
    await drainRunner(row.id);
    await fetchList();
    ElMessage.success('Runner 已进入 DRAINING 状态');
  } catch {
    ElMessage.error('Drain 失败');
  }
}

async function handleRestore(row) {
  try {
    const res = await restoreRunner(row.id);
    const restored = res?.data ?? res;
    ElMessage.success(
      restored?.status === 'OFFLINE'
        ? '已取消 Drain，等待 Runner 心跳恢复'
        : 'Runner 已恢复'
    );
    await fetchList();
  } catch { ElMessage.error('恢复失败'); }
}

onMounted(fetchList);
</script>

<style scoped>
.runner-list-page { padding: 16px; }
.page-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px;
}
.page-header h2 { margin: 0; font-size: 20px; }
.subtitle { margin: 4px 0 0; color: #909399; font-size: 13px; }
.stats-row { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-value.green { color: #67c23a; }
.stat-value.orange { color: #e6a23c; }
.stat-value.red { color: #f56c6c; }
.stat-value.blue { color: #409eff; }
.stat-label { color: #909399; font-size: 13px; margin-top: 4px; }
.text-success { color: #67c23a; }
.text-warning { color: #e6a23c; }
.text-danger { color: #f56c6c; }
.text-muted { color: #c0c4cc; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
