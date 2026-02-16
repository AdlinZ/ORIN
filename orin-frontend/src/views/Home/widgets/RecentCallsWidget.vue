<template>
  <div class="widget-placeholder">
    <div class="widget-header">
      <h3>{{ title }}</h3>
    </div>
    <el-empty :description="`${title} 组件开发中`" :image-size="80" />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  config: {
    type: Object,
    default: () => ({})
  },
  title: {
    type: String,
    default: 'Widget'
  }
})
</script>

<style scoped>
.widget-placeholder {
  padding: 24px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.widget-header h3 {
  font-size: 18px;
  font-weight: 600;
  color: var(--neutral-gray-900);
  margin: 0 0 20px 0;
}
</style>
