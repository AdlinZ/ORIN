<template>
  <el-card shadow="never" class="orin-data-table" :class="{ 'is-compact': compact }">
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <div v-if="title || description" class="data-table-titlebar">
      <strong>{{ title }}</strong>
      <span v-if="description">{{ description }}</span>
    </div>
    <slot />
    <footer v-if="$slots.footer" class="data-table-footer">
      <slot name="footer" />
    </footer>
  </el-card>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  compact: { type: Boolean, default: false }
})
</script>

<style scoped>
.orin-data-table {
  border-radius: var(--radius-base, 8px) !important;
  border-color: var(--orin-border-strong, #d8e0e8) !important;
}

.orin-data-table :deep(.el-card__body) {
  padding: 0;
}

.orin-data-table :deep(.el-card__header) {
  padding: 14px 16px;
  background: #ffffff;
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
}

.orin-data-table :deep(.el-table) {
  border-radius: 0;
  --el-table-header-bg-color: #ffffff;
  --el-table-row-hover-bg-color: rgba(13, 148, 136, 0.04);
}

.orin-data-table :deep(.el-table th.el-table__cell) {
  height: 40px;
  padding: 6px 0;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  font-weight: 700;
}

.orin-data-table :deep(.el-table td.el-table__cell) {
  padding: 7px 0;
}

.orin-data-table.is-compact :deep(.el-table th.el-table__cell) {
  height: 36px;
  padding: 5px 0;
}

.orin-data-table.is-compact :deep(.el-table td.el-table__cell) {
  padding: 5px 0;
}

.orin-data-table :deep(.el-table .cell) {
  line-height: 1.45;
}

.data-table-titlebar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
  background: #ffffff;
}

.data-table-titlebar strong {
  color: var(--neutral-gray-900, #101828);
  font-size: 14px;
}

.data-table-titlebar span {
  color: var(--neutral-gray-500, #667085);
  font-size: 13px;
}

.data-table-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
  background: #ffffff;
}
</style>
