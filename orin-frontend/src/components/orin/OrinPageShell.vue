<template>
  <div class="orin-page-shell">
    <PageHeader
      v-if="variant !== 'none'"
      :title="title"
      :description="description"
      :icon="icon"
      :variant="variant"
      :flat="flat"
    >
      <template v-if="showMeta && (maturity || domain)" #tag-content>
        <div class="tag-row">
          <OrinMaturityBadge v-if="maturity" :level="maturity" />
          <el-tag
            v-if="domain"
            size="small"
            type="info"
            effect="plain"
          >
            {{ domain }}
          </el-tag>
        </div>
      </template>
      <template v-if="$slots.actions" #actions>
        <slot name="actions" />
      </template>
      <template v-if="$slots.filters" #filters>
        <slot name="filters" />
      </template>
    </PageHeader>

    <slot />
  </div>
</template>

<script setup>
import PageHeader from '@/components/PageHeader.vue'
import OrinMaturityBadge from './OrinMaturityBadge.vue'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  icon: { type: [String, Object], default: null },
  domain: { type: String, default: '' },
  maturity: { type: String, default: '' },
  variant: {
    type: String,
    default: 'legacy',
    validator: (value) => ['legacy', 'plain', 'none'].includes(value)
  },
  flat: { type: Boolean, default: false },
  showMeta: { type: Boolean, default: false }
})
</script>

<style scoped>
.orin-page-shell {
  display: block;
}

.tag-row {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
