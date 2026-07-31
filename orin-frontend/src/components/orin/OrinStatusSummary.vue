<template>
  <section class="orin-status-summary">
    <article
      v-for="item in items"
      :key="item.key || item.label"
      class="summary-item"
      :class="item.intent ? `intent-${item.intent}` : ''"
    >
      <div class="summary-main">
        <span class="summary-label">{{ item.label }}</span>
        <strong class="summary-value">{{ item.value }}</strong>
      </div>
      <p v-if="item.meta" class="summary-meta">
        {{ item.meta }}
      </p>
    </article>
  </section>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.orin-status-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.summary-item {
  min-width: 0;
  padding: 14px 16px;
  border: 1px solid var(--orin-border, #dfe6e4);
  border-radius: var(--orin-radius-lg, 12px);
  background: var(--orin-surface, #ffffff);
  box-shadow: var(--orin-shadow-xs, 0 1px 2px rgba(15, 35, 31, 0.04));
}

.summary-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.summary-label {
  color: var(--orin-muted, #66736f);
  font-size: 12px;
  font-weight: 700;
}

.summary-value {
  color: var(--orin-ink, #17211f);
  font-size: 20px;
  line-height: 1;
  text-align: right;
}

.summary-meta {
  margin: 8px 0 0;
  color: var(--orin-muted, #66736f);
  font-size: 12px;
  line-height: 1.5;
}

.intent-danger .summary-value {
  color: var(--error-color, #ef4444);
}

.intent-warning .summary-value {
  color: var(--warning-color, #f59e0b);
}

.intent-success .summary-value {
  color: var(--success-color, #10b981);
}

html.dark .summary-item {
  background: var(--neutral-gray-50, #0f1c1c);
  border-color: rgba(148, 163, 184, 0.22);
}
</style>
