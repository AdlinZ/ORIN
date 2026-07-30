<template>
    <div class="agent-draft-page" v-loading="loading">
        <header class="page-header">
            <div class="title-block">
                <h2>{{ form.name || '（未命名 Agent）' }}</h2>
                <div class="state-row">
                    <el-tag v-if="stateLabel === 'FROZEN'" type="success" size="small">
                        可运行 · v{{ form.activeVersionNumber || '?' }}
                    </el-tag>
                    <el-tag v-else-if="stateLabel === 'DEPRECATED'" type="info" size="small">
                        已退役
                    </el-tag>
                    <el-tag v-else type="warning" size="small" effect="plain">编辑中</el-tag>
                    <el-button
                        v-if="stateLabel !== 'DRAFT'"
                        link
                        type="primary"
                        size="small"
                        @click="goVersions"
                    >查看版本</el-button>
                </div>
                <p class="hint">
                    完成名称、系统指令和模型配置后，冻结为一个可运行、可发布的版本。
                </p>
            </div>
            <div class="header-actions">
                <el-button :icon="Back" @click="goBack">返回列表</el-button>
            </div>
        </header>

        <el-alert
            v-if="errorMessage"
            type="error"
            :title="errorMessage.title"
            :description="errorMessage.detail"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
        />

        <el-alert
            v-if="validationWarnings.length"
            type="warning"
            :title="`校验提示（${validationWarnings.length} 项）`"
            show-icon
            :closable="false"
            style="margin-bottom: 16px"
        >
            <ul>
                <li v-for="(w, idx) in validationWarnings" :key="idx">{{ w }}</li>
            </ul>
        </el-alert>

        <el-alert
            v-if="lastSaveMessage"
            type="success"
            :title="lastSaveMessage"
            show-icon
            :closable="true"
            @close="lastSaveMessage = ''"
            style="margin-bottom: 16px"
        />

        <el-form
            :model="form"
            :rules="rules"
            label-position="top"
            class="agent-form"
        >
            <section class="form-section">
                <h3>基础信息</h3>
                <el-form-item label="名称" prop="name">
                    <el-input v-model="form.name" placeholder="例如 prod-sales-1" />
                </el-form-item>
                <el-form-item label="说明" prop="description">
                    <el-input
                        v-model="form.description"
                        type="textarea"
                        :rows="3"
                        placeholder="Agent 用途与边界"
                    />
                </el-form-item>
            </section>

            <section class="form-section">
                <h3>核心配置</h3>
                <p class="section-hint">这些内容直接决定 Agent 如何响应。</p>
                <el-form-item label="系统指令" prop="systemPrompt">
                    <el-input
                        v-model="form.systemPrompt"
                        type="textarea"
                        :rows="6"
                        placeholder="说明角色、目标、边界和期望的输出方式"
                    />
                </el-form-item>
                <el-form-item label="模型" prop="modelName">
                    <el-input v-model="form.modelName" placeholder="例如 gpt-4o、qwen-max" />
                </el-form-item>
            </section>

            <section class="form-section advanced-section">
                <el-collapse v-model="advancedPanels">
                    <el-collapse-item name="execution" title="高级执行参数">
                        <p class="section-hint">只有需要覆盖默认行为时才调整。</p>
                        <el-row :gutter="16">
                            <el-col :xs="24" :sm="12">
                                <el-form-item label="运行模式" prop="mode">
                                    <el-select v-model="form.mode" placeholder="选择模式">
                                        <el-option label="Agent" value="agent" />
                                        <el-option label="对话" value="chat" />
                                        <el-option label="文本补全" value="completion" />
                                        <el-option label="工作流" value="workflow" />
                                    </el-select>
                                </el-form-item>
                            </el-col>
                            <el-col :xs="24" :sm="12">
                                <el-form-item label="模型提供方" prop="providerType">
                                    <el-input v-model="form.providerType" placeholder="例如 OPENAI、LOCAL" />
                                </el-form-item>
                            </el-col>
                        </el-row>
                        <el-row :gutter="16">
                            <el-col :xs="24" :sm="8">
                                <el-form-item label="随机性">
                                    <el-input-number v-model="form.temperature" :step="0.05" :min="0" :max="2" :precision="2" />
                                </el-form-item>
                            </el-col>
                            <el-col :xs="24" :sm="8">
                                <el-form-item label="采样范围">
                                    <el-input-number v-model="form.topP" :step="0.05" :min="0" :max="1" :precision="2" />
                                </el-form-item>
                            </el-col>
                            <el-col :xs="24" :sm="8">
                                <el-form-item label="最大输出长度">
                                    <el-input-number v-model="form.maxTokens" :step="64" :min="1" />
                                </el-form-item>
                            </el-col>
                        </el-row>
                    </el-collapse-item>
                    <el-collapse-item name="secrets" title="凭据绑定">
                        <p class="section-hint">仅在模型或工具需要私密凭据时添加。凭据值不会写入 Agent 版本。</p>
                        <SecretReferenceEditor
                            v-model="secretRefs"
                            :available-secrets="availableSecrets"
                        />
                    </el-collapse-item>
                </el-collapse>
            </section>

            <section class="form-section sticky-actions">
                <div class="readiness">
                    <div>
                        <strong>冻结准备</strong>
                        <span>{{ readinessSummary }}</span>
                    </div>
                    <el-tag :type="validationWarnings.length ? 'warning' : 'success'" effect="plain">
                        {{ validationWarnings.length ? `还差 ${validationWarnings.length} 项` : '可以冻结' }}
                    </el-tag>
                </div>
                <div class="actions">
                    <el-button
                        :icon="Document"
                        @click="onSaveDraft"
                        :loading="saving"
                    >保存草稿</el-button>
                    <el-button
                        type="primary"
                        :icon="Lock"
                        :loading="freezing"
                        @click="onFreeze"
                    >校验并冻结</el-button>
                </div>
            </section>
        </el-form>
    </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Back, Document, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import SecretReferenceEditor from '@/domains/agent/components/SecretReferenceEditor.vue'
