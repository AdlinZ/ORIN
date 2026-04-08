<template>
  <div class="orin-page-shell">
    <PageHeader :title="title" :description="description" :icon="icon">
      <template #tag-content>
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
      <template #actions>
        <slot name="actions" />
      </template>
      <template #filters>
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
  maturity: { type: String, default: '' }
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
