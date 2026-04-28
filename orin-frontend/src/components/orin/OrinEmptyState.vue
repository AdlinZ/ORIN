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
  border: 1px dashed var(--orin-border-strong, #d8e0e8);
  border-radius: var(--radius-base, 8px);
  background: #ffffff;
}

html.dark .orin-empty-state {
  background: rgba(15, 23, 42, 0.46);
  border-color: rgba(148, 163, 184, 0.22);
}
</style>
