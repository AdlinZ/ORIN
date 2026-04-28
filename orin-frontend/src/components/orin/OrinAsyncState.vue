<template>
  <div class="orin-async-state">
    <el-skeleton v-if="status === 'loading' || status === 'retrying'" animated :rows="rows" />
    <OrinEmptyState
      v-else-if="status === 'empty'"
      :description="emptyText"
      :action-label="emptyActionLabel"
      @action="$emit('empty-action')"
    />
    <el-result
      v-else-if="status === 'error'"
      icon="error"
      :title="errorTitle"
      :sub-title="errorText"
    >
      <template #extra>
        <el-button type="primary" @click="$emit('retry')">
          重试
        </el-button>
      </template>
    </el-result>
    <slot v-else />
  </div>
</template>

<script setup>
import OrinEmptyState from './OrinEmptyState.vue'

defineEmits(['retry', 'empty-action'])

defineProps({
  status: {
    type: String,
    default: 'idle'
  },
  rows: {
    type: Number,
    default: 3
  },
  emptyText: {
    type: String,
    default: '暂无可用记录'
  },
  emptyActionLabel: {
    type: String,
    default: ''
  },
  errorTitle: {
    type: String,
    default: '数据加载失败'
  },
  errorText: {
    type: String,
    default: '请稍后重试'
  }
})
</script>

<style scoped>
.orin-async-state {
  min-height: 96px;
}
</style>
