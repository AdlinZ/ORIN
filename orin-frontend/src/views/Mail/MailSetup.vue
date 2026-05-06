<template>
  <div class="notification-channels-page page-container fade-in">
    <section class="notification-console">
      <header class="notification-hero">
        <div class="notification-hero-row">
          <div class="notification-hero-main">
            <div class="notification-icon">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="notification-title-block">
              <h1>通知设置</h1>
              <p>统一管理系统事件投递、外部通知渠道，以及邮件中心的收发和模板运营。</p>
            </div>
          </div>

          <div v-if="activeWorkspace === 'channels'" class="notification-hero-actions">
            <span class="status-chip">{{ enabledChannelCount }} 个渠道已启用</span>
            <span class="status-chip" :class="{ warning: notifConfig.criticalOnly }">
              {{ notifConfig.criticalOnly ? '仅关键告警' : '全部事件' }}
            </span>
            <el-button type="primary" :loading="notifSaving" @click="notifSaveConfig">
              保存配置
            </el-button>
          </div>
        </div>

        <div class="workspace-switch" aria-label="通知设置工作区">
          <button
            v-for="workspace in workspaces"
            :key="workspace.key"
            type="button"
            :class="['workspace-card', { active: activeWorkspace === workspace.key }]"
            @click="switchWorkspace(workspace.key)"
          >
            <el-icon><component :is="workspace.icon" /></el-icon>
            <span>
              <strong>{{ workspace.label }}</strong>
              <small>{{ workspace.summary }}</small>
            </span>
          </button>
        </div>

        <div v-if="activeWorkspace === 'channels'" class="notification-summary" data-testid="notification-workspaces">
          <article
            v-for="item in summaryCards"
            :key="item.key"
            class="notification-summary-card"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>
              <strong>{{ item.label }}</strong>
              <small>{{ item.value }}</small>
            </span>
          </article>
        </div>
      </header>

      <main v-if="activeWorkspace === 'channels'" v-loading="!notifConfigLoaded" class="notification-content-panel">
        <el-alert
          v-if="enabledChannelCount === 0"
          class="notification-alert"
          type="warning"
          :closable="false"
          show-icon
          title="至少需要启用一个通知渠道，避免关键事件无人接收。"
        />

        <div class="channels-layout">
          <section class="channel-grid" aria-label="通知渠道列表">
            <article class="channel-card" :class="{ active: notifConfig.emailEnabled }">
              <div class="channel-card-head">
                <div class="channel-title">
                  <el-icon><Message /></el-icon>
                  <span>
                    <strong>邮件通知</strong>
                    <small>面向管理员、运维和值班邮箱的基础通知通道</small>
                  </span>
                </div>
                <el-switch
                  v-model="notifConfig.emailEnabled"
                  @change="(value) => notifToggleChannel('email', value)"
                />
              </div>
              <el-input
                v-model="notifConfig.emailRecipients"
                type="textarea"
                :rows="4"
                placeholder="多个邮箱可用逗号、分号或换行分隔"
              />
              <div class="channel-actions">
                <el-button
                  size="small"
                  :loading="notifTesting.email"
                  @click="notifTestChannel('email')"
                >
                  测试邮件
                </el-button>
              </div>
            </article>

            <article class="channel-card" :class="{ active: notifConfig.dingtalkEnabled }">
              <div class="channel-card-head">
                <div class="channel-title">
                  <el-icon><ChatDotRound /></el-icon>
                  <span>
                    <strong>钉钉群机器人</strong>
                    <small>适合服务告警、同步异常和团队实时协同场景</small>
                  </span>
                </div>
                <el-switch
                  v-model="notifConfig.dingtalkEnabled"
                  @change="(value) => notifToggleChannel('dingtalk', value)"
                />
              </div>
              <el-input
                v-model="notifConfig.dingtalkWebhook"
                placeholder="https://oapi.dingtalk.com/robot/send?access_token=..."
                show-password
              />
              <div class="channel-actions">
                <el-button size="small" text @click="openDingtalkHelp">
                  配置说明
                </el-button>
                <el-button
                  size="small"
                  :loading="notifTesting.dingtalk"
                  @click="notifTestChannel('dingtalk')"
                >
                  测试钉钉
                </el-button>
              </div>
            </article>

            <article class="channel-card" :class="{ active: notifConfig.wecomEnabled }">
              <div class="channel-card-head">
                <div class="channel-title">
                  <el-icon><OfficeBuilding /></el-icon>
                  <span>
                    <strong>企业微信群机器人</strong>
                    <small>用于企业微信工作群、值班群和安全事件同步</small>
                  </span>
                </div>
                <el-switch
                  v-model="notifConfig.wecomEnabled"
                  @change="(value) => notifToggleChannel('wecom', value)"
                />
              </div>
              <el-input
                v-model="notifConfig.wecomWebhook"
                placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..."
                show-password
              />
              <div class="channel-actions">
                <el-button size="small" text @click="openWecomHelp">
                  配置说明
                </el-button>
                <el-button
                  size="small"
                  :loading="notifTesting.wecom"
                  @click="notifTestChannel('wecom')"
                >
                  测试企微
                </el-button>
              </div>
            </article>
          </section>

          <aside class="policy-panel">
            <div class="section-title">
              投递策略
            </div>

            <div class="policy-grid">
              <label class="policy-item">
                <span>
                  <strong>仅推送关键告警</strong>
                  <small>过滤低优先级事件，减少日常干扰</small>
                </span>
                <el-switch v-model="notifConfig.criticalOnly" />
              </label>
              <label class="policy-item">
                <span>
                  <strong>即时推送</strong>
                  <small>关闭后按合并窗口集中投递</small>
                </span>
                <el-switch v-model="notifConfig.instantPush" />
              </label>
              <label class="policy-item">
                <span>
                  <strong>站内通知</strong>
                  <small>在控制台消息中心保留通知记录</small>
                </span>
                <el-switch v-model="notifConfig.notifyInapp" />
              </label>
              <label class="policy-item">
                <span>
                  <strong>邮件投递</strong>
                  <small>允许策略层向邮件渠道投递事件</small>
                </span>
                <el-switch v-model="notifConfig.notifyEmail" />
              </label>
              <label class="policy-item">
                <span>
                  <strong>桌面提醒</strong>
                  <small>允许浏览器桌面通知提示</small>
                </span>
                <el-switch v-model="notifConfig.desktopNotification" />
              </label>
              <div class="policy-item merge-item">
                <span>
                  <strong>合并窗口</strong>
                  <small>分钟，0 表示不合并</small>
                </span>
                <el-input-number
                  v-model="notifConfig.mergeIntervalMinutes"
                  :min="0"
                  :max="1440"
                  controls-position="right"
                />
              </div>
            </div>
          </aside>
        </div>
      </main>

      <main v-else class="mail-center-panel">
        <MailWorkbench embedded />
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  ChatDotRound,
  Connection,
  Message,
  OfficeBuilding,
  Timer
} from '@element-plus/icons-vue'
import { getNotificationConfig, saveNotificationConfig, testNotificationChannel } from '@/api/alert'
import MailWorkbench from '@/views/Mail/MailWorkbench.vue'

