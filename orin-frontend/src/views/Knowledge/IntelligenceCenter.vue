<template>
  <div class="intelligence-center-container">
    <PageHeader 
      title="智力资产中心 Intelligence Center" 
      description="管控智能体的三大核心资产：长期记忆 (Memory)、专业技能 (Skills) 与 预设人设 (Prompts)。"
      icon="Cpu"
    />

    <div class="center-content">
      <el-tabs v-model="activeTab" class="main-tabs" type="border-card">
        
        <!-- Tab 1: Long-term Memory -->
        <el-tab-pane label="长期记忆 (Memory)" name="memory">
          <div class="tab-pane-content">
                <el-select v-model="selectedAgentId" placeholder="选择智能体" style="width: 250px" @change="loadAllData">
                    <el-option v-for="agent in agentOptions" :key="agent.agentId" :label="agent.name" :value="agent.agentId">
                        <div style="display: flex; align-items: center; gap: 8px">
                            <span v-if="agent.icon">{{ agent.icon }}</span>
                            <span>{{ agent.name }}</span>
                        </div>
                    </el-option>
                </el-select>
                <div class="right-tools">
                    <el-input 
                    v-model="memorySearch" 
                    placeholder="搜索记忆片段..." 
                    prefix-icon="Search" 
                    style="width: 250px; margin-right: 15px;" 
                    />
                    <el-button type="primary" :icon="Plus" @click="openAddMemory">新增记忆</el-button>
                </div>

            <!-- Vertical Timeline View -->
            <div class="memory-timeline-view">
                <el-timeline v-if="filteredMemories.length > 0">
                    <el-timeline-item
                        v-for="mem in filteredMemories"
                        :key="mem.memoryId"
                        :timestamp="mem.createdAt"
                        placement="top"
                        :type="getMemoryType(mem.key)"
                        :color="getMemoryColor(mem.key)"
                    >
                        <el-card class="memory-card">
                            <div class="memory-header">
                                <el-tag size="small" effect="dark" :color="getMemoryColor(mem.key)" style="border:none">
                                    {{ mem.key }}
                                </el-tag>
                                <div class="memory-actions">
                                    <el-button circle size="small" :icon="Edit" @click="editMemory(mem)" />
                                    <el-popconfirm title="确定要永久遗忘这条记忆吗？" @confirm="deleteMemory(mem)">
                                        <template #reference>
                                            <el-button circle size="small" type="danger" :icon="Delete" />
                                        </template>
                                    </el-popconfirm>
                                </div>
                            </div>
                            <div class="memory-body">
                                {{ mem.value }}
                            </div>
                        </el-card>
                    </el-timeline-item>
                </el-timeline>
                <el-empty v-else description="暂无相关记忆" />
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Skills & Tools -->
        <el-tab-pane label="技能与工具 (Skills)" name="skills">
            <div class="skill-layout">
                <div class="skill-list">
                    <div class="list-header">
                        <span>技能列表</span>
                        <el-button :icon="Plus" circle size="small" @click="createNewSkill" />
                    </div>
                    <div 
                        v-for="skill in skills" 
                        :key="skill.id" 
                        class="skill-item"
                        :class="{ active: selectedSkill && selectedSkill.id === skill.id }"
                        @click="selectSkill(skill)"
                    >
                        <div class="skill-name">{{ skill.name }}</div>
                        <div class="skill-type">{{ skill.type }}</div>
                    </div>
                </div>
                
                <div class="skill-editor" v-if="selectedSkill">
                    <div class="editor-toolbar">
                        <el-input v-model="selectedSkill.name" placeholder="技能名称 (e.g., web_search)" style="width: 300px" />
                        <div class="actions">
                             <el-button type="success" :icon="Check" @click="saveSkill">保存定义</el-button>
                        </div>
                    </div>
                    
                    <div class="json-editor-wrapper">
                        <label>Function Schema (JSON)</label>
                        <el-input
                           v-model="selectedSkill.schema"
                           type="textarea"
                           :rows="20"
                           class="code-input"
                           placeholder='{ "type": "function", "function": { ... } }'
                        />
                        <div class="validation-status" :class="jsonValid ? 'valid' : 'invalid'">
                            <el-icon v-if="jsonValid"><CircleCheck /></el-icon>
                            <el-icon v-else><Warning /></el-icon>
                            {{ jsonValid ? 'JSON 格式正确' : 'JSON 格式错误' }}
                        </div>
                    </div>
                </div>
                <div v-else class="empty-skill">
                    <el-empty description="选择或创建一个技能开始编辑" />
                </div>
            </div>
        </el-tab-pane>

        <!-- Tab 3: Prompt Versioning -->
        <el-tab-pane label="Prompt 编排与管理" name="prompts">
            <div class="prompt-container">
                <!-- Left Sidebar: Prompt List -->
                <div class="prompt-list-sidebar">
                    <div class="sidebar-header">
                        <span>Prompt 列表</span>
                        <el-button type="primary" link :icon="Plus" @click="createNewPrompt">新建</el-button>
                    </div>
                    <div class="prompt-list">
                        <div 
                            v-for="p in promptList" 
                            :key="p.id" 
                            class="prompt-item"
                            :class="{ active: currentPrompt && currentPrompt.id === p.id }"
                            @click="selectPrompt(p)"
                        >
                            <div class="prompt-item-header">
                                <span class="prompt-name">{{ p.name || '未命名 Prompt' }}</span>
                                <el-tag size="small" v-if="p.userId === userStore.userId">Private</el-tag>
                                <el-tag size="small" type="info" v-else>System</el-tag>
                            </div>
                            <div class="prompt-desc">{{ p.description || '暂无描述' }}</div>
                        </div>
                    </div>
                </div>

                <!-- Main Editor Area -->
                <div class="prompt-editor-area" v-if="currentPrompt">
                    <div class="editor-header">
                        <div class="header-inputs">
                             <el-input v-model="currentPrompt.name" placeholder="Prompt 名称" style="width: 200px; margin-right: 10px;" />
                             <el-input v-model="currentPrompt.description" placeholder="用途描述" style="width: 300px;" />
                        </div>
                        <div class="editor-actions">
                             <el-button type="danger" link :icon="Delete" @click="deletePrompt" v-if="currentPrompt.id">删除</el-button>
                             <el-button type="primary" size="small" @click="savePrompt">保存 Prompt</el-button>
                        </div>
                    </div>
                    <el-input 
                        v-model="currentPrompt.content" 
                        type="textarea" 
                        class="main-textarea"
                        placeholder="Enter system prompt..."
                    />
                </div>
                <div v-else class="empty-prompt">
                    <el-empty description="请选择或创建一个 Prompt" />
                </div>

                <!-- Floating Diff Window (Right Side) -->
                <transition name="slide-right">
                    <div class="diff-sidebar" v-if="showDiff">
                        <div class="diff-header">
                            <span>Diff vs {{ vRight }}</span>
                            <el-button link :icon="Close" @click="showDiff = false" />
                        </div>
                        <div class="diff-content">
                            <!-- Simple Visual Diff Simulation -->
                            <div class="diff-line removed">- You are ORIN, an AI Assistant.</div>
                            <div class="diff-line added">+ You are ORIN, an advanced AI Assistant.</div>
                            <div class="diff-line unchanged">  Your goal is to help users.</div>
                            <div class="diff-line added">+ Your primary goal is to assist users with system operations.</div>
                        </div>
                        <div class="diff-actions">
                             <el-select v-model="vRight" size="small" style="width: 100%">
                                 <el-option label="v1.9 (Stable)" value="v1.9" />
                                 <el-option label="v1.0 (Init)" value="v1.0" />
                             </el-select>
                        </div>
                    </div>
                </transition>

            </div>
        </el-tab-pane>

      </el-tabs>
    </div>

    <!-- Memory Dialog -->
    <el-dialog v-model="memoryDialog.visible" :title="memoryDialog.isEdit ? '编辑记忆' : '新增记忆'" width="500px">
       <el-form label-position="top">
           <el-form-item label="索引键 (Context Key)">
               <el-input v-model="memoryDialog.form.key" placeholder="e.g., user_preference_theme" />
           </el-form-item>
           <el-form-item label="记忆内容">
               <el-input v-model="memoryDialog.form.value" type="textarea" :rows="4" />
           </el-form-item>
       </el-form>
       <template #footer>
           <el-button @click="memoryDialog.visible = false">取消</el-button>
           <el-button type="primary" @click="submitMemory">确认保存</el-button>
       </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, reactive, watch, onMounted } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Plus, Search, Check, CircleCheck, Warning, 
  Delete, Edit, ChatLineRound, Close, Position 
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { 
    getMemories, saveMemory, deleteMemory as apiDeleteMemory,
    getSkills, saveSkill as apiSaveSkill, deleteSkill as apiDeleteSkill,
    getPrompts, savePrompt as apiSavePrompt, deletePrompt as apiDeletePrompt
} from '@/api/knowledge';
// chatAgent import removed as Sandbox is gone

