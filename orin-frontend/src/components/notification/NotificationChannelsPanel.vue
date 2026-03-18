<template>
  <div>
    <div v-if="!loaded" class="loading-tip">
      <el-icon class="is-loading">
        <Loading />
      </el-icon>
      <span>正在加载通知配置...</span>
    </div>

    <div v-else>
      <div class="channel-card" :class="{ 'channel-active': config.emailEnabled }">
        <div class="channel-header">
          <div class="channel-icon email-icon">
            <el-icon :size="24">
              <Message />
            </el-icon>
          </div>
          <div class="channel-info">
            <h3 class="channel-name">
              邮件通知
            </h3>
            <p class="channel-desc">
              通过邮件发送告警通知
            </p>
          </div>
          <el-switch
            v-model="config.emailEnabled"
            @change="(value) => onToggleChannel('email', value)"
          />
        </div>
        <div v-show="config.emailEnabled" class="channel-config">
          <el-form label-width="100px">
            <el-form-item label="收件人">
              <el-input
                v-model="config.emailRecipients"
                type="textarea"
                :rows="2"
                placeholder="多个收件人用逗号分隔，如: user1@example.com, user2@example.com"
              />
            </el-form-item>
          </el-form>
          <div class="channel-actions">
            <el-button
              type="primary"
              size="small"
              :loading="testing.email"
              :disabled="requireMailConnectedForEmailTest && !mailConnected"
              @click="onTestChannel('email')"
            >
              发送测试
            </el-button>
          </div>
        </div>
      </div>

      <div class="channel-card" :class="{ 'channel-active': config.dingtalkEnabled }">
        <div class="channel-header">
          <div class="channel-icon dingtalk-icon">
            <el-icon :size="24">
              <ChatDotRound />
            </el-icon>
          </div>
          <div class="channel-info">
            <h3 class="channel-name">
              钉钉通知
            </h3>
            <p class="channel-desc">
              通过钉钉机器人发送告警通知
            </p>
          </div>
          <el-switch
            v-model="config.dingtalkEnabled"
            @change="(value) => onToggleChannel('dingtalk', value)"
          />
        </div>
        <div v-show="config.dingtalkEnabled" class="channel-config">
          <el-form label-width="100px">
            <el-form-item label="Webhook">
              <el-input
                v-model="config.dingtalkWebhook"
                placeholder="钉钉机器人 Webhook 地址"
              />
              <div class="form-tip">
                <el-link type="primary" :underline="false" @click="openDingtalkHelp">
                  如何获取 Webhook？
                </el-link>
              </div>
            </el-form-item>
          </el-form>
          <div class="channel-actions">
            <el-button
              type="primary"
              size="small"
              :loading="testing.dingtalk"
              @click="onTestChannel('dingtalk')"
            >
              发送测试
            </el-button>
          </div>
        </div>
      </div>

      <div class="channel-card" :class="{ 'channel-active': config.wecomEnabled }">
        <div class="channel-header">
          <div class="channel-icon wecom-icon">
            <el-icon :size="24">
              <Connection />
            </el-icon>
          </div>
          <div class="channel-info">
            <h3 class="channel-name">
              企业微信通知
            </h3>
            <p class="channel-desc">
              通过企业微信机器人发送告警通知
            </p>
          </div>
          <el-switch
            v-model="config.wecomEnabled"
            @change="(value) => onToggleChannel('wecom', value)"
          />
        </div>
        <div v-show="config.wecomEnabled" class="channel-config">
          <el-form label-width="100px">
            <el-form-item label="Webhook">
              <el-input
                v-model="config.wecomWebhook"
                placeholder="企业微信机器人 Webhook 地址"
              />
              <div class="form-tip">
                <el-link type="primary" :underline="false" @click="openWecomHelp">
                  如何获取 Webhook？
                </el-link>
              </div>
            </el-form-item>
          </el-form>
          <div class="channel-actions">
            <el-button
              type="primary"
              size="small"
              :loading="testing.wecom"
              @click="onTestChannel('wecom')"
            >
              发送测试
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <el-card class="preference-card">
      <template #header>
        <div class="card-header">
          <span><el-icon><Setting /></el-icon> 通知偏好设置</span>
        </div>
      </template>

      <div class="preference-grid">
        <div class="preference-item">
          <div class="preference-label">
            <h4>仅接收关键告警</h4>
            <p>只接收 CRITICAL 和 ERROR 级别的告警通知</p>
          </div>
          <el-switch v-model="config.criticalOnly" />
        </div>

        <div class="preference-item">
          <div class="preference-label">
            <h4>失败立即推送</h4>
            <p>发送失败时立即推送通知，不等待合并</p>
          </div>
          <el-switch v-model="config.instantPush" />
        </div>

        <div class="preference-item">
          <div class="preference-label">
            <h4>低优先级合并推送</h4>
            <p>将低优先级通知合并后在指定时间发送摘要</p>
          </div>
          <el-select v-model="config.mergeIntervalMinutes" style="width: 140px;">
            <el-option :value="0" label="不合并" />
            <el-option :value="5" label="每 5 分钟" />
            <el-option :value="10" label="每 10 分钟" />
            <el-option :value="30" label="每 30 分钟" />
            <el-option :value="60" label="每小时" />
          </el-select>
        </div>

        <div class="preference-item">
          <div class="preference-label">
            <h4>通知方式</h4>
            <p>选择接收通知的方式</p>
          </div>
          <div class="notification-types">
            <el-checkbox v-model="config.notifyEmail">
              邮件
            </el-checkbox>
            <el-checkbox v-model="config.notifyInapp">
              站内通知
            </el-checkbox>
            <el-checkbox v-model="config.desktopNotification">
              桌面推送
            </el-checkbox>
          </div>
        </div>
      </div>
    </el-card>

    <div class="action-bar">
      <el-button
        type="primary"
        size="large"
        :loading="saving"
        @click="saveConfig"
      >
        保存配置
      </el-button>
    </div>

    <div v-if="showStatusInfo" class="status-info">
      <el-alert
        title="配置说明"
        type="info"
        :closable="false"
        show-icon
      >
        <template #default>
          <ul class="status-list">
            <li>至少需要开启一个通知渠道并完成配置</li>
            <li v-if="showMailCenterLink">
              邮件通知需要先在
              <el-link type="primary" :underline="false" @click="$emit('go-mail-center')">
                邮件中心
              </el-link>
              配置 SMTP 服务器
            </li>
            <li v-else-if="mailDependencyText">
              {{ mailDependencyText }}
            </li>
            <li>钉钉和企业微信需要配置机器人 Webhook 地址</li>
            <li>点击"发送测试"可以验证渠道配置是否正确</li>
          </ul>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<script setup>
import { onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Message, ChatDotRound, Connection, Setting, Loading } from '@element-plus/icons-vue';
import { useNotificationChannels } from '@/composables/useNotificationChannels';

const props = defineProps({
  mailConnected: {
    type: Boolean,
    default: true
  },
  requireMailConnectedForEmailTest: {
    type: Boolean,
    default: false
  },
  showStatusInfo: {
    type: Boolean,
    default: true
  },
  showMailCenterLink: {
    type: Boolean,
    default: false
  },
  mailDependencyText: {
    type: String,
    default: ''
  }
});

defineEmits(['go-mail-center']);

const {
  loaded,
  saving,
  testing,
  config,
  loadConfig,
  saveConfig,
  toggleChannel,
  testChannel
} = useNotificationChannels();

const onToggleChannel = async (channel, enabled) => {
  await toggleChannel(channel, enabled);
};

const onTestChannel = async (channel) => {
  await testChannel(channel, {
    requireMailConnected: props.requireMailConnectedForEmailTest,
    mailConnected: props.mailConnected
  });
};

const openDingtalkHelp = () => {
  ElMessage.info('请在钉钉群设置中添加自定义机器人，复制其 Webhook 地址填入上方');
};

const openWecomHelp = () => {
  ElMessage.info('请在企业微信群中添加自定义机器人，复制其 Webhook 地址填入上方');
};

onMounted(() => {
  loadConfig();
});
</script>

<style scoped>
.loading-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 40px;
  color: var(--el-text-color-secondary);
}

.loading-tip .el-icon {
  font-size: 20px;
}

.channels-grid {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 24px;
}

.channel-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px;
  transition: all 0.3s ease;
}

.channel-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.channel-card.channel-active {
  border-color: #409eff;
  background: linear-gradient(135deg, #f0f9ff 0%, #fff 100%);
}

.channel-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.channel-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.email-icon {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.dingtalk-icon {
  background: linear-gradient(135deg, #2ba471 0%, #1e8e5e 100%);
}

.wecom-icon {
  background: linear-gradient(135deg, #07c160 0%, #06ad56 100%);
}

.channel-info {
  flex: 1;
}

.channel-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 4px 0;
}

.channel-desc {
  font-size: 13px;
  color: #909399;
  margin: 0;
}

.channel-config {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #ebeef5;
}

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.channel-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.preference-card {
  margin-top: 24px;
}

.preference-card .card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.preference-grid {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.preference-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.preference-label h4 {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 500;
}

.preference-label p {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.notification-types {
  display: flex;
  gap: 16px;
}

.action-bar {
  display: flex;
  justify-content: center;
  margin: 24px 0;
}

.status-info {
  margin-top: 24px;
}

.status-list {
  margin: 8px 0 0 0;
  padding-left: 20px;
  font-size: 13px;
  color: #606266;
}

.status-list li {
  margin-bottom: 4px;
}
</style>
