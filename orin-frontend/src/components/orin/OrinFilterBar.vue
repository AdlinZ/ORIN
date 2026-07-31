<template>
  <div class="orin-filter-bar">
    <slot />
  </div>
</template>

<style scoped>
.orin-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  min-width: 0;
}

.orin-filter-bar :deep(.el-input),
.orin-filter-bar :deep(.el-select) {
  max-width: 100%;
}
</style>
