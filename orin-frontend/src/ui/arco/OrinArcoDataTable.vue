<template>
  <section class="orin-arco-data-table">
    <header v-if="$slots.header" class="table-header">
      <slot name="header" />
    </header>
    <a-table
      :columns="columns"
      :data="data"
      :loading="loading"
      :row-key="rowKey"
      :row-selection="rowSelection"
      :selected-keys="selectedKeys"
      :pagination="false"
      :bordered="{ wrapper: false, cell: false, headerCell: false, bodyCell: false }"
      :scroll="scroll"
      table-layout-fixed
      class="orin-arco-table"
      @selection-change="emitSelectionChange"
      @row-click="(record) => $emit('row-click', record)"
    >
      <template
        v-for="column in slottedColumns"
        :key="column.slotName"
        #[column.slotName]="slotProps"
      >
        <slot :name="column.slotName" v-bind="slotProps" />
      </template>
      <template #empty>
        <slot name="empty" />
      </template>
    </a-table>
    <footer v-if="$slots.footer" class="table-footer">
      <slot name="footer" />
    </footer>
  </section>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rowKey: { type: String, default: 'id' },
  selectable: { type: Boolean, default: false },
  selectedKeys: { type: Array, default: () => [] },
  scroll: { type: Object, default: () => ({ x: 'max-content' }) }
})

const emit = defineEmits(['selection-change', 'update:selectedKeys', 'row-click'])

const rowSelection = computed(() => {
  if (!props.selectable) return undefined
  return {
    type: 'checkbox',
    showCheckedAll: true,
    width: 44
  }
})

const slottedColumns = computed(() => props.columns.filter(column => column.slotName))

const emitSelectionChange = (keys) => {
  emit('update:selectedKeys', keys)
  emit('selection-change', keys)
}
</script>

<style scoped>
.orin-arco-data-table {
  overflow: hidden;
  border: 1px solid var(--orin-arco-border, #d8e0e8);
  border-radius: 8px;
  background: #ffffff;
  box-shadow: none;
}

.table-header {
  padding: 10px 14px;
  border-bottom: 1px solid var(--orin-arco-border, #d8e0e8);
  background: #ffffff;
}

.orin-arco-table {
  border-radius: 0;
}

.orin-arco-table :deep(.arco-table-cell) {
  min-width: 0;
}

.orin-arco-table :deep(.arco-table-th),
.orin-arco-table :deep(.arco-table-td) {
  white-space: nowrap;
}

.orin-arco-table :deep(.arco-table-col-fixed-right) {
  border-left: 1px solid var(--orin-arco-border-soft, #e6edf3);
  box-shadow: none;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px;
  border-top: 1px solid var(--orin-arco-border, #d8e0e8);
  background: #ffffff;
}
</style>
