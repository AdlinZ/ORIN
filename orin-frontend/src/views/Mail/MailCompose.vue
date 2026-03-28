<template>
  <div class="mail-compose-container">
    <PageHeader
      title="发送与模板"
      description="撰写邮件、选择模板、实时预览发送效果"
      icon="EditPen"
    />

    <div class="compose-content">
      <!-- 顶部操作栏 -->
      <div class="action-bar">
        <el-button
          type="primary"
          :icon="Promotion"
          :loading="sending"
          :disabled="!mailConnected"
          @click="sendMail"
        >
          发送邮件
        </el-button>
        <el-button :icon="Document" @click="openTemplateDrawer">
          选择模板
        </el-button>
        <el-button :icon="FolderOpened" @click="saveDraft">
          保存草稿
        </el-button>
        <el-button :icon="View" @click="togglePreview">
          {{ showPreview ? '隐藏预览' : '显示预览' }}
        </el-button>
      </div>

      <!-- 发送面板 -->
      <div class="compose-panel" :class="{ 'with-preview': showPreview }">
        <!-- 写信区 -->
        <div class="compose-form">
          <el-form :model="sendForm" label-width="80px">
            <el-form-item label="收件人">
              <el-input
                v-model="sendForm.to"
                placeholder="多个邮箱用逗号分隔"
                @blur="validateRecipients"
              >
                <template #prefix>
                  <el-icon><Message /></el-icon>
                </template>
              </el-input>
              <div v-if="recipientCount > 0" class="recipient-hint">
                共 {{ recipientCount }} 个收件人
              </div>
            </el-form-item>

            <el-form-item label="主题">
              <el-input v-model="sendForm.subject" placeholder="邮件主题">
                <template #prefix>
                  <el-icon><Edit /></el-icon>
                </template>
              </el-input>
            </el-form-item>

            <el-form-item label="模板">
              <div class="template-select">
                <el-select
                  v-model="sendForm.templateId"
                  placeholder="选择模板"
                  clearable
                  style="width: 100%;"
                  @change="onTemplateChange"
                >
                  <el-option
                    v-for="tpl in templates"
                    :key="tpl.id"
                    :label="tpl.name"
                    :value="tpl.id"
                  >
                    <span>{{ tpl.name }}</span>
                    <el-tag size="small" style="margin-left: 8px;">
                      {{ tpl.code }}
                    </el-tag>
                  </el-option>
                </el-select>
                <el-button :icon="Plus" @click="openTemplateDrawer">
                  新建
                </el-button>
              </div>
            </el-form-item>

            <el-form-item label="变量">
              <div v-if="templateVariables.length > 0" class="template-variables">
                <el-input
                  v-for="v in templateVariables"
                  :key="v.key"
                  v-model="sendForm.variables[v.key]"
                  :placeholder="`${v.key} (${v.description})`"
                >
                  <template #prefix>
                    <el-icon><Key /></el-icon>
                  </template>
                </el-input>
              </div>
              <div v-else class="no-variables">
                <el-text type="info">
                  当前模板无变量
                </el-text>
              </div>
            </el-form-item>

            <el-form-item label="内容">
              <el-input
                v-model="sendForm.content"
                type="textarea"
                :rows="12"
                placeholder="邮件正文（支持 HTML）"
              />
            </el-form-item>
          </el-form>

          <!-- 发送前检查 -->
          <div class="send-check">
            <el-alert
              v-if="sendForm.to && !isRecipientsValid"
              type="warning"
              title="邮箱格式有误"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="hasUnresolvedVariables"
              type="warning"
              title="存在未替换变量"
              :description="`变量 ${unresolvedVariables.join(', ')} 未填写值`"
              :closable="false"
              show-icon
            />
            <el-alert
              v-if="recipientCount > 10"
              type="info"
              :title="`批量发送：${recipientCount} 个收件人`"
              description="请确认发送对象是否正确"
              :closable="false"
              show-icon
            />
          </div>
        </div>

        <!-- 预览区 -->
        <div v-if="showPreview" class="compose-preview">
          <div class="preview-header">
            <span><el-icon><View /></el-icon> 预览</span>
          </div>
          <div class="preview-content">
            <div class="preview-meta">
              <div class="preview-item">
                <span class="label">收件人：</span>
                <span class="value">{{ sendForm.to || '-' }}</span>
              </div>
              <div class="preview-item">
                <span class="label">主题：</span>
                <span class="value">{{ sendForm.subject || '-' }}</span>
              </div>
            </div>
            <el-divider />
            <div class="preview-body" v-html="renderedContent" />
          </div>
        </div>
      </div>
    </div>

    <!-- 模板管理抽屉 -->
    <el-drawer
      v-model="templateDrawerVisible"
      title="模板管理"
      direction="rtl"
      size="500px"
    >
      <div class="template-drawer">
        <!-- 模板列表 -->
        <div class="template-list">
          <div class="template-list-header">
            <el-input
              v-model="templateSearch"
              placeholder="搜索模板"
              :prefix-icon="Search"
              clearable
              style="flex: 1;"
            />
            <el-button type="primary" :icon="Plus" @click="openTemplateEditor(null)">
              新建
            </el-button>
          </div>

          <div class="template-items">
            <div
              v-for="tpl in filteredTemplates"
              :key="tpl.id"
              class="template-item"
              :class="{ selected: selectedTemplate?.id === tpl.id }"
              @click="selectTemplate(tpl)"
            >
              <div class="template-item-header">
                <span class="template-name">{{ tpl.name }}</span>
                <el-tag size="small">
                  {{ tpl.code }}
                </el-tag>
              </div>
              <div class="template-subject">
                {{ tpl.subject }}
              </div>
              <div class="template-actions">
                <el-button size="small" text @click.stop="openTemplateEditor(tpl)">
                  编辑
                </el-button>
                <el-button
                  size="small"
                  text
                  type="danger"
                  @click.stop="deleteTemplate(tpl.id)"
                >
                  删除
                </el-button>
              </div>
            </div>

            <el-empty v-if="filteredTemplates.length === 0" description="暂无模板" />
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- 模板编辑对话框 -->
    <el-dialog
      v-model="templateEditorVisible"
      :title="editingTemplate?.id ? '编辑模板' : '新建模板'"
      width="600px"
    >
      <el-form :model="templateForm" label-width="100px">
        <el-form-item label="模板名称" required>
          <el-input v-model="templateForm.name" placeholder="如：验证码邮件" />
        </el-form-item>
        <el-form-item label="模板编码" required>
          <el-input v-model="templateForm.code" placeholder="如：verification" :disabled="!!editingTemplate?.id" />
        </el-form-item>
        <el-form-item label="邮件主题" required>
          <el-input v-model="templateForm.subject" placeholder="邮件主题，支持变量如 {{code}}" />
        </el-form-item>
        <el-form-item label="模板内容" required>
          <el-input
            v-model="templateForm.content"
            type="textarea"
            :rows="10"
            placeholder="邮件正文，支持 HTML 和变量如 {{code}}, {{name}}"
          />
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="templateForm.isDefault" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="templateForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateEditorVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="savingTemplate" @click="saveTemplate">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Promotion, Document, FolderOpened, View, Plus, Message, Edit,
  Key, Search, Delete, CircleCheck
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import PageHeader from '@/components/PageHeader.vue'
import StateView from '@/components/StateView.vue'