const userStore = useUserStore();
const activeTab = ref('memory');
const selectedAgentId = ref('');
const agentOptions = ref([]);

const loadAgents = async () => {
    try {
        const res = await getAgentList();
        agentOptions.value = res.data || res;
        if (agentOptions.value.length > 0) {
            selectedAgentId.value = agentOptions.value[0].agentId;
            loadAllData();
        }
    } catch (e) {
        ElMessage.error('加载智能体列表失败');
    }
};

const loadAllData = () => {
    if (!selectedAgentId.value) return;
    loadMemories();
    loadSkills();
    loadPrompts();
};

onMounted(() => {
    loadAgents();
});

// --- Memory Logic ---
const memorySearch = ref('');
const memories = ref([]);

const loadMemories = async () => {
    if (!selectedAgentId.value) return;
    try {
        const res = await getMemories(selectedAgentId.value);
        memories.value = res.data || res;
    } catch (e) {
        console.error(e);
    }
};

const filteredMemories = computed(() => {
    if(!memorySearch.value) return memories.value;
    return memories.value.filter(m => m.key.includes(memorySearch.value) || m.value.includes(memorySearch.value));
});

const memoryDialog = reactive({
    visible: false,
    isEdit: false,
    form: { id: null, key: '', value: '' }
});

const openAddMemory = () => {
    memoryDialog.isEdit = false;
    memoryDialog.form = {  key: '', value: '' };
    memoryDialog.visible = true;
};

