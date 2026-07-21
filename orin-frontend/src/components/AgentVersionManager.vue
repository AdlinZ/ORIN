<template>
    <OrinDataTable class="version-manager-redirect">
        <el-empty
            description="Agent 版本管理已迁移到 Workspace vNext"
            :image-size="80"
        >
            <template #default>
                <p class="hint">
                    旧的「创建新版本 / 回滚 / 对比」API 已被不可变 AgentVersion 流程取代。<br />
                    请前往 <code>/workspace/agents</code> 完成创建、冻结、查看与切换 active。
                </p>
                <el-button
                    v-if="agentId"
                    type="primary"
                    :icon="Right"
                    @click="goWorkspace"
                >前往 Agent 详情</el-button>
                <el-button
                    v-else
                    type="primary"
                    :icon="Right"
                    @click="goList"
                >前往 Workspace Agents 列表</el-button>
            </template>
        </el-empty>
    </OrinDataTable>
</template>

<script setup>
import { Right } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import { ROUTES } from '@/router/routes'

const props = defineProps({
    agentId: { type: String, default: '' },
})

const router = useRouter()
const goWorkspace = () => {
    if (props.agentId) router.push(ROUTES.AGENTS.WORKSPACE_DRAFT.replace(':agentId', props.agentId))
}
const goList = () => router.push(ROUTES.AGENTS.WORKSPACE_LIST)
</script>

<style scoped>
.hint {
    color: var(--text-secondary, #64748b);
    font-size: 13px;
    line-height: 1.6;
    margin: 12px 0 16px;
}
.hint code {
    background: #f1f5f9;
    padding: 1px 6px;
    border-radius: 4px;
    font-size: 12px;
}
</style>