const route = useRoute()
const router = useRouter()

// 页面状态
const pageState = ref('loading')

// 数据
const mailConnected = ref(false)
const templates = ref([])
const templateSearch = ref('')
const selectedTemplate = ref(null)

// 发送表单
const sendForm = reactive({
  to: '',
  subject: '',
  content: '',
  templateId: '',
  variables: {}
})

// 发送状态
const sending = ref(false)
const showPreview = ref(true)

// 模板抽屉
const templateDrawerVisible = ref(false)
const templateEditorVisible = ref(false)
const editingTemplate = ref(null)
const savingTemplate = ref(false)

// 模板表单
const templateForm = reactive({
  id: null,
  name: '',
  code: '',
  subject: '',
  content: '',
  isDefault: false,
  enabled: true
})

// 计算属性
const filteredTemplates = computed(() => {
  if (!templateSearch.value) return templates.value
  const search = templateSearch.value.toLowerCase()
  return templates.value.filter(t =>
    t.name?.toLowerCase().includes(search) ||
    t.code?.toLowerCase().includes(search)
  )
})

const recipientCount = computed(() => {
  if (!sendForm.to) return 0
  return sendForm.to.split(',').filter(e => e.trim()).length
})

const isRecipientsValid = computed(() => {
  if (!sendForm.to) return true
  const emails = sendForm.to.split(',').map(e => e.trim())
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emails.every(e => !e || emailRegex.test(e))
})