const editMemory = (row) => {
    memoryDialog.isEdit = true;
    memoryDialog.form = { ...row };
    memoryDialog.visible = true;
};

const deleteMemory = async (row) => {
    try {
        await apiDeleteMemory(row.id || row.memoryId);
        ElMessage.success('记忆已物理删除 (Forgotten)');
        loadMemories();
    } catch (e) {
        ElMessage.error('删除失败');
    }
};

const submitMemory = async () => {
    if (!selectedAgentId.value) {
        ElMessage.warning('请先选择智能体');
        return;
    }
    try {
        await saveMemory(selectedAgentId.value, memoryDialog.form.key, memoryDialog.form.value);
        memoryDialog.visible = false;
        ElMessage.success('记忆已保存');
        loadMemories();
    } catch (e) {
        ElMessage.error('保存失败');
    }
};

const getMemoryType = (key) => {
    if (key.includes('profile')) return 'primary';
    if (key.includes('preference')) return 'success';
    return 'info';
};

const getMemoryColor = (key) => {
    if (key.includes('profile')) return '#409EFF';
    if (key.includes('preference')) return '#67C23A';
    return '#909399';
};

// --- Skills Logic ---
const skills = ref([]);
const loadSkills = async () => {
    if (!selectedAgentId.value) return;
    try {
        const res = await getSkills(selectedAgentId.value);
        const rawSkills = res.data || res;
        // Transform backend definition (DSL) to schema for UI if needed, 
        // but here we just use it as 'schema'
        skills.value = rawSkills.map(s => ({
            ...s,
            schema: s.definition
        }));
    } catch (e) {
        console.error(e);
    }
};
const selectedSkill = ref(null);
const jsonValid = ref(true);

