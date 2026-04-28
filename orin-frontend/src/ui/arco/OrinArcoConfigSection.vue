<template>
  <section class="orin-config-section">
    <header class="section-header">
      <slot name="header">
        <div class="section-copy">
          <h3>{{ title }}</h3>
          <p v-if="description">{{ description }}</p>
        </div>
      </slot>
      <div v-if="$slots.actions" class="section-actions">
        <slot name="actions" />
      </div>
    </header>
    <div v-if="$slots.summary" class="section-summary">
      <slot name="summary" />
    </div>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.orin-config-section {
  overflow: hidden;
  border: 1px solid var(--orin-arco-border, #e3e9ef);
  border-radius: 8px;
  background: #ffffff;
}

.section-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-arco-border-soft, #edf2f6);
}

.section-copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.section-copy h3 {
  margin: 0;
  color: var(--orin-arco-text, #243244);
  font-size: 15px;
  font-weight: 680;
}

.section-copy p {
  margin: 0;
  color: var(--orin-arco-text-subtle, #6b7a90);
  font-size: 12px;
}

.section-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: none;
}

.section-summary {
  padding: 10px 16px;
  border-bottom: 1px solid var(--orin-arco-border-soft, #edf2f6);
}

.section-body {
  padding: 16px;
}

@media (max-width: 720px) {
  .section-header {
    flex-direction: column;
  }

  .section-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
