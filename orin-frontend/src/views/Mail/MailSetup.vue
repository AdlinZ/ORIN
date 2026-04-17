<template>
  <div class="notification-center-container">
    <PageHeader
      title="通知中心"
      description="统一配置邮件渠道、告警通知渠道与通知偏好设置"
      icon="Bell"
    />

    <div class="setup-content">
      <!-- 标签页导航 -->
      <el-tabs v-model="activeTab" class="notification-tabs">
        <!-- 标签1: 邮件渠道配置 -->
        <el-tab-pane label="邮件渠道" name="mail-service">
          <!-- 顶部快捷操作栏 -->
          <div class="quick-action-bar">
            <el-button
              type="primary"
              :icon="Promotion"
              :loading="sendingTest"
              :disabled="!mailConnected"
              @click="openTestMailDialog"
            >
              发送测试邮件
            </el-button>
            <el-button
              :icon="Connection"
              :loading="testingConnection"
              :disabled="!mailConnected"
              @click="testConnection"
            >
              验证连接
            </el-button>
          </div>

          <!-- 配置向导 -->
          <el-card class="setup-card">
            <template #header>
              <div class="card-header">
                <span><el-icon><Setting /></el-icon> 邮件渠道配置</span>
                <el-tag :type="mailConnected ? 'success' : 'warning'" size="small">
                  {{ mailConnected ? '已连接' : '未配置' }}
                </el-tag>
              </div>
            </template>

            <!-- 步骤指示器 -->
            <div class="config-steps">
              <div class="step-item" :class="{ active: configStep === 1, completed: configStep > 1 }">
                <div class="step-number">
                  1
                </div>
                <div class="step-text">
                  选择通道
                </div>
              </div>
              <div class="step-line" :class="{ active: configStep > 1 }" />
              <div class="step-item" :class="{ active: configStep === 2, completed: configStep > 2 }">
                <div class="step-number">
                  2
                </div>
                <div class="step-text">
                  填写凭据
                </div>
              </div>
              <div class="step-line" :class="{ active: configStep > 2 }" />
              <div class="step-item" :class="{ active: configStep === 3 }">
                <div class="step-number">
                  3
                </div>
                <div class="step-text">
                  验证配置
                </div>
              </div>
            </div>

            <!-- 步骤1: 选择通道 -->
            <div v-show="configStep === 1" class="config-step-content">
              <div class="mailer-selection">
                <div
                  class="mailer-option"
                  :class="{ selected: mailConfigForm.mailerType === 'mailersend' }"
                  @click="selectMailer('mailersend')"
                >
                  <div class="mailer-icon mailersend-icon">
                    <svg viewBox="0 0 24 24" width="40" height="40">
                      <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z" />
                    </svg>
                  </div>
                  <div class="mailer-info">
                    <h4>MailerSend API</h4>
                    <p>推荐 · 更快速、更可靠</p>
                  </div>
                  <el-icon v-if="mailConfigForm.mailerType === 'mailersend'" class="check-icon">
                    <CircleCheck />
                  </el-icon>
                </div>

                <div
                  class="mailer-option"
                  :class="{ selected: mailConfigForm.mailerType === 'smtp' }"
                  @click="selectMailer('smtp')"
                >
                  <div class="mailer-icon smtp-icon">
                    <el-icon :size="40">
                      <MessageBox />
                    </el-icon>
                  </div>
                  <div class="mailer-info">
                    <h4>SMTP</h4>
                    <p>通用 · 兼容各种邮件服务</p>
                  </div>
                  <el-icon v-if="mailConfigForm.mailerType === 'smtp'" class="check-icon">
                    <CircleCheck />
                  </el-icon>
                </div>

                <div
                  class="mailer-option"
                  :class="{ selected: mailConfigForm.mailerType === 'resend' }"
                  @click="selectMailer('resend')"
                >
                  <div class="mailer-icon resend-icon">
                    <svg viewBox="0 0 24 24" width="40" height="40">
                      <path fill="currentColor" d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5" />
                    </svg>
                  </div>
                  <div class="mailer-info">
                    <h4>Resend</h4>
                    <p>现代 · 开发者友好</p>
                  </div>
                  <el-icon v-if="mailConfigForm.mailerType === 'resend'" class="check-icon">
                    <CircleCheck />
                  </el-icon>
                </div>
              </div>

              <div class="step-actions">
                <el-button type="primary" :disabled="!mailConfigForm.mailerType" @click="configStep = 2">
                  下一步 <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>

            <!-- 步骤2: 填写凭据 -->
            <div v-show="configStep === 2" class="config-step-content">
              <el-form :model="mailConfigForm" label-width="140px" class="config-form">
                <!-- MailerSend / Resend 配置 -->
                <template v-if="mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend'">
                  <el-form-item label="API Token" required>
                    <el-input
                      v-model="mailConfigForm.apiKey"
                      type="password"
                      show-password
                      :placeholder="mailConfigForm.mailerType === 'mailersend' ? 'mls_xxxxxxxxxxxx' : 're_xxxxxxxxxxxx'"
                    >
                      <template #prefix>
                        <el-icon><Key /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="发件人邮箱" required>
                    <el-input v-model="mailConfigForm.fromEmail" placeholder="your-email@domain.com">
                      <template #prefix>
                        <el-icon><Message /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="发件人名称">
                    <el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统">
                      <template #prefix>
                        <el-icon><User /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>
                </template>

                <!-- SMTP 配置 -->
                <template v-if="mailConfigForm.mailerType === 'smtp'">
                  <el-form-item label="SMTP 服务器" required>
                    <el-input v-model="mailConfigForm.smtpHost" placeholder="smtp.mailersend.net">
                      <template #prefix>
                        <el-icon><Monitor /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="端口" required>
                    <el-select v-model="mailConfigForm.smtpPort" placeholder="选择端口" style="width: 100%;">
                      <el-option label="587 (TLS)" :value="587" />
                      <el-option label="465 (SSL)" :value="465" />
                      <el-option label="25 (无加密)" :value="25" />
                    </el-select>
                  </el-form-item>

                  <el-form-item label="用户名">
                    <el-input v-model="mailConfigForm.smtpUsername" placeholder="username">
                      <template #prefix>
                        <el-icon><User /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="密码">
                    <el-input
                      v-model="mailConfigForm.smtpPassword"
                      type="password"
                      show-password
                      placeholder="密码"
                    >
                      <template #prefix>
                        <el-icon><Lock /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="发件人邮箱" required>
                    <el-input v-model="mailConfigForm.fromEmail" placeholder="your-email@domain.com">
                      <template #prefix>
                        <el-icon><Message /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="发件人名称">
                    <el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统">
                      <template #prefix>
                        <el-icon><User /></el-icon>
                      </template>
                    </el-input>
                  </el-form-item>

                  <el-form-item label="启用 SSL">
                    <el-switch v-model="mailConfigForm.sslEnabled" />
                  </el-form-item>
                </template>
              </el-form>

              <div class="step-actions">
                <el-button @click="configStep = 1">
                  <el-icon><ArrowLeft /></el-icon> 上一步
                </el-button>
                <el-button type="primary" :disabled="!canProceedStep2" @click="configStep = 3">
                  下一步 <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>

            <!-- 步骤3: 验证配置 -->
            <div v-show="configStep === 3" class="config-step-content">
              <div class="verify-content">
                <div class="verify-summary">
                  <el-descriptions :column="2" border>
                    <el-descriptions-item label="发送方式">
                      <el-tag :type="getMailerTypeTag(mailConfigForm.mailerType)">
                        {{ getMailerTypeName(mailConfigForm.mailerType) }}
                      </el-tag>
                    </el-descriptions-item>
                    <el-descriptions-item label="发件人邮箱">
                      {{ mailConfigForm.fromEmail || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item label="发件人名称">
                      {{ mailConfigForm.fromName || '-' }}
                    </el-descriptions-item>
                    <el-descriptions-item v-if="mailConfigForm.mailerType === 'smtp'" label="SMTP 服务器">
                      {{ mailConfigForm.smtpHost }}:{{ mailConfigForm.smtpPort }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>

                <div class="verify-test">
                  <el-input
                    v-model="testRecipient"
                    placeholder="输入测试收件人邮箱"
                    style="width: 300px; margin-right: 12px;"
                  >
                    <template #prefix>
                      <el-icon><Message /></el-icon>
                    </template>
                  </el-input>
                  <el-button
                    type="primary"
                    :loading="sendingTest"
                    :disabled="!testRecipient"
                    @click="sendTestMail"
                  >
                    发送测试邮件
                  </el-button>
                </div>

                <!-- 测试结果反馈 -->
                <el-alert
                  v-if="testResult"
                  :title="testResult.success ? '测试成功' : '测试失败'"
                  :type="testResult.success ? 'success' : 'error'"
                  :description="testResult.message"
                  show-icon
                  closable
                  style="margin-top: 16px;"
                />
              </div>

              <div class="step-actions">
                <el-button @click="configStep = 2">
                  <el-icon><ArrowLeft /></el-icon> 上一步
                </el-button>
                <el-button type="primary" :loading="testingConnection" @click="saveAndTest">
                  <el-icon><Check /></el-icon> 保存配置
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 配置成功后的下一步行动 -->
          <el-card v-if="mailConnected" class="next-actions-card">
            <template #header>
              <div class="card-header">
                <span><el-icon><Lightning /></el-icon> 下一步行动</span>
              </div>
            </template>
            <div class="next-actions">
              <div class="next-action" @click="goToCompose">
                <el-icon :size="32">
                  <EditPen />
                </el-icon>
                <div class="next-action-content">
                  <h4>发送邮件</h4>
                  <p>撰写并发送邮件</p>
                </div>
                <el-icon class="arrow">
                  <ArrowRight />
                </el-icon>
              </div>
              <div class="next-action" @click="goToTemplates">
                <el-icon :size="32">
                  <Document />
                </el-icon>
                <div class="next-action-content">
                  <h4>管理模板</h4>
                  <p>创建和编辑邮件模板</p>
                </div>
                <el-icon class="arrow">
                  <ArrowRight />
                </el-icon>
              </div>
              <div class="next-action" @click="goToTracking">
                <el-icon :size="32">
                  <List />
                </el-icon>
                <div class="next-action-content">
                  <h4>查看发送日志</h4>
                  <p>追踪邮件发送状态</p>
                </div>
                <el-icon class="arrow">
                  <ArrowRight />
                </el-icon>
              </div>
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 标签2: 通知渠道 -->
        <el-tab-pane label="通知渠道" name="notification-channels">
          <NotificationChannelsPanel
            :mail-connected="mailConnected"
            :require-mail-connected-for-email-test="true"
            mail-dependency-text="邮件通知依赖于&quot;邮件渠道&quot;标签页中的邮件配置"
          />
        </el-tab-pane>

        <!-- 标签3: 站内消息 -->
        <el-tab-pane label="站内消息" name="messages">
          <el-card class="messages-card">
            <template #header>
              <div class="card-header">
                <span><el-icon><Bell /></el-icon> 消息列表</span>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  :loading="markingAllAsRead"
                  @click="handleMarkAllAsRead"
                >
                  全部已读
                </el-button>
              </div>
            </template>

            <el-table
              v-loading="loadingNotifications"
              :data="notificationList"
              style="width: 100%"
              empty-text="暂无消息"
            >
              <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
              <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
              <el-table-column prop="type" label="类型" width="100">
                <template #default="{ row }">
                  <el-tag
                    size="small"
                    :type="row.type === 'ERROR' ? 'danger' : row.type === 'WARNING' ? 'warning' : row.type === 'SYSTEM' ? 'info' : 'success'"
                  >
                    {{ row.type || 'INFO' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="scope" label="范围" width="80">
                <template #default="{ row }">
                  {{ row.scope === 'BROADCAST' ? '广播' : '用户' }}
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="时间" width="160">
                <template #default="{ row }">
                  {{ formatDateTime(row.createdAt) }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="!row.read"
                    type="primary"
                    link
                    size="small"
                    @click="handleMarkAsRead(row.id)"
                  >
                    标记已读
                  </el-button>
                  <span v-else class="text-muted">已读</span>
                </template>
              </el-table-column>
            </el-table>

            <div class="pagination-wrapper">
              <el-pagination
                v-model:current-page="notificationPage"
                v-model:page-size="notificationPageSize"
                :total="notificationTotal"
                :page-sizes="[10, 20, 50]"
                layout="total, sizes, prev, pager, next, jumper"
                @size-change="loadNotifications"
                @current-change="loadNotifications"
              />
            </div>
          </el-card>
        </el-tab-pane>

        <!-- 标签4: 消息发信 -->
        <el-tab-pane label="消息发信" name="send-notification">
          <el-card class="send-notification-card">
            <template #header>
              <div class="card-header">
                <span><el-icon><Promotion /></el-icon> 发送站内消息</span>
              </div>
            </template>

            <el-form :model="sendNotificationForm" label-width="100px" class="send-form">
              <el-form-item label="消息标题" required>
                <el-input
                  v-model="sendNotificationForm.title"
                  placeholder="请输入消息标题"
                  maxlength="100"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item label="消息内容" required>
                <el-input
                  v-model="sendNotificationForm.content"
                  type="textarea"
                  :rows="6"
                  placeholder="请输入消息内容"
                  maxlength="1000"
                  show-word-limit
                />
              </el-form-item>

              <el-form-item label="发送范围" required>
                <el-radio-group v-model="sendNotificationForm.scope">
                  <el-radio value="BROADCAST">广播全员</el-radio>
                  <el-radio value="USER">指定用户</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item v-if="sendNotificationForm.scope === 'USER'" label="用户ID">
                <el-input
                  v-model="sendNotificationForm.receiverId"
                  placeholder="请输入用户ID"
                />
              </el-form-item>

              <el-form-item>
                <el-button
                  type="primary"
                  :loading="sendingNotification"
                  @click="handleSendNotification"
                >
                  发送消息
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 测试邮件对话框 -->
    <el-dialog v-model="testMailDialogVisible" title="发送测试邮件" width="500px">
      <el-form :model="testMailForm" label-width="100px">
        <el-form-item label="收件人">
          <el-input v-model="testMailForm.to" placeholder="test@example.com">
            <template #prefix>
              <el-icon><Message /></el-icon>
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="邮件类型">
          <el-select v-model="testMailForm.type" style="width: 100%;">
            <el-option label="验证码" value="verification" />
            <el-option label="通知" value="notification" />
            <el-option label="告警" value="alert" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="testMailDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="sendingTest" @click="sendTestMailFromDialog">
          发送
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Setting, Connection, Warning, Promotion, ArrowRight, ArrowLeft,
  CircleCheck, MessageBox, Key, Message, User, Monitor, Lock, Check,
  EditPen, Document, List, Lightning, Bell
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageHeader from '@/components/PageHeader.vue'
import NotificationChannelsPanel from '@/components/notification/NotificationChannelsPanel.vue'
import { ROUTES } from '@/router/routes'
import {
  getNotifications,
  markAsRead,
  markAllAsRead,
  sendNotification as sendNotificationApi
} from '@/api/notification'

const router = useRouter()
const route = useRoute()

// 根据路由路径决定默认标签页
const getDefaultTab = () => {
  const path = route.path
  if (path.includes('notification-channels')) {
    return 'notification-channels'
  }
  return 'mail-service'
}

// 标签页
const activeTab = ref(getDefaultTab())

// 页面状态
const pageState = ref('loading')
const configStep = ref(1)

// 加载状态
const testingConnection = ref(false)
const sendingTest = ref(false)

// 配置状态
const mailConnected = ref(false)
const mailConfig = ref(null)

// 测试
const testRecipient = ref('')
const testResult = ref(null)
const testMailDialogVisible = ref(false)
const testMailForm = reactive({
  to: '',
  type: 'verification',
  code: '123456'
})

// 配置表单
const mailConfigForm = reactive({
  mailerType: 'mailersend',
  apiKey: '',
  smtpHost: 'smtp.mailersend.net',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  fromEmail: '',
  fromName: 'ORIN 系统',
  sslEnabled: true
})

// 计算属性
const canProceedStep2 = computed(() => {
  if (mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend') {
    return mailConfigForm.apiKey && mailConfigForm.fromEmail
  } else {
    return mailConfigForm.smtpHost && mailConfigForm.fromEmail
  }
})

// 方法
const selectMailer = (type) => {
  mailConfigForm.mailerType = type
}

const getMailerTypeName = (type) => {
  const map = {
    mailersend: 'MailerSend API',
    smtp: 'SMTP',
    resend: 'Resend'
  }
  return map[type] || type
}

const getMailerTypeTag = (type) => {
  const map = {
    mailersend: 'primary',
    smtp: 'info',
    resend: 'success'
  }
  return map[type] || 'info'
}

// ==================== 邮件服务相关方法 ====================

// 加载配置
const loadMailConfig = async () => {
  try {
    pageState.value = 'loading'
    const res = await request.get('/system/mail-config')
    if (res) {
      mailConfig.value = res
      mailConnected.value = res.enabled

      mailConfigForm.mailerType = res.mailerType || 'mailersend'
      if (res.mailerType === 'mailersend' || res.mailerType === 'resend') {
        mailConfigForm.apiKey = res.apiKey || ''
      } else {
        mailConfigForm.smtpHost = res.smtpHost || 'smtp.mailersend.net'
        mailConfigForm.smtpPort = res.smtpPort || 587
        mailConfigForm.smtpUsername = res.username || ''
        mailConfigForm.smtpPassword = res.password || ''
        mailConfigForm.sslEnabled = res.sslEnabled !== false
      }
      mailConfigForm.fromEmail = res.fromEmail || ''
      mailConfigForm.fromName = res.fromName || 'ORIN 系统'
    }
    pageState.value = 'success'
  } catch (e) {
    console.error('加载配置失败:', e)
    pageState.value = 'error'
  }
}

// 保存并测试
const saveAndTest = async () => {
  testingConnection.value = true
  try {
    const config = {
      id: mailConfig.value?.id,
      mailerType: mailConfigForm.mailerType,
      apiKey: (mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend') ? mailConfigForm.apiKey : null,
      smtpHost: mailConfigForm.smtpHost,
      smtpPort: mailConfigForm.smtpPort,
      username: mailConfigForm.smtpUsername,
      password: mailConfigForm.smtpPassword,
      fromEmail: mailConfigForm.fromEmail,
      fromName: mailConfigForm.fromName,
      sslEnabled: mailConfigForm.sslEnabled,
      enabled: true
    }

    await request.post('/system/mail-config', config)
    mailConfig.value = config
    mailConnected.value = true

    ElMessage.success('配置保存成功')
  } catch (e) {
    console.error('保存配置失败:', e)
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    testingConnection.value = false
  }
}

// 测试连接
const testConnection = async () => {
  testingConnection.value = true
  testResult.value = null
  try {
    const res = await request.post('/system/mail-config/test')
    testResult.value = {
      success: res.success || res.code === 0,
      message: res.message || '连接测试成功'
    }
    if (testResult.value.success) {
      ElMessage.success('连接验证成功')
    }
  } catch (e) {
    testResult.value = {
      success: false,
      message: e.message || '连接验证失败'
    }
  } finally {
    testingConnection.value = false
  }
}

// 打开测试邮件对话框
const openTestMailDialog = () => {
  testMailForm.to = ''
  testMailDialogVisible.value = true
}

// 发送测试邮件
const sendTestMailFromDialog = async () => {
  if (!testMailForm.to) {
    ElMessage.warning('请输入收件人邮箱')
    return
  }

  sendingTest.value = true
  try {
    await request.post('/system/mail-config/test', {
      to: testMailForm.to,
      type: testMailForm.type,
      code: testMailForm.code
    })
    ElMessage.success('测试邮件发送成功')
    testMailDialogVisible.value = false
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || '未知错误'))
  } finally {
    sendingTest.value = false
  }
}

// 发送测试邮件（步骤3）
const sendTestMail = async () => {
  if (!testRecipient.value) {
    ElMessage.warning('请输入测试收件人邮箱')
    return
  }

  sendingTest.value = true
  testResult.value = null
  try {
    await request.post('/system/mail-config/test', {
      to: testRecipient.value,
      type: 'verification',
      code: '123456'
    })
    testResult.value = {
      success: true,
      message: '测试邮件发送成功，请查收'
    }
    ElMessage.success('测试邮件发送成功')
  } catch (e) {
    testResult.value = {
      success: false,
      message: e.message || '发送失败'
    }
  } finally {
    sendingTest.value = false
  }
}

// ==================== 导航方法 ====================

const goToCompose = () => router.push(ROUTES.CONTROL.MAIL_COMPOSE)
const goToTemplates = () => router.push(ROUTES.CONTROL.MAIL_COMPOSE + '?tab=templates')
const goToTracking = () => router.push(ROUTES.CONTROL.MAIL_TRACKING)

watch(
  () => route.path,
  () => {
    activeTab.value = getDefaultTab()
  }
)

onMounted(() => {
  loadMailConfig()
  loadNotifications()
})

// ==================== 站内消息相关 ====================

const notificationList = ref([])
const notificationPage = ref(1)
const notificationPageSize = ref(20)
const notificationTotal = ref(0)
const loadingNotifications = ref(false)
const markingAllAsRead = ref(false)
const sendingNotification = ref(false)
const sendNotificationForm = reactive({
  title: '',
  content: '',
  scope: 'BROADCAST',
  receiverId: ''
})

const loadNotifications = async () => {
  loadingNotifications.value = true
  try {
    const res = await getNotifications({
      page: notificationPage.value - 1,
      size: notificationPageSize.value
    })
    notificationList.value = res?.content || []
    notificationTotal.value = res?.totalElements || 0
  } catch (e) {
    console.error('加载消息失败:', e)
    ElMessage.error('加载消息失败')
  } finally {
    loadingNotifications.value = false
  }
}

const handleMarkAsRead = async (id) => {
  try {
    await markAsRead(id)
    ElMessage.success('已标记为已读')
    loadNotifications()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleMarkAllAsRead = async () => {
  markingAllAsRead.value = true
  try {
    await markAllAsRead()
    ElMessage.success('已全部标记为已读')
    loadNotifications()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    markingAllAsRead.value = false
  }
}

const handleSendNotification = async () => {
  if (!sendNotificationForm.title.trim()) {
    ElMessage.warning('请输入消息标题')
    return
  }
  if (!sendNotificationForm.content.trim()) {
    ElMessage.warning('请输入消息内容')
    return
  }
  if (sendNotificationForm.scope === 'USER' && !sendNotificationForm.receiverId.trim()) {
    ElMessage.warning('请输入用户ID')
    return
  }

  sendingNotification.value = true
  try {
    await sendNotificationApi({
      title: sendNotificationForm.title,
      content: sendNotificationForm.content,
      type: 'INFO',
      scope: sendNotificationForm.scope,
      receiverId: sendNotificationForm.scope === 'USER' ? sendNotificationForm.receiverId : null
    })
    ElMessage.success('消息发送成功')
    sendNotificationForm.title = ''
    sendNotificationForm.content = ''
    sendNotificationForm.scope = 'BROADCAST'
    sendNotificationForm.receiverId = ''
    loadNotifications()
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || '未知错误'))
  } finally {
    sendingNotification.value = false
  }
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}`
}
</script>

<style scoped>
.notification-center-container {
  padding: 0;
}

.setup-content {
  max-width: 1200px;
  margin: 0 auto;
}

/* 标签页样式 */
.notification-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}

.notification-tabs :deep(.el-tabs__content) {
  padding-top: 20px;
}

/* 快捷操作栏 */
.quick-action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

/* 配置卡片 */
.setup-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 步骤指示器 */
.config-steps {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32px;
  padding: 20px 0;
}

.step-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.step-number {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  transition: all 0.3s;
}

.step-item.active .step-number {
  background: var(--el-color-primary);
  color: white;
}

.step-item.completed .step-number {
  background: var(--el-color-success);
  color: white;
}

.step-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.step-item.active .step-text {
  color: var(--el-color-primary);
  font-weight: 500;
}

.step-line {
  width: 80px;
  height: 2px;
  background: var(--el-fill-color-light);
  margin: 0 12px;
  margin-bottom: 24px;
  transition: all 0.3s;
}

.step-line.active {
  background: var(--el-color-primary);
}

/* 步骤内容 */
.config-step-content {
  padding: 20px 0;
}

/* 邮件服务商选择 */
.mailer-selection {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.mailer-option {
  position: relative;
  padding: 20px;
  border: 2px solid var(--el-border-color-lighter);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.mailer-option:hover {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.mailer-option.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.mailer-icon {
  color: var(--el-text-color-primary);
}

.check-icon {
  position: absolute;
  top: 12px;
  right: 12px;
  color: var(--el-color-primary);
  font-size: 20px;
}

.mailer-info h4 {
  margin: 0;
  font-size: 15px;
}

.mailer-info p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 表单 */
.config-form {
  max-width: 500px;
  margin: 0 auto;
}

/* 步骤操作 */
.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-lighter);
}

/* 验证内容 */
.verify-content {
  max-width: 600px;
  margin: 0 auto;
}

.verify-summary {
  margin-bottom: 24px;
}

.verify-test {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

/* 下一步行动 */
.next-actions-card {
  margin-top: 20px;
}

.next-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.next-action {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.next-action:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.next-action .arrow {
  margin-left: auto;
  color: var(--el-text-color-secondary);
}

.next-action-content h4 {
  margin: 0;
  font-size: 14px;
}

.next-action-content p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 消息卡片 */
.messages-card,
.send-notification-card {
  margin-bottom: 20px;
}

.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

/* 发送表单 */
.send-form {
  max-width: 600px;
  padding: 20px 0;
}

/* 分页 */
.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

</style>
