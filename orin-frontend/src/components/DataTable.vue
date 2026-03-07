<template>
  <div class="data-table-wrapper" :class="{ 'is-card': card }">
    <!-- 工具栏 -->
    <div v-if="$slots.toolbar || showToolbar" class="table-toolbar">
      <slot name="toolbar">
        <div class="toolbar-left">
          <slot name="toolbar-left"></slot>
        </div>
        <div class="toolbar-right">
          <slot name="toolbar-right">
            <el-button v-if="refreshable" :icon="Refresh" circle @click="handleRefresh" />
            <el-button v-if="exportable" :icon="Download" circle @click="handleExport" />
          </slot>
        </div>
      </slot>
    </div>

    <!-- 表格 -->
    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="tableData"
      :stripe="stripe"
      :border="border"
      :size="size"
      :height="height"
      :max-height="maxHeight"
      :row-key="rowKey"
      :expand-row-keys="expandRowKeys"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      @row-click="handleRowClick"
      class="orin-table"
    >
      <!-- 选择列 -->
      <el-table-column v-if="selectable" type="selection" width="50" align="center" />

      <!-- 序号列 -->
      <el-table-column v-if="showIndex" type="index" width="60" align="center" label="序号" />

      <!-- 表格列 -->
      <slot></slot>
    </el-table>

    <!-- 分页 -->
    <div v-if="pagination" class="table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="pageSizes"
        :layout="paginationLayout"
        :background="true"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { Refresh, Download } from '@element-plus/icons-vue';
import type { ElTable } from 'element-plus';

interface Props {
  data: any[];
  loading?: boolean;
  stripe?: boolean;
  border?: boolean;
  size?: 'large' | 'default' | 'small';
  height?: string | number;
  maxHeight?: string | number;
  rowKey?: string | ((row: any) => string);
  selectable?: boolean;
  showIndex?: boolean;
  showToolbar?: boolean;
  refreshable?: boolean;
  exportable?: boolean;
  card?: boolean;
  // 分页
  pagination?: boolean;
  total?: number;
  currentPage?: number;
  pageSize?: number;
  pageSizes?: number[];
  paginationLayout?: string;
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  stripe: true,
  border: false,
  size: 'default',
  selectable: false,
  showIndex: false,
  showToolbar: false,
  refreshable: false,
  exportable: false,
  card: false,
  pagination: true,
  total: 0,
  currentPage: 1,
  pageSize: 10,
  pageSizes: () => [10, 20, 50, 100],
  paginationLayout: 'total, sizes, prev, pager, next, jumper'
});

const emit = defineEmits<{
  refresh: [];
  export: [];
  'selection-change': [selection: any[]];
  'sort-change': [sort: { prop: string; order: string }];
  'row-click': [row: any, column: any, event: MouseEvent];
  'page-change': [page: number];
  'size-change': [size: number];
}>();

const tableRef = ref<InstanceType<typeof ElTable>>();
const tableData = computed(() => props.data);
const expandRowKeys = ref<string[]>([]);

// 内部分页状态
const currentPage = ref(props.currentPage);
const pageSize = ref(props.pageSize);

// 监听外部分页参数变化
watch(() => props.currentPage, (val) => { currentPage.value = val; });
watch(() => props.pageSize, (val) => { pageSize.value = val; });

const handleRefresh = () => emit('refresh');
const handleExport = () => emit('export');
const handleSelectionChange = (selection: any[]) => emit('selection-change', selection);
const handleSortChange = ({ prop, order }: any) => emit('sort-change', { prop, order });
const handleRowClick = (row: any, column: any, event: MouseEvent) => emit('row-click', row, column, event);
const handlePageChange = (page: number) => {
  currentPage.value = page;
  emit('page-change', page);
};
const handleSizeChange = (size: number) => {
  pageSize.value = size;
  emit('size-change', size);
};

// 暴露方法
defineExpose({
  tableRef,
  toggleRowSelection: (row: any, selected?: boolean) => tableRef.value?.toggleRowSelection(row, selected),
  toggleAllSelection: () => tableRef.value?.toggleAllSelection(),
  clearSelection: () => tableRef.value?.clearSelection(),
  setCurrentRow: (row: any) => tableRef.value?.setCurrentRow(row)
});
</script>

<style scoped>
.data-table-wrapper {
  width: 100%;
}

.data-table-wrapper.is-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
}

html.dark .data-table-wrapper.is-card {
  background: #0f172a;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

html.dark .table-toolbar {
  border-bottom-color: #1e293b;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.orin-table {
  --el-table-border-color: #e2e8f0;
  --el-table-header-bg-color: #f8fafc;
  width: 100%;
}

html.dark .orin-table {
  --el-table-border-color: #1e293b;
  --el-table-header-bg-color: #1e293b;
  --el-table-bg-color: #0f172a;
  --el-table-tr-bg-color: #0f172a;
  --el-table-row-hover-bg-color: #1e293b;
  --el-table-text-color: #f1f5f9;
  --el-table-header-text-color: #94a3b8;
}

.table-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

html.dark .table-pagination {
  border-top-color: #1e293b;
}
</style>
