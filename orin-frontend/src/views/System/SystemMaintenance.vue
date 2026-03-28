<template>
  <div class="system-maintenance-container">
    <PageHeader
      title="系统维护"
      description="系统备份、升级、日志归档、缓存清理等运维操作"
      icon="Tools"
    />

    <el-row :gutter="20">
      <!-- 左侧操作面板 -->
      <el-col :span="8">
        <el-card class="operations-card">
          <template #header>
            <span>运维操作</span>
          </template>

          <div class="operation-list">
            <!-- 备份与恢复 -->
            <div class="operation-item" @click="openBackupDialog">
              <div class="operation-icon backup">
                <el-icon size="24">
                  <Folder />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>数据备份</h4>
                <p>备份数据库和配置文件</p>
              </div>
            </div>

            <div class="operation-item" @click="openRestoreDialog">
              <div class="operation-icon restore">
                <el-icon size="24">
                  <Upload />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>数据恢复</h4>
                <p>从备份文件恢复数据</p>
              </div>
            </div>

            <div class="operation-item" @click="openUpgradeDialog">
              <div class="operation-icon upgrade">
                <el-icon size="24">
                  <UploadFilled />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>系统升级</h4>
                <p>检查并安装系统更新</p>
              </div>
            </div>

            <div class="operation-item" @click="openLogArchiveDialog">
              <div class="operation-icon log">
                <el-icon size="24">
                  <Document />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>日志归档</h4>
                <p>归档或清理历史日志</p>
              </div>
            </div>

            <div class="operation-item" @click="openCacheDialog">
              <div class="operation-icon cache">
                <el-icon size="24">
                  <Delete />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>缓存清理</h4>
                <p>清理系统缓存和临时文件</p>
              </div>
            </div>

            <div class="operation-item" @click="openHealthCheck">
              <div class="operation-icon health">
                <el-icon size="24">
                  <CircleCheck />
                </el-icon>
              </div>
              <div class="operation-info">
                <h4>健康检查</h4>
                <p>检查系统服务状态</p>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧状态面板 -->
      <el-col :span="16">
        <!-- 系统信息 -->
        <el-card class="info-card">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
              <el-button size="small" @click="refreshSystemInfo">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="系统版本">
              {{ systemInfo.version }}
            </el-descriptions-item>
            <el-descriptions-item label="运行时间">
              {{ systemInfo.uptime }}
            </el-descriptions-item>
            <el-descriptions-item label="数据库版本">
              {{ systemInfo.dbVersion }}
            </el-descriptions-item>
            <el-descriptions-item label="最后备份">
              {{ systemInfo.lastBackup || '从未备份' }}
            </el-descriptions-item>
            <el-descriptions-item label="CPU 使用率">
              <el-progress :percentage="systemInfo.cpuUsage" :color="getProgressColor(systemInfo.cpuUsage)" />
            </el-descriptions-item>
            <el-descriptions-item label="内存使用率">
              <el-progress :percentage="systemInfo.memoryUsage" :color="getProgressColor(systemInfo.memoryUsage)" />
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 运维日志 -->
        <el-card class="log-card" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>最近运维操作</span>
            </div>
          </template>

          <el-table v-loading="logsLoading" :data="maintenanceLogs" max-height="300">
            <el-table-column prop="operation" label="操作" width="120" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'warning'" size="small">
                  {{ row.status === 'success' ? '成功' : row.status === 'failed' ? '失败' : '进行中' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="timestamp" label="时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.timestamp) }}
              </template>
            </el-table-column>
            <el-table-column prop="message" label="详情" show-overflow-tooltip />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 备份对话框 -->
    <el-dialog v-model="backupDialogVisible" title="数据备份" width="500px">
      <el-form :model="backupForm" label-width="100px">
        <el-form-item label="备份类型">
          <el-radio-group v-model="backupForm.type">
            <el-radio value="full">
              完整备份
            </el-radio>
            <el-radio value="incremental">
              增量备份
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备份名称">
          <el-input v-model="backupForm.name" placeholder="自动生成" />
        </el-form-item>
        <el-form-item label="包含附件">
          <el-switch v-model="backupForm.includeAttachments" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="backupDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="backingUp" @click="executeBackup">
          开始备份
        </el-button>
      </template>
    </el-dialog>

    <!-- 恢复对话框 -->
    <el-dialog v-model="restoreDialogVisible" title="数据恢复" width="500px">
      <el-alert type="warning" :closable="false" show-icon>
        数据恢复将覆盖当前数据，请谨慎操作！
      </el-alert>
      <el-form :model="restoreForm" label-width="100px" style="margin-top: 20px;">
        <el-form-item label="选择备份">
          <el-select v-model="restoreForm.backupId" placeholder="请选择备份文件" style="width: 100%;">
            <el-option
              v-for="b in backupList"
              :key="b.id"
              :label="b.name + ' (' + b.date + ')'"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="恢复模式">
          <el-radio-group v-model="restoreForm.mode">
            <el-radio value="override">
              覆盖恢复
            </el-radio>
            <el-radio value="merge">
              合并恢复
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="restoreDialogVisible = false">
          取消
        </el-button>
        <el-button type="danger" :loading="restoring" @click="executeRestore">
          确认恢复
        </el-button>
      </template>
    </el-dialog>

    <!-- 升级对话框 -->
    <el-dialog v-model="upgradeDialogVisible" title="系统升级" width="500px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="当前版本">
          {{ systemInfo.version }}
        </el-descriptions-item>
        <el-descriptions-item label="最新版本">
          {{ upgradeInfo.latestVersion }}
        </el-descriptions-item>
        <el-descriptions-item label="更新内容">
          {{ upgradeInfo.releaseNotes || '暂无' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="upgradeDialogVisible = false">
          取消
        </el-button>
        <el-button
          v-if="upgradeInfo.hasUpdate"
          type="primary"
          :loading="upgrading"
          @click="executeUpgrade"
        >
          立即升级
        </el-button>
        <el-button v-else type="success" disabled>
          已是最新版本
        </el-button>
      </template>
    </el-dialog>

    <!-- 日志归档对话框 -->
    <el-dialog v-model="logArchiveDialogVisible" title="日志归档" width="500px">
      <el-form :model="logArchiveForm" label-width="100px">
        <el-form-item label="日志类型">
          <el-checkbox-group v-model="logArchiveForm.types">
            <el-checkbox label="system">
              系统日志
            </el-checkbox>
            <el-checkbox label="access">
              访问日志
            </el-checkbox>
            <el-checkbox label="error">
              错误日志
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="logArchiveForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item label="操作">
          <el-radio-group v-model="logArchiveForm.action">
            <el-radio value="archive">
              归档压缩
            </el-radio>
            <el-radio value="delete">
              直接删除
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="logArchiveDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="archiving" @click="executeArchive">
          执行
        </el-button>
      </template>
    </el-dialog>

    <!-- 缓存清理对话框 -->
    <el-dialog v-model="cacheDialogVisible" title="缓存清理" width="500px">
      <el-form :model="cacheForm" label-width="100px">
        <el-form-item label="清理范围">
          <el-checkbox-group v-model="cacheForm.types">
            <el-checkbox label="query">
              查询缓存
            </el-checkbox>
            <el-checkbox label="session">
              会话缓存
            </el-checkbox>
            <el-checkbox label="temp">
              临时文件
            </el-checkbox>
            <el-checkbox label="token">
              Token 缓存
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cacheDialogVisible = false">
          取消
        </el-button>
        <el-button type="danger" :loading="cleaning" @click="executeClean">
          清理缓存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

// 系统信息
const systemInfo = ref({
  version: '1.0.0',
  uptime: '15天 3小时',
  dbVersion: 'PostgreSQL 15.2',
  lastBackup: '2024-01-15 10:30',
  cpuUsage: 45,
  memoryUsage: 62
})

// 运维日志
const logsLoading = ref(false)
const maintenanceLogs = ref([
  { operation: '数据备份', status: 'success', operator: 'admin', timestamp: '2024-01-15T10:30:00', message: '备份完成，文件大小: 256MB' },
  { operation: '缓存清理', status: 'success', operator: 'admin', timestamp: '2024-01-14T15:20:00', message: '清理缓存成功，释放空间: 128MB' },
  { operation: '系统升级', status: 'success', operator: 'admin', timestamp: '2024-01-10T09:00:00', message: '升级到 v1.0.0 完成' }
])

// 升级信息
const upgradeInfo = ref({
  latestVersion: '1.0.0',
  hasUpdate: false,
  releaseNotes: ''
})

// 对话框状态
const backupDialogVisible = ref(false)
const restoreDialogVisible = ref(false)
const upgradeDialogVisible = ref(false)
const logArchiveDialogVisible = ref(false)
const cacheDialogVisible = ref(false)

// 表单数据
const backupForm = reactive({
  type: 'full',
  name: '',
  includeAttachments: true
})
const backingUp = ref(false)
const upgrading = ref(false)

const restoreForm = reactive({
  backupId: '',
  mode: 'override'
})
const restoring = ref(false)

const logArchiveForm = reactive({
  types: ['system'],
  dateRange: [],
  action: 'archive'
})
const archiving = ref(false)

const cacheForm = reactive({
  types: ['query']
})
const cleaning = ref(false)

// 备份列表
const backupList = ref([
  { id: 1, name: 'full-backup-20240115', date: '2024-01-15' },
  { id: 2, name: 'full-backup-20240110', date: '2024-01-10' }
])

// 刷新系统信息
const refreshSystemInfo = async () => {
  try {
    const res = await request.get('/system/maintenance/info')
    if (res) {
      systemInfo.value = { ...systemInfo.value, ...res }
    }
    ElMessage.success('已刷新')
  } catch (e) {
    console.error('刷新失败:', e)
  }
}

// 加载运维日志
const loadMaintenanceLogs = async () => {
  logsLoading.value = true
  try {
    const res = await request.get('/system/maintenance/logs')
    maintenanceLogs.value = res || []
  } catch (e) {
    console.error('加载日志失败:', e)
  } finally {
    logsLoading.value = false
  }
}

// 打开备份对话框
const openBackupDialog = () => {
  backupDialogVisible.value = true
}

// 执行备份
const executeBackup = async () => {
  backingUp.value = true
  try {
    await request.post('/system/maintenance/backup', backupForm)
    ElMessage.success('备份已开始，请稍后查看结果')
    backupDialogVisible.value = false
    loadMaintenanceLogs()
  } catch (e) {
    ElMessage.error('备份失败: ' + (e.message || e))
  } finally {
    backingUp.value = false
  }
}

// 打开恢复对话框
const openRestoreDialog = async () => {
  restoreDialogVisible.value = true
}

// 执行恢复
const executeRestore = async () => {
  if (!restoreForm.backupId) {
    ElMessage.warning('请选择备份文件')
    return
  }

  try {
    await ElMessageBox.confirm('此操作将覆盖当前数据，是否继续?', '警告', { type: 'warning' })
    restoring.value = true
    await request.post('/system/maintenance/restore', restoreForm)
    ElMessage.success('恢复成功')
    restoreDialogVisible.value = false
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('恢复失败: ' + (e.message || e))
    }
  } finally {
    restoring.value = false
  }
}

// 打开升级对话框
const openUpgradeDialog = async () => {
  upgradeDialogVisible.value = true
}

// 执行升级
const executeUpgrade = async () => {
  upgrading.value = true
  try {
    await request.post('/system/maintenance/upgrade')
    ElMessage.success('系统升级完成，请刷新页面')
    upgradeDialogVisible.value = false
    refreshSystemInfo()
  } catch (e) {
    ElMessage.error('升级失败: ' + (e.message || e))
  } finally {
    upgrading.value = false
  }
}

// 打开日志归档对话框
const openLogArchiveDialog = () => {
  logArchiveDialogVisible.value = true
}

// 执行归档
const executeArchive = async () => {
  if (logArchiveForm.types.length === 0) {
    ElMessage.warning('请选择日志类型')
    return
  }

  archiving.value = true
  try {
    await request.post('/system/maintenance/log-archive', logArchiveForm)
    ElMessage.success('日志归档完成')
    logArchiveDialogVisible.value = false
  } catch (e) {
    ElMessage.error('归档失败: ' + (e.message || e))
  } finally {
    archiving.value = false
  }
}

// 打开缓存清理对话框
const openCacheDialog = () => {
  cacheDialogVisible.value = true
}

// 执行清理
const executeClean = async () => {
  if (cacheForm.types.length === 0) {
    ElMessage.warning('请选择清理类型')
    return
  }

  cleaning.value = true
  try {
    await request.post('/system/maintenance/cache-clean', cacheForm)
    ElMessage.success('缓存清理完成')
    cacheDialogVisible.value = false
  } catch (e) {
    ElMessage.error('清理失败: ' + (e.message || e))
  } finally {
    cleaning.value = false
  }
}

// 健康检查
const openHealthCheck = async () => {
  try {
    const res = await request.get('/system/maintenance/health')
    if (res.status === 'healthy') {
      ElMessage.success('系统健康检查通过')
    } else {
      ElMessage.warning(`发现 ${res.issues?.length || 0} 个问题`)
    }
  } catch (e) {
    ElMessage.error('健康检查失败: ' + (e.message || e))
  }
}

const getProgressColor = (value) => {
  if (value < 60) return '#67c23a'
  if (value < 80) return '#e6a23c'
  return '#f56c6c'
}

const formatDate = (dateStr) => {
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  refreshSystemInfo()
  loadMaintenanceLogs()
})
</script>

<style scoped>
.system-maintenance-container {
  padding: 20px;
}

.operations-card {
  position: sticky;
  top: 20px;
}

.operation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.operation-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.operation-item:hover {
  background-color: #f5f7fa;
  border-color: #409eff;
}

.operation-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.operation-icon.backup { background: #409eff; }
.operation-icon.restore { background: #67c23a; }
.operation-icon.upgrade { background: #e6a23c; }
.operation-icon.log { background: #909399; }
.operation-icon.cache { background: #f56c6c; }
.operation-icon.health { background: #67c23a; }

.operation-info h4 {
  margin: 0;
  font-size: 14px;
}

.operation-info p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #909399;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
