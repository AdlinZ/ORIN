<template>
  <div class="meta-section">
    <el-tabs type="border-card" class="meta-tabs">
      <!-- Prompt Templates Tab -->
      <el-tab-pane label="Prompt 模板">
        <el-card shadow="never" class="tab-card">
          <template #header>
            <div class="card-header">
              <span class="title">提示词模板管理</span>
              <div class="header-actions">
                <el-button link type="primary" :icon="Refresh" @click="loadMeta">刷新</el-button>
                <el-button type="primary" size="small" :icon="Plus" @click="openPromptDialog">新建模板</el-button>
              </div>
            </div>
          </template>
          <el-table border :data="prompts" style="width: 100%" v-loading="loading">
            <el-table-column prop="name" label="模板名称" />
            <el-table-column prop="type" label="类型" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ row.type }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="right">
              <template #default="{ row }">
                <el-button link @click="viewContent(row)">查看</el-button>
                <el-button link type="primary" @click="handleEditPrompt(row)">编辑</el-button>
                <el-button link type="danger" @click="deletePrompt(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- Long-term Memory Tab -->
      <el-tab-pane label="长期记忆 (DB)">
        <el-card shadow="never" class="tab-card">
          <template #header>
            <div class="card-header">
              <span class="title">结构化长期记忆</span>
              <div class="header-actions">
                <el-button link type="primary" :icon="Plus" @click="openExtractDialog">AI 记忆提取</el-button>
                <el-button link type="danger" :icon="Delete" @click="clearMemory" :disabled="memory.length === 0">清空全部</el-button>
                <el-button link type="primary" :icon="Refresh" @click="loadMeta">刷新</el-button>
              </div>
            </div>
          </template>
          <el-table border :data="memory" style="width: 100%" v-loading="loading">
            <el-table-column prop="key" label="键 (Key)" width="180" />
            <el-table-column prop="value" label="内容 (Value)" show-overflow-tooltip />
            <el-table-column prop="updatedAt" label="最后更新" width="160">
              <template #default="{ row }">
                {{ formatTime(row.updatedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" align="right">
              <template #default="{ row }">
                <el-button link type="danger" @click="deleteMemoryItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="memory.length === 0" class="empty-hint">
            暂无长期记忆数据，可通过对话提取或手动导入。
          </div>
        </el-card>
      </el-tab-pane>

      <!-- Short-term Memory Tab -->
      <el-tab-pane label="短期记忆 (Redis)">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-card shadow="never" class="session-card">
              <template #header>
                <div class="card-header">
                  <span>活跃会话列表</span>
                  <el-button link type="primary" :icon="Refresh" @click="loadSessions"></el-button>
                </div>
              </template>
              <div class="session-list" v-loading="sessionLoading">
                <div 
                  v-for="session in sessions" 
                  :key="session" 
                  class="session-item"
                  :class="{ active: selectedSession === session }"
                  @click="viewSessionMemory(session)"
                >
                  <el-icon><ChatDotRound /></el-icon>
                  <span class="session-id">{{ session }}</span>
                  <el-button 
                    link 
                    type="danger" 
                    size="small" 
                    :icon="Close" 
                    @click.stop="handleClearSession(session)"
                  />
                </div>
                <div v-if="sessions.length === 0" class="empty-logs">暂无活跃会话</div>
              </div>
            </el-card>
          </el-col>
          <el-col :span="16">
            <el-card shadow="never" class="memory-viewer">
              <template #header>
                <div class="card-header">
                  <span>会话上下文：{{ selectedSession || '未选择' }}</span>
                </div>
              </template>
              <div class="memory-stream" v-if="selectedSession" v-loading="contextLoading">
                <div v-for="(msg, idx) in stMemory" :key="idx" class="memory-msg" :class="msg.role">
                  <div class="msg-header">
                    <span class="role-tag">{{ msg.role.toUpperCase() }}</span>
                    <span class="msg-time">{{ formatTime(msg.timestamp) }}</span>
                  </div>
                  <div class="msg-content">{{ msg.content }}</div>
                </div>
                <div v-if="stMemory.length === 0" class="empty-logs">会话已清空或无内容</div>
              </div>
              <div v-else class="select-hint">请从左侧选择一个会话以查看详情</div>
            </el-card>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>
  </div>

  <!-- Add/Edit Prompt Dialog (Already existing but keeping integrated) -->
  <el-dialog v-model="promptDialogVisible" :title="promptForm.id ? '编辑 Prompt 模板' : '新建 Prompt 模板'" width="500px">
    <el-form :model="promptForm" label-width="100px">
      <el-form-item label="模板名称">
        <el-input v-model="promptForm.name" placeholder="例如：客服标准回复" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="promptForm.type" placeholder="选择类型" style="width: 100%">
          <el-option label="角色设定 (ROLE)" value="ROLE" />
          <el-option label="指令 (INSTRUCTION)" value="INSTRUCTION" />
          <el-option label="少样本 (FEW_SHOT)" value="FEW_SHOT" />
        </el-select>
      </el-form-item>
      <el-form-item label="内容">
        <el-input 
          v-model="promptForm.content" 
          type="textarea" 
          :rows="6" 
          placeholder="输入 Prompt 内容..." 
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="promptDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPrompt">确认保存</el-button>
      </span>
    </template>
  </el-dialog>

  <!-- View Content Dialog -->
  <el-dialog v-model="viewVisible" :title="viewTitle" width="600px">
    <div class="view-content-box">
      {{ viewText }}
    </div>
  </el-dialog>
  
  <!-- Extract Memory Dialog -->
  <el-dialog v-model="extractDialogVisible" title="AI 记忆自动提取" width="500px">
    <el-form :model="extractForm" label-width="100px">
      <el-form-item label="输入对话历史">
        <el-input 
          v-model="extractForm.content" 
          type="textarea" 
          :rows="8" 
          placeholder="粘贴一段对话或描述，AI 将自动分析并提取出其中的关键事实（如用户偏好、背景信息等）存入长期记忆库。" 
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="extractDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="extractLoading" @click="submitExtract">分析并保存</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { Plus, Refresh, Edit, Delete, ChatDotRound, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
  agentId: {
    type: String,
    required: true
  }
})

// Watch agentId change
watch(() => props.agentId, (newVal) => {
  if (newVal) {
    loadMeta()
    loadSessions()
    selectedSession.value = null
    stMemory.value = []
  }
})

const loading = ref(false)
const prompts = ref([])
const memory = ref([])

// Short-term memory states
const sessions = ref([])
const selectedSession = ref(null)
const stMemory = ref([])
const sessionLoading = ref(false)
const contextLoading = ref(false)

const promptDialogVisible = ref(false)
const promptForm = ref({
  id: null,
  name: '',
  type: 'ROLE',
  content: ''
})

const viewVisible = ref(false)
const viewTitle = ref('')
const viewText = ref('')

const viewContent = (row) => {
  viewTitle.value = row.name
  viewText.value = row.content
  viewVisible.value = true
}

// Formatters
const formatTime = (ts) => {
  if (!ts) return '-'
  return new Date(ts).toLocaleString()
}

const loadMeta = async () => {
  if (!props.agentId) return
  loading.value = true
  try {
    const [pRes, mRes] = await Promise.all([
      request.get(`/knowledge/agents/${props.agentId}/meta/prompts`),
      request.get(`/knowledge/agents/${props.agentId}/meta/memory`)
    ])
    prompts.value = pRes || []
    memory.value = mRes || []
  } catch (e) {
    console.error(e)
    ElMessage.error('加载元数据失败')
  } finally {
    loading.value = false
  }
}

const loadSessions = async () => {
  if (!props.agentId) return
  sessionLoading.value = true
  try {
    const res = await request.get(`/knowledge/agents/${props.agentId}/meta/memory/sessions`)
    sessions.value = res || []
  } catch (e) {
    console.error(e)
  } finally {
    sessionLoading.value = false
  }
}

const viewSessionMemory = async (sessionId) => {
  selectedSession.value = sessionId
  contextLoading.value = true
  try {
    const res = await request.get(`/knowledge/agents/${props.agentId}/meta/memory/sessions/${sessionId}`)
    stMemory.value = res || []
  } catch (e) {
    ElMessage.error('加载会话记忆失败')
  } finally {
    contextLoading.value = false
  }
}

const handleClearSession = (sessionId) => {
  ElMessageBox.confirm(`确定要清除会话 "${sessionId}" 的短期记忆吗？`, '警告', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/knowledge/agents/${props.agentId}/meta/memory/sessions/${sessionId}`)
      ElMessage.success('清除成功')
      loadSessions()
      if (selectedSession.value === sessionId) {
        selectedSession.value = null
        stMemory.value = []
      }
    } catch (e) {
      ElMessage.error('清除失败')
    }
  })
}

// Long-term memory actions
const deleteMemoryItem = (row) => {
  ElMessageBox.confirm('确定要删除这条长期记忆事实吗？', '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/knowledge/agents/${props.agentId}/meta/memory/${row.id}`)
      ElMessage.success('已删除')
      loadMeta()
    } catch (e) {
      ElMessage.error('删除失败')
    }
  })
}

