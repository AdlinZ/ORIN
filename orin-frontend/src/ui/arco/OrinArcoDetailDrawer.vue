<template>
  <a-drawer
    :visible="modelValue"
    :title="title"
    :width="width"
    :footer="false"
    unmount-on-close
    class="orin-arco-detail-drawer"
    @update:visible="$emit('update:modelValue', $event)"
    @cancel="$emit('update:modelValue', false)"
  >
    <slot />
  </a-drawer>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: [Number, String], default: 420 }
})

defineEmits(['update:modelValue'])
</script>
