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
            <div class="toolbar">
                <el-input 
                  v-model="memorySearch" 
                  placeholder="搜索记忆片段..." 
                  prefix-icon="Search" 
                  style="width: 300px" 
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
        <el-tab-pane label="Prompt 版本管理" name="prompts">
            <div class="prompt-container">
                <!-- Main Editor Area -->
                <div class="prompt-editor-area">
                    <div class="editor-header">
                        <span>System Prompt (v2.1)</span>
                        <div class="editor-actions">
                             <el-switch v-model="showDiff" active-text="Show Diff" />
                             <el-button type="primary" size="small" @click="savePrompt">Save Version</el-button>
                        </div>
                    </div>
                    <el-input 
                        v-model="prompts.current" 
                        type="textarea" 
                        class="main-textarea"
                        placeholder="Enter system prompt..."
                    />
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

                <!-- Sandbox FAB -->
                <div class="sandbox-fab" @click="toggleSandbox" title="试一试 (Sandbox)">
                    <el-icon :size="24"><ChatLineRound /></el-icon>
                </div>

                <!-- Sandbox Chat Window -->
                <transition name="pop-up">
                    <div class="sandbox-window" v-if="showSandbox">
                        <div class="sandbox-header">
                            <span>Prompt Sandbox (Preview)</span>
                            <el-icon @click="showSandbox = false" style="cursor:pointer"><Close /></el-icon>
                        </div>
                        <div class="sandbox-messages">
                            <div class="msg ai">Hello! I am running with your UNSAVED prompt. How can I help?</div>
                            <div class="msg user">Test command execution.</div>
                            <div class="msg ai">I can verify that command. Please provide specific args...</div>
                        </div>
                        <div class="sandbox-input">
                            <el-input placeholder="Type a message..." size="small">
                                <template #append><el-button :icon="Position" /></template>
                            </el-input>
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
import { ref, computed, reactive, watch } from 'vue';
import PageHeader from '@/components/PageHeader.vue';
import { 
  Plus, Search, Check, CircleCheck, Warning, 
  Delete, Edit, ChatLineRound, Close, Position 
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';

const activeTab = ref('memory');

// --- Memory Logic ---
const memorySearch = ref('');
const memories = ref([
    { memoryId: 'M001', key: 'user_profile', value: '用户是高级Java工程师，偏好Spring Boot架构', createdAt: '2026-01-20 10:00' },
    { memoryId: 'M002', key: 'project_preference', value: '项目倾向于使用 DDD 领域驱动设计', createdAt: '2026-01-22 14:30' },
    { memoryId: 'M003', key: 'ui_theme', value: '喜欢深色模式 (Dark Mode)', createdAt: '2026-01-25 09:15' }
]);

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

const deleteMemory = (row) => {
    // Already confirmed by popconfirm
    memories.value = memories.value.filter(m => m.memoryId !== row.memoryId);
    ElMessage.success('记忆已物理删除 (Forgotten)');
};

const submitMemory = () => {
    if(memoryDialog.isEdit) {
        // Find and update
        const idx = memories.value.findIndex(m => m.memoryId === memoryDialog.form.memoryId);
        if(idx !== -1) memories.value[idx] = { ...memories.value[idx], ...memoryDialog.form };
    } else {
        memories.value.unshift({
            memoryId: 'M' + Date.now(),
            key: memoryDialog.form.key,
            value: memoryDialog.form.value,
            createdAt: new Date().toLocaleString()
        });
    }
    memoryDialog.visible = false;
    ElMessage.success('记忆已保存');
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
const skills = ref([
    { id: 'S1', name: 'google_search', type: 'tool', schema: '{\n  "type": "function",\n  "function": {\n    "name": "google_search",\n    "description": "Search query on Google",\n    "parameters": {\n      "type": "object",\n      "properties": {\n        "query": { "type": "string" }\n      }\n    }\n  }\n}' },
    { id: 'S2', name: 'send_email', type: 'action', schema: '{\n  "type": "function",\n  "function": {\n    "name": "send_email",\n    "parameters": { ... }\n  }\n}' }
]);
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

const saveSkill = () => {
    if(!jsonValid.value) {
        ElMessage.error('JSON 格式错误，请修正后再保存');
        return;
    }
    // Update list
    const idx = skills.value.findIndex(s => s.id === selectedSkill.value.id);
    if(idx !== -1) skills.value[idx] = { ...selectedSkill.value };
    ElMessage.success('技能定义已保存');
};

// --- Prompt Logic ---
const showDiff = ref(false);
const showSandbox = ref(false);
const vRight = ref('v1.9');
const prompts = reactive({
    current: `You are ORIN, an advanced AI Assistant.
Your primary goal is to assist users with system operations.

Rules:
1. Be concise.
2. Always output JSON for structured data.
3. Verify actions before execution.`,
    previous: `You are ORIN, an AI Assistant.
Your goal is to help users.

Rules:
1. Be helpful.
2. Verify actions.`
});

const toggleSandbox = () => {
    showSandbox.value = !showSandbox.value;
};

const savePrompt = () => {
    ElMessage.success('Prompt saved as new version v2.2');
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
    position: relative;
    overflow: hidden;
}

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
    font-weight: 600;
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

/* Floating Diff Sidebar */
.diff-sidebar {
    width: 350px;
    background: white;
    box-shadow: -5px 0 20px rgba(0,0,0,0.1);
    border-left: 1px solid #ebeef5;
    z-index: 10;
    display: flex;
    flex-direction: column;
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
}

.diff-header {
    padding: 15px;
    border-bottom: 1px solid #ebeef5;
    font-weight: 600;
    display: flex;
    justify-content: space-between;
}

.diff-content {
    flex: 1;
    overflow-y: auto;
    padding: 15px;
    font-family: monospace;
    font-size: 13px;
    background: #2d2d2d;
}

.diff-line { padding: 2px 4px; white-space: pre-wrap; }
.diff-line.removed { background: #4b1818; color: #ffa39e; text-decoration: line-through; }
.diff-line.added { background: #135200; color: #b7eb8f; }
.diff-line.unchanged { color: #8b949e; }

.diff-actions {
    padding: 15px;
    border-top: 1px solid #ebeef5;
}

/* Sandbox FAB & Window */
.sandbox-fab {
    position: absolute;
    bottom: 30px;
    right: 30px;
    width: 56px;
    height: 56px;
    background: var(--primary-color);
    color: white;
    border-radius: 50%;
    box-shadow: 0 6px 16px rgba(0,0,0,0.15);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: pointer;
    transition: all 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
    z-index: 100;
}

.sandbox-fab:hover {
    transform: scale(1.1);
}

.sandbox-window {
    position: absolute;
    bottom: 100px;
    right: 30px;
    width: 320px;
    height: 400px;
    background: white;
    border-radius: 12px;
    box-shadow: 0 6px 24px rgba(0,0,0,0.12);
    display: flex;
    flex-direction: column;
    z-index: 99;
    border: 1px solid #ebeef5;
}

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
