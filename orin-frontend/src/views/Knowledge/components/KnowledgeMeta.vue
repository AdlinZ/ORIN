<template>
  <div class="meta-section">
      <el-row :gutter="20">
          <el-col :span="12">
              <el-card shadow="hover" class="meta-card">
                  <template #header>
                       <div class="card-header">
                           <span>Prompt 模板</span>
                           <div class="header-actions">
                               <el-button link type="primary" :icon="Refresh" @click="loadMeta"></el-button>
                               <el-button link type="primary" :icon="Plus" @click="openPromptDialog">新建</el-button>
                           </div>
                       </div>
                  </template>
                  <el-table :data="prompts" style="width: 100%" v-loading="loading">
                      <el-table-column prop="name" label="模板名称" />
                      <el-table-column prop="type" label="类型" width="100">
                          <template #default="{ row }">
                              <el-tag size="small">{{ row.type }}</el-tag>
                          </template>
                      </el-table-column>
                        <el-table-column label="操作" width="180">
                            <template #default="{ row }">
                                <el-button link @click="viewContent(row)">查看</el-button>
                                <el-button link type="primary" @click="handleEditPrompt(row)">编辑</el-button>
                                <el-button link type="danger" @click="deletePrompt(row)">删除</el-button>
                            </template>
                        </el-table-column>
                  </el-table>
              </el-card>
          </el-col>
          
          <el-col :span="12">
              <el-card shadow="hover" class="meta-card">
                  <template #header>
                       <div class="card-header">
                           <span>长期记忆 (Long-term Memory)</span>
                           <div class="header-actions">
                               <el-button link type="primary" :icon="Plus" @click="openExtractDialog">记忆提取</el-button>
                               <el-button link type="primary" :icon="Refresh" @click="loadMeta"></el-button>
                           </div>
                       </div>
                  </template>
                  <el-table :data="memory" style="width: 100%" v-loading="loading">
                      <el-table-column prop="key" label="Key" width="150" />
                      <el-table-column prop="value" label="Value" show-overflow-tooltip />
                      <el-table-column prop="updatedAt" label="最后更新" width="150">
                           <template #default="{ row }">
                                {{ new Date(row.updatedAt).toLocaleDateString() }}
                           </template>
                      </el-table-column>
                  </el-table>
              </el-card>
          </el-col>
      </el-row>
  </div>
  
  <!-- Add/Edit Prompt Dialog -->
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
      <div style="white-space: pre-wrap; background: #f5f7fa; padding: 15px; border-radius: 4px; max-height: 500px; overflow-y: auto;">
          {{ viewText }}
      </div>
  </el-dialog>
  
  <!-- Extract Memory Dialog -->
    <el-dialog v-model="extractDialogVisible" title="手动提取记忆" width="500px">
        <el-form :model="extractForm" label-width="100px">
            <el-form-item label="对话历史">
                <el-input 
                    v-model="extractForm.content" 
                    type="textarea" 
                    :rows="8" 
                    placeholder="粘贴一段对话或描述，系统将自动提取结构化记忆 (Key-Value)..." 
                />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="extractDialogVisible = false">取消</el-button>
                <el-button type="primary" :loading="extractLoading" @click="submitExtract">开始提取</el-button>
            </span>
        </template>
    </el-dialog>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, Refresh, Edit, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const props = defineProps({
    agentId: {
        type: String,
        required: true
    }
})

// Watch agentId change
import { watch } from 'vue'
watch(() => props.agentId, (newVal) => {
    if(newVal) loadMeta()
})

const loading = ref(false)
const prompts = ref([])
const memory = ref([])

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

const loadMeta = async () => {
    if (!props.agentId) return
    loading.value = true
    try {
        const [pRes, mRes] = await Promise.all([
            request.get(`/knowledge/agents/${props.agentId}/meta/prompts`),
            request.get(`/knowledge/agents/${props.agentId}/meta/memory`)
        ])
        prompts.value = pRes.data
        memory.value = mRes.data
    } catch (e) {
        console.error(e)
        prompts.value = []
        memory.value = []
        ElMessage.error('加载元数据失败')
    } finally {
        loading.value = false
    }
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
    if(!promptForm.value.name || !promptForm.value.content) {
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
        ElMessage.success('提取并保存成功')
        extractDialogVisible.value = false
        loadMeta()
    } catch (e) {
        ElMessage.error('提取失败：' + (e.message || '未知错误'))
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
.card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
}
.meta-card {
    min-height: 400px;
}
</style>
