<template>
  <div class="page-container">
    <PageHeader 
      title="系统审计日志" 
      description="追踪全量 API 调用轨迹，包含耗时、Token 消耗及异常堆栈"
      icon="List"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport" :disabled="logs.length === 0">导出审计报告</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card premium-card">
      <el-table :data="logs" style="width: 100%" v-loading="loading" stripe>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-descriptions title="详细请求参数" :column="2" border>
                <el-descriptions-item label="端点 (Endpoint)">{{ row.endpoint }}</el-descriptions-item>
                <el-descriptions-item label="方法 (Method)">{{ row.method }}</el-descriptions-item>
                <el-descriptions-item label="请求 IP">{{ row.ipAddress }}</el-descriptions-item>
                <el-descriptions-item label="User Agent">{{ row.userAgent }}</el-descriptions-item>
                <el-descriptions-item label="响应状态">{{ row.statusCode }}</el-descriptions-item>
                <el-descriptions-item label="预估成本">${{ row.estimatedCost }}</el-descriptions-item>
                <el-descriptions-item label="错误信息" :span="2">
                  <span :class="row.success ? '' : 'text-danger'">{{ row.errorMessage || '无异常' }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="请求参数 (JSON)" :span="2">
                  <pre class="json-block">{{ row.requestParams }}</pre>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="时间" width="180" sortable>
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        
        <el-table-column prop="model" label="模型" width="140">
          <template #default="{ row }">
            <el-tag size="small">{{ row.model || 'System' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="endpoint" label="接口" min-width="200" show-overflow-tooltip />

        <el-table-column prop="totalTokens" label="Tokens" width="100" align="center" sortable>
          <template #default="{ row }">
             <span v-if="row.totalTokens" class="font-bold">{{ row.totalTokens }}</span>
             <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="responseTime" label="耗时" width="100" align="center" sortable>
          <template #default="{ row }">
            <el-tag :type="getLatencyType(row.responseTime)" size="small">
              {{ row.responseTime }}ms
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="success" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[15, 30, 50, 100]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          @size-change="fetchLogs"
          @current-change="fetchLogs"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue';
import { Download, List } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import request from '@/utils/request';
import { ElMessage } from 'element-plus';

const loading = ref(false);
const logs = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(15);

const fetchLogs = async () => {
  loading.value = true;
  try {
    const res = await request.get('/audit/logs', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value,
        sortBy: 'createdAt',
        direction: 'desc'
      }
    });
    logs.value = res.data.content;
    total.value = res.data.totalElements;
  } catch (error) {
    ElMessage.error('获取审计日志失败');
  } finally {
    loading.value = false;
  }
};

const formatDateTime = (val) => {
  if (!val) return '-';
  const d = new Date(val);
  return d.toLocaleString();
};

const getLatencyType = (ms) => {
  if (ms > 5000) return 'danger';
  if (ms > 2000) return 'warning';
  return 'info';
};

const handleExport = () => {
  const headers = ['时间', '接口', '方法', '模型', 'Tokens', '耗时(ms)', '状态', 'IP'];
  const rows = logs.value.map(l => [
    formatDateTime(l.createdAt),
    l.endpoint,
    l.method,
    l.model || '-',
    l.totalTokens || 0,
    l.responseTime,
    l.success ? '成功' : '失败',
    l.ipAddress
  ]);

  const csvContent = "\ufeff" + [headers, ...rows].map(e => e.join(",")).join("\n");
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.setAttribute("href", url);
  link.setAttribute("download", `ORIN_Audit_Report_${new Date().getTime()}.csv`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
};

onMounted(() => {
  fetchLogs();
  
  // 监听全局刷新事件
  window.addEventListener('global-refresh', fetchLogs);
});

onUnmounted(() => {
  // 清理全局刷新事件监听器
  window.removeEventListener('global-refresh', fetchLogs);
});
</script>

<style scoped>
.page-container {
  padding: 0;
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.expand-content {
  padding: 20px;
  background: var(--neutral-gray-50);
}

.json-block {
  background: #fdfdfd;
  padding: 10px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  white-space: pre-wrap;
  word-break: break-all;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.font-bold {
  font-weight: 600;
}
</style>