const route = useRoute()
const router = useRouter()

const getInitialWorkspace = () => {
  const tab = String(route.query.tab || '').toLowerCase()
  if (tab && !['notification-channels', 'channels'].includes(tab)) return 'mail'
  return String(route.query.workspace || '') === 'mail' ? 'mail' : 'channels'
}

const activeWorkspace = ref(getInitialWorkspace())
const workspaces = [
  {
    key: 'channels',
    label: '通知渠道',
    title: '通知渠道',
    description: '统一管理系统告警、任务状态和关键事件的外部投递渠道。',
    summary: '告警、机器人、站内通知',
    icon: Bell
  },
  {
    key: 'mail',
    label: '邮件中心',
    title: '邮件中心',
    description: '管理邮件服务配置、收件箱、发送模板与邮件投递记录。',
    summary: '收件箱、发信、模板、追踪',
    icon: Message
  }
]

const switchWorkspace = (workspace) => {
  activeWorkspace.value = workspace
  const nextQuery = { ...route.query, workspace }
  if (workspace === 'channels') nextQuery.tab = 'notification-channels'
  else if (!nextQuery.tab || nextQuery.tab === 'notification-channels') nextQuery.tab = 'overview'
  router.replace({ path: route.path, query: nextQuery }).catch(() => {})
}

