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
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: rgba(255, 255, 255, 0.82);
  -webkit-backdrop-filter: blur(10px);
  backdrop-filter: blur(10px);
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
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(148, 163, 184, 0.22);
}
</style>
