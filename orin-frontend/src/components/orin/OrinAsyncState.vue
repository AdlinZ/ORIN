<template>
  <div class="orin-async-state">
    <el-skeleton v-if="status === 'loading' || status === 'retrying'" animated :rows="rows" />
    <el-empty v-else-if="status === 'empty'" :description="emptyText" />
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
defineEmits(['retry'])

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
    default: '暂无数据'
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