watch(
  () => route.query.workspace,
  () => {
    activeWorkspace.value = getInitialWorkspace()
  }
)

const notifConfigLoaded = ref(false)
const notifSaving = ref(false)
const notifTesting = reactive({ email: false, dingtalk: false, wecom: false })
const notifConfig = reactive({
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
})

const enabledChannelCount = computed(() => {
  return ['emailEnabled', 'dingtalkEnabled', 'wecomEnabled']
    .filter((key) => notifConfig[key])
    .length
})

const robotChannelCount = computed(() => {
  return [notifConfig.dingtalkEnabled, notifConfig.wecomEnabled].filter(Boolean).length
})

const summaryCards = computed(() => [
  {
    key: 'channels',
    label: '外部渠道',
    value: `${enabledChannelCount.value} 个已启用`,
    icon: Connection
  },
  {
    key: 'email',
    label: '邮件接收人',
    value: notifConfig.emailRecipients.trim() ? '已配置' : '未配置',
    icon: Message
  },
  {
    key: 'robots',
    label: '群机器人',
    value: `${robotChannelCount.value} 个已启用`,
    icon: ChatDotRound
  },
  {
    key: 'policy',
    label: '投递节奏',
    value: notifConfig.instantPush ? '即时推送' : `${notifConfig.mergeIntervalMinutes || 0} 分钟合并`,
    icon: Timer
  }
])

const loadNotifConfig = async () => {
  notifConfigLoaded.value = false
  try {
    const data = await getNotificationConfig()
    notifConfig.emailEnabled = data.emailEnabled ?? true
    notifConfig.emailRecipients = data.emailRecipients || ''
    notifConfig.dingtalkEnabled = data.dingtalkEnabled ?? false
    notifConfig.dingtalkWebhook = data.dingtalkWebhook || ''
    notifConfig.wecomEnabled = data.wecomEnabled ?? false
    notifConfig.wecomWebhook = data.wecomWebhook || ''
    notifConfig.criticalOnly = data.criticalOnly ?? false
    notifConfig.instantPush = data.instantPush ?? true
    notifConfig.mergeIntervalMinutes = data.mergeIntervalMinutes ?? 0
    notifConfig.desktopNotification = data.desktopNotification ?? true
    notifConfig.notifyEmail = data.notifyEmail ?? true
    notifConfig.notifyInapp = data.notifyInapp ?? true
  } catch (error) {
    console.error('load notification config failed', error)
    ElMessage.error('通知渠道配置加载失败')
  } finally {
    notifConfigLoaded.value = true
  }
}

const validateNotifConfig = () => {
  if (notifConfig.emailEnabled && !notifConfig.emailRecipients.trim()) {
    ElMessage.warning('请填写邮件收件人')
    return false
  }
  if (notifConfig.dingtalkEnabled && !notifConfig.dingtalkWebhook.trim()) {
    ElMessage.warning('请填写钉钉 Webhook 地址')
    return false
  }
  if (notifConfig.wecomEnabled && !notifConfig.wecomWebhook.trim()) {
    ElMessage.warning('请填写企业微信 Webhook 地址')
    return false
  }
  if (enabledChannelCount.value === 0) {
    ElMessage.warning('请至少开启一个通知渠道')
    return false
  }
  return true
}

const notifPersistConfig = async () => {
  if (notifSaving.value) return false
  if (!validateNotifConfig()) return false
  notifSaving.value = true
  try {
    await saveNotificationConfig({ ...notifConfig })
    return true
  } catch (error) {
    console.error('save notification config failed', error)
    return false
  } finally {
    notifSaving.value = false
  }
}

