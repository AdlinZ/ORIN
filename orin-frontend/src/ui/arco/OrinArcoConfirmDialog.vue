<template>
  <a-modal
    :visible="modelValue"
    :title="title"
    :ok-text="okText"
    :cancel-text="cancelText"
    :ok-button-props="{ status }"
    @ok="$emit('confirm')"
    @cancel="$emit('update:modelValue', false)"
    @update:visible="$emit('update:modelValue', $event)"
  >
    <slot />
  </a-modal>
</template>

<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '确认操作' },
  okText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  status: { type: String, default: 'normal' }
})

defineEmits(['update:modelValue', 'confirm'])
</script>
