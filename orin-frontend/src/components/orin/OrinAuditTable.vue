<template>
  <el-table
    :data="rows"
    v-bind="$attrs"
    stripe
    border
  >
    <el-table-column prop="time" label="时间" width="180" />
    <el-table-column prop="actor" label="操作者" width="140" />
    <el-table-column prop="action" label="动作" min-width="180" />
    <el-table-column prop="resource" label="资源" min-width="180" />
    <el-table-column prop="result" label="结果" width="120">
      <template #default="{ row }">
        <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">
          {{ row.result || '-' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column
      prop="traceId"
      label="Trace ID"
      min-width="220"
      show-overflow-tooltip
    />
    <slot />
  </el-table>
</template>

<script setup>
defineOptions({ inheritAttrs: false })

defineProps({
  rows: {
    type: Array,
    default: () => []
  }
})
</script>