const notifSaveConfig = async () => {
  const ok = await notifPersistConfig()
  if (ok) ElMessage.success('通知渠道配置已保存')
  else if (!notifSaving.value) ElMessage.error('通知渠道配置保存失败')
}

const notifToggleChannel = async (channel, enabled) => {
  const key = `${channel}Enabled`
  if (!enabled && enabledChannelCount.value === 0) {
    notifConfig[key] = true
    ElMessage.warning('请至少开启一个通知渠道')
    return
  }

  if (enabled) {
    if (channel === 'email' && !notifConfig.emailRecipients.trim()) {
      notifConfig[key] = false
      ElMessage.warning('请先填写邮件收件人再开启邮件通知')
      return
    }
    if (channel === 'dingtalk' && !notifConfig.dingtalkWebhook.trim()) {
      notifConfig[key] = false
      ElMessage.warning('请先填写钉钉 Webhook 地址再开启钉钉通知')
      return
    }
    if (channel === 'wecom' && !notifConfig.wecomWebhook.trim()) {
      notifConfig[key] = false
      ElMessage.warning('请先填写企业微信 Webhook 地址再开启企业微信通知')
      return
    }
  }

  const ok = await notifPersistConfig()
  if (ok) {
    const name = channel === 'email' ? '邮件' : channel === 'dingtalk' ? '钉钉' : '企业微信'
    ElMessage.success(`${name}通知已${enabled ? '开启' : '关闭'}`)
  }
}

const notifTestChannel = async (channel) => {
  if (channel === 'email' && !notifConfig.emailRecipients.trim()) {
    ElMessage.warning('请先填写邮件收件人')
    return
  }
  if (channel === 'dingtalk' && !notifConfig.dingtalkWebhook.trim()) {
    ElMessage.warning('请先填写钉钉 Webhook 地址')
    return
  }
  if (channel === 'wecom' && !notifConfig.wecomWebhook.trim()) {
    ElMessage.warning('请先填写企业微信 Webhook 地址')
    return
  }

  notifTesting[channel] = true
  try {
    const res = await testNotificationChannel(channel)
    if (res?.success) ElMessage.success(res.message || '测试通知发送成功')
    else ElMessage.warning(res?.message || '测试通知发送失败')
  } catch (error) {
    console.error('test notification channel failed', error)
    ElMessage.error('测试通知发送失败')
  } finally {
    notifTesting[channel] = false
  }
}

const openDingtalkHelp = () => {
  ElMessage.info('请在钉钉群设置中添加自定义机器人，复制其 Webhook 地址填入上方')
}

const openWecomHelp = () => {
  ElMessage.info('请在企业微信群中添加自定义机器人，复制其 Webhook 地址填入上方')
}

onMounted(() => {
  loadNotifConfig()
})
</script>

<style scoped>
.notification-channels-page {
  color: #243244;
}

.notification-console {
  overflow: visible;
  border: 1px solid var(--orin-border, #e2e8f0);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
  box-shadow: 0 14px 36px -34px rgba(15, 23, 42, 0.5);
}

.notification-hero {
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--orin-border, #e2e8f0);
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.82), rgba(255, 255, 255, 0.96) 48%),
    var(--neutral-white, #ffffff);
}

.notification-hero-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.notification-hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  min-width: 0;
}

.notification-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(240, 253, 250, 0.78);
  color: var(--orin-primary, #0d9488);
  font-size: 18px;
}

.notification-title-block {
  min-width: 0;
}

.notification-title-block h1 {
  margin: 0;
  color: #0f172a;
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: 0;
}

