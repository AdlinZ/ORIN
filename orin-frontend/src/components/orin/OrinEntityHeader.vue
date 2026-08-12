<template>
  <section class="orin-entity-header">
    <div class="header-main">
      <div class="header-copy">
        <div class="title-line">
          <span v-if="domain && domain !== title" class="header-domain">{{ domain }}</span>
          <h1>{{ title }}</h1>
        </div>
        <p v-if="description">
          {{ description }}
        </p>
        <div v-if="summary.length || $slots.summary" class="header-summary">
          <slot name="summary">
            <span
              v-for="item in summary"
              :key="item.key || item.label"
              class="summary-item"
            >
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </span>
          </slot>
        </div>
      </div>
      <div v-if="$slots.actions" class="header-actions">
        <slot name="actions" />
      </div>
    </div>

    <div v-if="$slots.filters" class="header-workbar">
      <div v-if="$slots.filters" class="header-filters">
        <slot name="filters" />
      </div>
    </div>
  </section>
</template>

<script setup>
defineProps({
  domain: { type: String, default: '' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  summary: { type: Array, default: () => [] }
})
</script>

<style scoped>
.orin-entity-header {
  display: grid;
  gap: 0;
  margin-bottom: 14px;
  border: 1px solid var(--orin-border, #dfe6e4);
  border-radius: var(--orin-radius-lg, 12px);
  background: var(--orin-surface, #ffffff);
  box-shadow: var(--orin-shadow-xs, 0 1px 2px rgba(15, 35, 31, 0.04));
  overflow: hidden;
}

.header-main {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  min-height: 96px;
  padding: 20px 22px 18px;
}

.header-copy {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.title-line {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.header-domain {
  flex: none;
  color: var(--orin-primary, #0f766e);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.header-copy h1 {
  margin: 0;
  color: var(--orin-ink, #17211f);
  font-size: 24px;
  line-height: 1.18;
  font-weight: 680;
  letter-spacing: 0;
}

.header-copy p {
  max-width: 680px;
  margin: 0;
  color: var(--orin-muted, #66736f);
  font-size: 13px;
  line-height: 1.5;
}

.header-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex: none;
  padding-top: 8px;
}

.header-workbar {
  display: flex;
  align-items: center;
  min-height: 54px;
  padding: 10px 16px 10px 22px;
  border-top: 1px solid var(--orin-border-soft, #edf1f0);
  background: var(--orin-surface, #ffffff);
}

.header-filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  width: 100%;
  min-width: 0;
}

.header-summary {
  display: inline-flex;
  align-items: center;
  gap: 0;
  flex-wrap: wrap;
  min-width: 0;
  margin-top: 2px;
}

.summary-item {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding-right: 14px;
  margin-right: 14px;
  border-right: 1px solid var(--orin-border-soft, #edf1f0);
  color: var(--orin-muted, #66736f);
  font-size: 12px;
}

.summary-item:last-child {
  margin-right: 0;
  padding-right: 0;
  border-right: 0;
}

.summary-item strong {
  color: var(--orin-ink, #17211f);
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 960px) {
  .header-main {
    align-items: flex-start;
    flex-direction: column;
    min-height: 0;
  }

  .header-actions {
    justify-content: flex-start;
    flex-wrap: wrap;
    width: 100%;
    padding-top: 0;
  }

  .header-workbar {
    min-height: 0;
  }

  .header-filters {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