const selectSkill = (s) => {
    selectedSkill.value = { ...s }; // Clone
    validateJson();
};

const createNewSkill = () => {
    const newSkill = { id: 'NEW', name: 'new_tool', type: 'tool', schema: '{\n  "type": "function",\n  "function": {\n    "name": "",\n    "description": ""\n  }\n}' };
    skills.value.push(newSkill);
    selectedSkill.value = newSkill;
};

const validateJson = () => {
    try {
        JSON.parse(selectedSkill.value.schema);
        jsonValid.value = true;
    } catch (e) {
        jsonValid.value = false;
    }
};

watch(() => selectedSkill.value?.schema, () => {
    validateJson();
});

const saveSkill = async () => {
    if(!jsonValid.value) {
        ElMessage.error('JSON 格式错误，请修正后再保存');
        return;
    }
    try {
        const payload = {
            ...selectedSkill.value,
            agentId: selectedAgentId.value,
            definition: selectedSkill.value.schema,
            triggerName: selectedSkill.value.name
        };
        await apiSaveSkill(payload);
        ElMessage.success('技能定义已保存');
        loadSkills();
    } catch (e) {
        ElMessage.error('保存失败');
    }
};

// --- Prompt Logic ---
const showSandbox = ref(false);
const promptList = ref([]);
const currentPrompt = ref(null);

const toggleSandbox = () => {
    showSandbox.value = !showSandbox.value;
};

const loadPrompts = async () => {
    if (!selectedAgentId.value) return;
    try {
        const res = await getPrompts(selectedAgentId.value, userStore.userId);
        promptList.value = res.data || res;
        
        // Auto select first if available and nothing selected
        if (promptList.value.length > 0 && !currentPrompt.value) {
            currentPrompt.value = { ...promptList.value[0] };
        }
    } catch (e) {
        console.error(e);
    }
};

const selectPrompt = (p) => {
    currentPrompt.value = { ...p };
};

const createNewPrompt = () => {
    currentPrompt.value = {
        agentId: selectedAgentId.value,
        name: 'New Prompt',
        description: '',
        content: '',
        type: 'INSTRUCTION',
        userId: userStore.userId,
        isActive: true
    };
};

const savePrompt = async () => {
    if (!currentPrompt.value) return;
    try {
        const payload = { ...currentPrompt.value };
        // Ensure core fields
        if (!payload.agentId) payload.agentId = selectedAgentId.value;
        if (!payload.userId) payload.userId = userStore.userId;
        
        await apiSavePrompt(payload);
        ElMessage.success('Prompt saved');
        loadPrompts();
    } catch (e) {
        ElMessage.error('保存失败');
    }
};

const deletePrompt = async () => {
    if (!currentPrompt.value || !currentPrompt.value.id) return;
    try {
        await apiDeletePrompt(currentPrompt.value.id);
        ElMessage.success('已删除');
        currentPrompt.value = null;
        loadPrompts();
    } catch(e) {
        ElMessage.error('删除失败');
    }
};

</script>

<style scoped>
.intelligence-center-container {
    height: 100vh;
    display: flex;
    flex-direction: column;
}

.center-content {
    flex: 1;
    padding: 20px;
    background: #f5f7fa;
    display: flex;
    flex-direction: column;
}

.main-tabs {
    flex: 1;
    display: flex;
    flex-direction: column;
}

/* Tab Content Fix */
:deep(.el-tabs__content) {
    flex: 1;
    overflow: hidden;
    padding: 0;
    display: flex;
    flex-direction: column;
}

.tab-pane-content {
    padding: 20px;
    height: 100%;
    overflow-y: auto;
}

/* Memory Styles */
.toolbar {
    margin-bottom: 20px;
    display: flex;
    justify-content: space-between;
}

.memory-timeline-view {
    padding: 10px 20px;
}

.memory-card {
    border-radius: 8px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.memory-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;
}

