<template>
    <div class="secret-ref-editor">
        <el-table :data="rows" border>
            <el-table-column label="alias" min-width="160">
                <template #default="{ row, $index }">
                    <el-input
                        v-model="row.alias"
                        :disabled="disabled"
                        placeholder="openai.primary"
                        @change="emitUpdate"
                    />
                </template>
            </el-table-column>
            <el-table-column label="source" width="140" align="center">
                <template #default="{ row }">
                    <el-tag size="small" effect="plain">CONTROL_PLANE</el-tag>
                </template>
            </el-table-column>
            <el-table-column label="secret_id / GatewaySecret" min-width="280">
                <template #default="{ row }">
                    <el-select
                        v-model="row.secretId"
                        :disabled="disabled"
                        placeholder="选择已存的 GatewaySecret"
                        filterable
                        @change="emitUpdate"
                    >
                        <el-option
                            v-for="opt in availableSecrets"
                            :key="opt.secret_id || opt.secretId"
                            :value="opt.secret_id || opt.secretId"
                            :label="`${opt.secret_id || opt.secretId} (${opt.key_prefix || ''}…${opt.last4 || ''})`"
                        />
                    </el-select>
                </template>
            </el-table-column>
            <el-table-column label="inject_as" min-width="180">
                <template #default="{ row }">
                    <el-input
                        v-model="row.injectAs"
                        :disabled="disabled"
                        placeholder="OPENAI_API_KEY"
                        @change="emitUpdate"
                    />
                </template>
            </el-table-column>
            <el-table-column label="required" width="100" align="center">
                <template #default="{ row }">
                    <el-switch v-model="row.required" :disabled="disabled" @change="emitUpdate" />
                </template>
            </el-table-column>
            <el-table-column label="#" width="80" align="center">
                <template #default="{ $index }">
                    <el-button
                        v-if="!disabled"
                        link
                        type="danger"
                        @click="removeRow($index)"
                    >删除</el-button>
                </template>
            </el-table-column>
        </el-table>

        <div class="add-row" v-if="!disabled">
            <el-button type="primary" link :icon="Plus" @click="addRow">新增 SecretReference</el-button>
        </div>

        <p class="hint" v-if="!rows.length">
            至少一条 SecretReference 才能冻结；先在 Admin/Providers/Secrets 创建 GatewaySecret。
        </p>
    </div>
</template>

<script setup>
import { computed } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const props = defineProps({
    modelValue: { type: Array, default: () => [] },
    availableSecrets: { type: Array, default: () => [] },
    disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue'])

const rows = computed({
    get: () => props.modelValue,
    set: (v) => emit('update:modelValue', v),
})

const addRow = () => {
    emit('update:modelValue', [
        ...rows.value,
        { alias: '', source: 'CONTROL_PLANE', secretId: '', injectAs: '', required: true },
    ])
}

const removeRow = (idx) => {
    const next = rows.value.slice()
    next.splice(idx, 1)
    emit('update:modelValue', next)
}

const emitUpdate = () => {
    emit('update:modelValue', [...rows.value])
}
</script>

<style scoped>
.secret-ref-editor {
    display: flex;
    flex-direction: column;
    gap: 12px;
}
.add-row {
    text-align: right;
}
.hint {
    margin: 0;
    color: var(--text-secondary, #64748b);
    font-size: 12px;
}
</style>
