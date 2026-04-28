<template>
  <section class="orin-step-flow" aria-label="流程进度">
    <article
      v-for="(step, index) in steps"
      :key="step.key || step.title"
      class="step-item"
      :class="{
        'is-active': index === active,
        'is-complete': index < active
      }"
    >
      <span class="step-index">{{ String(index + 1).padStart(2, '0') }}</span>
      <span class="step-title">{{ step.title }}</span>
      <span v-if="step.description" class="step-description">{{ step.description }}</span>
    </article>
  </section>
</template>

<script setup>
defineProps({
  steps: {
    type: Array,
    default: () => []
  },
  active: {
    type: Number,
    default: 0
  }
})
</script>

<style scoped>
.orin-step-flow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 1px;
  overflow: hidden;
  border: 1px solid var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: var(--orin-border-strong, #d8e0e8);
}

.step-item {
  position: relative;
  min-width: 0;
  padding: 14px 16px;
  background: var(--orin-surface, #ffffff);
}

.step-index,
.step-description {
  display: block;
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  line-height: 1.45;
}

.step-index {
  margin-bottom: 8px;
  font-weight: 700;
}

.step-title {
  display: block;
  color: var(--text-primary, #1e293b);
  font-size: 14px;
  font-weight: 700;
  line-height: 1.35;
}

.step-description {
  margin-top: 6px;
}

.is-active {
  box-shadow: inset 0 0 0 1px var(--orin-primary, #1a4f4f);
}

.is-active .step-index,
.is-active .step-title,
.is-complete .step-title {
  color: var(--orin-primary, #1a4f4f);
}

html.dark .step-item {
  background: var(--neutral-gray-50, #0f1c1c);
}
</style>