.memory-body {
    font-size: 14px;
    color: var(--text-primary);
    line-height: 1.6;
}

/* Skills Styles */
.skill-layout {
    display: flex;
    height: 100%;
}

.skill-list {
    width: 250px;
    border-right: 1px solid #ebeef5;
    background: #fafafa;
    display: flex;
    flex-direction: column;
}

.list-header {
    padding: 15px;
    font-weight: 600;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.skill-item {
    padding: 15px;
    cursor: pointer;
    border-bottom: 1px solid #f0f2f5;
}

.skill-item:hover { background: #f0f2f5; }
.skill-item.active { background: white; border-left: 3px solid var(--primary-color); }

.skill-name { font-weight: 500; font-size: 14px; }
.skill-type { font-size: 12px; color: #909399; margin-top: 4px; }

.skill-editor {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 20px;
}

.editor-toolbar {
    display: flex;
    justify-content: space-between;
    margin-bottom: 20px;
}

.json-editor-wrapper {
    flex: 1;
    display: flex;
    flex-direction: column;
}

.json-editor-wrapper label {
    margin-bottom: 10px;
    font-size: 13px;
    color: #606266;
    font-weight: 600;
}

.code-input :deep(.el-textarea__inner) {
    font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
    background: #282c34;
    color: #abb2bf;
    line-height: 1.5;
}

.validation-status {
    margin-top: 10px;
    font-size: 13px;
    display: flex;
    align-items: center;
    gap: 6px;
}
.valid { color: var(--success-color); }
.invalid { color: var(--danger-color); }

/* Prompts Styles */
.prompt-container {
    height: 100%;
    display: flex;
    overflow: hidden;
    position: relative;
}

.prompt-list-sidebar {
    width: 280px;
    border-right: 1px solid #ebeef5;
    background: #fafafa;
    display: flex;
    flex-direction: column;
}

.sidebar-header {
    padding: 15px;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: 600;
}

.prompt-list {
    flex: 1;
    overflow-y: auto;
}

.prompt-item {
    padding: 12px 15px;
    border-bottom: 1px solid #f0f2f5;
    cursor: pointer;
    transition: background 0.2s;
}
.prompt-item:hover { background: #f0f2f5; }
.prompt-item.active { background: white; border-left: 3px solid var(--primary-color); }

.prompt-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 4px;
}
.prompt-name { font-weight: 500; font-size: 14px; color: #303133; }
.prompt-desc { font-size: 12px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

.prompt-editor-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    padding: 20px;
}

.editor-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
}

.header-inputs {
    display: flex;
    gap: 10px;
    flex: 1;
}

.editor-actions {
    display: flex;
    align-items: center;
    gap: 15px;
}

.main-textarea :deep(.el-textarea__inner) {
    height: 100%;
    font-family: monospace;
    font-size: 14px;
    line-height: 1.6;
}

/* Floating Diff Sidebar - Hidden for now */
.diff-sidebar { display: none; }

/* Old Sandbox styles removed */

.sandbox-header {
    padding: 12px 16px;
    background: #f5f7fa;
    border-bottom: 1px solid #ebeef5;
    border-radius: 12px 12px 0 0;
    font-weight: 600;
    font-size: 14px;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.sandbox-messages {
    flex: 1;
    padding: 15px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 12px;
}

.msg {
    padding: 10px;
    border-radius: 8px;
    font-size: 13px;
    max-width: 85%;
    line-height: 1.4;
}

.msg.ai { background: #f0f2f5; align-self: flex-start; color: #303133; }
.msg.user { background: var(--primary-color-light-9, #ecf5ff); align-self: flex-end; color: var(--primary-color); }

.sandbox-input {
    padding: 10px;
    border-top: 1px solid #ebeef5;
}

/* Animations */
.slide-right-enter-active, .slide-right-leave-active { transition: transform 0.3s; }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); }

.pop-up-enter-active, .pop-up-leave-active { transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
.pop-up-enter-from, .pop-up-leave-to { transform: scale(0.8); opacity: 0; }
</style>