const templateVariables = computed(() => {
  if (!selectedTemplate.value?.content) return []
  const matches = selectedTemplate.value.content.match(/\{\{(\w+)\}\}/g) || []
  const vars = [...new Set(matches.map(m => m.replace(/\{\{|\}\}/g, '')))]
  return vars.map(v => ({
    key: v,
    description: getVariableDescription(v)
  }))
})

const hasUnresolvedVariables = computed(() => {
  if (!templateVariables.value.length) return false
  return templateVariables.value.some(v => !sendForm.variables[v.key])
})

const unresolvedVariables = computed(() => {
  return templateVariables.value
    .filter(v => !sendForm.variables[v.key])
    .map(v => v.key)
})

const renderedContent = computed(() => {
  let content = sendForm.content || '（无内容）'

  // 替换变量
  Object.entries(sendForm.variables).forEach(([key, value]) => {
    content = content.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), value || `{{${key}}}`)
  })

  // 换行处理
  return content.replace(/\n/g, '<br>')
})

// 方法
const getVariableDescription = (key) => {
  const descriptions = {
    code: '验证码',
    name: '用户名',
    email: '邮箱',
    time: '时间',
    link: '链接'
  }
  return descriptions[key] || key
}

const validateRecipients = () => {
  if (!isRecipientsValid.value) {
    ElMessage.warning('收件人邮箱格式有误')
  }
}

const loadMailConfig = async () => {
  try {
    const res = await request.get('/system/mail-config')
    mailConnected.value = res?.enabled || false
  } catch (e) {
    console.error('加载配置失败:', e)
  }
}

const loadTemplates = async () => {
  try {
    pageState.value = 'loading'
    const res = await request.get('/system/mail-templates')
    templates.value = res || []
    pageState.value = 'success'
  } catch (e) {
    console.error('加载模板失败:', e)
    pageState.value = 'error'
  }
}

const onTemplateChange = (templateId) => {
  const tpl = templates.value.find(t => t.id === templateId)
  if (tpl) {
    selectedTemplate.value = tpl
    sendForm.subject = tpl.subject || ''
    sendForm.content = tpl.content || ''
    sendForm.variables = {}
  } else {
    selectedTemplate.value = null
  }
}

const selectTemplate = (tpl) => {
  selectedTemplate.value = tpl
  sendForm.templateId = tpl.id
  sendForm.subject = tpl.subject || ''
  sendForm.content = tpl.content || ''
  sendForm.variables = {}
  templateDrawerVisible.value = false
}

const openTemplateDrawer = () => {
  templateDrawerVisible.value = true
}

const openTemplateEditor = (tpl) => {
  if (tpl) {
    editingTemplate.value = tpl
    Object.assign(templateForm, tpl)
  } else {
    editingTemplate.value = null
    Object.assign(templateForm, {
      id: null,
      name: '',
      code: '',
      subject: '',
      content: '',
      isDefault: false,
      enabled: true
    })
  }
  templateEditorVisible.value = true
}

const saveTemplate = async () => {
  if (!templateForm.name || !templateForm.code || !templateForm.subject || !templateForm.content) {
    ElMessage.warning('请填写完整信息')
    return
  }

  savingTemplate.value = true
  try {
    if (templateForm.id) {
      await request.put(`/system/mail-templates/${templateForm.id}`, templateForm)
      ElMessage.success('模板更新成功')
    } else {
      await request.post('/system/mail-templates', templateForm)
      ElMessage.success('模板创建成功')
    }
    templateEditorVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    savingTemplate.value = false
  }
}

