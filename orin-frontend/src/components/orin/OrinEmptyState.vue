<template>
  <div class="orin-empty-state">
    <el-empty :description="description" :image-size="imageSize">
      <template v-if="$slots.default" #default>
        <slot />
      </template>
      <template v-else-if="actionLabel" #default>
        <el-button type="primary" @click="$emit('action')">
          {{ actionLabel }}
        </el-button>
      </template>
    </el-empty>
  </div>
</template>

<script setup>
defineEmits(['action'])

defineProps({
  description: { type: String, default: '暂无可用记录' },
  imageSize: { type: Number, default: 72 },
  actionLabel: { type: String, default: '' }
})
</script>

<style scoped>
.orin-empty-state {
  display: grid;
  place-items: center;
  min-height: 180px;
  padding: 24px;
  border: 1px dashed #cbd8d5;
  border-radius: var(--orin-radius-lg, 12px);
  background: var(--orin-surface-subtle, #f8faf9);
}

html.dark .orin-empty-state {
  background: var(--orin-surface-subtle, #182421);
  border-color: var(--orin-border, #293936);
}
</style>
