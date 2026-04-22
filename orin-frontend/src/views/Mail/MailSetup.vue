<template>
  <div class="mail-workbench gmail-layout">
    <div class="gmail-topbar">
      <div class="brand-block">
        <el-icon :size="18"><Message /></el-icon>
        <span>ORIN Mail</span>
      </div>
      <div class="search-block">
        <el-input v-model="inboxKeyword" placeholder="搜索邮件（发件人、主题、内容）" clearable>
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
      <div class="top-actions">
        <div class="status-cluster">
          <span class="status-chip">{{ unreadInboxCount }} 封未读</span>
          <span class="status-chip" :class="{ warning: !imapConfigured }">
            IMAP {{ imapConfigured ? '已配置' : '未配置' }}
          </span>
        </div>
        <el-button class="top-setting-btn" text @click="switchModule('service')">设置</el-button>
      </div>
    </div>

    <div class="gmail-main">
      <aside class="gmail-sidebar">
        <el-button class="compose-btn" type="primary" @click="switchModule('compose')">撰写</el-button>

        <button class="nav-item" :class="{ active: activeTab === 'inbox' && inboxFolder === 'all' }" @click="switchModule('inbox'); inboxFolder = 'all'">
          收件箱
          <span>{{ inboxList.length }}</span>
        </button>
        <button class="nav-item" :class="{ active: activeTab === 'inbox' && inboxFolder === 'unread' }" @click="switchModule('inbox'); inboxFolder = 'unread'">
          未读
          <span>{{ unreadInboxCount }}</span>
        </button>
        <button class="nav-item" :class="{ active: activeTab === 'inbox' && inboxFolder === 'read' }" @click="switchModule('inbox'); inboxFolder = 'read'">
          已读
          <span>{{ Math.max(inboxList.length - unreadInboxCount, 0) }}</span>
        </button>
        <button class="nav-item" :class="{ active: activeTab === 'tracking' }" @click="switchModule('tracking')">
          已发送追踪
          <span>{{ trackingTotal }}</span>
        </button>
        <button class="nav-item" :class="{ active: activeTab === 'templates' }" @click="switchModule('templates')">模板</button>
        <button class="nav-item" :class="{ active: activeTab === 'service' }" @click="switchModule('service')">邮箱设置</button>

        <div class="sidebar-divider" />
        <div class="sidebar-group-title">快捷操作</div>
        <el-button class="quick-action-btn" size="small" :loading="fetchingInbox" :disabled="!imapConfigured" @click="fetchInboxAction">拉取新邮件</el-button>
        <el-button class="quick-action-btn" size="small" :loading="loadingInbox" :disabled="!imapConfigured" @click="loadInbox">刷新列表</el-button>
        <div v-if="!imapConfigured" class="sidebar-tip">请先在“邮箱设置”完成 IMAP 配置</div>
      </aside>

      <section class="gmail-content">
        <template v-if="activeTab === 'inbox'">
          <div class="list-toolbar">
            <div class="toolbar-left">
              <el-checkbox
                :model-value="filteredInboxList.length > 0 && selectedMailIds.length === filteredInboxList.length"
                :disabled="!imapConfigured"
                @change="toggleSelectAllMails"
              >
                全选
              </el-checkbox>
              <el-button text @click="batchMarkRead" :disabled="!imapConfigured || selectedMailIds.length === 0">标记已读</el-button>
              <el-button text type="danger" @click="batchDeleteMail" :disabled="!imapConfigured || selectedMailIds.length === 0">删除</el-button>
            </div>
            <div class="toolbar-right">
              <el-tag type="info">已选 {{ selectedMailIds.length }}</el-tag>
              <el-tag :type="mailConnected ? 'success' : 'warning'">{{ mailConnected ? '邮件服务已连接' : '邮件服务未连接' }}</el-tag>
            </div>
          </div>

          <div class="mail-split">
            <div class="mail-list" :class="{ 'is-empty': !loadingInbox && filteredInboxList.length === 0 }">
              <div
                v-for="mail in filteredInboxList"
                :key="mail.id"
                class="mail-row"
                :class="{ active: selectedInboxMail?.id === mail.id, unread: isInboxUnread(mail) }"
                @click="openInboxMail(mail)"
              >
                <div class="mail-row-left">
                  <el-checkbox :model-value="selectedMailIds.includes(mail.id)" @change="(v) => toggleMailSelection(mail.id, v)" @click.stop />
                  <button class="star-btn" @click.stop="toggleStar(mail)">{{ mail.starred ? '★' : '☆' }}</button>
                </div>
                <div class="mail-row-content">
                  <div class="from">{{ mail.fromEmail || '-' }}</div>
                  <div class="subject">{{ mail.subject || '(无主题)' }}</div>
                </div>
                <div class="time">{{ formatDateTime(mail.receivedAt) }}</div>
              </div>
              <div v-if="!loadingInbox && !imapConfigured" class="mail-empty">
                <div class="mail-empty-card">
                  <div class="mail-empty-title">IMAP 尚未配置</div>
                  <div class="mail-empty-desc">配置后即可自动拉取并显示收件箱邮件。</div>
                  <div class="mail-empty-actions">
                    <el-button type="primary" @click="switchModule('service')">去配置 IMAP</el-button>
                  </div>
                </div>
              </div>
              <div v-else-if="!loadingInbox && filteredInboxList.length === 0" class="mail-empty">
                <div class="mail-empty-card">
                  <div class="mail-empty-title">当前没有邮件</div>
                  <div class="mail-empty-desc">可以先拉取最新邮件，或更换筛选条件查看历史记录。</div>
                  <div class="mail-empty-actions">
                    <el-button type="primary" :loading="fetchingInbox" @click="fetchInboxAction">拉取新邮件</el-button>
                  </div>
                </div>
              </div>
            </div>

            <div class="mail-reader">
              <template v-if="selectedInboxMail">
                <div class="reader-header">
                  <h3>{{ selectedInboxMail.subject || '(无主题)' }}</h3>
                  <div class="reader-meta">
                    <span>发件人：{{ selectedInboxMail.fromEmail || '-' }}</span>
                    <span>收件人：{{ selectedInboxMail.toEmail || '-' }}</span>
                    <span>时间：{{ formatDateTime(selectedInboxMail.receivedAt) }}</span>
                  </div>
                  <div class="reader-actions">
                    <el-button size="small" @click="markInboxRead(selectedInboxMail)" :disabled="!isInboxUnread(selectedInboxMail)">已读</el-button>
                    <el-button size="small" type="danger" @click="deleteInboxMailAction(selectedInboxMail)">删除</el-button>
                  </div>
                </div>
                <div class="reader-body" v-html="selectedInboxMail.contentHtml || selectedInboxMail.content || '-'" />
              </template>
              <div v-else class="reader-empty">
                <div class="reader-empty-card">
                  <div class="reader-empty-title">{{ imapConfigured ? '选择一封邮件开始阅读' : '先完成 IMAP 配置后再查看邮件详情' }}</div>
                  <div class="mail-empty-desc">
                    {{ imapConfigured ? '左侧列表支持按未读/已读筛选，并可直接批量处理。' : '配置路径：邮箱设置 -> 启用 IMAP -> 填写服务器与账号信息。' }}
                  </div>
                  <div v-if="!imapConfigured" class="mail-empty-actions">
                    <el-button type="primary" @click="switchModule('service')">前往邮箱设置</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="inboxPagination.page"
              v-model:page-size="inboxPagination.size"
              :total="inboxPagination.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              @current-change="loadInbox"
              @size-change="loadInbox"
            />
          </div>
        </template>

        <template v-else-if="activeTab === 'compose'">
          <div class="module-sheet compose-sheet">
            <div class="sheet-title">撰写邮件</div>
            <div class="compose-actions">
              <el-button type="primary" :loading="sendingMail" :disabled="!mailConnected" @click="sendMailAction">发送</el-button>
              <el-button @click="openPreviewDialog">预览</el-button>
              <el-button @click="triggerAttachmentPicker">附件</el-button>
              <el-dropdown trigger="click">
                <el-button>
                  发信设置
                  <el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item>
                      <el-checkbox v-model="sendForm.separateSend">分别发送（逐个收件人）</el-checkbox>
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button @click="saveDraft">保存草稿</el-button>
              <el-button @click="applyDefaultTemplate">套用默认模板</el-button>
            </div>
            <el-form :model="sendForm" label-width="90px" class="compose-form compose-qq-form">
              <el-form-item label="收件人" required>
                <div class="recipient-row">
                  <el-input v-model="sendForm.to" placeholder="多个邮箱用逗号分隔" clearable />
                  <el-button text @click="showCc = !showCc">抄送</el-button>
                  <el-button text @click="showBcc = !showBcc">密送</el-button>
                </div>
              </el-form-item>
              <el-form-item v-if="showCc" label="抄送">
                <el-input v-model="sendForm.cc" placeholder="多个邮箱用逗号分隔" clearable />
              </el-form-item>
              <el-form-item v-if="showBcc" label="密送">
                <el-input v-model="sendForm.bcc" placeholder="多个邮箱用逗号分隔" clearable />
              </el-form-item>
              <el-form-item label="主题" required>
                <el-input v-model="sendForm.subject" placeholder="邮件主题" clearable />
              </el-form-item>
              <el-form-item label="模板">
                <el-select v-model="sendForm.templateId" clearable placeholder="可选模板" style="width: 100%;" @change="handleTemplateChange">
                  <el-option v-for="template in templates" :key="template.id" :label="template.name" :value="template.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="templateVariables.length > 0" label="变量">
                <div class="variables-grid">
                  <el-input v-for="field in templateVariables" :key="field" v-model="sendForm.variables[field]" :placeholder="field" />
                </div>
              </el-form-item>
              <el-form-item label="内容" required class="editor-form-item">
                <div class="qq-editor-wrap">
                  <div class="editor-toolbar">
                    <el-button text @click="execEditorCommand('bold')">B</el-button>
                    <el-button text @click="execEditorCommand('italic')"><i>I</i></el-button>
                    <el-button text @click="execEditorCommand('underline')"><u>U</u></el-button>
                    <el-divider direction="vertical" />
                    <el-button text @click="execEditorCommand('insertUnorderedList')">• 列表</el-button>
                    <el-button text @click="execEditorCommand('insertOrderedList')">1. 列表</el-button>
                    <el-button text @click="insertQuote">引用</el-button>
                    <el-button text @click="insertCodeBlock">&lt;/&gt;</el-button>
                    <el-button text @click="insertEmoji">😀</el-button>
                  </div>
                  <div
                    ref="composeEditorRef"
                    class="qq-editor"
                    contenteditable="true"
                    @input="onComposeEditorInput"
                    @paste="onComposeEditorPaste"
                  />
                </div>
              </el-form-item>
              <el-form-item label="附件">
                <div class="attachment-block">
                  <input ref="attachmentInputRef" type="file" multiple style="display: none;" @change="onAttachmentChange" />
                  <el-button @click="triggerAttachmentPicker">添加附件</el-button>
                  <span class="attachment-hint">当前版本先记录附件清单，后端暂不作为 MIME 附件发送。</span>
                  <div v-if="attachments.length > 0" class="attachment-list">
                    <div v-for="(item, idx) in attachments" :key="`${item.name}-${idx}`" class="attachment-item">
                      <span>{{ item.name }} ({{ formatFileSize(item.size) }})</span>
                      <el-button text type="danger" @click="removeAttachment(idx)">移除</el-button>
                    </div>
                  </div>
                </div>
              </el-form-item>
            </el-form>
          </div>
        </template>

        <template v-else-if="activeTab === 'tracking'">
          <div class="module-sheet">
            <div class="sheet-title">发送追踪</div>
            <div class="tab-actions">
              <el-select v-model="trackingFilter.status" placeholder="状态" clearable style="width: 140px;" @change="loadLogs">
                <el-option label="待发送" value="PENDING" />
                <el-option label="成功" value="SUCCESS" />
                <el-option label="失败" value="FAILED" />
              </el-select>
              <el-button :loading="loadingLogs" @click="loadLogs">刷新</el-button>
              <el-button type="warning" :disabled="selectedFailedLogs.length === 0" :loading="retryingBatch" @click="batchRetryLogs">
                批量重试失败 ({{ selectedFailedLogs.length }})
              </el-button>
            </div>
            <el-table v-loading="loadingLogs" :data="logs" border @selection-change="onLogSelectionChange">
              <el-table-column type="selection" width="48" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }"><el-tag :type="getStatusTagType(row.status)">{{ row.status }}</el-tag></template>
              </el-table-column>
              <el-table-column prop="recipients" label="收件人" min-width="220" show-overflow-tooltip />
              <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
              <el-table-column prop="createdAt" label="时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
              </el-table-column>
              <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
              <el-table-column label="操作" width="100" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'FAILED'" text type="warning" :loading="retryingSingleId === row.id" @click="retrySingleLog(row)">重试</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>

        <template v-else-if="activeTab === 'templates'">
          <div class="module-sheet">
            <div class="sheet-title">模板管理</div>
            <div class="tab-actions">
              <el-button type="primary" @click="openTemplateEditor()">新建模板</el-button>
              <el-input v-model="templateSearch" placeholder="按名称或编码过滤" style="width: 260px;" clearable />
            </div>
            <el-table :data="filteredTemplates" border>
              <el-table-column prop="name" label="名称" min-width="150" />
              <el-table-column prop="code" label="编码" min-width="120" />
              <el-table-column prop="subject" label="主题" min-width="220" show-overflow-tooltip />
              <el-table-column label="默认" width="80">
                <template #default="{ row }"><el-tag v-if="row.isDefault" type="success" size="small">默认</el-tag><span v-else>-</span></template>
              </el-table-column>
              <el-table-column label="启用" width="90">
                <template #default="{ row }"><el-switch :model-value="row.enabled" @change="(v) => toggleTemplateEnabled(row, v)" /></template>
              </el-table-column>
              <el-table-column label="操作" width="170" fixed="right">
                <template #default="{ row }">
                  <el-button text type="primary" @click="openTemplateEditor(row)">编辑</el-button>
                  <el-button text type="danger" @click="deleteTemplateAction(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>

        <template v-else>
          <div class="module-sheet">
            <div class="sheet-title">邮箱设置</div>
            <div class="tab-actions">
              <el-button type="primary" :loading="savingConfig" @click="saveMailConfigAction">保存配置</el-button>
              <el-input v-model="testEmail" placeholder="输入测试邮箱" style="width: 320px;" clearable />
              <el-button :loading="testingConfig" :disabled="!mailConnected && !hasConfigDraft" @click="testMailConfigAction">发送测试邮件</el-button>
            </div>

            <el-form :model="mailConfigForm" label-width="140px" class="config-form">
              <el-form-item label="发送方式" required>
                <el-radio-group v-model="mailConfigForm.mailerType">
                  <el-radio-button value="mailersend">MailerSend API</el-radio-button>
                  <el-radio-button value="resend">Resend</el-radio-button>
                  <el-radio-button value="smtp">SMTP</el-radio-button>
                </el-radio-group>
              </el-form-item>

              <template v-if="mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend'">
                <el-form-item label="API Key" required>
                  <el-input v-model="mailConfigForm.apiKey" type="password" show-password placeholder="输入 API Key" />
                </el-form-item>
              </template>
              <template v-else>
                <el-form-item label="SMTP Host" required><el-input v-model="mailConfigForm.smtpHost" placeholder="smtp.example.com" /></el-form-item>
                <el-form-item label="SMTP Port" required><el-input-number v-model="mailConfigForm.smtpPort" :min="1" :max="65535" /></el-form-item>
                <el-form-item label="用户名"><el-input v-model="mailConfigForm.smtpUsername" placeholder="SMTP 用户名" /></el-form-item>
                <el-form-item label="密码"><el-input v-model="mailConfigForm.smtpPassword" type="password" show-password placeholder="SMTP 密码" /></el-form-item>
                <el-form-item label="SSL"><el-switch v-model="mailConfigForm.sslEnabled" /></el-form-item>
              </template>

              <el-form-item label="发件邮箱" required><el-input v-model="mailConfigForm.fromEmail" placeholder="noreply@example.com" /></el-form-item>
              <el-form-item label="发件人名称"><el-input v-model="mailConfigForm.fromName" placeholder="ORIN 系统" /></el-form-item>
              <el-form-item label="启用服务"><el-switch v-model="mailConfigForm.enabled" /></el-form-item>
              <el-divider>IMAP 收件箱配置</el-divider>
              <el-form-item label="启用 IMAP"><el-switch v-model="mailConfigForm.imapEnabled" /></el-form-item>
              <template v-if="mailConfigForm.imapEnabled">
                <el-form-item label="IMAP Host" required><el-input v-model="mailConfigForm.imapHost" placeholder="imap.example.com" /></el-form-item>
                <el-form-item label="IMAP Port" required><el-input-number v-model="mailConfigForm.imapPort" :min="1" :max="65535" /></el-form-item>
                <el-form-item label="IMAP 用户名"><el-input v-model="mailConfigForm.imapUsername" placeholder="邮箱账号" /></el-form-item>
                <el-form-item label="IMAP 密码"><el-input v-model="mailConfigForm.imapPassword" type="password" show-password placeholder="邮箱密码或授权码" /></el-form-item>
              </template>
            </el-form>
          </div>
        </template>
      </section>
    </div>

    <el-dialog v-model="templateDialogVisible" :title="templateEditing.id ? '编辑模板' : '新建模板'" width="680px">
      <el-form :model="templateEditing" label-width="100px">
        <el-form-item label="模板名称" required><el-input v-model="templateEditing.name" /></el-form-item>
        <el-form-item label="模板编码" required><el-input v-model="templateEditing.code" :disabled="Boolean(templateEditing.id)" /></el-form-item>
        <el-form-item label="邮件主题" required><el-input v-model="templateEditing.subject" /></el-form-item>
        <el-form-item label="模板内容" required><el-input v-model="templateEditing.content" type="textarea" :rows="10" /></el-form-item>
        <el-form-item label="默认模板"><el-switch v-model="templateEditing.isDefault" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="templateEditing.enabled" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplateAction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="composePreviewVisible" title="邮件预览" width="900px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="收件人">{{ sendForm.to || '-' }}</el-descriptions-item>
        <el-descriptions-item label="抄送">{{ sendForm.cc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="密送">{{ sendForm.bcc || '-' }}</el-descriptions-item>
        <el-descriptions-item label="主题">{{ renderedSubject || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="preview-body" v-html="renderedContent || '<p>-</p>'" />
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { Message, Search, ArrowDown } from '@element-plus/icons-vue'
import { getNotificationConfig, saveNotificationConfig, testNotificationChannel } from '@/api/alert'
import {
  getMailConfig,
  saveMailConfig,
  testMailConnection,
  sendMail,
  getMailTemplates,
  saveMailTemplate,
  deleteMailTemplate,
  getMailSendLogs,
  retryMailSendLog,
  batchRetryMailSendLogs,
  getMailInbox,
  getUnreadMailCount,
  markMailAsRead,
  deleteMail,
  fetchMail,
  getImapStatus
} from '@/api/mail'

const route = useRoute()
const router = useRouter()

const TAB_MAP = {
  overview: 'inbox',
  service: 'service',
  setup: 'service',
  compose: 'compose',
  templates: 'templates',
  tracking: 'tracking',
  inbox: 'inbox',
  'notification-channels': 'notification-channels'
}

const getTabFromRoute = () => {
  const raw = String(route.query.tab || 'overview').toLowerCase()
  return TAB_MAP[raw] || 'overview'
}

const activeTab = ref(getTabFromRoute())
const moduleNavItems = [
  { key: 'inbox', label: '收件箱' },
  { key: 'compose', label: '发送邮件' },
  { key: 'tracking', label: '发送追踪' },
  { key: 'templates', label: '模板管理' },
  { key: 'service', label: '服务配置' }
]

const mailConfig = ref(null)
const mailConnected = ref(false)
const savingConfig = ref(false)
const testingConfig = ref(false)
const testEmail = ref('')

const mailConfigForm = reactive({
  id: null,
  mailerType: 'mailersend',
  apiKey: '',
  smtpHost: 'smtp.mailersend.net',
  smtpPort: 587,
  smtpUsername: '',
  smtpPassword: '',
  sslEnabled: true,
  fromEmail: '',
  fromName: 'ORIN 系统',
  imapEnabled: false,
  imapHost: '',
  imapPort: 993,
  imapUsername: '',
  imapPassword: '',
  enabled: true
})

const sendForm = reactive({
  to: '',
  cc: '',
  bcc: '',
  subject: '',
  content: '',
  templateId: null,
  variables: {},
  separateSend: false
})
const showCc = ref(false)
const showBcc = ref(false)
const composePreviewVisible = ref(false)
const composeEditorRef = ref(null)
const attachmentInputRef = ref(null)
const attachments = ref([])

const sendingMail = ref(false)
const templates = ref([])
const templateSearch = ref('')
const templateDialogVisible = ref(false)
const savingTemplate = ref(false)
const templateEditing = reactive({
  id: null,
  name: '',
  code: '',
  subject: '',
  content: '',
  enabled: true,
  isDefault: false
})

const logs = ref([])
const loadingLogs = ref(false)
const retryingSingleId = ref(null)
const retryingBatch = ref(false)
const selectedFailedLogs = ref([])
const trackingFilter = reactive({
  status: ''
})
const logPagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const inboxList = ref([])
const loadingInbox = ref(false)
const fetchingInbox = ref(false)
const inboxUnreadCount = ref(0)
const inboxPagination = reactive({
  page: 1,
  size: 20,
  total: 0
})
const imapConfigured = ref(false)
const inboxDetailVisible = ref(false)
const selectedInboxMail = ref(null)
const inboxFolder = ref('all')
const inboxKeyword = ref('')
const selectedMailIds = ref([])

// ==================== 通知渠道 ====================
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

const notifEnabledChannelCount = () => {
  return ['emailEnabled', 'dingtalkEnabled', 'wecomEnabled'].filter((k) => notifConfig[k]).length
}

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
    console.error('load notif config failed', error)
  } finally {
    notifConfigLoaded.value = true
  }
}

const notifPersistConfig = async () => {
  if (notifSaving.value) return false
  notifSaving.value = true
  try {
    await saveNotificationConfig({ ...notifConfig })
    return true
  } catch (error) {
    console.error('save notif config failed', error)
    return false
  } finally {
    notifSaving.value = false
  }
}

const notifSaveConfig = async () => {
  if (notifConfig.emailEnabled && !notifConfig.emailRecipients.trim()) {
    ElMessage.warning('请填写邮件收件人')
    return
  }
  if (notifConfig.dingtalkEnabled && !notifConfig.dingtalkWebhook.trim()) {
    ElMessage.warning('请填写钉钉 Webhook 地址')
    return
  }
  if (notifConfig.wecomEnabled && !notifConfig.wecomWebhook.trim()) {
    ElMessage.warning('请填写企业微信 Webhook 地址')
    return
  }
  if (notifEnabledChannelCount() === 0) {
    ElMessage.warning('请至少开启一个通知渠道')
    return
  }
  const ok = await notifPersistConfig()
  if (ok) ElMessage.success('配置保存成功')
  else ElMessage.error('配置保存失败')
}

const notifToggleChannel = async (channel, enabled) => {
  const key = `${channel}Enabled`
  if (!enabled && notifEnabledChannelCount() === 0) {
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
  if (channel === 'email') {
    if (!notifConfig.emailRecipients.trim()) {
      ElMessage.warning('请先填写邮件收件人')
      return
    }
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

const trackingStats = computed(() => {
  const stats = { pending: 0, success: 0, failed: 0 }
  logs.value.forEach((item) => {
    if (item.status === 'PENDING') stats.pending += 1
    if (item.status === 'SUCCESS') stats.success += 1
    if (item.status === 'FAILED') stats.failed += 1
  })
  return stats
})

const trackingTotal = computed(() => logPagination.total || logs.value.length)

const visibleModuleNavItems = computed(() => moduleNavItems)

const hasConfigDraft = computed(() => {
  if (mailConfigForm.mailerType === 'smtp') {
    return Boolean(mailConfigForm.smtpHost && mailConfigForm.fromEmail)
  }
  return Boolean(mailConfigForm.apiKey && mailConfigForm.fromEmail)
})

const filteredTemplates = computed(() => {
  const keyword = templateSearch.value.trim().toLowerCase()
  if (!keyword) return templates.value
  return templates.value.filter((item) => {
    return String(item.name || '').toLowerCase().includes(keyword)
      || String(item.code || '').toLowerCase().includes(keyword)
  })
})

const templateVariables = computed(() => {
  const values = new Set()
  const match = /{{\s*([a-zA-Z0-9_\-.]+)\s*}}/g
  ;[sendForm.subject, sendForm.content].forEach((text) => {
    if (!text) return
    let result = match.exec(text)
    while (result) {
      values.add(result[1])
      result = match.exec(text)
    }
    match.lastIndex = 0
  })
  return Array.from(values)
})

const unreadInboxCount = computed(() => inboxList.value.filter((item) => !item.read && !item.isRead).length)

const filteredInboxList = computed(() => {
  const keyword = inboxKeyword.value.trim().toLowerCase()
  return inboxList.value.filter((item) => {
    const unread = !item.read && !item.isRead
    if (inboxFolder.value === 'unread' && !unread) return false
    if (inboxFolder.value === 'read' && unread) return false
    if (!keyword) return true
    const full = `${item.fromEmail || ''} ${item.subject || ''} ${item.content || ''}`.toLowerCase()
    return full.includes(keyword)
  })
})

const switchModule = (tab) => {
  activeTab.value = tab
}

const onTabChange = (tab) => {
  router.replace({
    path: route.path,
    query: { ...route.query, tab }
  })
}

const isInboxUnread = (row) => {
  return !row.read && !row.isRead
}

const formatMailerType = (value) => {
  if (value === 'mailersend') return 'MailerSend'
  if (value === 'resend') return 'Resend'
  if (value === 'smtp') return 'SMTP'
  return '-'
}

const formatDateTime = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d} ${h}:${min}`
}

const getStatusTagType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'PENDING') return 'warning'
  return 'info'
}

const syncConfigForm = (cfg) => {
  mailConfigForm.id = cfg?.id || null
  mailConfigForm.mailerType = cfg?.mailerType || 'mailersend'
  mailConfigForm.apiKey = cfg?.apiKey || ''
  mailConfigForm.smtpHost = cfg?.smtpHost || 'smtp.mailersend.net'
  mailConfigForm.smtpPort = cfg?.smtpPort || 587
  mailConfigForm.smtpUsername = cfg?.username || ''
  mailConfigForm.smtpPassword = cfg?.password || ''
  mailConfigForm.sslEnabled = cfg?.sslEnabled !== false
  mailConfigForm.fromEmail = cfg?.fromEmail || ''
  mailConfigForm.fromName = cfg?.fromName || 'ORIN 系统'
  mailConfigForm.imapEnabled = Boolean(cfg?.imapEnabled)
  mailConfigForm.imapHost = cfg?.imapHost || ''
  mailConfigForm.imapPort = cfg?.imapPort || 993
  mailConfigForm.imapUsername = cfg?.imapUsername || ''
  mailConfigForm.imapPassword = cfg?.imapPassword || ''
  mailConfigForm.enabled = cfg?.enabled !== false
}

const loadMailConfigAction = async () => {
  try {
    const data = await getMailConfig()
    mailConfig.value = data || null
    mailConnected.value = Boolean(data?.enabled)
    syncConfigForm(data || {})
  } catch (error) {
    console.error('load mail config failed', error)
  }
}

const loadTemplates = async () => {
  try {
    const data = await getMailTemplates()
    templates.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.error('load templates failed', error)
  }
}

const loadLogs = async () => {
  loadingLogs.value = true
  try {
    const data = await getMailSendLogs({
      page: logPagination.page - 1,
      size: logPagination.size,
      status: trackingFilter.status || undefined
    })
    logs.value = data?.content || []
    logPagination.total = data?.totalElements || 0
  } catch (error) {
    console.error('load logs failed', error)
  } finally {
    loadingLogs.value = false
  }
}

const loadInboxMeta = async () => {
  try {
    const [unread, imap] = await Promise.all([
      getUnreadMailCount(),
      getImapStatus()
    ])
    inboxUnreadCount.value = unread?.count || 0
    imapConfigured.value = Boolean(imap?.configured)
  } catch (error) {
    console.error('load inbox meta failed', error)
  }
}

const loadInbox = async () => {
  loadingInbox.value = true
  try {
    const data = await getMailInbox({
      page: inboxPagination.page - 1,
      size: inboxPagination.size
    })
    inboxList.value = data?.content || []
    inboxPagination.total = data?.totalElements || 0
  } catch (error) {
    console.error('load inbox failed', error)
  } finally {
    loadingInbox.value = false
  }
}

const toggleMailSelection = (id, checked) => {
  const next = new Set(selectedMailIds.value)
  if (checked) next.add(id)
  else next.delete(id)
  selectedMailIds.value = Array.from(next)
}

const toggleSelectAllMails = (checked) => {
  if (checked) {
    selectedMailIds.value = filteredInboxList.value.map((mail) => mail.id)
    return
  }
  selectedMailIds.value = []
}

const batchMarkRead = async () => {
  const selected = inboxList.value.filter((mail) => selectedMailIds.value.includes(mail.id))
  for (const mail of selected) {
    if (!isInboxUnread(mail)) continue
    // eslint-disable-next-line no-await-in-loop
    await markMailAsRead(mail.id)
    mail.isRead = true
  }
  ElMessage.success('已批量标记已读')
  await loadInboxMeta()
}

const batchDeleteMail = async () => {
  if (selectedMailIds.value.length === 0) return
  await ElMessageBox.confirm(`确认删除选中的 ${selectedMailIds.value.length} 封邮件？`, '批量删除', { type: 'warning' })
  for (const id of selectedMailIds.value) {
    // eslint-disable-next-line no-await-in-loop
    await deleteMail(id)
  }
  selectedMailIds.value = []
  ElMessage.success('已批量删除')
  await loadInbox()
  await loadInboxMeta()
}

const toggleStar = (mail) => {
  mail.starred = !mail.starred
}

const saveMailConfigAction = async () => {
  if (!mailConfigForm.fromEmail) {
    ElMessage.warning('请填写发件邮箱')
    return
  }
  if ((mailConfigForm.mailerType === 'mailersend' || mailConfigForm.mailerType === 'resend') && !mailConfigForm.apiKey) {
    ElMessage.warning('请填写 API Key')
    return
  }
  if (mailConfigForm.mailerType === 'smtp' && !mailConfigForm.smtpHost) {
    ElMessage.warning('请填写 SMTP Host')
    return
  }
  if (mailConfigForm.imapEnabled && !mailConfigForm.imapHost) {
    ElMessage.warning('请填写 IMAP Host')
    return
  }

  savingConfig.value = true
  try {
    const payload = {
      id: mailConfigForm.id,
      mailerType: mailConfigForm.mailerType,
      apiKey: mailConfigForm.apiKey,
      smtpHost: mailConfigForm.smtpHost,
      smtpPort: mailConfigForm.smtpPort,
      username: mailConfigForm.smtpUsername,
      password: mailConfigForm.smtpPassword,
      sslEnabled: mailConfigForm.sslEnabled,
      fromEmail: mailConfigForm.fromEmail,
      fromName: mailConfigForm.fromName,
      imapEnabled: mailConfigForm.imapEnabled,
      imapHost: mailConfigForm.imapHost,
      imapPort: mailConfigForm.imapPort,
      imapUsername: mailConfigForm.imapUsername,
      imapPassword: mailConfigForm.imapPassword,
      enabled: mailConfigForm.enabled
    }
    const saved = await saveMailConfig(payload)
    mailConfig.value = saved
    mailConnected.value = Boolean(saved?.enabled)
    syncConfigForm(saved || payload)
    ElMessage.success('邮件配置保存成功')
    await loadInboxMeta()
  } finally {
    savingConfig.value = false
  }
}

const testMailConfigAction = async () => {
  const target = testEmail.value.trim() || mailConfigForm.fromEmail
  if (!target) {
    ElMessage.warning('请先填写测试邮箱')
    return
  }

  testingConfig.value = true
  try {
    const result = await testMailConnection({ testEmail: target })
    if (result?.success) {
      ElMessage.success(result.message || '测试邮件发送成功')
    } else {
      ElMessage.warning(result?.message || '测试邮件发送失败')
    }
  } finally {
    testingConfig.value = false
  }
}

const renderTemplateVariables = (text, keepMissing = false) => {
  if (!text) return ''
  return text.replace(/{{\s*([a-zA-Z0-9_\-.]+)\s*}}/g, (match, key) => {
    const value = sendForm.variables[key]
    if (value === null || value === undefined || String(value).trim() === '') {
      return keepMissing ? match : ''
    }
    return value
  })
}

const renderedSubject = computed(() => renderTemplateVariables(sendForm.subject, true))
const renderedContent = computed(() => renderTemplateVariables(sendForm.content, true))

const escapeHtml = (value) => {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const decorateTemplateVariables = (html) => {
  if (!html) return ''
  return html.replace(/{{\s*([a-zA-Z0-9_\-.]+)\s*}}/g, (_, key) => {
    const value = sendForm.variables[key]
    const text = value === null || value === undefined || String(value).trim() === ''
      ? `{{${key}}}`
      : String(value)
    return `<span class="template-var-chip" contenteditable="false" data-var-key="${escapeHtml(key)}">${escapeHtml(text)}</span>`
  })
}

const recoverTemplateVariables = (html) => {
  if (!html) return ''
  const container = document.createElement('div')
  container.innerHTML = html
  container.querySelectorAll('span.template-var-chip[data-var-key]').forEach((node) => {
    const key = node.getAttribute('data-var-key')
    if (key) {
      node.replaceWith(`{{${key}}}`)
    }
  })
  return container.innerHTML
}

const validateEmails = (value) => {
  const emails = value.split(/[,\n;]/).map((item) => item.trim()).filter(Boolean)
  if (emails.length === 0) return false
  const regex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emails.every((email) => regex.test(email))
}

const syncComposeEditor = (html) => {
  if (!composeEditorRef.value) return
  composeEditorRef.value.innerHTML = decorateTemplateVariables(html || '')
}

const onComposeEditorInput = (event) => {
  sendForm.content = recoverTemplateVariables(event.target.innerHTML)
}

const onComposeEditorPaste = (event) => {
  event.preventDefault()
  const text = event.clipboardData?.getData('text/plain') || ''
  document.execCommand('insertText', false, text)
  sendForm.content = recoverTemplateVariables(composeEditorRef.value?.innerHTML || '')
}

const execEditorCommand = (command) => {
  composeEditorRef.value?.focus()
  document.execCommand(command, false)
  sendForm.content = recoverTemplateVariables(composeEditorRef.value?.innerHTML || '')
}

const insertQuote = () => {
  composeEditorRef.value?.focus()
  document.execCommand('insertHTML', false, '<blockquote style="border-left:3px solid #d0d7e5;padding-left:8px;color:#5c6a80;">引用内容</blockquote>')
  sendForm.content = recoverTemplateVariables(composeEditorRef.value?.innerHTML || '')
}

const insertCodeBlock = () => {
  composeEditorRef.value?.focus()
  document.execCommand('insertHTML', false, '<pre style="background:#f5f7fb;border:1px solid #dce3ef;border-radius:6px;padding:10px;"><code>code here</code></pre>')
  sendForm.content = recoverTemplateVariables(composeEditorRef.value?.innerHTML || '')
}

const insertEmoji = () => {
  composeEditorRef.value?.focus()
  document.execCommand('insertText', false, '😀')
  sendForm.content = recoverTemplateVariables(composeEditorRef.value?.innerHTML || '')
}

const openPreviewDialog = () => {
  composePreviewVisible.value = true
}

const triggerAttachmentPicker = () => {
  attachmentInputRef.value?.click()
}

const onAttachmentChange = (event) => {
  const files = Array.from(event.target.files || [])
  if (files.length === 0) return
  attachments.value = [...attachments.value, ...files.map((file) => ({ name: file.name, size: file.size }))]
  event.target.value = ''
}

const removeAttachment = (index) => {
  attachments.value.splice(index, 1)
}

const formatFileSize = (size) => {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / (1024 * 1024)).toFixed(1)} MB`
}

const sendMailAction = async () => {
  if (!mailConnected.value) {
    ElMessage.warning('请先完成邮件服务配置')
    return
  }
  if (!validateEmails(sendForm.to || '')) {
    ElMessage.warning('收件人邮箱格式不正确')
    return
  }
  if (sendForm.cc && !validateEmails(sendForm.cc)) {
    ElMessage.warning('抄送邮箱格式不正确')
    return
  }
  if (sendForm.bcc && !validateEmails(sendForm.bcc)) {
    ElMessage.warning('密送邮箱格式不正确')
    return
  }
  if (!sendForm.subject.trim() || !sendForm.content.trim()) {
    ElMessage.warning('请填写主题和内容')
    return
  }
  if (attachments.value.length > 0) {
    ElMessage.info('当前版本附件仅作为写信清单展示，后端尚未做 MIME 附件透传。')
  }

  sendingMail.value = true
  try {
    const payload = {
      to: sendForm.to,
      cc: sendForm.cc,
      bcc: sendForm.bcc,
      separateSend: sendForm.separateSend,
      subject: renderTemplateVariables(sendForm.subject),
      content: renderTemplateVariables(sendForm.content)
    }
    const result = await sendMail(payload)
    if (result?.success) {
      ElMessage.success(result.message || '邮件发送成功')
      localStorage.removeItem('mail_workbench_draft')
      await loadLogs()
    } else {
      ElMessage.warning(result?.message || '邮件发送失败')
    }
  } finally {
    sendingMail.value = false
  }
}

const saveDraft = () => {
  localStorage.setItem('mail_workbench_draft', JSON.stringify(sendForm))
  ElMessage.success('草稿已保存')
}

const loadDraft = () => {
  try {
    const raw = localStorage.getItem('mail_workbench_draft')
    if (!raw) return
    const draft = JSON.parse(raw)
    sendForm.to = draft.to || ''
    sendForm.cc = draft.cc || ''
    sendForm.bcc = draft.bcc || ''
    sendForm.subject = draft.subject || ''
    sendForm.content = draft.content || ''
    sendForm.templateId = draft.templateId || null
    sendForm.variables = draft.variables || {}
    sendForm.separateSend = Boolean(draft.separateSend)
    showCc.value = Boolean(sendForm.cc)
    showBcc.value = Boolean(sendForm.bcc)
    syncComposeEditor(sendForm.content)
  } catch (error) {
    console.error('load draft failed', error)
  }
}

const handleTemplateChange = (templateId) => {
  const target = templates.value.find((item) => item.id === templateId)
  if (!target) return
  sendForm.subject = target.subject || ''
  sendForm.content = target.content || ''
  sendForm.variables = {}
  syncComposeEditor(sendForm.content)
}

const applyDefaultTemplate = () => {
  const target = templates.value.find((item) => item.isDefault)
  if (!target) {
    ElMessage.warning('当前没有默认模板')
    return
  }
  sendForm.templateId = target.id
  handleTemplateChange(target.id)
}

const resetTemplateEditor = () => {
  templateEditing.id = null
  templateEditing.name = ''
  templateEditing.code = ''
  templateEditing.subject = ''
  templateEditing.content = ''
  templateEditing.enabled = true
  templateEditing.isDefault = false
}

const openTemplateEditor = (row) => {
  if (!row) {
    resetTemplateEditor()
  } else {
    templateEditing.id = row.id
    templateEditing.name = row.name || ''
    templateEditing.code = row.code || ''
    templateEditing.subject = row.subject || ''
    templateEditing.content = row.content || ''
    templateEditing.enabled = row.enabled !== false
    templateEditing.isDefault = Boolean(row.isDefault)
  }
  templateDialogVisible.value = true
}

const saveTemplateAction = async () => {
  if (!templateEditing.name || !templateEditing.code || !templateEditing.subject || !templateEditing.content) {
    ElMessage.warning('请完整填写模板字段')
    return
  }

  savingTemplate.value = true
  try {
    await saveMailTemplate({ ...templateEditing })
    templateDialogVisible.value = false
    ElMessage.success('模板保存成功')
    await loadTemplates()
  } finally {
    savingTemplate.value = false
  }
}

const toggleTemplateEnabled = async (row, enabled) => {
  try {
    await saveMailTemplate({ ...row, enabled })
    ElMessage.success('模板状态已更新')
    await loadTemplates()
  } catch (error) {
    console.error('toggle template failed', error)
  }
}

const deleteTemplateAction = async (row) => {
  await ElMessageBox.confirm(`确认删除模板「${row.name}」？`, '删除确认', {
    type: 'warning'
  })
  await deleteMailTemplate(row.id)
  ElMessage.success('模板已删除')
  await loadTemplates()
}

const onLogSelectionChange = (rows) => {
  selectedFailedLogs.value = rows.filter((item) => item.status === 'FAILED')
}

const retrySingleLog = async (row) => {
  retryingSingleId.value = row.id
  try {
    const result = await retryMailSendLog(row.id)
    if (result?.success) {
      ElMessage.success(result.message || '重试任务已提交')
      await loadLogs()
    } else {
      ElMessage.warning(result?.message || '重试失败')
    }
  } finally {
    retryingSingleId.value = null
  }
}

const batchRetryLogs = async () => {
  if (selectedFailedLogs.value.length === 0) return
  retryingBatch.value = true
  try {
    const ids = selectedFailedLogs.value.map((item) => item.id)
    const result = await batchRetryMailSendLogs(ids)
    if (result?.success) {
      ElMessage.success(result.message || '批量重试任务已提交')
      selectedFailedLogs.value = []
      await loadLogs()
    } else {
      ElMessage.warning(result?.message || '批量重试失败')
    }
  } finally {
    retryingBatch.value = false
  }
}

const switchToTracking = async (status) => {
  trackingFilter.status = status
  activeTab.value = 'tracking'
  await loadLogs()
}

const fetchInboxAction = async () => {
  fetchingInbox.value = true
  try {
    const result = await fetchMail()
    if (result?.success) {
      ElMessage.success(result.message || '拉取成功')
      await loadInbox()
      await loadInboxMeta()
    } else {
      ElMessage.warning(result?.message || '拉取失败')
    }
  } finally {
    fetchingInbox.value = false
  }
}

const markInboxRead = async (row) => {
  const result = await markMailAsRead(row.id)
  if (result?.success) {
    ElMessage.success('已标记为已读')
    row.isRead = true
    await loadInboxMeta()
  }
}

const openInboxMail = async (row) => {
  selectedInboxMail.value = row
  if (!row.isRead && !row.read) {
    await markInboxRead(row)
  }
}

const viewInboxMail = async (row) => {
  selectedInboxMail.value = row
  inboxDetailVisible.value = true
  if (!row.isRead && !row.read) {
    await markInboxRead(row)
  }
}

const deleteInboxMailAction = async (row) => {
  await ElMessageBox.confirm('确认删除该邮件？', '删除确认', { type: 'warning' })
  const result = await deleteMail(row.id)
  if (result?.success) {
    ElMessage.success('邮件已删除')
    await loadInbox()
    await loadInboxMeta()
  }
}

watch(
  () => route.query.tab,
  () => {
    activeTab.value = getTabFromRoute()
  }
)

watch(activeTab, (value) => {
  onTabChange(value)
  if (value === 'tracking') {
    loadLogs()
  }
})

watch(
  () => sendForm.variables,
  () => {
    if (!composeEditorRef.value) return
    const isEditorFocused = document.activeElement === composeEditorRef.value
    if (!isEditorFocused) {
      syncComposeEditor(sendForm.content)
    }
  },
  { deep: true }
)

onMounted(async () => {
  await Promise.all([
    loadMailConfigAction(),
    loadTemplates(),
    loadLogs(),
    loadInboxMeta(),
    loadInbox(),
    loadNotifConfig()
  ])
  loadDraft()
  syncComposeEditor(sendForm.content)
})
</script>

<style scoped>
.gmail-layout {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100%;
  min-height: 100%;
  background: #f7f9fd;
  border: 1px solid #e7ecf5;
  border-radius: 16px;
  overflow: hidden;
}

.gmail-topbar {
  height: 64px;
  padding: 0 16px;
  display: grid;
  grid-template-columns: 200px 1fr 220px;
  gap: 12px;
  align-items: center;
  background: #fff;
  border-bottom: 1px solid #e6eaf2;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #223047;
}

.search-block :deep(.el-input__wrapper) {
  border-radius: 24px;
  background: #eef3fd;
  box-shadow: none;
}

.top-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
}

.status-cluster {
  display: flex;
  gap: 8px;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid #d9e2ef;
  background: #f4f8ff;
  color: #2c3e56;
  font-size: 12px;
  font-weight: 600;
}

.status-chip.warning {
  border-color: #f5d2a3;
  background: #fff8ee;
  color: #b76a00;
}

.top-setting-btn {
  color: #304767;
  font-weight: 600;
}

.gmail-main {
  flex: 1;
  display: grid;
  grid-template-columns: 220px 1fr;
  min-height: 0;
}

.gmail-sidebar {
  background: #fff;
  border-right: 1px solid #e6eaf2;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.compose-btn {
  width: 100%;
  border-radius: 18px;
  margin-bottom: 8px;
}

.nav-item {
  border: 0;
  background: transparent;
  border-radius: 18px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #37455c;
  font-size: 13px;
  cursor: pointer;
}

.nav-item:hover {
  background: #f3f6fc;
}

.nav-item.active {
  background: #d3e3fd;
  color: #173b72;
  font-weight: 600;
}

.sidebar-group-title {
  margin-top: 6px;
  padding-left: 8px;
  font-size: 11px;
  color: #7b8798;
  letter-spacing: 0.2px;
}

.sidebar-divider {
  margin: 4px 6px 0;
  border-top: 1px solid #edf2f8;
}

.quick-action-btn {
  width: 100%;
  border-radius: 10px;
}

.sidebar-tip {
  font-size: 12px;
  color: #8e5a00;
  padding: 6px 8px;
  border: 1px dashed #f5d2a3;
  background: #fff8ee;
  border-radius: 10px;
}

.gmail-content {
  min-width: 0;
  min-height: 0;
  height: 100%;
  padding: 12px;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.list-toolbar {
  height: 44px;
  background: #fff;
  border: 1px solid #e6eaf2;
  border-bottom: 0;
  border-radius: 12px 12px 0 0;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.mail-split {
  flex: 1;
  min-height: 0;
  border: 1px solid #e6eaf2;
  border-radius: 0 0 12px 12px;
  overflow: hidden;
  background: #fff;
  display: grid;
  grid-template-columns: 54% 46%;
}

.mail-list {
  min-width: 0;
  overflow: auto;
  border-right: 1px solid #e6eaf2;
}

.mail-list.is-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.mail-row {
  height: 46px;
  border-bottom: 1px solid #edf1f7;
  padding: 0 10px;
  display: grid;
  grid-template-columns: 96px 1fr 150px;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.mail-row:hover {
  background: #f9fbff;
}

.mail-row.active {
  background: #eef4ff;
}

.mail-row.unread .from,
.mail-row.unread .subject {
  font-weight: 700;
  color: #1f2d3d;
}

.mail-row-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.star-btn {
  border: 0;
  background: transparent;
  color: #f6b100;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.mail-row-content {
  min-width: 0;
  display: grid;
  grid-template-columns: 170px 1fr;
  align-items: center;
  gap: 10px;
}

.from,
.subject,
.time {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.from,
.time {
  color: #5f6f85;
  font-size: 12px;
}

.subject {
  color: #27364a;
  font-size: 13px;
}

.mail-reader {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.reader-header {
  padding: 12px;
  border-bottom: 1px solid #edf1f7;
}

.reader-header h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #1e2b3b;
}

.reader-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 12px;
  color: #5f6f85;
}

.reader-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.reader-body {
  flex: 1;
  overflow: auto;
  padding: 14px;
  font-size: 13px;
  color: #28384d;
}

.reader-empty,
.mail-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  margin: 0;
  color: #5b6d86;
  font-size: 13px;
  padding: 24px;
  text-align: center;
}

.mail-empty-card,
.reader-empty-card {
  max-width: 360px;
  margin: 0 auto;
}

.mail-empty-title,
.reader-empty-title {
  font-size: 16px;
  font-weight: 700;
  color: #223047;
  margin-bottom: 8px;
}

.mail-empty-desc {
  font-size: 13px;
  line-height: 1.6;
  color: #677a94;
}

.mail-empty-actions {
  margin-top: 14px;
}

.module-sheet {
  background: #fff;
  border: 1px solid #e8edf5;
  border-radius: 14px;
  padding: 16px;
}

.sheet-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2d3f;
  margin-bottom: 10px;
}

.compose-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #edf1f7;
}

.tab-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.compose-form {
  max-width: 980px;
}

.recipient-row {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 8px;
  align-items: center;
}

.compose-sheet {
  background: #fff;
}

.compose-qq-form :deep(.el-form-item) {
  margin-bottom: 0;
  padding: 8px 0;
  border-bottom: 1px solid #edf1f7;
}

.compose-qq-form :deep(.el-form-item:last-child) {
  border-bottom: 0;
}

.compose-qq-form :deep(.el-form-item__label) {
  color: #2c3a4d;
  font-weight: 600;
}

.compose-qq-form :deep(.el-input__wrapper),
.compose-qq-form :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: 0 !important;
  background: transparent !important;
  padding-left: 0;
}

.compose-qq-form :deep(.el-input__inner) {
  font-size: 15px;
  color: #223047;
}

.qq-editor-wrap {
  width: 100%;
  border: 1px solid #d7e0ee;
  border-radius: 10px;
  overflow: hidden;
  background: #fff;
}

.editor-toolbar {
  height: 44px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  border-bottom: 1px solid #e8edf5;
  background: #f7faff;
}

.qq-editor {
  min-height: 320px;
  padding: 14px;
  outline: none;
  font-size: 15px;
  line-height: 1.7;
  color: #243447;
}

.qq-editor:empty::before {
  content: '输入正文';
  color: #9aa8bd;
}

.qq-editor :deep(.template-var-chip),
.qq-editor .template-var-chip {
  display: inline-block;
  margin: 0 2px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #b8d4ff;
  background: #edf4ff;
  color: #2457a6;
  font-size: 12px;
  line-height: 18px;
  vertical-align: baseline;
}

.attachment-block {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 6px;
}

.attachment-hint {
  font-size: 12px;
  color: #8a99b1;
}

.attachment-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.attachment-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 1px solid #e6ebf3;
  border-radius: 8px;
  padding: 6px 10px;
  background: #fafcff;
}

