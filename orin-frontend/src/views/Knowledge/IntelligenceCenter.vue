<template>
  <div class="page-container">
    <PageHeader
      title="智力资产中心"
      description="管理智能体的长期记忆、技能和 Prompt 模板"
      icon="Headset"
    />

    <el-tabs v-model="activeTab" class="intelligence-tabs">
      <!-- 长期记忆 Tab -->
      <el-tab-pane label="长期记忆" name="memories">
        <el-card class="intelligence-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>长期记忆管理</span>
              <el-button type="primary" :icon="Plus" @click="showMemoryDialog = true">
                新增记忆
              </el-button>
            </div>
          </template>

          <el-form :inline="true" class="filter-form">
            <el-form-item label="Agent">
              <el-select v-model="selectedAgentId" placeholder="选择 Agent" clearable style="width: 200px">
                <el-option
                  v-for="agent in agentList"
                  :key="agent.agentId"
                  :label="agent.name"
                  :value="agent.agentId"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadMemories" :icon="Search">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="memories" v-loading="memoriesLoading" stripe>
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="key" label="Key" width="150" />
            <el-table-column prop="value" label="Value" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="deleteMemory(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 技能管理 Tab -->
      <el-tab-pane label="技能管理" name="skills">
        <el-card class="intelligence-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>技能管理</span>
              <el-button type="primary" :icon="Plus" @click="openSkillDialog()">
                新增技能
              </el-button>
            </div>
          </template>

          <el-form :inline="true" class="filter-form">
            <el-form-item label="Agent">
              <el-select v-model="selectedAgentId" placeholder="选择 Agent" clearable style="width: 200px">
                <el-option
                  v-for="agent in agentList"
                  :key="agent.agentId"
                  :label="agent.name"
                  :value="agent.agentId"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadSkills" :icon="Search">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="skills" v-loading="skillsLoading" stripe>
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="triggerName" label="触发词" width="150" />
            <el-table-column prop="definition" label="定义" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" width="180">
              <template #default="{ row }">
                {{ formatDateTime(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openSkillDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="deleteSkill(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- Prompt 模板 Tab -->
      <el-tab-pane label="Prompt 模板" name="prompts">
        <el-card class="intelligence-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <span>Prompt 模板管理</span>
              <el-button type="primary" :icon="Plus" @click="openPromptDialog()">
                新增模板
              </el-button>
            </div>
          </template>

          <el-form :inline="true" class="filter-form">
            <el-form-item label="Agent">
              <el-select v-model="selectedAgentId" placeholder="选择 Agent" clearable style="width: 200px">
                <el-option
                  v-for="agent in agentList"
                  :key="agent.agentId"
                  :label="agent.name"
                  :value="agent.agentId"
                />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadPrompts" :icon="Search">查询</el-button>
            </el-form-item>
          </el-form>

          <el-table :data="prompts" v-loading="promptsLoading" stripe>
            <el-table-column prop="id" label="ID" width="180" show-overflow-tooltip />
            <el-table-column prop="name" label="名称" width="150" />
            <el-table-column prop="template" label="模板内容" show-overflow-tooltip />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPromptDialog(row)">编辑</el-button>
                <el-button link type="danger" @click="deletePrompt(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新增/编辑记忆对话框 -->
    <el-dialog v-model="showMemoryDialog" title="新增记忆" width="500px">
      <el-form :model="memoryForm" label-width="80px">
        <el-form-item label="Agent">
          <el-select v-model="memoryForm.agentId" placeholder="选择 Agent" style="width: 100%">
            <el-option
              v-for="agent in agentList"
              :key="agent.agentId"
              :label="agent.name"
              :value="agent.agentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Key">
          <el-input v-model="memoryForm.key" placeholder="记忆的 Key" />
        </el-form-item>
        <el-form-item label="Value">
          <el-input v-model="memoryForm.value" type="textarea" :rows="3" placeholder="记忆的内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showMemoryDialog = false">取消</el-button>
        <el-button type="primary" @click="saveMemory">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑技能对话框 -->
    <el-dialog v-model="showSkillDialog" :title="skillForm.id ? '编辑技能' : '新增技能'" width="500px">
      <el-form :model="skillForm" label-width="80px">
        <el-form-item label="Agent">
          <el-select v-model="skillForm.agentId" placeholder="选择 Agent" style="width: 100%">
            <el-option
              v-for="agent in agentList"
              :key="agent.agentId"
              :label="agent.name"
              :value="agent.agentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="触发词">
          <el-input v-model="skillForm.triggerName" placeholder="触发技能的名称" />
        </el-form-item>
        <el-form-item label="定义">
          <el-input v-model="skillForm.definition" type="textarea" :rows="4" placeholder="技能的定义描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showSkillDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSkill">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑 Prompt 对话框 -->
    <el-dialog v-model="showPromptDialog" :title="promptForm.id ? '编辑模板' : '新增模板'" width="600px">
      <el-form :model="promptForm" label-width="80px">
        <el-form-item label="Agent">
          <el-select v-model="promptForm.agentId" placeholder="选择 Agent" style="width: 100%">
            <el-option
              v-for="agent in agentList"
              :key="agent.agentId"
              :label="agent.name"
              :value="agent.agentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="promptForm.name" placeholder="模板名称" />
        </el-form-item>
        <el-form-item label="模板内容">
          <el-input v-model="promptForm.template" type="textarea" :rows="6" placeholder="Prompt 模板内容，支持变量如 {{variable}}" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="promptForm.description" type="textarea" :rows="2" placeholder="模板描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPromptDialog = false">取消</el-button>
        <el-button type="primary" @click="savePrompt">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getAgentList } from '@/api/agent'
import {
  getAgentMemories,
  saveAgentMemory,
  deleteAgentMemory,
  getAgentSkills,
  saveAgentSkill,
  deleteAgentSkill,
  getIntelligencePrompts,
  saveIntelligencePrompt,
  deleteIntelligencePrompt
} from '@/api/knowledge'

const activeTab = ref('memories')
const agentList = ref([])
const selectedAgentId = ref('')

// 记忆相关
const memories = ref([])
const memoriesLoading = ref(false)
const showMemoryDialog = ref(false)
const memoryForm = ref({
  agentId: '',
  key: '',
  value: ''
})

// 技能相关
const skills = ref([])
const skillsLoading = ref(false)
const showSkillDialog = ref(false)
const skillForm = ref({
  id: null,
  agentId: '',
  triggerName: '',
  definition: ''
})

// Prompt 相关
const prompts = ref([])
const promptsLoading = ref(false)
const showPromptDialog = ref(false)
const promptForm = ref({
  id: null,
  agentId: '',
  name: '',
  template: '',
  description: ''
})

// 加载 Agent 列表
const loadAgentList = async () => {
  try {
    const res = await getAgentList()
    agentList.value = res.data || []
  } catch (error) {
    console.error('加载 Agent 列表失败:', error)
  }
}

// 加载记忆
const loadMemories = async () => {
  if (!selectedAgentId.value) {
    ElMessage.warning('请先选择 Agent')
    return
  }
  memoriesLoading.value = true
  try {
    const res = await getAgentMemories(selectedAgentId.value)
    memories.value = res.data || []
  } catch (error) {
    console.error('加载记忆失败:', error)
    ElMessage.error('加载记忆失败')
  } finally {
    memoriesLoading.value = false
  }
}

// 保存记忆
const saveMemory = async () => {
  if (!memoryForm.value.agentId || !memoryForm.value.key || !memoryForm.value.value) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await saveAgentMemory(memoryForm.value.agentId, memoryForm.value.key, memoryForm.value.value)
    ElMessage.success('保存成功')
    showMemoryDialog.value = false
    memoryForm.value = { agentId: '', key: '', value: '' }
    if (selectedAgentId.value === memoryForm.value.agentId) {
      loadMemories()
    }
  } catch (error) {
    console.error('保存记忆失败:', error)
    ElMessage.error('保存失败')
  }
}

// 删除记忆
const deleteMemory = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该记忆?', '警告', { type: 'warning' })
    await deleteAgentMemory(id)
    ElMessage.success('删除成功')
    loadMemories()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除记忆失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 加载技能
const loadSkills = async () => {
  if (!selectedAgentId.value) {
    ElMessage.warning('请先选择 Agent')
    return
  }
  skillsLoading.value = true
  try {
    const res = await getAgentSkills(selectedAgentId.value)
    skills.value = res.data || []
  } catch (error) {
    console.error('加载技能失败:', error)
    ElMessage.error('加载技能失败')
  } finally {
    skillsLoading.value = false
  }
}

// 打开技能对话框
const openSkillDialog = (row = null) => {
  if (row) {
    skillForm.value = { ...row }
  } else {
    skillForm.value = { id: null, agentId: selectedAgentId.value, triggerName: '', definition: '' }
  }
  showSkillDialog.value = true
}

// 保存技能
const saveSkill = async () => {
  if (!skillForm.value.agentId || !skillForm.value.triggerName || !skillForm.value.definition) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await saveAgentSkill(skillForm.value)
    ElMessage.success('保存成功')
    showSkillDialog.value = false
    loadSkills()
  } catch (error) {
    console.error('保存技能失败:', error)
    ElMessage.error('保存失败')
  }
}