const deleteTemplate = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个模板吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.delete(`/system/mail-templates/${id}`)
    ElMessage.success('模板已删除')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const togglePreview = () => {
  showPreview.value = !showPreview.value
}

const saveDraft = () => {
  // 本地保存草稿
  localStorage.setItem('mail_draft', JSON.stringify(sendForm))
  ElMessage.success('草稿已保存')
}

const sendMail = async () => {
  if (!sendForm.to) {
    ElMessage.warning('请填写收件人')
    return
  }

  if (!isRecipientsValid.value) {
    ElMessage.warning('收件人邮箱格式有误')
    return
  }

  if (hasUnresolvedVariables.value) {
    ElMessage.warning('请填写所有模板变量')
    return
  }

  // 二次确认
  if (recipientCount.value > 1) {
    try {
      await ElMessageBox.confirm(
        `确定要发送给 ${recipientCount.value} 个收件人吗？`,
        '批量发送确认',
        {
          confirmButtonText: '确定发送',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
    } catch {
      return
    }
  }

  sending.value = true
  try {
    await request.post('/system/mail-config/send', {
      to: sendForm.to,
      subject: sendForm.subject,
      content: sendForm.content,
      templateId: sendForm.templateId || null,
      variables: sendForm.variables || {}
    })
    ElMessage.success('邮件发送成功')

    // 清理草稿
    localStorage.removeItem('mail_draft')

    // 重置表单
    sendForm.to = ''
    sendForm.subject = ''
    sendForm.content = ''
    sendForm.templateId = ''
    sendForm.variables = {}
    selectedTemplate.value = null
  } catch (e) {
    ElMessage.error('发送失败: ' + (e.message || '未知错误'))
  } finally {
    sending.value = false
  }
}

// 加载草稿
const loadDraft = () => {
  const draft = localStorage.getItem('mail_draft')
  if (draft) {
    try {
      const data = JSON.parse(draft)
      Object.assign(sendForm, data)
      ElMessage.info('已恢复草稿')
    } catch (e) {
      console.error('恢复草稿失败:', e)
    }
  }
}

onMounted(async () => {
  await Promise.all([loadMailConfig(), loadTemplates()])
  loadDraft()

  // 处理 URL 参数
  if (route.query.tab === 'templates') {
    templateDrawerVisible.value = true
  }
})
</script>

<style scoped>
.mail-compose-container {
  padding: 20px;
}

.compose-content {
  max-width: 1400px;
  margin: 0 auto;
}

/* 操作栏 */
.action-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

/* 发送面板 */
.compose-panel {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}

.compose-panel.with-preview {
  grid-template-columns: 1fr 1fr;
}

/* 写信表单 */
.compose-form {
  background: white;
  padding: 24px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.recipient-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

.template-select {
  display: flex;
  gap: 8px;
}

.template-variables {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 200px;
  overflow-y: auto;
}

.no-variables {
  padding: 8px 0;
}

/* 发送前检查 */
.send-check {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}

/* 预览区 */
.compose-preview {
  background: white;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}

.preview-header {
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 500;
}

.preview-content {
  padding: 20px;
  max-height: 600px;
  overflow-y: auto;
}

.preview-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-item {
  display: flex;
  gap: 8px;
}

.preview-item .label {
  color: var(--el-text-color-secondary);
  min-width: 60px;
}

.preview-item .value {
  color: var(--el-text-color-primary);
}

.preview-body {
  line-height: 1.8;
  word-break: break-word;
}

/* 模板抽屉 */
.template-drawer {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.template-list {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.template-list-header {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.template-items {
  flex: 1;
  overflow-y: auto;
}

.template-item {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.template-item:hover {
  background: var(--el-fill-color-light);
}

.template-item.selected {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.template-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.template-name {
  font-weight: 500;
}

.template-subject {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}

.template-actions {
  display: flex;
  gap: 8px;
}
</style>
