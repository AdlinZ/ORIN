<template>
  <div class="settings-page">
    <el-tabs v-model="activeTab" class="settings-tabs">
      <!-- 邮件服务配置 -->
      <el-tab-pane label="邮件服务" name="mail-config">
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
              <el-button type="success" :loading="saving" @click="saveMailConfig">
                保存配置
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 模板管理 -->
      <el-tab-pane label="模板管理" name="templates">
        <el-card class="settings-card">
          <template #header>
            <div class="card-header">
              <span>邮件模板</span>
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
      </el-tab-pane>
    </el-tabs>

    <!-- 模板编辑对话框 -->
    <el-dialog v-model="templateDialogVisible" :title="editingTemplate?.id ? '编辑模板' : '新增模板'" width="600px">
      <el-form :model="templateForm" label-width="100px">
        <el-form-item label="模板名称">
          <el-input v-model="templateForm.name" placeholder="如：账号激活邮件" />
        </el-form-item>
        <el-form-item label="模板代码">
          <el-input v-model="templateForm.code" placeholder="如：account_activation" :disabled="!!editingTemplate?.id" />
        </el-form-item>
        <el-form-item label="邮件主题">
          <el-input v-model="templateForm.subject" placeholder="如：您的 ORIN 账号已激活" />
        </el-form-item>
        <el-form-item label="邮件内容">
          <el-input v-model="templateForm.content" type="textarea" :rows="8" placeholder="支持 HTML，使用 {{variable}} 占位变量" />
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMailConfig, saveMailConfig as saveMailConfigApi, testMailConnection, getMailTemplates, saveMailTemplate as saveTemplateApi, deleteMailTemplate as deleteTemplateApi } from '@/api/mail'

const activeTab = ref('mail-config')
const saving = ref(false)
const testing = ref(false)
const templatesLoading = ref(false)
const templateDialogVisible = ref(false)
const editingTemplate = ref(null)

const mailStatus = ref({ connected: false })
const mailConfig = reactive({
  mailerType: 'smtp',
  apiKey: '',
  host: '',
  port: 587,
  username: '',
  password: '',
  fromEmail: '',
  fromName: 'ORIN 系统',
  ssl: true
})

const mailTemplates = ref([])
const templateForm = reactive({
  name: '',
  code: '',
  subject: '',
  content: '',
  isDefault: false,
  enabled: true
})

// 加载邮件配置
const loadMailConfig = async () => {
  try {
    const res = await getMailConfig()
    if (res) {
      Object.assign(mailConfig, res)
      mailStatus.value.connected = !!res.fromEmail
    }
  } catch (e) {
    console.error('加载邮件配置失败:', e)
  }
}

// 保存邮件配置
const saveMailConfig = async () => {
  saving.value = true
  try {
    await saveMailConfigApi(mailConfig)
    ElMessage.success('邮件配置保存成功')
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  } finally {
    saving.value = false
  }
}

// 测试连接
const testMail = async () => {
  testing.value = true
  try {
    const res = await testMailConnection(mailConfig)
    ElMessage.success(res === true ? '连接测试成功' : '连接测试失败: ' + res)
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || '未知错误'))
  } finally {
    testing.value = false
  }
}

// 加载模板
const loadTemplates = async () => {
  templatesLoading.value = true
  try {
    const res = await getMailTemplates()
    mailTemplates.value = res || []
  } catch (e) {
    console.error('加载模板失败:', e)
  } finally {
    templatesLoading.value = false
  }
}

// 打开模板对话框
const openTemplateDialog = (row) => {
  editingTemplate.value = row || null
  if (row) {
    Object.assign(templateForm, row)
  } else {
    Object.assign(templateForm, {
      name: '',
      code: '',
      subject: '',
      content: '',
      isDefault: false,
      enabled: true
    })
  }
  templateDialogVisible.value = true
}

// 保存模板
const saveTemplate = async () => {
  try {
    await saveTemplateApi(templateForm)
    ElMessage.success('模板保存成功')
    templateDialogVisible.value = false
    loadTemplates()
  } catch (e) {
    ElMessage.error('保存失败: ' + (e.message || '未知错误'))
  }
}

// 删除模板
const deleteTemplate = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除此模板吗？', '提示', { type: 'warning' })
    await deleteTemplateApi(row.id)
    ElMessage.success('删除成功')
    loadTemplates()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || '未知错误'))
    }
  }
}

onMounted(() => {
  loadMailConfig()
  loadTemplates()
})
</script>

<style scoped>
.settings-page {
  max-width: 900px;
}

.settings-tabs {
  margin-top: 0;
}

.settings-card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.form-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
</style>