.preview-body {
  margin-top: 12px;
  border: 1px solid #e5eaf3;
  border-radius: 8px;
  min-height: 220px;
  padding: 12px;
  max-height: 380px;
  overflow: auto;
}

.config-form {
  max-width: 820px;
}

.variables-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

@media (max-width: 1280px) {
  .mail-split {
    grid-template-columns: 1fr;
  }

  .mail-list {
    border-right: 0;
    border-bottom: 1px solid #e6eaf2;
    max-height: 320px;
  }

}

@media (max-width: 980px) {
  .gmail-topbar {
    grid-template-columns: 140px 1fr;
  }

  .top-actions {
    display: none;
  }

  .gmail-main {
    grid-template-columns: 1fr;
  }

  .gmail-sidebar {
    border-right: 0;
    border-bottom: 1px solid #e6eaf2;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    align-items: start;
  }

  .compose-btn {
    grid-column: span 3;
  }

  .sidebar-group-title,
  .gmail-sidebar :deep(.el-button--small) {
    display: none;
  }
}

@media (max-width: 768px) {
  .mail-row {
    grid-template-columns: 70px 1fr 100px;
  }

  .mail-row-content {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .from {
    display: none;
  }

  .variables-grid {
    grid-template-columns: 1fr;
  }

  .recipient-row {
    grid-template-columns: 1fr;
  }
}
</style>
