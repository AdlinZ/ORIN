<template>
  <section class="orin-metric-strip">
    <article
      v-for="metric in metrics"
      :key="metric.key || metric.label"
      class="metric-item"
      :class="{ 'is-critical': metric.intent === 'danger' }"
    >
      <span class="metric-label">{{ metric.label }}</span>
      <strong class="metric-value">{{ metric.value }}</strong>
      <span v-if="metric.meta" class="metric-meta">{{ metric.meta }}</span>
    </article>
  </section>
</template>

<script setup>
defineProps({
  metrics: {
    type: Array,
    default: () => []
  }
})
</script>

<style scoped>
.orin-metric-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 0;
  overflow: hidden;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: var(--orin-surface, #ffffff);
}

.metric-item {
  min-width: 0;
  padding: 14px 16px;
  border-right: 1px solid var(--orin-border-strong, #d8e0e8);
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
  background: var(--orin-surface, #ffffff);
}

.metric-label,
.metric-meta {
  display: block;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  line-height: 1.4;
}

.metric-label {
  font-weight: 700;
}

.metric-value {
  display: block;
  margin: 6px 0 4px;
  color: var(--text-primary, #1e293b);
  font-size: 24px;
  line-height: 1;
}

.is-critical .metric-value {
  color: var(--error-color, #ef4444);
}

html.dark .metric-item {
  background: var(--neutral-gray-50, #0f1c1c);
}
</style>
