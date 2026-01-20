<template>
  <div class="resizable-table-wrapper">
    <el-table
      v-bind="$attrs"
      :data="data"
      :class="['resizable-table', tableClass]"
      :header-cell-style="mergedHeaderCellStyle"
      :cell-style="cellStyle"
      :stripe="stripe"
      :border="border"
      ref="tableRef"
      @header-dragend="handleHeaderDragend"
    >
      <slot />
    </el-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  tableClass: {
    type: String,
    default: ''
  },
  headerCellStyle: {
    type: Object,
    default: () => ({})
  },
  cellStyle: {
    type: Object,
    default: () => ({})
  },
  stripe: {
    type: Boolean,
    default: true
  },
  border: {
    type: Boolean,
    default: false
  },
  enableResize: {
    type: Boolean,
    default: true
  }
});

const tableRef = ref(null);

// Merge default header styles with custom ones
const mergedHeaderCellStyle = computed(() => ({
  background: 'var(--neutral-gray-50)',
  color: 'var(--neutral-gray-900)',
  fontWeight: '600',
  fontSize: '14px',
  borderBottom: '2px solid var(--neutral-gray-200)',
  ...props.headerCellStyle
}));

const handleHeaderDragend = (newWidth, oldWidth, column, event) => {
  // Store column widths in localStorage for persistence
  const columnKey = column.property || column.label;
  if (columnKey) {
    const storageKey = `table-column-width-${columnKey}`;
    localStorage.setItem(storageKey, newWidth);
  }
};

// Enable column resizing by making headers draggable
onMounted(() => {
  if (!props.enableResize) return;
  
  nextTick(() => {
    const table = tableRef.value?.$el;
    if (!table) return;

    const headers = table.querySelectorAll('.el-table__header th');
    headers.forEach((header, index) => {
      // Skip selection and expand columns
      if (header.classList.contains('el-table-column--selection') || 
          header.classList.contains('el-table__expand-column')) {
        return;
      }

      const resizer = document.createElement('div');
      resizer.className = 'column-resizer';
      resizer.style.cssText = `
        position: absolute;
        right: 0;
        top: 0;
        width: 8px;
        height: 100%;
        cursor: col-resize;
        user-select: none;
        z-index: 1;
      `;

      let startX = 0;
      let startWidth = 0;
      let currentHeader = null;

      const onMouseDown = (e) => {
        e.preventDefault();
        e.stopPropagation();
        
        currentHeader = header;
        startX = e.pageX;
        startWidth = header.offsetWidth;

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
        
        // Add visual feedback
        resizer.style.background = 'var(--primary-color)';
        document.body.style.cursor = 'col-resize';
        document.body.style.userSelect = 'none';
      };

      const onMouseMove = (e) => {
        if (!currentHeader) return;
        
        const diff = e.pageX - startX;
        const newWidth = Math.max(50, startWidth + diff); // Minimum width of 50px
        
        // Update column width
        const colIndex = Array.from(headers).indexOf(currentHeader);
        const cols = table.querySelectorAll('colgroup col');
        if (cols[colIndex]) {
          cols[colIndex].setAttribute('width', newWidth);
        }
        currentHeader.style.width = newWidth + 'px';
      };

      const onMouseUp = () => {
        document.removeEventListener('mousemove', onMouseMove);
        document.removeEventListener('mouseup', onMouseUp);
        
        // Remove visual feedback
        resizer.style.background = '';
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
        
        currentHeader = null;
      };

      resizer.addEventListener('mousedown', onMouseDown);
      
      // Position the header relatively so the resizer can be absolutely positioned
      header.style.position = 'relative';
      header.appendChild(resizer);
    });
  });
});

defineExpose({
  tableRef
});
</script>

<style scoped>
.resizable-table-wrapper {
  width: 100%;
  position: relative;
}

.resizable-table {
  width: 100%;
  font-size: 14px;
}

/* Enhanced table styling */
.resizable-table :deep(.el-table__header) {
  border-radius: var(--radius-base);
}

.resizable-table :deep(.el-table__header th) {
  background: var(--neutral-gray-50);
  color: var(--neutral-gray-900);
  font-weight: 600;
  font-size: 14px;
  padding: 14px 0;
  transition: background-color 0.2s ease;
}

.resizable-table :deep(.el-table__header th:hover) {
  background: var(--neutral-gray-100);
}

.resizable-table :deep(.el-table__body tr) {
  transition: all 0.2s ease;
}

.resizable-table :deep(.el-table__body tr:hover) {
  background-color: var(--neutral-gray-50) !important;
}

.resizable-table :deep(.el-table__body td) {
  padding: 12px 0;
  color: var(--neutral-gray-700);
  border-bottom: 1px solid var(--neutral-gray-100);
}

/* Stripe styling */
.resizable-table :deep(.el-table__body .el-table__row--striped) {
  background-color: #fafafa;
}

/* Selection column styling */
.resizable-table :deep(.el-table-column--selection .el-checkbox) {
  display: flex;
  justify-content: center;
}

/* Empty state */
.resizable-table :deep(.el-table__empty-block) {
  padding: 40px 0;
}

/* Column resizer hover effect */
.resizable-table :deep(.column-resizer:hover) {
  background: var(--primary-light) !important;
  opacity: 0.5;
}

/* Smooth transitions */
.resizable-table :deep(.el-table__body td),
.resizable-table :deep(.el-table__header th) {
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

/* Fixed column shadows */
.resizable-table :deep(.el-table__fixed),
.resizable-table :deep(.el-table__fixed-right) {
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.08);
}

/* Loading overlay */
.resizable-table :deep(.el-loading-mask) {
  background-color: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(2px);
}
</style>
