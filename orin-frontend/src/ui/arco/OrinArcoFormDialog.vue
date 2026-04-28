<template>
  <a-modal
    :visible="modelValue"
    :title="title"
    :width="width"
    :footer="false"
    :mask-closable="maskClosable"
    unmount-on-close
    class="orin-arco-form-dialog"
    @update:visible="$emit('update:modelValue', $event)"
    @cancel="$emit('update:modelValue', false)"
  >
    <a-form
      ref="innerFormRef"
      :model="model"
      :rules="rules"
      layout="vertical"
      class="orin-arco-form"
    >
      <slot />
    </a-form>
    <footer v-if="$slots.footer" class="form-dialog-footer">
      <slot name="footer" />
    </footer>
  </a-modal>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: [Number, String], default: 520 },
  model: { type: Object, default: () => ({}) },
  rules: { type: Object, default: () => ({}) },
  maskClosable: { type: Boolean, default: false }
})

defineEmits(['update:modelValue'])

const innerFormRef = ref(null)

const validate = (callback) => innerFormRef.value?.validate(callback)
const resetFields = () => innerFormRef.value?.resetFields()
const clearValidate = () => innerFormRef.value?.clearValidate()

defineExpose({
  validate,
  resetFields,
  clearValidate
})
</script>

<style scoped>
.form-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--orin-arco-border, #d8e0e8);
}
</style>
