<template>
  <section class="orin-arco-action-bar">
    <div v-if="$slots.context" class="action-context">
      <slot name="context" />
    </div>
    <div v-if="$slots.filters" class="action-filters">
      <slot name="filters" />
    </div>
    <div v-if="$slots.actions" class="action-actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<style scoped>
.orin-arco-action-bar {
  display: grid;
  grid-template-columns: minmax(180px, auto) minmax(260px, 1fr) auto;
  grid-template-areas:
    "context filters actions";
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--orin-arco-border, #d8e0e8);
  border-radius: 8px;
  background: #ffffff;
}

.action-context {
  grid-area: context;
  min-width: 0;
}

.action-filters {
  grid-area: filters;
  min-width: 0;
  align-self: center;
}

.action-actions {
  grid-area: actions;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .orin-arco-action-bar {
    grid-template-columns: 1fr;
    grid-template-areas:
      "context"
      "filters"
      "actions";
  }

  .action-actions {
    justify-content: flex-start;
  }
}
</style>