const clearMemory = () => {
  ElMessageBox.confirm('确定要清空该智能体的所有长期记忆吗？此操作不可逆！', '极度警告', {
    confirmButtonText: '确定清空',
    cancelButtonText: '点错了',
    type: 'error'
  }).then(async () => {
    try {
      await request.delete(`/knowledge/agents/${props.agentId}/meta/memory`)
      ElMessage.success('已清空长期记忆库')
      loadMeta()
    } catch (e) {
      ElMessage.error('操作失败')
    }
  })
}

const openPromptDialog = () => {
  promptForm.value = { id: null, name: '', type: 'ROLE', content: '' }
  promptDialogVisible.value = true
}

const handleEditPrompt = (row) => {
  promptForm.value = { ...row }
  promptDialogVisible.value = true
}

const deletePrompt = (row) => {
  ElMessageBox.confirm(`确定要删除模板 "${row.name}" 吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    try {
      await request.delete(`/knowledge/agents/${props.agentId}/meta/prompts/${row.id}`)
      ElMessage.success('删除成功')
      loadMeta()
    } catch (e) {
      ElMessage.error('删除失败')
    }
  })
}

const submitPrompt = async () => {
  if (!promptForm.value.name || !promptForm.value.content) {
    ElMessage.warning('请填写名称和内容')
    return
  }
  
  try {
    await request.post(`/knowledge/agents/${props.agentId}/meta/prompts`, {
      ...promptForm.value,
      agentId: props.agentId,
      isActive: true
    })
    ElMessage.success('保存成功')
    promptDialogVisible.value = false
    loadMeta()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

onMounted(() => {
  loadMeta()
  loadSessions()
})

const extractDialogVisible = ref(false)
const extractLoading = ref(false)
const extractForm = ref({ content: '' })

const openExtractDialog = () => {
  extractForm.value = { content: '' }
  extractDialogVisible.value = true
}

const submitExtract = async () => {
  if (!extractForm.value.content) return
  extractLoading.value = true
  try {
    await request.post(`/knowledge/agents/${props.agentId}/meta/extract_memory`, {
      content: extractForm.value.content
    })
    ElMessage.success('分析提取并保存成功')
    extractDialogVisible.value = false
    loadMeta()
  } catch (e) {
    ElMessage.error('分析失败：' + (e.message || '系统异常'))
  } finally {
    extractLoading.value = false
  }
}

defineExpose({
  openPromptDialog,
  openExtractDialog
})
</script>

<style scoped>
.meta-tabs {
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-sm);
}
.tab-card {
  border: none !important;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header .title {
  font-weight: 700;
  color: var(--neutral-gray-800);
}
.empty-hint {
  text-align: center;
  padding: 40px;
  color: var(--neutral-gray-400);
  font-size: 14px;
}

/* Session List Styles */
.session-card { min-height: 500px; }
.session-list {
  max-height: 440px;
  overflow-y: auto;
}
.session-item {
  padding: 12px 15px;
  margin-bottom: 8px;
  border-radius: var(--radius-base);
  background: var(--neutral-gray-50);
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.session-item:hover { background: var(--neutral-gray-100); }
.session-item.active {
  background: var(--orin-primary-soft);
  color: var(--orin-primary);
  border: 1px solid var(--orin-primary);
}
.session-id { flex: 1; font-size: 12px; font-family: monospace; overflow: hidden; text-overflow: ellipsis; }

/* Memory Viewer Styles */
.memory-viewer { min-height: 500px; }
.memory-stream {
  max-height: 440px;
  overflow-y: auto;
  padding: 10px;
}
.memory-msg {
  margin-bottom: 16px;
  padding: 12px;
  border-radius: 8px;
  max-width: 90%;
}
.memory-msg.user { background: #eef2ff; align-self: flex-start; }
.memory-msg.assistant { background: #f0f9eb; align-self: flex-end; margin-left: 10%; }
.msg-header {
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  margin-bottom: 6px;
  color: #888;
}
.role-tag { font-weight: bold; }
.msg-content { font-size: 13px; line-height: 1.5; white-space: pre-wrap; }
.select-hint {
  height: 400px;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--neutral-gray-400);
}
.view-content-box {
  white-space: pre-wrap;
  background: #f5f7fa;
  padding: 20px;
  border-radius: 8px;
  max-height: 500px;
  overflow-y: auto;
  line-height: 1.6;
}
</style>
