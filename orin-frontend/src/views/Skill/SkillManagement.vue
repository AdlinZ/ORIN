<template>
  <div :class="['skill-management', { 'is-embedded': embedded }]">
    <PageHeader
      v-if="!embedded"
      title="技能绑定"
      description="管理 Agent 的核心能力扩展，支持 API、知识库、Shell 和复合工作流"
      icon="MagicStick"
    >
      <template #actions>
        <el-button type="success" @click="showImportDialog">
          <el-icon><Download /></el-icon>
          导入技能
        </el-button>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          创建技能
        </el-button>
      </template>

      <template #filters>
        <el-form :inline="true" class="skill-filter-form">
          <el-form-item label="技能类型">
            <el-select
              v-model="filterType"
              placeholder="全部类型"
              clearable
              class="filter-select"
              @change="loadSkills"
            >
              <el-option label="全部" value="" />
              <el-option label="API 调用" value="API" />
              <el-option label="知识库检索" value="KNOWLEDGE" />
              <el-option label="Shell 命令" value="SHELL" />
              <el-option label="复合工作流" value="COMPOSITE" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select
              v-model="filterStatus"
              placeholder="全部状态"
              clearable
              class="filter-select"
              @change="loadSkills"
            >
              <el-option label="全部" value="" />
              <el-option label="活跃" value="ACTIVE" />
              <el-option label="未激活" value="INACTIVE" />
              <el-option label="已废弃" value="DEPRECATED" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadSkills">
              查询
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </PageHeader>

    <div v-else class="embedded-toolbar">
      <div class="embedded-toolbar-main">
        <div class="embedded-title-group">
          <h2 class="embedded-title">技能</h2>
          <p class="embedded-description">管理可供智能体使用的技能能力，支持 API、知识库、Shell 和复合工作流</p>
        </div>
        <div class="embedded-actions">
        <el-button type="success" @click="showImportDialog">
          <el-icon><Download /></el-icon>
          导入技能
        </el-button>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          创建技能
        </el-button>
        </div>
      </div>
      <el-form :inline="true" class="skill-filter-form embedded-filters">
        <el-form-item label="技能类型">
          <el-select
            v-model="filterType"
            placeholder="全部类型"
            clearable
            class="filter-select"
            @change="loadSkills"
          >
            <el-option label="全部" value="" />
            <el-option label="API 调用" value="API" />
            <el-option label="知识库检索" value="KNOWLEDGE" />
            <el-option label="Shell 命令" value="SHELL" />
            <el-option label="复合工作流" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="filterStatus"
            placeholder="全部状态"
            clearable
            class="filter-select"
            @change="loadSkills"
          >
            <el-option label="全部" value="" />
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="未激活" value="INACTIVE" />
            <el-option label="已废弃" value="DEPRECATED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadSkills">
            查询
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 技能列表 -->
    <el-card class="table-card">
      <el-alert
        v-if="loadError"
        type="error"
        show-icon
        :closable="false"
        class="load-error"
        :title="loadError"
      >
        <template #default>
          <el-button type="primary" text @click="loadSkills">重试</el-button>
        </template>
      </el-alert>
      <el-table
        v-loading="loading"
        :data="skills"
        empty-text="暂无技能，点击右上角“创建技能”开始添加"
        stripe
        border
      >
        <el-table-column
          prop="id"
          label="ID"
          width="70"
          align="center"
        />
        <el-table-column prop="skillName" label="技能名称" min-width="150" />
        <el-table-column prop="skillType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.skillType)" effect="dark">
              {{ getTypeLabel(row.skillType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          label="描述"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="version"
          label="版本"
          width="90"
          align="center"
        />
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewSkillMd(row)">
              查看文件(SKILL.md)
            </el-button>
            <el-button size="small" type="primary" @click="editSkill(row)">
              编辑
            </el-button>
            <el-button size="small" type="danger" @click="deleteSkill(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑技能' : '创建技能'"
      width="750px"
      destroy-on-close
    >
      <el-form :model="form" label-width="120px" label-position="left">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基本信息" name="basic">
            <el-form-item label="技能名称" required>
              <el-input v-model="form.skillName" placeholder="例如: CheckDisk" />
            </el-form-item>
            <el-form-item label="技能类型" required>
              <el-select v-model="form.skillType" placeholder="请选择类型" class="w-full">
                <el-option label="API 调用" value="API" />
                <el-option label="知识库检索" value="KNOWLEDGE" />
                <el-option label="Shell 命令" value="SHELL" />
                <el-option label="复合工作流" value="COMPOSITE" />
              </el-select>
            </el-form-item>
            <el-form-item label="描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                placeholder="请输入技能的详细功能描述"
              />
            </el-form-item>
            <el-form-item label="版本">
              <el-input v-model="form.version" placeholder="1.0.0" />
            </el-form-item>
          </el-tab-pane>

          <el-tab-pane label="配置参数" name="config">
            <!-- API 类型配置 -->
            <template v-if="form.skillType === 'API'">
              <el-form-item label="API 端点" required>
                <el-input v-model="form.apiEndpoint" placeholder="https://api.example.com/endpoint" />
              </el-form-item>
              <el-form-item label="HTTP 方法" required>
                <el-radio-group v-model="form.apiMethod">
                  <el-radio-button label="GET" />
                  <el-radio-button label="POST" />
                  <el-radio-button label="PUT" />
                  <el-radio-button label="DELETE" />
                </el-radio-group>
              </el-form-item>
              <el-form-item label="请求头">
                <div v-for="(val, key) in form.apiHeaders" :key="key" class="header-row mb-2">
                  <el-input
                    v-model="tempHeaders[key]"
                    placeholder="Value"
                    size="small"
                    class="mr-2"
                  />
                  <el-button
                    type="danger"
                    circle
                    size="small"
                    @click="removeHeader(key)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button type="dashed" size="small" @click="addHeader">
                  添加 Header
                </el-button>
              </el-form-item>
            </template>

            <!-- 知识库类型配置 -->
            <template v-if="form.skillType === 'KNOWLEDGE'">
              <el-form-item label="知识库 ID" required>
                <el-input-number v-model="form.knowledgeConfigId" :min="1" class="w-full" />
              </el-form-item>
              <div class="tip-info">
                此项将绑定指定的 Milvus 集合进行语义检索。
              </div>
            </template>

            <!-- Shell 类型配置 -->
            <template v-if="form.skillType === 'SHELL'">
              <el-form-item label="Shell 命令" required>
                <el-input
                  v-model="form.shellCommand"
                  type="textarea"
                  :rows="4"
                  placeholder="例如: ls -la ${path}"
                />
              </el-form-item>
              <div class="tip-info">
                支持变量替换，例如 ${path} 将从执行输入中读取。
              </div>
            </template>

            <!-- 复合类型配置 -->
            <template v-if="form.skillType === 'COMPOSITE'">
              <el-form-item label="工作流 ID">
                <el-input-number v-model="form.workflowId" :min="1" class="w-full" />
              </el-form-item>
              <el-form-item label="外部平台">
                <el-select v-model="form.externalPlatform" clearable class="w-full">
                  <el-option label="n8n" value="n8n" />
                  <el-option label="Dify" value="dify" />
                  <el-option label="Coze" value="coze" />
                </el-select>
              </el-form-item>
              <el-form-item label="外部引用">
                <el-input v-model="form.externalReference" placeholder="外部工作流 ID 或 API 路径" />
              </el-form-item>
            </template>
          </el-tab-pane>

          <el-tab-pane label="Schema 定义" name="schema">
            <el-form-item label="输入 Schema">
              <el-input
                v-model="inputSchemaStr"
                type="textarea"
                :rows="6"
                placeholder="JSON 格式，例如: {&quot;path&quot;: &quot;string&quot;}"
              />
            </el-form-item>
            <el-form-item label="输出 Schema">
              <el-input
                v-model="outputSchemaStr"
                type="textarea"
                :rows="6"
                placeholder="JSON 格式"
              />
            </el-form-item>
          </el-tab-pane>
        </el-tabs>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" @click="submitForm">
          保存技能
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入对话框 -->
    <el-dialog v-model="importDialogVisible" title="从外部平台导入技能" width="500px">
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="平台" required>
          <el-select v-model="importForm.platform" class="w-full">
            <el-option label="n8n" value="n8n" />
            <el-option label="Dify" value="dify" />
            <el-option label="Coze" value="coze" />
          </el-select>
        </el-form-item>
        <el-form-item label="引用 ID" required>
          <el-input v-model="importForm.reference" placeholder="输入外部平台的 Workflow ID" />
        </el-form-item>
        <el-form-item label="技能名称">
          <el-input v-model="importForm.name" placeholder="导入后的显示名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- SKILL.md 文件预览对话框 -->
    <el-dialog v-model="mdDialogVisible" title="SKILL.md 文件预览" width="850px">
      <div v-if="mdLoading" class="p-10 text-center">
        <el-icon class="is-loading">
          <Loading />
        </el-icon> 加载中...
      </div>
      <div v-else class="markdown-preview" v-html="renderedMd" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Download, Loading } from '@element-plus/icons-vue'
import { 
  getSkillList, 
  createSkill, 
  updateSkill, 
  deleteSkill as apiDeleteSkill, 
  getSkillMd, 
  importSkill 
} from '@/api/skill'
import { marked } from 'marked'
import dayjs from 'dayjs'
import PageHeader from '@/components/PageHeader.vue'

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const skills = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const mdDialogVisible = ref(false)
const importDialogVisible = ref(false)
const isEdit = ref(false)
const activeTab = ref('basic')
const filterType = ref('')
const filterStatus = ref('')
const renderedMd = ref('')
const mdLoading = ref(false)
const loadError = ref('')

// 导入相关
const importForm = reactive({
  platform: 'n8n',
  reference: '',
  name: ''
})

const form = ref({
  id: null,
  skillName: '',
  skillType: 'API',
  description: '',
  apiEndpoint: '',
  apiMethod: 'GET',
  apiHeaders: {},
  knowledgeConfigId: null,
  workflowId: null,
  shellCommand: '',
  externalPlatform: '',
  externalReference: '',
  inputSchema: {},
  outputSchema: {},
  version: '1.0.0'
})

const tempHeaders = reactive({})
const inputSchemaStr = ref('{}')
const outputSchemaStr = ref('{}')

onMounted(() => {
  loadSkills()
  window.addEventListener('page-refresh', loadSkills)
})

onUnmounted(() => {
  window.removeEventListener('page-refresh', loadSkills)
})

const loadSkills = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const params = {}
    if (filterType.value) params.type = filterType.value
    if (filterStatus.value) params.status = filterStatus.value

    const data = await getSkillList(params)
    skills.value = data
  } catch (error) {
    // Error is handled by request interceptor, but we can add secondary logging
    console.error('Failed to load skills:', error)
    loadError.value = `技能列表加载失败：${error?.message || '未知错误'}`
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

const formatTime = (time) => {
  return time ? dayjs(time).format('YYYY-MM-DD HH:mm') : '-'
}

const showCreateDialog = () => {
  isEdit.value = false
  activeTab.value = 'basic'
  resetForm()
  dialogVisible.value = true
}

const resetForm = () => {
  form.value = {
    skillName: '',
    skillType: 'API',
    description: '',
    apiEndpoint: '',
    apiMethod: 'GET',
    apiHeaders: {},
    knowledgeConfigId: null,
    workflowId: null,
    shellCommand: '',
    externalPlatform: '',
    externalReference: '',
    inputSchema: {},
    outputSchema: {},
    version: '1.0.0'
  }
  inputSchemaStr.value = '{}'
  outputSchemaStr.value = '{}'
  Object.keys(tempHeaders).forEach(k => delete tempHeaders[k])
}

const editSkill = (skill) => {
  isEdit.value = true
  activeTab.value = 'basic'
  form.value = JSON.parse(JSON.stringify(skill))
  inputSchemaStr.value = JSON.stringify(skill.inputSchema || {}, null, 2)
  outputSchemaStr.value = JSON.stringify(skill.outputSchema || {}, null, 2)

  // Initialize temp headers
  Object.keys(tempHeaders).forEach(k => delete tempHeaders[k])
  if (skill.apiHeaders) {
    Object.assign(tempHeaders, skill.apiHeaders)
  }

  dialogVisible.value = true
}

const submitForm = async () => {
  try {
    // Sync schemas
    try {
      form.value.inputSchema = JSON.parse(inputSchemaStr.value)
      form.value.outputSchema = JSON.parse(outputSchemaStr.value)
    } catch (e) {
      return ElMessage.error('Schema JSON 格式不正确')
    }

    // Sync headers
    form.value.apiHeaders = { ...tempHeaders }

    if (isEdit.value) {
      await updateSkill(form.value.id, form.value)
      ElMessage.success('技能更新成功')
    } else {
      await createSkill(form.value)
      ElMessage.success('技能创建成功')
    }
    dialogVisible.value = false
    loadSkills()
  } catch (error) {
    // Error notification handled by request utility
  }
}

const deleteSkill = async (skill) => {
  try {
    await ElMessageBox.confirm(`确定要永久删除技能 "${skill.skillName}" 吗? 此操作无法撤销。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
    await apiDeleteSkill(skill.id)
    ElMessage.success('删除成功')
    loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('Delete failed:', error)
    }
  }
}

const viewSkillMd = async (skill) => {
  mdLoading.value = true
  renderedMd.value = ''
  mdDialogVisible.value = true
  try {
    const data = await getSkillMd(skill.id)
    renderedMd.value = marked(data.content)
  } catch (error) {
    mdDialogVisible.value = false
  } finally {
    mdLoading.value = false
  }
}

const showImportDialog = () => {
  importForm.platform = 'n8n'
  importForm.reference = ''
  importForm.name = ''
  importDialogVisible.value = true
}

const submitImport = async () => {
  if (!importForm.reference) return ElMessage.warning('请输入引用 ID')
  try {
    await importSkill(importForm)
    ElMessage.success('导入技能成功')
    importDialogVisible.value = false
    loadSkills()
  } catch (error) {
    // Error notification handled by request utility
  }
}

// Header management
const addHeader = () => {
  const key = prompt('输入 Header 名称 (例如 Authorization)')
  if (key) {
    tempHeaders[key] = ''
  }
}
const removeHeader = (key) => {
  delete tempHeaders[key]
}

const getTypeTagType = (type) => {
  const types = { API: 'primary', KNOWLEDGE: 'success', SHELL: 'danger', COMPOSITE: 'warning' }
  return types[type] || 'info'
}

const getTypeLabel = (type) => {
  const labels = { API: 'API 调用', KNOWLEDGE: '知识库', SHELL: 'Shell 命令', COMPOSITE: '复合技能' }
  return labels[type] || type
}

const getStatusTagType = (status) => {
  const types = { ACTIVE: 'success', INACTIVE: 'info', DEPRECATED: 'danger' }
  return types[status] || 'info'
}

const getStatusLabel = (status) => {
  const labels = { ACTIVE: '活跃', INACTIVE: '未激活', DEPRECATED: '已废弃' }
  return labels[status] || status
}
</script>

<style scoped>
.skill-management {
  padding: 24px;
}

.skill-management.is-embedded {
  padding: 0;
}

.embedded-toolbar {
  margin-bottom: 12px;
  padding: 18px 20px 6px;
  border: 1px solid var(--orin-border);
  border-radius: 12px;
  background: var(--neutral-white);
}

.embedded-toolbar-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 6px;
}

.embedded-title-group {
  min-width: 0;
}

.embedded-title {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
  color: #0f172a;
}

.embedded-description {
  margin: 8px 0 0;
  color: #64748b;
  font-size: 14px;
}

.embedded-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.embedded-filters {
  margin-bottom: -18px;
}

.skill-filter-form {
  margin-bottom: -18px;
}

.filter-select {
  width: 180px;
}

.table-card {
  border-radius: 12px;
  margin-top: 4px;
  border: 1px solid var(--orin-border);
}

.table-card :deep(.el-card__body) {
  padding-top: 14px;
}

.load-error {
  margin-bottom: 12px;
}

.tip-info {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: -10px;
  margin-bottom: 20px;
  padding-left: 120px;
}

.header-row {
  display: flex;
  align-items: center;
}

.w-full {
  width: 100%;
}

.markdown-preview {
  padding: 30px;
  background: var(--neutral-white);
  border-radius: 8px;
  max-height: 70vh;
  overflow-y: auto;
  line-height: 1.6;
}

html.dark .markdown-preview {
  background: var(--neutral-gray-800);
  color: #e2e8f0;
}

.markdown-preview :deep(h1) {
  font-size: 28px;
  border-bottom: 1px solid #eee;
  padding-bottom: 10px;
  margin-bottom: 20px;
}

html.dark .markdown-preview :deep(h1) {
  border-bottom-color: var(--neutral-gray-600);
}

.markdown-preview :deep(h2) {
  font-size: 22px;
  margin-top: 30px;
  color: #2c3e50;
}

html.dark .markdown-preview :deep(h2) {
  color: #e2e8f0;
}

.markdown-preview :deep(pre) {
  background: #f6f8fa;
  padding: 16px;
  border-radius: 6px;
  overflow-x: auto;
}

html.dark .markdown-preview :deep(pre) {
  background: #1e1e1e;
}

.markdown-preview :deep(code) {
  background: rgba(175, 184, 193, 0.2);
  padding: 0.2em 0.4em;
  border-radius: 6px;
}

html.dark .markdown-preview :deep(code) {
  background: rgba(255, 255, 255, 0.1);
}

.markdown-preview :deep(pre code) {
  background: transparent;
  padding: 0;
}

@media (max-width: 1024px) {
  .embedded-toolbar-main {
    flex-direction: column;
  }
}
</style>
