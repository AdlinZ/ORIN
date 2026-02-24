<template>
  <div class="page-container">
    <PageHeader 
      title="日志与审计中心" 
      description="统一追踪所有系统事件，管理审计日志的保留策略、记录级别及存储空间优化"
      icon="List"
    >
      <template #actions>
        <el-button v-if="activeTab === 'logs'" :icon="Download" @click="handleExport" :disabled="logs.length === 0">导出审计报告</el-button>
        <el-button v-if="activeTab !== 'logs'" type="primary" :loading="saving" :icon="Check" @click="saveAll">
          应用并保存配置
        </el-button>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="config-tabs">
      <!-- 实时审计日志 Tab -->
      <el-tab-pane label="实时审计记录" name="logs">
        <el-card shadow="never" class="table-card premium-card">
          <ResizableTable :data="logs" v-loading="loading">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-content">
                  <el-descriptions title="详细请求参数" :column="2" border>
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
                      <JsonViewer :data="row.requestParams" title="Request Payload" />
                    </el-descriptions-item>
                    <el-descriptions-item label="响应内容 (JSON)" :span="2">
                      <JsonViewer :data="row.responseContent || '{}'" title="Response Payload" />
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
            
            <el-table-column prop="model" label="类型/模型" width="160">
              <template #default="{ row }">
                <el-tag size="small" v-if="row.model">{{ row.model }}</el-tag>
                <el-tag size="small" type="info" v-else>{{ row.providerType || 'System' }}</el-tag>
              </template>
            </el-table-column>

            <el-table-column prop="endpoint" label="动作/接口" min-width="200" show-overflow-tooltip />

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
      </el-tab-pane>

      <!-- 审计日志配置 Tab -->
      <el-tab-pane label="审计存储配置" name="audit">
        <!-- Stats Section -->
        <el-row :gutter="20" class="margin-bottom-xl" style="padding: 0 4px;">
          <el-col :span="8">
            <el-card shadow="never" class="stat-mini-card">
              <div class="stat-content">
                <div class="label text-secondary">已记录日志总量</div>
                <div class="value">{{ stats.totalCount || 0 }} <small>条</small></div>
              </div>
              <el-icon class="icon" color="var(--primary-color)"><Document /></el-icon>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-mini-card">
              <div class="stat-content">
                <div class="label text-secondary">预估占用空间</div>
                <div class="value">{{ stats.estimatedSizeMb || 0 }} <small>MB</small></div>
              </div>
              <el-icon class="icon" color="var(--success-color)"><Coin /></el-icon>
            </el-card>
          </el-col>
          <el-col :span="8">
            <el-card shadow="never" class="stat-mini-card">
              <div class="stat-content">
                <div class="label text-secondary">最早日志记录</div>
                <div class="value">{{ formatSimpleDate(stats.oldestLog) }}</div>
              </div>
              <el-icon class="icon" color="var(--warning-color)"><Calendar /></el-icon>
            </el-card>
          </el-col>
        </el-row>

        <el-row :gutter="24">
          <!-- Configuration Forms -->
          <el-col :lg="16">
            <el-card class="premium-card margin-bottom-lg">
              <template #header>
                <div class="card-header">
                  <el-icon><Filter /></el-icon>
                  <span>审计拦截策略</span>
                </div>
              </template>
              
              <el-form label-position="left" label-width="140px" class="config-form">
                <el-form-item label="全局审计开关">
                  <div class="flex-between w-100">
                    <el-switch 
                      v-model="config.auditEnabled" 
                      style="--el-switch-on-color: var(--success-color);"
                    />
                    <el-tag :type="config.auditEnabled ? 'success' : 'info'" size="small">
                      {{ config.auditEnabled ? '运行中' : '已停止' }}
                    </el-tag>
                  </div>
                  <p class="form-tip">控制是否记录 API 调用轨迹，关闭后将停止所有审计数据落库。</p>
                </el-form-item>

                <el-divider border-style="dashed" />

                <el-form-item label="日志分级控制">
                  <el-select v-model="config.logLevel" placeholder="选择级别" class="w-100">
                    <el-option label="全部记录 (ALL) - 记录所有请求响应" value="ALL" />
                    <el-option label="标准审计 (AUDIT_ONLY) - 仅记录关键业务" value="AUDIT_ONLY" />
                    <el-option label="错误追溯 (ERROR_ONLY) - 仅记录异常请求" value="ERROR_ONLY" />
                  </el-select>
                  <p class="form-tip">高负载环境下建议设置为 ERROR_ONLY 以节省空间。</p>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card class="premium-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Timer /></el-icon>
                  <span>数据生命周期 (Retention)</span>
                </div>
              </template>
              <el-form label-position="left" label-width="140px">
                <el-form-item label="自动清理周期">
                   <el-input-number v-model="config.retentionDays" :min="1" :max="365" controls-position="right" />
                   <span class="unit-text">天</span>
                   <p class="form-tip">系统将自动删除超过此保留期限的历史数据。目前每天凌晨 02:00 执行。</p>
                </el-form-item>
              </el-form>
            </el-card>
          </el-col>

          <!-- Operations Side -->
          <el-col :lg="8">
            <el-card class="premium-card maintenance-card">
              <template #header>
                <div class="card-header">
                  <el-icon><Connection /></el-icon>
                  <span>运维操作</span>
                </div>
              </template>
              
              <div class="op-item">
                <div class="op-info">
                  <div class="op-title">即时存储优化</div>
                  <div class="op-desc">立即删除指定日期前的历史日志，释放数据库碎片。</div>
                  <div class="op-hint" v-if="stats.oldestLog">
                    <el-icon><InfoFilled /></el-icon>
                    <span>最早日志: {{ formatSimpleDate(stats.oldestLog) }}</span>
                  </div>
                </div>
                <div class="op-action">
                  <el-popover placement="top" :width="280" trigger="click" v-model:visible="cleanupPopoverVisible">
                    <template #reference>
                      <el-button type="danger" plain class="w-100" :icon="Delete">手动清理</el-button>
                    </template>
                    <div class="cleanup-popover">
                       <p class="cleanup-title">清理历史日志</p>
                       <el-form label-width="80px" size="small">
                         <el-form-item label="清理范围">
                           <el-input-number 
                             v-model="cleanupDays" 
                             :min="0" 
                             :max="365" 
                             controls-position="right"
                             style="width: 100%"
                           />
                           <span class="unit-hint">天前</span>
                         </el-form-item>
                         <el-form-item>
                           <el-alert 
                             :title="`将删除 ${cleanupDays} 天前的所有日志`" 
                             type="warning" 
                             :closable="false"
                             show-icon
                           />
                         </el-form-item>
                       </el-form>
                       <div class="cleanup-actions">
                         <el-button size="small" @click="cleanupPopoverVisible = false">取消</el-button>
                         <el-button size="small" @click="handleManualCleanup" type="danger">确认清理</el-button>
                       </div>
                    </div>
                  </el-popover>
                </div>
              </div>


              <el-divider />

              <div class="op-item">
                <div class="op-info">
                  <div class="op-title">强制数据同步</div>
                  <div class="op-desc">从配置中心强制同步最新的全局配置变量。</div>
                </div>
                <div class="op-action">
                  <el-button class="w-100" @click="loadConfig" :icon="Refresh">重新加载</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- 动态日志级别管理 Tab -->
      <el-tab-pane label="日志控制台" name="loggers">
        <el-card class="premium-card">
          <template #header>
            <div class="card-header">
              <el-icon><DataLine /></el-icon>
              <span>运行时日志级别调整</span>
              <el-tag size="small" type="success" class="ml-2">无需重启</el-tag>
            </div>
            <div>
              <el-button :icon="Refresh" @click="loadLoggers" size="small">刷新</el-button>
              <el-button :icon="RefreshLeft" @click="resetAllLoggers" size="small" type="warning">全部重置</el-button>
            </div>
          </template>

          <div class="logger-manager">
            <el-table border :data="loggers" stripe v-loading="loadingLoggers">
              <el-table-column label="Logger 名称" prop="name" min-width="200">
                <template #default="{ row }">
                  <code class="logger-name">{{ row.name }}</code>
                </template>
              </el-table-column>
              <el-table-column label="当前级别" width="120">
                <template #default="{ row }">
                  <el-tag :type="getLevelTagType(row.level)" size="small">
                    {{ row.level || 'INHERITED' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="设置级别" width="180">
                <template #default="{ row }">
                  <el-select 
                    v-model="row.newLevel" 
                    size="small" 
                    placeholder="选择级别"
                    @change="handleLevelChange(row)"
                  >
                    <el-option label="继承默认" value="NULL" />
                    <el-option 
                      v-for="level in supportedLevels" 
                      :key="level" 
                      :label="level" 
                      :value="level" 
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button 
                    size="small" 
                    text 
                    type="danger" 
                    @click="resetLogger(row)"
                  >
                    重置
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="logger-tips">
              <el-alert type="info" :closable="false" show-icon>
                <template #title>
                  <span style="font-weight: 600">日志级别说明</span>
                </template>
                <ul style="margin: 8px 0; padding-left: 20px; line-height: 1.8">
                  <li><strong>TRACE</strong>: 最详细的跟踪信息</li>
                  <li><strong>DEBUG</strong>: 调试信息 (开发环境推荐)</li>
                  <li><strong>INFO</strong>: 一般信息 (生产环境推荐)</li>
                  <li><strong>WARN</strong>: 警告信息</li>
                  <li><strong>ERROR</strong>: 错误信息</li>
                  <li><strong>OFF</strong>: 关闭日志输出</li>
                </ul>
              </el-alert>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive } from 'vue';
import { 
  Setting, Check, Document, Coin, Calendar, 
  Filter, Timer, Connection, Delete, Refresh,
  DataLine, RefreshLeft, InfoFilled, Download, List 
} from '@element-plus/icons-vue';
import PageHeader from '@/components/PageHeader.vue';
import ResizableTable from '@/components/ResizableTable.vue';
import JsonViewer from '@/components/JsonViewer.vue';
import request from '@/utils/request';
import { ElMessage, ElMessageBox } from 'element-plus';

const activeTab = ref('logs');
const saving = ref(false);
const loading = ref(false);

const logs = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(15);
const logType = ref('ALL'); // Default to ALL

// --- Stats and Config ---
const loadingLoggers = ref(false);
const cleanupPopoverVisible = ref(false);
const cleanupDays = ref(30);

const config = reactive({
  auditEnabled: true,
  logLevel: 'ALL',
  retentionDays: 30
});

const stats = ref({
  totalCount: 0,
  estimatedSizeMb: 0,
  oldestLog: null
});

const loggers = ref([]);
const supportedLevels = ref(['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'OFF']);

const CONFIG_KEYS = {
    AUDIT_ENABLED: 'log.audit.enabled',
    LOG_LEVEL: 'log.level',
    RETENTION: 'log.retention.days'
};

const formatSimpleDate = (val) => {
  if (!val) return '无记录';
  const date = new Date(val);
  return `${date.getUTCFullYear()}-${(date.getUTCMonth()+1).toString().padStart(2, '0')}-${date.getUTCDate().toString().padStart(2, '0')}`;
};

const formatDateTime = (val) => {
  if (!val) return '-';
  const d = new Date(val);
  return d.toLocaleString();
};

const getLatencyTagConfig = (ms) => {
  const val = Number(ms) || 0;
  if (val < 500) return { type: 'success', effect: 'light' };
  if (val < 2000) return { color: '#e1f3d8', style: { color: '#67c23a', border: '1px solid #c2e7b0' } };
  if (val < 5000) return { color: '#95d475', style: { color: 'white', border: 'none' } };
  if (val < 10000) return { color: '#67c23a', style: { color: 'white', border: 'none' } };
  return { color: '#3c8c25', style: { color: 'white', border: 'none' } };
};

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
    ElMessage.error('获取系统日志失败');
  } finally {
    loading.value = false;
  }
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
    
    const escapeCsv = (field) => {
      if (field === null || field === undefined) return '';
      const stringField = String(field);
      if (stringField.includes(',') || stringField.includes('"') || stringField.includes('\n')) {
        return `"${stringField.replace(/"/g, '""')}"`;
      }
      return stringField;
    };

    const headers = ['时间', '类型', '接口', '方法', '模型', '耗时(ms)', '状态', 'IP', '错误信息'];
    
    const rows = allLogs.map(l => [
      `\t${formatDateTime(l.createdAt)}`, 
      l.providerType,
      l.endpoint,
      l.method,
      l.model || '-',
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

const fetchStats = async () => {
  try {
    const res = await request.get('/system/log-config/stats');
    stats.value = res;
  } catch (e) { console.error(e); }
};

const loadConfig = async () => {
    try {
        const res = await request.get('/system/log-config');
        const data = res;
        if (Array.isArray(data)) {
            data.forEach(item => {
                if (item.configKey === CONFIG_KEYS.AUDIT_ENABLED) {
                    config.auditEnabled = item.configValue === 'true';
                } else if (item.configKey === CONFIG_KEYS.LOG_LEVEL) {
                    config.logLevel = item.configValue;
                } else if (item.configKey === CONFIG_KEYS.RETENTION) {
                    config.retentionDays = parseInt(item.configValue, 10);
                }
            });
        }
        await fetchStats();
    } catch (error) {
        ElMessage.error('加载系统配置失败');
    }
};

const saveAll = async () => {
    saving.value = true;
    try {
        await Promise.all([
           request.put(`/system/log-config/${CONFIG_KEYS.AUDIT_ENABLED}`, { value: String(config.auditEnabled) }),
           request.put(`/system/log-config/${CONFIG_KEYS.LOG_LEVEL}`, { value: config.logLevel }),
           request.put(`/system/log-config/${CONFIG_KEYS.RETENTION}`, { value: String(config.retentionDays) })
        ]);
        ElMessage.success('配置已生效并保存至数据库');
        await fetchStats();
    } catch (error) {
        ElMessage.error('应用配置失败');
    } finally {
        saving.value = false;
    }
};

const handleManualCleanup = async () => {
  try {
    const res = await request.post('/system/log-config/cleanup', null, { params: { days: cleanupDays.value } });
    const result = res;
    cleanupPopoverVisible.value = false;
    
    if (result.success) {
      if (result.deletedCount === 0) {
        ElMessage.info({ message: `没有找到 ${result.days} 天前的日志记录`, duration: 3000 });
      } else {
        ElMessage.success({ message: `清理完成！已删除 ${result.deletedCount} 条日志记录（${result.days} 天前）`, duration: 5000 });
      }
    } else {
      ElMessage.warning('清理任务已启动，但未返回结果');
    }
    await fetchStats();
    fetchLogs(); // refresh logs
  } catch (e) { 
    console.error('清理失败:', e);
    ElMessage.error('清理失败: ' + (e.response?.data?.message || e.message)); 
  }
};

const loadLoggers = async () => {
  loadingLoggers.value = true;
  try {
    const res = await request.get('/system/log-config/loggers');
    loggers.value = Object.entries(res || {}).map(([name, level]) => ({
      name,
      level,
      newLevel: level
    }));
  } catch (error) {
    ElMessage.error('加载 Logger 列表失败');
  } finally {
    loadingLoggers.value = false;
  }
};

const handleLevelChange = async (row) => {
  try {
    await request.put(`/system/log-config/loggers/${row.name}`, { level: row.newLevel });
    row.level = row.newLevel;
    ElMessage.success(`Logger "${row.name}" 级别已更新为 ${row.newLevel}`);
  } catch (error) {
    ElMessage.error('更新日志级别失败');
    row.newLevel = row.level;
  }
};

const resetLogger = async (row) => {
  try {
    await request.delete(`/system/log-config/loggers/${row.name}`);
    ElMessage.success(`Logger "${row.name}" 已重置为默认级别`);
    await loadLoggers();
  } catch (error) {
    ElMessage.error('重置失败');
  }
};

const resetAllLoggers = async () => {
  try {
    await ElMessageBox.confirm('确定要将所有 Logger 重置为默认级别吗？', '重置确认', {
        confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning'
    });
    await request.post('/system/log-config/loggers/reset-all');
    ElMessage.success('所有 Logger 已重置为默认级别');
    await loadLoggers();
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('重置失败');
  }
};

const getLevelTagType = (level) => {
  const typeMap = { 'TRACE': 'info', 'DEBUG': 'primary', 'INFO': 'success', 'WARN': 'warning', 'ERROR': 'danger', 'OFF': 'info' };
  return typeMap[level] || '';
};

onMounted(() => {
  fetchLogs();
  loadConfig();
  loadLoggers();
  window.addEventListener('page-refresh', fetchLogs);
});

onUnmounted(() => {
  window.removeEventListener('page-refresh', fetchLogs);
});
</script>

<style scoped>
.page-container { padding: 0; animation: fadeIn 0.4s ease-out; }
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}

.premium-card {
  border-radius: var(--radius-xl) !important;
  border: 1px solid var(--neutral-gray-100) !important;
}

.expand-content { padding: 20px; background: var(--neutral-gray-50); }
.pagination-container { margin-top: 20px; display: flex; justify-content: flex-end; }
.font-bold { font-weight: 600; }

.stat-mini-card { border-radius: var(--radius-xl) !important; display: flex; align-items: center; position: relative; overflow: hidden; border: 1px solid var(--neutral-gray-100) !important; }
.stat-content { flex: 1; }
.stat-mini-card .label { font-size: 13px; margin-bottom: 4px; }
.stat-mini-card .value { font-size: 20px; font-weight: 700; color: var(--neutral-gray-900); }
.stat-mini-card .value small { font-size: 12px; font-weight: normal; color: var(--neutral-gray-500); margin-left: 2px; }
.stat-mini-card .icon { font-size: 32px; opacity: 0.15; position: absolute; right: -5px; bottom: -5px; transform: rotate(-15deg); }
.card-header { display: flex; align-items: center; gap: 10px; font-weight: 700; color: var(--neutral-gray-800); }
.form-tip { font-size: 12px; color: var(--neutral-gray-500); margin-top: 8px; line-height: 1.5; }
.unit-text { margin-left: 12px; font-weight: 600; color: var(--neutral-gray-700); }

.w-100 { width: 100%; }
.margin-bottom-xl { margin-bottom: 24px; }
.margin-bottom-lg { margin-bottom: 16px; }
.ml-2 { margin-left: 8px; }

.op-item { display: flex; flex-direction: column; gap: 12px; }
.op-title { font-weight: 600; font-size: 14px; margin-bottom: 4px; }
.op-desc { font-size: 12px; color: var(--neutral-gray-500); line-height: 1.4; }
.config-form :deep(.el-form-item) { margin-bottom: 24px; }
.maintenance-card :deep(.el-divider) { margin: 16px 0; }
.config-tabs { margin-top: 24px; }
.logger-manager { padding: 10px 0; }
.logger-name { font-family: 'Courier New', monospace; font-size: 12px; background: var(--neutral-gray-50); padding: 2px 6px; border-radius: 4px; color: var(--primary-color); }
.logger-tips { margin-top: 20px; }
.logger-tips ul { font-size: 13px; color: var(--neutral-gray-700); }
.premium-card :deep(.el-card__header) { display: flex; justify-content: space-between; align-items: center; }
.op-hint { display: flex; align-items: center; gap: 6px; margin-top: 8px; font-size: 12px; color: var(--primary-color); }
.op-hint .el-icon { font-size: 14px; }
.cleanup-popover { padding: 4px 0; }
.cleanup-title { font-weight: 600; font-size: 14px; margin-bottom: 12px; color: var(--neutral-gray-800); }
.cleanup-popover .el-form { margin-bottom: 12px; }
.cleanup-popover .el-form-item { margin-bottom: 12px; }
.unit-hint { margin-left: 8px; font-size: 12px; color: var(--neutral-gray-600); }
.cleanup-actions { display: flex; justify-content: flex-end; gap: 8px; padding-top: 8px; border-top: 1px solid var(--neutral-gray-100); }
</style>
