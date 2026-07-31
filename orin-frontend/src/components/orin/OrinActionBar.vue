<template>
  <section class="orin-action-bar">
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
.orin-action-bar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    "context actions"
    "filters filters";
  gap: 12px 16px;
  padding: 14px 16px;
  border: 1px solid var(--orin-border, #dfe6e4);
  border-radius: var(--orin-radius-lg, 12px);
  background: var(--orin-surface, #ffffff);
  box-shadow: var(--orin-shadow-xs, 0 1px 2px rgba(15, 35, 31, 0.04));
}

.action-context {
  grid-area: context;
  min-width: 0;
}

.action-filters {
  grid-area: filters;
  min-width: 0;
}

.action-actions {
  grid-area: actions;
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 960px) {
  .orin-action-bar {
    grid-template-columns: 1fr;
    grid-template-areas:
      "context"
      "filters"
      "actions";
  }

  .action-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}

html.dark .orin-action-bar {
  background: var(--orin-surface, #131d1b);
  border-color: var(--orin-border, #293936);
}
</style>
