<template>
  <div class="distributed-lock-container">
    <PageHeader
      title="分布式锁"
      description="管理分布式锁配置和监控锁的使用状态"
      icon="Lock"
    />

    <el-tabs v-model="activeTab" class="lock-tabs">
      <!-- 锁概览 -->
      <el-tab-pane label="锁概览" name="overview">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">
                {{ lockStats.totalLocks }}
              </div>
              <div class="stat-label">
                总锁数量
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">
                {{ lockStats.activeLocks }}
              </div>
              <div class="stat-label">
                当前持有
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">
                {{ lockStats.waiting }}
              </div>
              <div class="stat-label">
                等待队列
              </div>
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card class="stat-card">
              <div class="stat-value">
                {{ lockStats.released }}
              </div>
              <div class="stat-label">
                今日释放
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-card style="margin-top: 20px;">
          <template #header>
            <span>活跃锁列表</span>
          </template>
          <el-table v-loading="loading" :data="activeLocks">
            <el-table-column prop="name" label="锁名称" />
            <el-table-column
              prop="key"
              label="锁 Key"
              min-width="200"
              show-overflow-tooltip
            />
            <el-table-column prop="owner" label="持有者" width="120" />
            <el-table-column prop="acquireTime" label="获取时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.acquireTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="expireTime" label="过期时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.expireTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="forceRelease(row)"
                >
                  强制释放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 锁配置 -->
      <el-tab-pane label="锁配置" name="config" :lazy="true">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>分布式锁配置</span>
              <el-button type="primary" size="small" @click="openConfigDialog()">
                <el-icon><Plus /></el-icon>
                添加配置
              </el-button>
            </div>
          </template>

          <el-table v-loading="configLoading" :data="lockConfigs">
            <el-table-column prop="name" label="锁名称" width="150" />
            <el-table-column
              prop="keyPattern"
              label="Key 模式"
              min-width="200"
              show-overflow-tooltip
            />
            <el-table-column prop="timeout" label="默认超时" width="100" />
            <el-table-column prop="retryTimes" label="重试次数" width="100" />
            <el-table-column prop="retryInterval" label="重试间隔" width="100" />
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch v-model="row.enabled" @change="toggleConfig(row)" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="editConfig(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="deleteConfig(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 配置对话框 -->
    <el-dialog v-model="configDialogVisible" :title="isEdit ? '编辑锁配置' : '添加锁配置'" width="600px">
      <el-form :model="configForm" label-width="120px">
        <el-form-item label="锁名称" required>
          <el-input v-model="configForm.name" placeholder="如: 知识库更新锁" />
        </el-form-item>
        <el-form-item label="Key 模式" required>
          <el-input v-model="configForm.keyPattern" placeholder="lock:knowledge:{id}" />
          <div class="form-tip">
            支持占位符，如 {id}
          </div>
        </el-form-item>
        <el-form-item label="默认超时(秒)">
          <el-input-number v-model="configForm.timeout" :min="1" :max="3600" />
        </el-form-item>
        <el-form-item label="重试次数">
          <el-input-number v-model="configForm.retryTimes" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="重试间隔(毫秒)">
          <el-input-number
            v-model="configForm.retryInterval"
            :min="10"
            :max="5000"
            :step="10"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="configForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" @click="saveConfig">
          保存
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

const activeTab = ref('overview')
const loading = ref(false)
const configLoading = ref(false)

// 锁统计
const lockStats = ref({
  totalLocks: 15,
  activeLocks: 3,
  waiting: 5,
  released: 128
})

// 活跃锁列表
const activeLocks = ref([])

// 锁配置
const lockConfigs = ref([])
const configDialogVisible = ref(false)
const isEdit = ref(false)
const configForm = reactive({
  id: null,
  name: '',
  keyPattern: '',
  timeout: 30,
  retryTimes: 3,
  retryInterval: 100,
  enabled: true
})

// 加载活跃锁
const loadActiveLocks = async () => {
  loading.value = true
  try {
    const res = await request.get('/system/distributed-lock/active')
    activeLocks.value = res || []
  } catch (e) {
    console.error('加载活跃锁失败:', e)
  } finally {
    loading.value = false
  }
}

// 加载锁配置
const loadLockConfigs = async () => {
  configLoading.value = true
  try {
    const res = await request.get('/system/distributed-lock/configs')
    lockConfigs.value = res || []
  } catch (e) {
    console.error('加载锁配置失败:', e)
  } finally {
    configLoading.value = false
  }
}

// 强制释放
const forceRelease = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要强制释放锁 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await request.delete(`/system/distributed-lock/${row.id}/force-release`)
    ElMessage.success('锁已强制释放')
    loadActiveLocks()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('释放失败: ' + (e.message || e))
  }
}

// 打开配置对话框
const openConfigDialog = () => {
  isEdit.value = false
  Object.assign(configForm, { id: null, name: '', keyPattern: '', timeout: 30, retryTimes: 3, retryInterval: 100, enabled: true })
  configDialogVisible.value = true
}

// 编辑配置
const editConfig = (row) => {
  isEdit.value = true
  Object.assign(configForm, row)
  configDialogVisible.value = true
}

// 保存配置
const saveConfig = async () => {
  try {
    if (isEdit.value) {
      await request.put(`/system/distributed-lock/configs/${configForm.id}`, configForm)
      ElMessage.success('配置已更新')
    } else {
      await request.post('/system/distributed-lock/configs', configForm)
      ElMessage.success('配置已添加')
    }
    configDialogVisible.value = false
    loadLockConfigs()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  }
}

// 删除配置
const deleteConfig = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除配置 "${row.name}" 吗?`, '提示', { type: 'warning' })
    await request.delete(`/system/distributed-lock/configs/${row.id}`)
    ElMessage.success('配置已删除')
    loadLockConfigs()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败: ' + (e.message || e))
  }
}

// 切换配置状态
const toggleConfig = async (row) => {
  try {
    await request.patch(`/system/distributed-lock/configs/${row.id}`, { enabled: row.enabled })
    ElMessage.success(row.enabled ? '配置已启用' : '配置已禁用')
  } catch (e) {
    row.enabled = !row.enabled
    ElMessage.error('操作失败: ' + (e.message || e))
  }
}

const formatDate = (dateStr) => {
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadActiveLocks()
  loadLockConfigs()
})
</script>

<style scoped>
.distributed-lock-container {
  padding: 20px;
}

.lock-tabs {
  margin-top: 20px;
}

.stat-card {
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--neutral-gray-900);
}

.stat-label {
  font-size: 13px;
  color: var(--neutral-gray-500);
  margin-top: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
}
</style>