import { ROUTES } from '@/router/routes'
import {
    freezeAgentVersion,
    getAgentDraft,
    listActiveGatewaySecrets,
    upsertAgentDraft
} from '@/domains/agent/api'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const freezing = ref(false)
const availableSecrets = ref([])
const secretRefs = ref([])
const errorMessage = ref(null)
const validationWarnings = ref([])
const lastSaveMessage = ref('')
const advancedPanels = ref([])

const form = reactive({
    name: '',
    description: '',
    systemPrompt: '',
    mode: 'agent',
    modelName: '',
    providerType: '',
    temperature: 0.7,
    topP: 1.0,
    maxTokens: 2048,
    activeVersionId: null,
    activeVersionNumber: null,
    activeVersionStatus: null,
})

const rules = {
    name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
}

const stateLabel = computed(() => form.activeVersionStatus || 'DRAFT')
const readinessSummary = computed(() => validationWarnings.value.length
    ? '补齐必要配置后即可生成可运行版本'
    : `核心配置已完成${secretRefs.value.length ? `，已绑定 ${secretRefs.value.length} 个凭据` : ''}`)

const loadDraft = async () => {
    loading.value = true
    errorMessage.value = null
    try {
        const draft = await getAgentDraft(route.params.agentId)
        Object.assign(form, {
            name: draft.name,
            description: draft.description,
            systemPrompt: draft.system_prompt || draft.systemPrompt,
            mode: draft.mode,
            modelName: draft.modelName,
            providerType: draft.providerType,
            temperature: draft.temperature,
            topP: draft.topP,
            maxTokens: draft.maxTokens,
            activeVersionId: draft.activeVersionId || draft.active_version_id,
            activeVersionNumber: draft.activeVersionNumber || draft.active_version_number,
            activeVersionStatus: draft.activeVersionStatus || draft.active_version_status,
        })
        // 反序列化草稿上的 secret refs
        const refsJson = draft.pendingSecretRefs || draft.pending_secret_refs
        if (refsJson) {
            try {
                const parsed = JSON.parse(refsJson)
                secretRefs.value = parsed.map((r) => ({
                    alias: r.alias,
                    source: r.source || 'CONTROL_PLANE',
                    secretId: r.secret_id || r.secretId,
                    injectAs: r.inject_as || r.injectAs,
                    required: r.required !== false,
                }))
            } catch (e) {
                console.error('parse pendingSecretRefs failed', e)
            }
        }
        await validateForm()
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
        throw e
    } finally {
        loading.value = false
    }
}

const loadAvailableSecrets = async () => {
    try {
        const list = await listActiveGatewaySecrets()
        availableSecrets.value = list || []
    } catch (_) {
        availableSecrets.value = []
    }
}

const validateForm = async () => {
    const warnings = []
    if (!form.name?.trim()) warnings.push('请填写 Agent 名称')
    if (!form.modelName?.trim()) warnings.push('请选择或填写模型')
    if (!form.systemPrompt?.trim()) warnings.push('请填写系统指令')
    validationWarnings.value = warnings
}

