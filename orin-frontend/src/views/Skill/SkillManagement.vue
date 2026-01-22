<template>
  <div class="skill-management">
    <el-card class="header-card">
      <div class="header-content">
        <h2>技能管理</h2>
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          创建技能
        </el-button>
      </div>
    </el-card>

    <!-- 筛选器 -->
    <el-card class="filter-card">
      <el-form :inline="true">
        <el-form-item label="技能类型">
          <el-select v-model="filterType" placeholder="全部" clearable @change="loadSkills">
            <el-option label="全部" value="" />
            <el-option label="API" value="API" />
            <el-option label="知识库" value="KNOWLEDGE" />
            <el-option label="复合" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterStatus" placeholder="全部" clearable @change="loadSkills">
            <el-option label="全部" value="" />
            <el-option label="活跃" value="ACTIVE" />
            <el-option label="未激活" value="INACTIVE" />
            <el-option label="已废弃" value="DEPRECATED" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 技能列表 -->
    <el-card class="table-card">
      <el-table :data="skills" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="skillName" label="技能名称" min-width="150" />
        <el-table-column prop="skillType" label="类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTypeTagType(row.skillType)">
              {{ getTypeLabel(row.skillType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="viewSkillMd(row)">查看 SKILL.md</el-button>
            <el-button size="small" type="primary" @click="editSkill(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteSkill(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑技能' : '创建技能'"
      width="600px"
    >
      <el-form :model="form" label-width="120px">
        <el-form-item label="技能名称" required>
          <el-input v-model="form.skillName" placeholder="请输入技能名称" />
        </el-form-item>
        <el-form-item label="技能类型" required>
          <el-select v-model="form.skillType" placeholder="请选择类型">
            <el-option label="API 调用" value="API" />
            <el-option label="知识库检索" value="KNOWLEDGE" />
            <el-option label="复合技能" value="COMPOSITE" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入技能描述"
          />
        </el-form-item>

        <!-- API 类型配置 -->
        <template v-if="form.skillType === 'API'">
          <el-form-item label="API 端点" required>
            <el-input v-model="form.apiEndpoint" placeholder="https://api.example.com/endpoint" />
          </el-form-item>
          <el-form-item label="HTTP 方法" required>
            <el-select v-model="form.apiMethod">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
        </template>

        <!-- 知识库类型配置 -->
        <template v-if="form.skillType === 'KNOWLEDGE'">
          <el-form-item label="知识库配置 ID" required>
            <el-input-number v-model="form.knowledgeConfigId" :min="1" />
          </el-form-item>
        </template>

        <!-- 复合类型配置 -->
        <template v-if="form.skillType === 'COMPOSITE'">
          <el-form-item label="工作流 ID">
            <el-input-number v-model="form.workflowId" :min="1" />
          </el-form-item>
          <el-form-item label="外部平台">
            <el-select v-model="form.externalPlatform" clearable>
              <el-option label="n8n" value="n8n" />
              <el-option label="Dify" value="dify" />
              <el-option label="Coze" value="coze" />
            </el-select>
          </el-form-item>
          <el-form-item label="外部引用">
            <el-input v-model="form.externalReference" placeholder="Workflow ID 或 API 路径" />
          </el-form-item>
        </template>

        <el-form-item label="版本">
          <el-input v-model="form.version" placeholder="1.0.0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- SKILL.md 预览对话框 -->
    <el-dialog v-model="mdDialogVisible" title="SKILL.md 预览" width="800px">
      <div class="markdown-preview" v-html="renderedMd"></div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import axios from 'axios'
import { marked } from 'marked'

const skills = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const mdDialogVisible = ref(false)
const isEdit = ref(false)
const filterType = ref('')
const filterStatus = ref('')
const renderedMd = ref('')

const form = ref({
  skillName: '',
  skillType: 'API',
  description: '',
  apiEndpoint: '',
  apiMethod: 'GET',
  knowledgeConfigId: null,
  workflowId: null,
  externalPlatform: '',
  externalReference: '',
  version: '1.0.0'
})

onMounted(() => {
  loadSkills()
})

const loadSkills = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterType.value) params.type = filterType.value
    if (filterStatus.value) params.status = filterStatus.value

    const response = await axios.get('/api/skills', { params })
    skills.value = response.data
  } catch (error) {
    ElMessage.error('加载技能列表失败: ' + error.message)
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  isEdit.value = false
  form.value = {
    skillName: '',
    skillType: 'API',
    description: '',
    apiEndpoint: '',
    apiMethod: 'GET',
    knowledgeConfigId: null,
    workflowId: null,
    externalPlatform: '',
    externalReference: '',
    version: '1.0.0'
  }
  dialogVisible.value = true
}

const editSkill = (skill) => {
  isEdit.value = true
  form.value = { ...skill }
  dialogVisible.value = true
}

const submitForm = async () => {
  try {
    if (isEdit.value) {
      await axios.put(`/api/skills/${form.value.id}`, form.value)
      ElMessage.success('技能更新成功')
    } else {
      await axios.post('/api/skills', form.value)
      ElMessage.success('技能创建成功')
    }
    dialogVisible.value = false
    loadSkills()
  } catch (error) {
    ElMessage.error('操作失败: ' + error.message)
  }
}

const deleteSkill = async (skill) => {
  try {
    await ElMessageBox.confirm(`确定要删除技能 "${skill.skillName}" 吗?`, '确认删除', {
      type: 'warning'
    })
    await axios.delete(`/api/skills/${skill.id}`)
    ElMessage.success('删除成功')
    loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败: ' + error.message)
    }
  }
}

const viewSkillMd = async (skill) => {
  try {
    const response = await axios.get(`/api/skills/${skill.id}/skill-md`)
    renderedMd.value = marked(response.data.content)
    mdDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取 SKILL.md 失败: ' + error.message)
  }
}

const getTypeTagType = (type) => {
  const types = { API: 'primary', KNOWLEDGE: 'success', COMPOSITE: 'warning' }
  return types[type] || 'info'
}

const getTypeLabel = (type) => {
  const labels = { API: 'API', KNOWLEDGE: '知识库', COMPOSITE: '复合' }
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
  padding: 20px;
}

.header-card {
  margin-bottom: 20px;
}

.header-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-content h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
}

.filter-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.markdown-preview {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
  max-height: 600px;
  overflow-y: auto;
}

.markdown-preview :deep(h1) {
  font-size: 24px;
  margin-bottom: 16px;
}

.markdown-preview :deep(h2) {
  font-size: 20px;
  margin-top: 24px;
  margin-bottom: 12px;
}

.markdown-preview :deep(pre) {
  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 4px;
  overflow-x: auto;
}

.markdown-preview :deep(code) {
  font-family: 'Courier New', monospace;
}
</style>
