<template>
  <div class="page-container">
    <PageHeader 
      title="AI 调用日志" 
      description="追踪智能体与模型的调用轨迹、Token 消耗及响应性能"
      icon="Cpu"
    >
      <template #actions>
        <el-button :icon="Download" @click="handleExport" :disabled="logs.length === 0">导出调用报告</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="table-card premium-card">
      <ResizableTable :data="logs" v-loading="loading">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-content">
              <el-descriptions title="详细调用参数" :column="2" border>
                <el-descriptions-item label="类型">{{ row.providerType }}</el-descriptions-item>
                <el-descriptions-item label="端点 (Endpoint)">{{ row.endpoint }}</el-descriptions-item>
                <el-descriptions-item label="Conversation ID">{{ row.conversationId || '-' }}</el-descriptions-item>
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
        
        <el-table-column prop="model" label="模型" width="180">
          <template #default="{ row }">
            <el-tag size="small" v-if="row.model">{{ row.model }}</el-tag>
            <el-tag size="small" type="info" v-else>{{ row.providerType }}</el-tag>
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
            <el-tag v-bind="getLatencyTagConfig(row.responseTime)" size="small">
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
      </ResizableTable>

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
import { Download, Cpu } from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import ResizableTable from '@/components/ResizableTable.vue';
import request from '@/utils/request';
import { ElMessage } from 'element-plus';

const loading = ref(false);
const logs = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(15);
const logType = ref('BUSINESS'); // Hardcoded to Business

const fetchLogs = async () => {
  loading.value = true;
  try {
    const res = await request.get('/audit/logs', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value,
        sortBy: 'createdAt',
        direction: 'desc',
        type: logType.value
      }
    });
    logs.value = res.content;
    total.value = res.totalElements;
  } catch (error) {
    ElMessage.error('获取调用日志失败');
  } finally {
    loading.value = false;
  }
};

const formatDateTime = (val) => {
  if (!val) return '-';
  
  // Handle Array format [yyyy, MM, dd, HH, mm, ss]
  if (Array.isArray(val)) {
    return new Date(val[0], val[1] - 1, val[2], val[3] || 0, val[4] || 0, val[5] || 0).toLocaleString();
  }

  // Handle space separator for Safari compatibility
  const dateStr = String(val).replace(' ', 'T');
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return '-';
  return d.toLocaleString();
};

const getLatencyTagConfig = (ms) => {
  const val = Number(ms) || 0;
  // Green scale: Darker as time increases (indicating magnitude/effort)
  
  // < 500ms: Lightest Green (Default Success Light)
  if (val < 500) return { type: 'success', effect: 'light' };
  
  // 500ms - 2s: Light Green
  if (val < 2000) return { color: '#e1f3d8', style: { color: '#67c23a', border: '1px solid #c2e7b0' } };
  
  // 2s - 5s: Medium Green
  if (val < 5000) return { color: '#95d475', style: { color: 'white', border: 'none' } };
  
  // 5s - 10s: Standard Green
  if (val < 10000) return { color: '#67c23a', style: { color: 'white', border: 'none' } };
  
  // > 10s: Dark Green
  return { color: '#3c8c25', style: { color: 'white', border: 'none' } };
};

const handleExport = async () => {
  if (total.value > 10000) {
    ElMessage.warning('日志数量过多，仅导出最近 10,000 条');
  }
  
  const exportLoading = ElMessage({
    message: '正在生成导出数据...',
    type: 'info',
    duration: 0
  });

  try {
    // Fetch all logs (up to 10000)
    const res = await request.get('/audit/logs', {
      params: {
        page: 0,
        size: 10000, 
        sortBy: 'createdAt',
        direction: 'desc',
        type: logType.value 
      }
    });
    
    const allLogs = res.content || [];
    
    // Helper to escape CSV fields
    const escapeCsv = (field) => {
      if (field === null || field === undefined) return '';
      const stringField = String(field);
      // If contains comma, quote, or newline, wrap in quotes and escape internal quotes
      if (stringField.includes(',') || stringField.includes('"') || stringField.includes('\n')) {
        return `"${stringField.replace(/"/g, '""')}"`;
      }
      return stringField;
    };

    const headers = ['时间', '类型', '接口', '方法', '模型', 'Tokens', '耗时(ms)', '状态', 'IP', '错误信息'];
    
    const rows = allLogs.map(l => [
      `\t${formatDateTime(l.createdAt)}`, // Prepend tab to force text format in Excel to avoid ####
      l.providerType,
      l.endpoint,
      l.method,
      l.model || '-',
      l.totalTokens || 0,
      l.responseTime,
      l.success ? '成功' : '失败',
      l.ipAddress,
      l.errorMessage || ''
    ].map(escapeCsv));

    const csvContent = "\ufeff" + [headers.map(escapeCsv), ...rows].map(e => e.join(",")).join("\n");
    
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.setAttribute("href", url);
    link.setAttribute("download", `ORIN_Audit_Report_${logType.value}_${new Date().getTime()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    
    ElMessage.success(`成功导出 ${allLogs.length} 条记录`);
  } catch (error) {
    console.error(error);
    ElMessage.error('导出失败');
  } finally {
    exportLoading.close();
  }
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
