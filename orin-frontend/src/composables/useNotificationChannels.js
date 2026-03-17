import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import request from '@/utils/request';
import {
  getNotificationConfig,
  saveNotificationConfig,
  testNotification
} from '@/api/alert';

const DEFAULT_CONFIG = {
  emailEnabled: true,
  emailRecipients: '',
  dingtalkEnabled: false,
  dingtalkWebhook: '',
  wecomEnabled: false,
  wecomWebhook: '',
  criticalOnly: false,
  instantPush: true,
  mergeIntervalMinutes: 0,
  desktopNotification: true,
  notifyEmail: true,
  notifyInapp: true
};

const cloneConfig = (value) => JSON.parse(JSON.stringify(value));

const normalizeConfig = (data = {}) => ({
  emailEnabled: data.emailEnabled ?? true,
  emailRecipients: data.emailRecipients || '',
  dingtalkEnabled: data.dingtalkEnabled ?? false,
  dingtalkWebhook: data.dingtalkWebhook || '',
  wecomEnabled: data.wecomEnabled ?? false,
  wecomWebhook: data.wecomWebhook || '',
  criticalOnly: data.criticalOnly ?? false,
  instantPush: data.instantPush ?? true,
  mergeIntervalMinutes: data.mergeIntervalMinutes ?? 0,
  desktopNotification: data.desktopNotification ?? true,
  notifyEmail: data.notifyEmail ?? true,
  notifyInapp: data.notifyInapp ?? true
});

const enabledChannelCount = (cfg) => {
  const enabledKeys = ['emailEnabled', 'dingtalkEnabled', 'wecomEnabled'];
  return enabledKeys.filter((key) => cfg[key]).length;
};

export const useNotificationChannels = () => {
  const loaded = ref(false);
  const saving = ref(false);
  const config = ref(cloneConfig(DEFAULT_CONFIG));
  const testing = reactive({
    email: false,
    dingtalk: false,
    wecom: false
  });

  const loadConfig = async () => {
    loaded.value = false;
    try {
      const data = await getNotificationConfig();
      config.value = normalizeConfig(data);
    } catch (primaryError) {
      try {
        const data = await request.get('/alert/notification-config');
        config.value = normalizeConfig(data);
      } catch (fallbackError) {
        console.error('加载通知配置失败:', primaryError, fallbackError);
        ElMessage.error('加载通知配置失败，请刷新重试');
      }
    } finally {
      loaded.value = true;
    }
  };

  const validateConfig = () => {
    if (config.value.emailEnabled && !config.value.emailRecipients.trim()) {
      ElMessage.warning('请填写邮件收件人');
      return false;
    }
    if (config.value.dingtalkEnabled && !config.value.dingtalkWebhook.trim()) {
      ElMessage.warning('请填写钉钉 Webhook 地址');
      return false;
    }
    if (config.value.wecomEnabled && !config.value.wecomWebhook.trim()) {
      ElMessage.warning('请填写企业微信 Webhook 地址');
      return false;
    }
    if (enabledChannelCount(config.value) === 0) {
      ElMessage.warning('请至少开启一个通知渠道');
      return false;
    }
    return true;
  };

  const persistConfig = async () => {
    if (saving.value) {
      ElMessage.warning('配置正在保存，请稍候');
      return false;
    }

    saving.value = true;
    try {
      await saveNotificationConfig(config.value);
      return true;
    } catch (primaryError) {
      try {
        await request.post('/alert/notification-config', config.value);
        return true;
      } catch (fallbackError) {
        console.error('保存通知配置失败:', primaryError, fallbackError);
        return false;
      }
    } finally {
      saving.value = false;
    }
  };

  const saveConfig = async () => {
    if (!validateConfig()) {
      return;
    }

    const ok = await persistConfig();
    if (ok) {
      ElMessage.success('配置保存成功');
    } else {
      ElMessage.error('配置保存失败');
    }
  };

  const toggleChannel = async (channel, enabled) => {
    const key = `${channel}Enabled`;
    const previousValue = !enabled;

    if (!enabled && enabledChannelCount(config.value) === 0) {
      config.value[key] = true;
      ElMessage.warning('请至少开启一个通知渠道');
      return;
    }

    const ok = await persistConfig();
    if (ok) {
      const channelName = channel === 'email' ? '邮件' : channel === 'dingtalk' ? '钉钉' : '企业微信';
      ElMessage.success(`${channelName}通知已${enabled ? '开启' : '关闭'}`);
    } else {
      config.value[key] = previousValue;
      ElMessage.error('保存失败，已恢复原状态');
    }
  };

  const testChannel = async (channel, options = {}) => {
    const { requireMailConnected = false, mailConnected = true } = options;

    if (channel === 'email') {
      if (requireMailConnected && !mailConnected) {
        ElMessage.warning('请先完成邮件服务配置');
        return;
      }
      if (!config.value.emailRecipients.trim()) {
        ElMessage.warning('请先填写邮件收件人');
        return;
      }
    }

    if (channel === 'dingtalk' && !config.value.dingtalkWebhook.trim()) {
      ElMessage.warning('请先填写钉钉 Webhook 地址');
      return;
    }

    if (channel === 'wecom' && !config.value.wecomWebhook.trim()) {
      ElMessage.warning('请先填写企业微信 Webhook 地址');
      return;
    }

    testing[channel] = true;
    try {
      let res;
      try {
        res = await testNotification(channel);
      } catch (primaryError) {
        res = await request.post('/alert/notification-test', { channel });
      }

      if (res?.success) {
        ElMessage.success(res.message || '测试通知发送成功');
      } else {
        ElMessage.warning(res?.message || '测试通知发送失败');
      }
    } catch (error) {
      console.error('测试通知失败:', error);
      ElMessage.error('测试通知发送失败');
    } finally {
      testing[channel] = false;
    }
  };

  return {
    loaded,
    saving,
    testing,
    config,
    loadConfig,
    saveConfig,
    toggleChannel,
    testChannel
  };
};
