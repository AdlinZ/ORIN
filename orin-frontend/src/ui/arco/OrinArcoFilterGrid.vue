<template>
  <div class="orin-arco-filter-grid">
    <div class="filter-fields">
      <slot />
    </div>
    <div v-if="$slots.search || $slots.reset" class="filter-search-row">
      <div class="filter-search">
        <slot name="search" />
      </div>
      <div v-if="$slots.reset" class="filter-reset">
        <slot name="reset" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.orin-arco-filter-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.filter-fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 8px;
  min-width: 0;
}

.filter-search-row {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.filter-search,
.filter-reset {
  min-width: 0;
}

.filter-reset {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .filter-search-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