const onSaveDraft = async () => {
    saving.value = true
    errorMessage.value = null
    try {
        await validateForm()
        await upsertAgentDraft(route.params.agentId, {
            name: form.name,
            description: form.description,
            systemPrompt: form.systemPrompt,
            mode: form.mode,
            modelName: form.modelName,
            providerType: form.providerType,
            temperature: form.temperature,
            topP: form.topP,
            maxTokens: form.maxTokens,
            changeDescription: '手动保存草稿',
            pendingSecretRefs: secretRefs.value.map((r) => ({
                alias: r.alias,
                source: r.source,
                secret_id: r.secretId,
                required: r.required,
                inject_as: r.injectAs,
            })),
        })
        await loadDraft()
        lastSaveMessage.value = `草稿已保存（${new Date().toLocaleTimeString()}）`
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
        ElMessage.error(formatErrorShort(e))
    } finally {
        saving.value = false
    }
}

const onFreeze = async () => {
    freezing.value = true
    errorMessage.value = null
    try {
        // 先把当前表单（包括 secret refs）持久化到草稿，保证 freeze 看到的就是用户最新输入
        await onSaveDraft()
        if (errorMessage.value) {
            // 保存失败，不能 freeze
            return
        }
        const resp = await freezeAgentVersion(route.params.agentId)
        ElMessage.success(`v${resp.versionNumber} 已冻结，现在可以运行或发布`)
        router.push(ROUTES.AGENTS.WORKSPACE_VERSION_DETAIL
            .replace(':agentId', route.params.agentId)
            .replace(':versionId', resp.agent_version_id))
    } catch (e) {
        errorMessage.value = formatErrorTitle(e)
    } finally {
        freezing.value = false
    }
}

const formatErrorTitle = (e) => {
    const data = e?.response?.data || e?.data || {}
    return {
        title: `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`,
        detail: data.detail || JSON.stringify(data).slice(0, 280),
    }
}

const formatErrorShort = (e) => {
    const data = e?.response?.data || e?.data || {}
    return `[${data.code || 'ERR'}] ${data.message || e?.message || '未知错误'}`
}

const goBack = () => router.push(ROUTES.AGENTS.WORKSPACE_LIST)
const goVersions = () => router.push(ROUTES.AGENTS.WORKSPACE_VERSIONS.replace(':agentId', route.params.agentId))

onMounted(async () => {
    await Promise.all([loadDraft(), loadAvailableSecrets()])
})
</script>

<style scoped>
.agent-draft-page {
    padding: 24px;
    max-width: 920px;
    margin: 0 auto;
}
.page-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 16px;
    gap: 16px;
}
.title-block h2 {
    margin: 0 0 6px;
    font-size: 22px;
    color: var(--text-primary, #0f172a);
}
.state-row {
    display: flex;
    gap: 8px;
    align-items: center;
    margin-bottom: 6px;
    flex-wrap: wrap;
}
.hint {
    margin: 0;
    color: var(--text-secondary, #64748b);
    font-size: 13px;
    line-height: 1.55;
    max-width: 720px;
}
.header-actions {
    flex: 0 0 auto;
}
.form-section {
    margin-bottom: 24px;
    padding: 16px 20px;
    background: #fff;
    border-radius: 10px;
    border: 1px solid var(--orin-border, #e2e8f0);
}
.form-section h3 {
    margin: 0 0 12px;
    font-size: 16px;
    color: var(--text-primary, #0f172a);
}
.section-hint {
    margin: 0 0 12px;
    color: var(--text-secondary, #64748b);
    font-size: 12px;
}
.advanced-section {
    padding-top: 6px;
    padding-bottom: 6px;
}
.advanced-section :deep(.el-collapse) {
    border: 0;
}
.advanced-section :deep(.el-collapse-item__header) {
    font-size: 15px;
    font-weight: 600;
}
.advanced-section :deep(.el-collapse-item__wrap) {
    border-bottom: 0;
}
.advanced-section :deep(.el-select),
.advanced-section :deep(.el-input-number) {
    width: 100%;
}
.sticky-actions {
    position: sticky;
    bottom: 0;
    background: #f8fafc;
    border-color: var(--orin-border-strong, #d8e0e8);
    box-shadow: 0 -2px 8px rgba(15, 23, 42, 0.04);
}
.readiness {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
}
.readiness div {
    display: flex;
    flex-direction: column;
    gap: 3px;
}
.readiness span {
    color: var(--text-secondary, #64748b);
    font-size: 13px;
}
.actions {
    display: flex;
    gap: 12px;
    justify-content: flex-end;
}
</style>