.notification-title-block p {
  margin: 7px 0 0;
  max-width: 760px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.notification-hero-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.workspace-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 260px));
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.workspace-card {
  min-width: 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: #475569;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.workspace-card:hover,
.workspace-card.active {
  border-color: rgba(15, 118, 110, 0.22);
  background: #ffffff;
  color: var(--orin-primary, #0d9488);
  box-shadow: 0 8px 18px -16px rgba(15, 23, 42, 0.45);
}

.workspace-card .el-icon {
  margin-top: 2px;
  color: var(--orin-primary, #0d9488);
}

.workspace-card span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.workspace-card strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.workspace-card small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border: 1px solid #d9e2ef;
  border-radius: 999px;
  background: #f4f8ff;
  color: #2c3e56;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.status-chip.warning {
  border-color: #f5d2a3;
  background: #fff8ee;
  color: #b76a00;
}

.notification-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.notification-summary-card {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
}

.notification-summary-card .el-icon,
.channel-title .el-icon {
  margin-top: 2px;
  color: var(--orin-primary, #0d9488);
}

.notification-summary-card span,
.channel-title span,
.policy-item span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.notification-summary-card strong,
.channel-title strong,
.policy-item strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.notification-summary-card small,
.channel-title small,
.policy-item small {
  overflow: hidden;
  color: #64748b;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
}

.notification-summary-card small {
  white-space: nowrap;
}

.notification-content-panel,
.mail-center-panel {
  padding: 14px;
}

.mail-center-panel :deep(.mail-workbench) {
  min-height: 0;
  padding: 0 !important;
  margin: 0 !important;
  background: transparent !important;
}

.mail-center-panel :deep(.gmail-main) {
  border-radius: 8px;
}

.notification-alert {
  margin-bottom: 12px;
}

.channels-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 0.44fr);
  gap: 14px;
  align-items: start;
}

.channel-grid {
  display: grid;
  gap: 12px;
}

.channel-card,
.policy-panel {
  min-width: 0;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  background: #ffffff;
}

.channel-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.channel-card.active {
  border-color: rgba(15, 118, 110, 0.32);
  box-shadow: 0 10px 22px -20px rgba(15, 23, 42, 0.48);
}

.channel-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.channel-title {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.policy-panel {
  padding: 16px;
  position: sticky;
  top: 12px;
}

.section-title {
  margin-bottom: 12px;
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0;
}

.policy-grid {
  display: grid;
  gap: 10px;
}

.policy-item {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border: 1px solid #e1e8f0;
  border-radius: 8px;
  background: #fbfdff;
}

.merge-item :deep(.el-input-number) {
  width: 150px;
}

.channel-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

html.dark .notification-console,
html.dark .notification-hero,
html.dark .channel-card,
html.dark .policy-panel {
  background: #111827;
  border-color: #243247;
}

html.dark .notification-hero {
  background:
    linear-gradient(135deg, rgba(20, 83, 77, 0.26), rgba(17, 24, 39, 0.96) 48%),
    #111827;
}

html.dark .notification-summary,
html.dark .workspace-switch,
html.dark .policy-item,
html.dark .status-chip {
  background: #111c2f;
  border-color: #243247;
}

html.dark .workspace-card:hover,
html.dark .workspace-card.active {
  background: #111c2f;
  border-color: #243247;
  color: #5eead4;
}

html.dark .notification-title-block h1,
html.dark .workspace-card strong,
html.dark .notification-summary-card strong,
html.dark .channel-title strong,
html.dark .policy-item strong,
html.dark .section-title {
  color: #e2e8f0;
}

html.dark .notification-title-block p,
html.dark .workspace-card small,
html.dark .notification-summary-card small,
html.dark .channel-title small,
html.dark .policy-item small {
  color: #94a3b8;
}

html.dark .status-chip {
  color: #c7d2fe;
}

html.dark .status-chip.warning {
  border-color: #7c5a1a;
  background: #2b2215;
  color: #f3c87a;
}

@media (max-width: 1200px) {
  .notification-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .channel-grid {
    grid-template-columns: 1fr;
  }

  .channels-layout {
    grid-template-columns: 1fr;
  }

  .policy-panel {
    position: static;
  }
}

@media (max-width: 780px) {
  .notification-hero-row {
    flex-direction: column;
  }

  .notification-hero-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .workspace-switch {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .notification-summary,
  .policy-grid {
    grid-template-columns: 1fr;
  }

  .policy-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
