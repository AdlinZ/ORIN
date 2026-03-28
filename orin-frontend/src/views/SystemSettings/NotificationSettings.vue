<template>
  <div class="settings-page">
    <el-card class="settings-card">
      <template #header>
        <div class="card-header">
          <span>通知渠道配置</span>
        </div>
      </template>

      <!-- 邮件通知 -->
      <div class="channel-section">
        <div class="channel-header">
          <div class="channel-info">
            <el-icon size="24">
              <Message />
            </el-icon>
            <div class="channel-text">
              <div class="channel-title">
                邮件通知
              </div>
              <div class="channel-desc">
                通过邮件发送告警通知
              </div>
            </div>
          </div>
          <el-switch v-model="channels.email.enabled" />
        </div>
        <el-form
          v-if="channels.email.enabled"
          :model="channels.email"
          label-width="100px"
          class="channel-form"
        >
          <el-form-item label="收件人">
            <el-input v-model="channels.email.recipients" placeholder="多个邮箱用逗号分隔" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="testChannel('email')">
              发送测试
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <!-- 钉钉通知 -->
      <div class="channel-section">
        <div class="channel-header">
          <div class="channel-info">
            <el-icon size="24">
              <ChatDotRound />
            </el-icon>
            <div class="channel-text">
              <div class="channel-title">
                钉钉通知
              </div>
              <div class="channel-desc">
                通过钉钉机器人发送告警消息
              </div>
            </div>
          </div>
          <el-switch v-model="channels.dingtalk.enabled" />
        </div>
        <el-form
          v-if="channels.dingtalk.enabled"
          :model="channels.dingtalk"
          label-width="100px"
          class="channel-form"
        >
          <el-form-item label="Webhook">
            <el-input v-model="channels.dingtalk.webhook" placeholder="钉钉机器人 Webhook 地址" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="testChannel('dingtalk')">
              发送测试
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <!-- 企业微信通知 -->
      <div class="channel-section">
        <div class="channel-header">
          <div class="channel-info">
            <el-icon size="24">
              <OfficeBuilding />
            </el-icon>
            <div class="channel-text">
              <div class="channel-title">
                企业微信通知
              </div>
              <div class="channel-desc">
                通过企业微信机器人发送告警消息
              </div>
            </div>
          </div>
          <el-switch v-model="channels.wechat.enabled" />
        </div>
        <el-form
          v-if="channels.wechat.enabled"
          :model="channels.wechat"
          label-width="100px"
          class="channel-form"
        >
          <el-form-item label="Webhook">
            <el-input v-model="channels.wechat.webhook" placeholder="企业微信机器人 Webhook 地址" />
          </el-form-item>
          <el-form-item>
            <el-button size="small" @click="testChannel('wechat')">
              发送测试
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- 通知偏好设置 -->
    <el-card class="settings-card" style="margin-top: 16px;">
      <template #header>
        <div class="card-header">
          <span>通知偏好</span>
        </div>
      </template>

      <el-form label-width="150px">
        <el-form-item label="仅关键告警">
          <el-switch v-model="preferences.criticalOnly" />
          <div class="form-tip">
            开启后仅发送严重级别告警
          </div>
        </el-form-item>
        <el-form-item label="失败立即推送">
          <el-switch v-model="preferences.immediateFailure" />
          <div class="form-tip">
            任务失败时立即推送通知
          </div>
        </el-form-item>
        <el-form-item label="低优先级合并">
          <el-switch v-model="preferences.mergeLowPriority" />
          <div class="form-tip">
            低优先级告警合并发送
          </div>
        </el-form-item>
        <el-form-item label="通知方式">
          <el-checkbox-group v-model="preferences.notificationTypes">
            <el-checkbox label="站内">
              站内消息
            </el-checkbox>
            <el-checkbox label="桌面">
              桌面推送
            </el-checkbox>
            <el-checkbox label="邮件">
              邮件
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 保存按钮 -->
    <div class="form-actions">
      <el-button type="primary" :loading="saving" @click="handleSave">
        保存配置
      </el-button>
      <el-button @click="handleReset">
        重置
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getNotificationConfig, saveNotificationConfig, testNotification } from '@/api/notification'

const saving = ref(false)

const channels = reactive({
  email: { enabled: false, recipients: '' },
  dingtalk: { enabled: false, webhook: '' },
  wechat: { enabled: false, webhook: '' }
})

const preferences = reactive({
  criticalOnly: false,
  immediateFailure: true,
  mergeLowPriority: false,
  notificationTypes: ['站内', '邮件']
})

// 加载配置
const loadConfig = async () => {
  try {
    const res = await getNotificationConfig()
    if (res) {
      if (res.channels) {
        Object.assign(channels, res.channels)
      }
      if (res.preferences) {
        Object.assign(preferences, res.preferences)
      }
    }
  } catch (e) {
    console.error('加载通知配置失败:', e)
  }
}

// 保存配置
const handleSave = async () => {
  saving.value = true
  try {
    await saveNotificationConfig({ channels, preferences })
    ElMessage.success('通知配置保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 测试渠道
const testChannel = async (channel) => {
  try {
    await testNotification(channel)
    ElMessage.success(`${channel} 测试消息已发送`)
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || '未知错误'))
  }
}

// 重置
const handleReset = () => {
  loadConfig()
}

onMounted(() => {
  loadConfig()
})
</script>

<style scoped>
.settings-page {
  max-width: 800px;
}

.settings-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.channel-section {
  padding: 8px 0;
}

.channel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.channel-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.channel-text {
  display: flex;
  flex-direction: column;
}

.channel-title {
  font-size: 14px;
  font-weight: 500;
}

.channel-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.channel-form {
  margin-top: 16px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  margin-left: 8px;
}

.form-actions {
  margin-top: 24px;
  padding-top: 24px;
  border-top: 1px solid var(--el-border-color-lighter);
  display: flex;
  gap: 12px;
}
</style>
