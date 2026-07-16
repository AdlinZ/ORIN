<template>
  <el-card
    v-if="surface === 'card'"
    shadow="never"
    class="orin-data-table orin-data-table--card"
    :class="{ 'is-compact': compact }"
  >
    <template v-if="$slots.header" #header>
      <slot name="header" />
    </template>
    <div v-if="!$slots.header && (title || description)" class="data-table-titlebar">
      <strong>{{ title }}</strong>
      <span v-if="description">{{ description }}</span>
    </div>
    <slot />
    <footer v-if="$slots.footer" class="data-table-footer">
      <slot name="footer" />
    </footer>
  </el-card>

  <div
    v-else
    class="orin-data-table orin-data-table--bare"
    :class="{ 'is-compact': compact }"
  >
    <header v-if="$slots.header" class="data-table-header">
      <slot name="header" />
    </header>
    <div v-else-if="title || description" class="data-table-titlebar">
      <strong>{{ title }}</strong>
      <span v-if="description">{{ description }}</span>
    </div>
    <div class="data-table-content">
      <slot />
    </div>
    <footer v-if="$slots.footer" class="data-table-footer">
      <slot name="footer" />
    </footer>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' },
  compact: { type: Boolean, default: false },
  surface: {
    type: String,
    default: 'card',
    validator: (value) => ['card', 'bare'].includes(value)
  }
})
</script>

<style scoped>
.orin-data-table {
  --orin-data-table-surface: var(--orin-surface, var(--el-bg-color));
  --orin-data-table-border: var(--orin-border-strong, var(--el-border-color));

  width: 100%;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  box-sizing: border-box;
}

.orin-data-table--card {
  border-radius: var(--radius-base, 8px) !important;
  border-color: var(--orin-data-table-border) !important;
  background: var(--orin-data-table-surface) !important;
}

.orin-data-table :deep(.el-card__body) {
  padding: 0;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.orin-data-table :deep(.el-card__header) {
  padding: 14px 16px;
  background: var(--orin-data-table-surface);
  border-bottom: 1px solid var(--orin-data-table-border);
}

.data-table-content {
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.orin-data-table :deep(.el-table) {
  width: 100%;
  max-width: 100%;
  border-radius: 0;
  --el-table-bg-color: var(--orin-data-table-surface);
  --el-table-tr-bg-color: var(--orin-data-table-surface);
  --el-table-header-bg-color: var(--orin-data-table-surface);
  --el-table-row-hover-bg-color: color-mix(
    in srgb,
    var(--orin-primary, var(--el-color-primary)) 4%,
    transparent
  );
  --el-table-border-color: var(--orin-data-table-border);
}

.orin-data-table :deep(.el-table th.el-table__cell) {
  height: 40px;
  padding: 6px 0;
  color: var(--text-secondary, var(--el-text-color-secondary));
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
  border-bottom: 1px solid var(--orin-data-table-border);
  background: var(--orin-data-table-surface);
}

.data-table-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-data-table-border);
  background: var(--orin-data-table-surface);
}

.data-table-titlebar strong {
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-size: 14px;
}

.data-table-titlebar span {
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  font-size: 13px;
}

.data-table-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid var(--orin-data-table-border);
  background: var(--orin-data-table-surface);
}
</style>