// 删除技能
const deleteSkill = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该技能?', '警告', { type: 'warning' })
    await deleteAgentSkill(id)
    ElMessage.success('删除成功')
    loadSkills()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除技能失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 加载 Prompts
const loadPrompts = async () => {
  if (!selectedAgentId.value) {
    ElMessage.warning('请先选择 Agent')
    return
  }
  promptsLoading.value = true
  try {
    const res = await getIntelligencePrompts(selectedAgentId.value)
    prompts.value = res.data || []
  } catch (error) {
    console.error('加载 Prompt 模板失败:', error)
    ElMessage.error('加载 Prompt 模板失败')
  } finally {
    promptsLoading.value = false
  }
}

// 打开 Prompt 对话框
const openPromptDialog = (row = null) => {
  if (row) {
    promptForm.value = { ...row }
  } else {
    promptForm.value = { id: null, agentId: selectedAgentId.value, name: '', template: '', description: '' }
  }
  showPromptDialog.value = true
}

// 保存 Prompt
const savePrompt = async () => {
  if (!promptForm.value.agentId || !promptForm.value.name || !promptForm.value.template) {
    ElMessage.warning('请填写完整信息')
    return
  }
  try {
    await saveIntelligencePrompt(promptForm.value)
    ElMessage.success('保存成功')
    showPromptDialog.value = false
    loadPrompts()
  } catch (error) {
    console.error('保存 Prompt 模板失败:', error)
    ElMessage.error('保存失败')
  }
}

// 删除 Prompt
const deletePrompt = async (id) => {
  try {
    await ElMessageBox.confirm('确认删除该 Prompt 模板?', '警告', { type: 'warning' })
    await deleteIntelligencePrompt(id)
    ElMessage.success('删除成功')
    loadPrompts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除 Prompt 模板失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 格式化日期时间
const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

onMounted(() => {
  loadAgentList()
})
</script>

<style scoped>
.page-container {
  padding: 24px;
}

.intelligence-tabs {
  margin-top: 20px;
}

.intelligence-card {
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.filter-form {
  margin-bottom: 20px;
}
</style>
