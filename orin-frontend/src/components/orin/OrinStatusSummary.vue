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
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, #ffffff);
}

.summary-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}

.summary-label {
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  font-weight: 700;
}

.summary-value {
  color: var(--text-primary, #1e293b);
  font-size: 20px;
  line-height: 1;
  text-align: right;
}

.summary-meta {
  margin: 8px 0 0;
  color: var(--text-secondary, #64748b);
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
