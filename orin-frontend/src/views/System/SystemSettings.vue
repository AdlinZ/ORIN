<template>
  <div class="settings-container">
    <PageHeader
      title="系统设置"
      description="配置邮件服务、告警通知、系统参数"
      icon="Setting"
    />

    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- 邮件服务配置 -->
      <el-tab-pane label="邮件服务" name="mail">
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>邮件 SMTP 配置</span>
              <el-tag :type="mailStatus.connected ? 'success' : 'info'">
                {{ mailStatus.connected ? '已配置' : '未配置' }}
              </el-tag>
            </div>
          </template>

          <el-form :model="mailConfig" label-width="120px">
            <el-form-item label="SMTP 服务器">
              <el-input v-model="mailConfig.host" placeholder="smtp.qq.com" />
            </el-form-item>
            <el-form-item label="端口">
              <el-input-number v-model="mailConfig.port" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="邮箱地址">
              <el-input v-model="mailConfig.username" placeholder="your-email@qq.com" />
            </el-form-item>
            <el-form-item label="授权码/密码">
              <el-input v-model="mailConfig.password" type="password" show-password />
            </el-form-item>
            <el-form-item label="启用 SSL">
              <el-switch v-model="mailConfig.ssl" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="testing" @click="testMail">
                测试连接
              </el-button>
              <el-button type="success" @click="saveMailConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
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

// 邮件配置
const mailConfig = reactive({
  host: 'smtp.qq.com',
  port: 587,
  username: '',
  password: '',
  ssl: true
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

// 系统配置
const systemConfig = reactive({
  appName: 'ORIN',
  historyRetentionDays: 30,
  maxConcurrentRequests: 100,
  auditLogEnabled: true
})

// 测试邮件
const testMail = async () => {
  testing.value = true
  try {
    const res = await request.get('/auth/mail-test', {
      params: { to: mailConfig.username }
    })
    if (res.success) {
      ElMessage.success('测试邮件发送成功')
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    testing.value = false
  }
}

// 保存邮件配置
const saveMailConfig = () => {
  ElMessage.info('配置已保存（前端演示）')
}

// 保存通知配置
const saveNotificationConfig = () => {
  ElMessage.success('通知配置已保存')
}

// 测试通知
const testNotification = async (channel) => {
  try {
    const res = await request.post('/alerts/notification/test', { channel })
    if (res.success) {
      ElMessage.success(`${channel} 测试通知发送成功`)
    } else {
      ElMessage.error(res.message || '发送失败')
    }
  } catch (e) {
    ElMessage.error('测试失败: ' + e.message)
  }
}

// 保存系统配置
const saveSystemConfig = () => {
  ElMessage.success('系统配置已保存')
}

onMounted(() => {
  // 加载配置
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
