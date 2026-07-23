<template>
  <div class="runner-detail-page" v-loading="loading">
    <!-- 返回 + 标题行 -->
    <div class="page-header">
      <el-button link :icon="ArrowLeft" @click="$router.push('/dashboard/runtime/server')">
        返回列表
      </el-button>
      <div v-if="runner" style="margin-left:12px">
        <h2>{{ runner.name }}</h2>
        <span class="subtitle">Runner ID: {{ runner.id }}</span>
      </div>
      <div class="header-actions" v-if="runner">
        <el-button
          v-if="canDrain" type="warning" plain @click="handleDrain"
        >Drain</el-button>
        <el-button
          v-if="canRestore" type="success" plain @click="handleRestore"
        >恢复</el-button>
        <el-button
          v-if="runner.status !== 'REVOKED'" type="danger" plain @click="handleRevoke"
        >Revoke</el-button>
      </div>
    </div>

    <template v-if="runner">
      <!-- 状态 + 资源卡片 -->
      <el-row :gutter="16" class="info-row">
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="metric-label">状态</div>
            <el-tag :type="statusTagType(runner.status)" size="large" effect="dark">
              {{ runner.status }}
            </el-tag>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="metric-label">最后心跳</div>
            <div class="metric-value">
              {{ runner.lastHeartbeatAgeSec != null ? formatAge(runner.lastHeartbeatAgeSec) + '前' : '-' }}
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="metric-label">活跃 / 排队 Run</div>
            <div class="metric-value">{{ runner.activeRuns ?? 0 }} / {{ runner.queuedRuns ?? 0 }}</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <div class="metric-label">最大并发</div>
            <div class="metric-value">{{ runner.maxConcurrency ?? 1 }}</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 详情卡片 -->
      <el-row :gutter="16">
        <el-col :span="12">
          <el-card shadow="never" header="服务器信息">
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="主机名">{{ runner.hostname || '-' }}</el-descriptions-item>
              <el-descriptions-item label="OS">{{ runner.os || '-' }}</el-descriptions-item>
              <el-descriptions-item label="架构">{{ runner.arch || '-' }}</el-descriptions-item>
              <el-descriptions-item label="版本">{{ runner.version || '-' }}</el-descriptions-item>
              <el-descriptions-item label="CPU 核">{{ runner.cpuCores ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="依赖健康">{{ runner.lastDependencyHealth || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" header="最新资源快照">
            <el-descriptions v-if="latestSnapshot" :column="2" size="small" border>
              <el-descriptions-item label="CPU 使用">
                {{ latestSnapshot.cpuUsage != null ? latestSnapshot.cpuUsage + '%' : '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="内存">
                {{ formatBytes(latestSnapshot.memoryUsed) }} / {{ formatBytes(latestSnapshot.memoryTotal) }}
              </el-descriptions-item>
              <el-descriptions-item label="磁盘">
                {{ formatBytes(latestSnapshot.diskUsed) }} / {{ formatBytes(latestSnapshot.diskTotal) }}
              </el-descriptions-item>
              <el-descriptions-item label="上报时间">
                {{ formatTime(latestSnapshot.reportedAt) }}
              </el-descriptions-item>
            </el-descriptions>
            <el-empty v-else description="暂无心跳数据" :image-size="60" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 凭据信息 -->
      <el-card shadow="never" header="凭据" class="section-card" v-if="runner.credential">
        <el-descriptions :column="3" size="small">
          <el-descriptions-item label="Credential ID">{{ runner.credential.credentialId }}</el-descriptions-item>
          <el-descriptions-item label="前缀">{{ runner.credential.keyPrefix || '-' }}</el-descriptions-item>
          <el-descriptions-item label="末4位">{{ runner.credential.last4 || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 最近心跳 -->
      <el-card shadow="never" header="最近心跳记录" class="section-card">
        <OrinDataTable>
        <el-table :data="runner.recentSnapshots || []" size="small" empty-text="暂无心跳记录">
          <el-table-column label="时间" width="180">
            <template #default="{ row }">{{ formatTime(row.reportedAt) }}</template>
          </el-table-column>
          <el-table-column label="CPU" width="80">
            <template #default="{ row }">{{ row.cpuUsage != null ? row.cpuUsage + '%' : '-' }}</template>
          </el-table-column>
          <el-table-column label="内存使用" width="110">
            <template #default="{ row }">{{ formatBytes(row.memoryUsed) }}</template>
          </el-table-column>
          <el-table-column label="磁盘使用" width="110">
            <template #default="{ row }">{{ formatBytes(row.diskUsed) }}</template>
          </el-table-column>
          <el-table-column label="依赖健康" min-width="120">
            <template #default="{ row }">{{ row.dependencyHealth || '-' }}</template>
          </el-table-column>
        </el-table>
        </OrinDataTable>
      </el-card>
    </template>

    <!-- 未找到 -->
    <el-empty v-if="!runner && !loading" description="Runner 不存在" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft } from '@element-plus/icons-vue';
import { getRunnerDetail, drainRunner, restoreRunner, revokeRunner } from '@/api/runner';
import OrinDataTable from '@/components/orin/OrinDataTable.vue';

const route = useRoute();
const router = useRouter();

const loading = ref(false);
const runner = ref(null);

const latestSnapshot = computed(() => {
  return runner.value?.latestSnapshot || null;
});

const canDrain = computed(() => {
  const s = runner.value?.status;
  return s === 'ONLINE' || s === 'DEGRADED';
});

const canRestore = computed(() => {
  const s = runner.value?.status;
  return s === 'DRAINING' || Boolean(runner.value?.drainRequested);
});

function statusTagType(s) {
  const map = { ONLINE: 'success', ENROLLING: 'warning', DRAINING: 'info', DEGRADED: 'danger', OFFLINE: 'info', REVOKED: '', NEW: 'warning' };
  return map[s] || 'info';
}

function formatAge(sec) {
  if (sec == null) return '-';
  if (sec < 60) return Math.floor(sec) + 's';
  if (sec < 3600) return Math.floor(sec / 60) + 'm';
  return Math.floor(sec / 3600) + 'h';
}

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '-';
  if (bytes < 1024) return bytes + ' B';
  const units = ['KB', 'MB', 'GB', 'TB'];
  let v = bytes / 1024, i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return v.toFixed(i === 0 ? 0 : 1) + ' ' + units[i];
}

function formatTime(ts) {
  if (!ts) return '-';
  return new Date(ts).toLocaleString();
}

function fetchDetail() {
  const id = route.params.serverId || route.params.id;
  if (!id) return;
  loading.value = true;
  getRunnerDetail(id)
    .then(res => { runner.value = res?.data ?? res; })
    .catch(() => { ElMessage.error('获取 Runner 详情失败'); })
    .finally(() => { loading.value = false; });
}

async function handleDrain() {
  const id = runner.value.id;
  try {
    await ElMessageBox.confirm('确认 Drain 此 Runner？', '确认', { type: 'warning' });
    await drainRunner(id);
    ElMessage.success('Drain 已触发');
    fetchDetail();
  } catch { /* cancelled */ }
}

async function handleRestore() {
  try {
    const res = await restoreRunner(runner.value.id);
    const restored = res?.data ?? res;
    ElMessage.success(
      restored?.status === 'OFFLINE'
        ? '已取消 Drain，等待 Runner 心跳恢复'
        : '已恢复'
    );
    fetchDetail();
  } catch { ElMessage.error('恢复失败'); }
}

async function handleRevoke() {
  const id = runner.value.id;
  try {
    await ElMessageBox.confirm(
      `确认撤销 Runner "${runner.value.name}"？此操作不可逆，凭据将永久失效。`,
      '确认 Revoke', { type: 'error', confirmButtonText: '确认撤销' }
    );
    await revokeRunner(id);
    ElMessage.success('Runner 已撤销');
    fetchDetail();
  } catch { /* cancelled */ }
}

onMounted(fetchDetail);
</script>

<style scoped>
.runner-detail-page { padding: 16px; max-width: 1200px; }
.page-header { display: flex; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.subtitle { color: #909399; font-size: 12px; margin-left: 4px; }
.header-actions { margin-left: auto; display: flex; gap: 8px; }
.info-row { margin-bottom: 16px; }
.metric-label { color: #909399; font-size: 13px; margin-bottom: 4px; }
.metric-value { font-size: 22px; font-weight: 600; }
.section-card { margin-top: 16px; }
</style>
