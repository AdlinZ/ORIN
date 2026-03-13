<template>
  <div class="settings-container">
    <PageHeader
      title="系统设置"
      description="配置邮件服务、告警通知、系统参数"
      icon="Setting"
    />

    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- 邮件配置 -->
      <el-tab-pane label="邮件" name="mail">
        <!-- 邮件服务配置 -->
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>邮件服务配置</span>
              <el-tag :type="mailStatus.connected ? 'success' : 'info'">
                {{ mailStatus.connected ? '已配置' : '未配置' }}
              </el-tag>
            </div>
          </template>

          <el-form :model="mailConfig" label-width="120px">
            <el-form-item label="邮件类型">
              <el-radio-group v-model="mailConfig.mailerType">
                <el-radio value="smtp">SMTP</el-radio>
                <el-radio value="mailersend">MailerSend API</el-radio>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="API Key" v-if="mailConfig.mailerType === 'mailersend'">
              <el-input v-model="mailConfig.apiKey" type="password" show-password placeholder="MailerSend API Token" />
              <div class="form-tip">在 MailerSend 后台获取 API Token</div>
            </el-form-item>
            <el-form-item label="SMTP 服务器" v-if="mailConfig.mailerType === 'smtp'">
              <el-input v-model="mailConfig.host" placeholder="smtp.mailersend.net" />
            </el-form-item>
            <el-form-item label="端口" v-if="mailConfig.mailerType === 'smtp'">
              <el-input-number v-model="mailConfig.port" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="SMTP 用户名" v-if="mailConfig.mailerType === 'smtp'">
              <el-input v-model="mailConfig.username" placeholder="username" />
            </el-form-item>
            <el-form-item label="SMTP 密码" v-if="mailConfig.mailerType === 'smtp'">
              <el-input v-model="mailConfig.password" type="password" show-password placeholder="SMTP 密码" />
            </el-form-item>
            <el-form-item label="发件人邮箱">
              <el-input v-model="mailConfig.fromEmail" placeholder="your-domain@trial-xxxxx.mailersend.com" />
              <div class="form-tip">在 MailerSend 域名设置中验证的邮箱</div>
            </el-form-item>
            <el-form-item label="发件人名称">
              <el-input v-model="mailConfig.fromName" placeholder="ORIN 系统" />
            </el-form-item>
            <el-form-item label="启用 SSL" v-if="mailConfig.mailerType === 'smtp'">
              <el-switch v-model="mailConfig.ssl" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="testing" @click="testMail">
                测试连接
              </el-button>
              <el-button type="success" :loading="loading" @click="saveMailConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 邮件模板管理 -->
        <el-card class="settings-card" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>邮件模板管理</span>
              <el-button type="primary" size="small" @click="openTemplateDialog()">
                新增模板
              </el-button>
            </div>
          </template>

          <el-table :data="mailTemplates" v-loading="templatesLoading" stripe>
            <el-table-column prop="name" label="模板名称" width="150" />
            <el-table-column prop="code" label="模板代码" width="120" />
            <el-table-column prop="subject" label="邮件主题" min-width="200" show-overflow-tooltip />
            <el-table-column prop="isDefault" label="默认" width="60">
              <template #default="{ row }">
                <el-tag :type="row.isDefault ? 'success' : 'info'" size="small">
                  {{ row.isDefault ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="60">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="openTemplateDialog(row)">
                  编辑
                </el-button>
                <el-button type="danger" link size="small" @click="deleteTemplate(row)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 批量发送 -->
        <el-card class="settings-card" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>批量发送邮件</span>
            </div>
          </template>

          <el-form :model="batchSendForm" label-width="120px">
            <el-form-item label="选择模板">
              <el-select v-model="batchSendForm.templateId" placeholder="请选择模板" style="width: 100%;">
                <el-option
                  v-for="t in enabledTemplates"
                  :key="t.id"
                  :label="t.name + ' - ' + t.subject"
                  :value="t.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="收件人">
              <el-input
                v-model="batchSendForm.recipients"
                type="textarea"
                :rows="4"
                placeholder="请输入收件人邮箱，每行一个或多邮箱用逗号分隔"
              />
            </el-form-item>
            <el-form-item label="变量替换">
              <el-input
                v-model="batchSendForm.variables"
                type="textarea"
                :rows="3"
                placeholder="请输入变量，格式: key=value, 每行一个变量&#10;例如:&#10;name=张三&#10;date=2024-01-01"
              />
              <div class="form-tip">模板中的 {{变量名}} 将被替换</div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="batchSending" @click="batchSend">
                发送邮件
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 发送结果 -->
          <el-divider v-if="batchResult" />
          <div v-if="batchResult" class="batch-result">
            <el-alert
              :title="'发送完成: 成功 ' + batchResult.successCount + ' 封, 失败 ' + batchResult.failedCount + ' 封'"
              :type="batchResult.failedCount > 0 ? 'warning' : 'success'"
              :closable="false"
            />
          </div>
        </el-card>

        <!-- 邮件发送日志 -->
        <el-card class="settings-card" style="margin-top: 20px;">
          <template #header>
            <div class="card-header">
              <span>发送日志</span>
            </div>
          </template>

          <!-- 筛选 -->
          <el-form :inline="true" class="filter-form">
            <el-form-item label="状态">
              <el-select v-model="logFilters.status" placeholder="全部" clearable @change="loadMailLogs">
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
                <el-option label="待发送" value="PENDING" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadMailLogs">查询</el-button>
              <el-button @click="resetLogFilters">重置</el-button>
            </el-form-item>
          </el-form>

          <!-- 日志列表 -->
          <el-table :data="mailLogs" v-loading="logsLoading" stripe max-height="300">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="subject" label="主题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="recipients" label="收件人" min-width="120" show-overflow-tooltip />
            <el-table-column prop="mailerType" label="发送方式" width="90">
              <template #default="{ row }">
                <el-tag :type="row.mailerType === 'mailersend' ? 'primary' : 'info'" size="small">
                  {{ row.mailerType === 'mailersend' ? 'MailerSend' : 'SMTP' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="getLogStatusType(row.status)" size="small">
                  {{ getLogStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="发送时间" width="150">
              <template #default="{ row }">
                {{ formatLogDate(row.createdAt) }}
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="logPagination.page"
              v-model:page-size="logPagination.size"
              :total="logPagination.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @size-change="loadMailLogs"
              @current-change="loadMailLogs"
            />
          </div>
        </el-card>

        <!-- 模板编辑对话框 -->
        <el-dialog v-model="templateDialogVisible" :title="templateForm.id ? '编辑模板' : '新增模板'" width="600px">
          <el-form :model="templateForm" label-width="100px">
            <el-form-item label="模板名称" required>
              <el-input v-model="templateForm.name" placeholder="如: 系统通知" />
            </el-form-item>
            <el-form-item label="模板代码" required>
              <el-input v-model="templateForm.code" placeholder="如: system_notification" :disabled="!!templateForm.id" />
              <div class="form-tip">唯一标识，建议使用英文</div>
            </el-form-item>
            <el-form-item label="邮件主题" required>
              <el-input v-model="templateForm.subject" placeholder="如: 【{{name}}】系统通知" />
              <div class="form-tip">支持变量替换，如 {{name}}</div>
            </el-form-item>
            <el-form-item label="邮件内容" required>
              <el-input v-model="templateForm.content" type="textarea" :rows="8" placeholder="邮件正文内容，支持变量替换" />
            </el-form-item>
            <el-form-item label="设为默认">
              <el-switch v-model="templateForm.isDefault" />
            </el-form-item>
            <el-form-item label="启用状态">
              <el-switch v-model="templateForm.enabled" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="templateDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="saveTemplate">保存</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 告警通知配置 -->
      <el-tab-pane label="告警通知" name="notification">
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>通知渠道配置</span>
            </div>
          </template>

          <el-form label-width="120px">
            <!-- 邮件通知 -->
            <el-divider>邮件通知</el-divider>
            <el-form-item label="启用邮件">
              <el-switch v-model="notificationConfig.email.enabled" />
            </el-form-item>
            <el-form-item label="收件人" v-if="notificationConfig.email.enabled">
              <el-input v-model="notificationConfig.email.recipients" placeholder="admin@example.com,user@example.com" />
              <div class="form-tip">多个邮箱用逗号分隔</div>
            </el-form-item>

            <!-- 钉钉通知 -->
            <el-divider>钉钉通知</el-divider>
            <el-form-item label="启用钉钉">
              <el-switch v-model="notificationConfig.dingtalk.enabled" />
            </el-form-item>
            <el-form-item label="Webhook" v-if="notificationConfig.dingtalk.enabled">
              <el-input v-model="notificationConfig.dingtalk.webhook" placeholder="钉钉机器人 Webhook 地址" />
            </el-form-item>

            <!-- 企业微信 -->
            <el-divider>企业微信</el-divider>
            <el-form-item label="启用企微">
              <el-switch v-model="notificationConfig.wecom.enabled" />
            </el-form-item>
            <el-form-item label="Webhook" v-if="notificationConfig.wecom.enabled">
              <el-input v-model="notificationConfig.wecom.webhook" placeholder="企业微信机器人 Webhook 地址" />
            </el-form-item>

            <el-form-item>
              <el-button type="primary" @click="saveNotificationConfig">
                保存配置
              </el-button>
              <el-button @click="testNotification('email')">
                测试邮件
              </el-button>
              <el-button @click="testNotification('dingtalk')">
                测试钉钉
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 系统参数配置 -->
      <el-tab-pane label="系统参数" name="system">
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>系统参数</span>
            </div>
          </template>

          <el-form :model="systemConfig" label-width="150px">
            <el-form-item label="系统名称">
              <el-input v-model="systemConfig.appName" placeholder="ORIN" />
            </el-form-item>
            <el-form-item label="会话历史保留天数">
              <el-input-number v-model="systemConfig.historyRetentionDays" :min="1" :max="365" />
            </el-form-item>
            <el-form-item label="最大并发请求">
              <el-input-number v-model="systemConfig.maxConcurrentRequests" :min="1" :max="1000" />
            </el-form-item>
            <el-form-item label="启用审计日志">
              <el-switch v-model="systemConfig.auditLogEnabled" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="saveSystemConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import request from '@/utils/request'

const activeTab = ref('mail')
const testing = ref(false)
const loading = ref(false)

// 邮件配置
const mailConfig = reactive({
  mailerType: 'mailersend',
  apiKey: '',
  host: 'smtp.mailersend.net',
  port: 587,
  username: '',
  password: '',
  ssl: true,
  fromEmail: '',
  fromName: 'ORIN'
})

const mailStatus = ref({ connected: false })

// 通知配置
const notificationConfig = reactive({
  email: {
    enabled: true,
    recipients: ''
  },
  dingtalk: {
    enabled: false,
    webhook: ''
  },
  wecom: {
    enabled: false,
    webhook: ''
  }
})

const notificationStatus = ref({})

// 系统配置
const systemConfig = reactive({
  appName: 'ORIN',
  historyRetentionDays: 30,
  maxConcurrentRequests: 100,
  auditLogEnabled: true
})

// 邮件日志
const logsLoading = ref(false)
const mailLogs = ref([])
const logFilters = reactive({
  status: ''
})
const logPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 邮件模板
const templatesLoading = ref(false)
const mailTemplates = ref([])
const templateDialogVisible = ref(false)
const templateForm = reactive({
  id: null,
  name: '',
  code: '',
  subject: '',
  content: '',
  isDefault: false,
  enabled: true
})

// 批量发送
const batchSending = ref(false)
const batchSendForm = reactive({
  templateId: null,
  recipients: '',
  variables: ''
})
const batchResult = ref(null)

const enabledTemplates = ref([])

// 加载邮件配置
const loadMailConfig = async () => {
  try {
    const res = await request.get('/system/mail-config')
    if (res) {
      mailConfig.mailerType = res.mailerType || 'smtp'
      mailConfig.apiKey = res.apiKey || ''
      mailConfig.host = res.smtpHost || 'smtp.mailersend.net'
      mailConfig.port = res.smtpPort || 587
      mailConfig.username = res.username || ''
      mailConfig.password = res.password || ''
      mailConfig.ssl = res.sslEnabled !== false
      mailConfig.fromEmail = res.fromEmail || ''
      mailConfig.fromName = res.fromName || 'ORIN'
      mailStatus.value.connected = res.enabled || false
    }
  } catch (e) {
    console.error('加载邮件配置失败:', e)
  }
}

// 测试邮件
const testMail = async () => {
  const testEmail = mailConfig.mailerType === 'mailersend'
    ? mailConfig.fromEmail
    : mailConfig.username

  if (!testEmail) {
    ElMessage.warning('请先填写发件人邮箱')
    return
  }
  testing.value = true
  try {
    const res = await request.post('/system/mail-config/test', null, {
      params: { testEmail: testEmail }
    })
    if (res.success !== false) {
      ElMessage.success('测试邮件发送成功')
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  } finally {
    testing.value = false
  }
}

// 保存邮件配置
const saveMailConfig = async () => {
  loading.value = true
  try {
    const config = {
      mailerType: mailConfig.mailerType,
      apiKey: mailConfig.mailerType === 'mailersend' ? mailConfig.apiKey : null,
      smtpHost: mailConfig.host,
      smtpPort: mailConfig.port,
      username: mailConfig.username,
      password: mailConfig.password,
      fromEmail: mailConfig.fromEmail || mailConfig.username,
      fromName: mailConfig.fromName,
      sslEnabled: mailConfig.ssl,
      enabled: true
    }
    const res = await request.post('/system/mail-config', config)
    if (res) {
      mailStatus.value.connected = true
      ElMessage.success('邮件配置已保存')
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  } finally {
    loading.value = false
  }
}

// 加载通知配置
const loadNotificationConfig = async () => {
  try {
    const res = await request.get('/alerts/notification-config')
    if (res) {
      notificationConfig.email.enabled = res.emailEnabled
      notificationConfig.email.recipients = res.emailRecipients || ''
      notificationConfig.dingtalk.enabled = res.dingtalkEnabled
      notificationConfig.dingtalk.webhook = res.dingtalkWebhook || ''
      notificationConfig.wecom.enabled = res.wecomEnabled
      notificationConfig.wecom.webhook = res.wecomWebhook || ''
    }
    // 加载状态
    const statusRes = await request.get('/alerts/notification-config/status')
    if (statusRes) {
      notificationStatus.value = statusRes
    }
  } catch (e) {
    console.error('加载通知配置失败:', e)
  }
}

// 保存通知配置
const saveNotificationConfig = async () => {
  try {
    const res = await request.post('/alerts/notification-config', {
      emailEnabled: notificationConfig.email.enabled,
      emailRecipients: notificationConfig.email.recipients,
      dingtalkEnabled: notificationConfig.dingtalk.enabled,
      dingtalkWebhook: notificationConfig.dingtalk.webhook,
      wecomEnabled: notificationConfig.wecom.enabled,
      wecomWebhook: notificationConfig.wecom.webhook
    })
    if (res) {
      ElMessage.success('通知配置已保存')
      // 刷新状态
      const statusRes = await request.get('/alerts/notification-config/status')
      if (statusRes) {
        notificationStatus.value = statusRes
      }
    }
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || e))
  }
}

// 测试通知
const testNotification = async (channel) => {
  try {
    const res = await request.post('/alerts/notification-config/test', { channel })
    if (res.success) {
      ElMessage.success(`${channel} 测试通知发送成功`)
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  }
}

// 保存系统配置
const saveSystemConfig = () => {
  ElMessage.success('系统配置已保存')
}

// 加载邮件日志
const loadMailLogs = async () => {
  logsLoading.value = true
  try {
    const params = {
      page: logPagination.page - 1,
      size: logPagination.size,
      status: logFilters.status || undefined
    }
    const res = await request.get('/system/mail-logs', { params })
    if (res) {
      mailLogs.value = res.content || []
      logPagination.total = res.totalElements || 0
    }
  } catch (e) {
    console.error('加载邮件日志失败:', e)
  } finally {
    logsLoading.value = false
  }
}

const resetLogFilters = () => {
  logFilters.status = ''
  logPagination.page = 1
  loadMailLogs()
}

const getLogStatusType = (status) => {
  const map = {
    'SUCCESS': 'success',
    'FAILED': 'danger',
    'PENDING': 'warning'
  }
  return map[status] || 'info'
}

const getLogStatusText = (status) => {
  const map = {
    'SUCCESS': '成功',
    'FAILED': '失败',
    'PENDING': '待发送'
  }
  return map[status] || status
}

const formatLogDate = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 加载邮件模板
const loadMailTemplates = async () => {
  templatesLoading.value = true
  try {
    const res = await request.get('/system/mail-templates')
    mailTemplates.value = res || []
    enabledTemplates.value = mailTemplates.value.filter(t => t.enabled)
  } catch (e) {
    console.error('加载邮件模板失败:', e)
  } finally {
    templatesLoading.value = false
  }
}

// 打开模板对话框
const openTemplateDialog = (template = null) => {
  if (template) {
    templateForm.id = template.id
    templateForm.name = template.name
    templateForm.code = template.code
    templateForm.subject = template.subject
    templateForm.content = template.content
    templateForm.isDefault = template.isDefault || false
    templateForm.enabled = template.enabled !== false
  } else {
    templateForm.id = null
    templateForm.name = ''
    templateForm.code = ''
    templateForm.subject = ''
    templateForm.content = ''
    templateForm.isDefault = false
    templateForm.enabled = true
  }
  templateDialogVisible.value = true
}

// 保存模板
const saveTemplate = async () => {
  if (!templateForm.name || !templateForm.code || !templateForm.subject || !templateForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    const data = {
      name: templateForm.name,
      code: templateForm.code,
      subject: templateForm.subject,
      content: templateForm.content,
      isDefault: templateForm.isDefault,
      enabled: templateForm.enabled
    }
    if (templateForm.id) {
      await request.put(`/system/mail-templates/${templateForm.id}`, data)
      ElMessage.success('模板已更新')
    } else {
      await request.post('/system/mail-templates', data)
      ElMessage.success('模板已创建')
    }
    templateDialogVisible.value = false
    loadMailTemplates()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

// 删除模板
const deleteTemplate = async (template) => {
  try {
    await request.delete(`/system/mail-templates/${template.id}`)
    ElMessage.success('模板已删除')
    loadMailTemplates()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

// 批量发送邮件
const batchSend = async () => {
  if (!batchSendForm.templateId) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!batchSendForm.recipients) {
    ElMessage.warning('请输入收件人')
    return
  }

  // 解析收件人
  const recipients = batchSendForm.recipients
    .split(/[\n,]/)
    .map(e => e.trim())
    .filter(e => e && e.includes('@'))

  if (recipients.length === 0) {
    ElMessage.warning('请输入有效的收件人邮箱')
    return
  }

  // 解析变量
  const variables = {}
  if (batchSendForm.variables) {
    batchSendForm.variables.split('\n').forEach(line => {
      const [key, ...valueParts] = line.split('=')
      if (key && valueParts.length > 0) {
        variables[key.trim()] = valueParts.join('=').trim()
      }
    })
  }

  batchSending.value = true
  batchResult.value = null
  try {
    const res = await request.post('/system/mail-templates/batch-send', {
      recipients,
      templateId: batchSendForm.templateId,
      variables
    })
    if (res) {
      batchResult.value = res
      if (res.failedCount > 0) {
        ElMessage.warning(`发送完成: 成功 ${res.successCount} 封, 失败 ${res.failedCount} 封`)
      } else {
        ElMessage.success(`发送成功: ${res.successCount} 封`)
      }
    }
  } catch (e) {
    ElMessage.error(e.message || '发送失败')
  } finally {
    batchSending.value = false
  }
}

onMounted(() => {
  loadMailConfig()
  loadMailTemplates()
  loadNotificationConfig()
})
</script>

<style scoped>
.settings-container {
  padding: 20px;
}

.settings-tabs {
  margin-top: 20px;
}

.settings-card {
  max-width: 800px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
