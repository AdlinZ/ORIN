<template>
  <aside class="orin-detail-panel">
    <header v-if="title || $slots.actions" class="detail-header">
      <div>
        <span v-if="eyebrow" class="detail-eyebrow">{{ eyebrow }}</span>
        <h3 v-if="title">
          {{ title }}
        </h3>
      </div>
      <div v-if="$slots.actions" class="detail-actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </aside>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  eyebrow: { type: String, default: '' }
})
</script>

<style scoped>
.orin-detail-panel {
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--orin-border, #dfe6e4);
  border-radius: var(--orin-radius-lg, 12px);
  background: var(--orin-surface, #ffffff);
  box-shadow: var(--orin-shadow-xs, 0 1px 2px rgba(15, 35, 31, 0.04));
}

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-border-soft, #edf1f0);
}

.detail-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: var(--orin-muted, #66736f);
  font-size: 12px;
  font-weight: 700;
}

h3 {
  margin: 0;
  color: var(--orin-ink, #17211f);
  font-size: 15px;
}

.detail-actions {
  flex: 0 0 auto;
}

html.dark .orin-detail-panel {
  background: var(--neutral-gray-50, #0f1c1c);
  border-color: rgba(148, 163, 184, 0.22);
}
</style>
