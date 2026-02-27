<template>
  <div class="alert-manager">
    <el-tabs v-model="activeTab">
      <!-- 告警规则 Tab -->
      <el-tab-pane label="告警规则" name="rules">
        <el-card class="premium-card">
          <template #header>
            <div class="card-header">
              <div>
                <el-icon><Bell /></el-icon>
                <span>告警规则配置</span>
                <el-tag size="small" type="info" class="ml-2">{{ rules.length }} 条规则</el-tag>
              </div>
              <el-button type="primary" :icon="Plus" @click="showCreateDialog">
                创建规则
              </el-button>
            </div>
          </template>

          <el-table border :data="rules" v-loading="loading" stripe>
            <el-table-column prop="ruleName" label="规则名称" min-width="150" />
            <el-table-column prop="ruleType" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getRuleTypeText(row.ruleType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="severity" label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="notificationChannels" label="通知渠道" width="150">
              <template #default="{ row }">
                <el-tag
                  v-for="channel in row.notificationChannels?.split(',')"
                  :key="channel"
                  size="small"
                  class="mr-1"
                >
                  {{ channel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  @change="toggleRule(row)"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" :icon="View" @click="viewRule(row)">
                  查看
                </el-button>
                <el-button size="small" :icon="Notification" @click="testRule(row)">
                  测试
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  :icon="Delete"
                  @click="deleteRule(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 告警历史 Tab -->
      <el-tab-pane label="告警历史" name="history">
        <el-card class="premium-card">
          <template #header>
            <div class="card-header">
              <div>
                <el-icon><Clock /></el-icon>
                <span>告警历史记录</span>
              </div>
              <div class="stats">
                <el-statistic title="活跃告警" :value="stats.activeAlerts" />
                <el-statistic title="总告警数" :value="stats.totalAlerts" class="ml-4" />
              </div>
            </div>
          </template>

          <el-table border :data="history" v-loading="loadingHistory" stripe>
            <el-table-column prop="alertMessage" label="告警消息" min-width="200" />
            <el-table-column prop="severity" label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="getSeverityType(row.severity)" size="small">
                  {{ row.severity }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="agentId" label="智能体" width="150" />
            <el-table-column prop="triggeredAt" label="触发时间" width="180">
              <template #default="{ row }">
                {{ formatTime(row.triggeredAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'RESOLVED' ? 'success' : 'warning'" size="small">
                  {{ row.status === 'RESOLVED' ? '已解决' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-if="row.status === 'TRIGGERED'"
                  size="small"
                  type="success"
                  @click="resolveAlert(row)"
                >
                  解决
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="totalHistory"
            @current-change="loadHistory"
            layout="total, prev, pager, next"
            class="mt-4"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建/编辑规则对话框 -->
    <el-dialog
      v-model="ruleDialog"
      :title="editingRule ? '编辑规则' : '创建规则'"
      width="600px"
    >
      <el-form :model="ruleForm" label-width="120px">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.ruleName" placeholder="例如: CPU 使用率过高" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="ruleForm.ruleType" placeholder="选择类型">
            <el-option label="健康检查" value="HEALTH_CHECK" />
            <el-option label="性能监控" value="PERFORMANCE" />
            <el-option label="错误率" value="ERROR_RATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件表达式">
          <el-input
            v-model="ruleForm.conditionExpr"
            placeholder="例如: cpu_usage > 80"
          />
        </el-form-item>
        <el-form-item label="阈值">
          <el-input-number v-model="ruleForm.thresholdValue" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="严重程度">
          <el-select v-model="ruleForm.severity">
            <el-option label="信息" value="INFO" />
            <el-option label="警告" value="WARNING" />
            <el-option label="错误" value="ERROR" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知渠道">
          <el-checkbox-group v-model="selectedChannels">
            <el-checkbox label="EMAIL">邮件</el-checkbox>
            <el-checkbox label="DINGTALK">钉钉</el-checkbox>
            <el-checkbox label="WECHAT">企业微信</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="接收人列表">
          <el-input
            v-model="ruleForm.recipientList"
            type="textarea"
            :rows="2"
            placeholder="多个接收人用逗号分隔"
          />
        </el-form-item>
        <el-form-item label="冷却时间">
          <el-input-number v-model="ruleForm.cooldownMinutes" :min="1" :max="60" />
          <span class="ml-2">分钟</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="saving">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import dayjs from 'dayjs'
import {
  Bell, Plus, View, Notification, Delete, Clock
} from '@element-plus/icons-vue'

const activeTab = ref('rules')
const loading = ref(false)
const loadingHistory = ref(false)
const saving = ref(false)
const rules = ref([])
const history = ref([])
const stats = ref({
  totalRules: 0,
  enabledRules: 0,
  activeAlerts: 0,
  totalAlerts: 0
})

const ruleDialog = ref(false)
const editingRule = ref(null)
const selectedChannels = ref([])
const ruleForm = ref({
  ruleName: '',
  ruleType: 'PERFORMANCE',
  conditionExpr: '',
  thresholdValue: 80,
  severity: 'WARNING',
  notificationChannels: '',
  recipientList: '',
  cooldownMinutes: 5,
  enabled: true
})

const currentPage = ref(1)
const pageSize = ref(20)
const totalHistory = ref(0)

const loadRules = async () => {
  loading.value = true
  try {
    const res = await request.get('/alerts/rules')
    rules.value = res || []
  } catch (error) {
    ElMessage.error('加载告警规则失败')
  } finally {
    loading.value = false
  }
}

const loadHistory = async () => {
  loadingHistory.value = true
  try {
    const res = await request.get('/alerts/history', {
      params: {
        page: currentPage.value - 1,
        size: pageSize.value
      }
    })
    history.value = res.content || []
    totalHistory.value = res.totalElements || 0
  } catch (error) {
    ElMessage.error('加载告警历史失败')
  } finally {
    loadingHistory.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await request.get('/alerts/stats')
    stats.value = res || {}
  } catch (error) {
    console.error('加载统计信息失败', error)
  }
}

const showCreateDialog = () => {
  editingRule.value = null
  ruleForm.value = {
    ruleName: '',
    ruleType: 'PERFORMANCE',
    conditionExpr: '',
    thresholdValue: 80,
    severity: 'WARNING',
    notificationChannels: '',
    recipientList: '',
    cooldownMinutes: 5,
    enabled: true
  }
  selectedChannels.value = []
  ruleDialog.value = true
}

const viewRule = (rule) => {
  editingRule.value = rule
  ruleForm.value = { ...rule }
  selectedChannels.value = rule.notificationChannels?.split(',') || []
  ruleDialog.value = true
}

const saveRule = async () => {
  ruleForm.value.notificationChannels = selectedChannels.value.join(',')
  
  saving.value = true
  try {
    if (editingRule.value) {
      await request.put(`/alerts/rules/${editingRule.value.id}`, ruleForm.value)
      ElMessage.success('规则更新成功')
    } else {
      await request.post('/alerts/rules', ruleForm.value)
      ElMessage.success('规则创建成功')
    }
    ruleDialog.value = false
    await loadRules()
    await loadStats()
  } catch (error) {
    ElMessage.error('保存规则失败')
  } finally {
    saving.value = false
  }
}

const toggleRule = async (rule) => {
  try {
    await request.put(`/alerts/rules/${rule.id}`, rule)
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已禁用')
  } catch (error) {
    ElMessage.error('更新规则状态失败')
    rule.enabled = !rule.enabled
  }
}

const testRule = async (rule) => {
  try {
    await request.post(`/alerts/rules/${rule.id}/test`)
    ElMessage.success('测试通知已发送')
  } catch (error) {
    ElMessage.error('发送测试通知失败')
  }
}

const deleteRule = async (rule) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除规则 "${rule.ruleName}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await request.delete(`/alerts/rules/${rule.id}`)
    ElMessage.success('规则删除成功')
    await loadRules()
    await loadStats()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除规则失败')
    }
  }
}

const resolveAlert = async (alert) => {
  try {
    await request.post(`/alerts/history/${alert.id}/resolve`)
    ElMessage.success('告警已解决')
    await loadHistory()
    await loadStats()
  } catch (error) {
    ElMessage.error('解决告警失败')
  }
}

const getRuleTypeText = (type) => {
  const map = {
    'HEALTH_CHECK': '健康检查',
    'PERFORMANCE': '性能监控',
    'ERROR_RATE': '错误率'
  }
  return map[type] || type
}

const getSeverityType = (severity) => {
  const map = {
    'INFO': 'info',
    'WARNING': 'warning',
    'ERROR': 'danger',
    'CRITICAL': 'danger'
  }
  return map[severity] || 'info'
}

const formatTime = (time) => {
  if (!time) return ''
  // Handle Spring Boot LocalDateTime array format: [year, month, day, hour, minute, second, ns]
  if (Array.isArray(time)) {
    if (time.length >= 3) {
      const year = time[0]
      const month = String(time[1]).padStart(2, '0')
      const day = String(time[2]).padStart(2, '0')
      const hour = time.length >= 4 ? String(time[3]).padStart(2, '0') : '00'
      const minute = time.length >= 5 ? String(time[4]).padStart(2, '0') : '00'
      const second = time.length >= 6 ? String(time[5]).padStart(2, '0') : '00'
      return `${year}/${month}/${day} ${hour}:${minute}:${second}`
    }
  }
  return dayjs(time).format('YYYY/MM/DD HH:mm:ss')
}

watch(activeTab, (newTab) => {
  if (newTab === 'history') {
    loadHistory()
  }
})

onMounted(() => {
  loadRules()
  loadStats()
})
</script>

<style scoped>
.alert-manager {
  padding: 20px;
}

.premium-card {
  border-radius: var(--radius-xl);
  border: 1px solid var(--neutral-gray-200);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.stats {
  display: flex;
  gap: 20px;
}

.ml-2 {
  margin-left: 8px;
}

.ml-4 {
  margin-left: 16px;
}

.mr-1 {
  margin-right: 4px;
}

.mt-4 {
  margin-top: 16px;
}
</style>
