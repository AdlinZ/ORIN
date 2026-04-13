<template>
  <el-tag
    :type="type"
    :effect="effect"
    size="small"
    class="orin-maturity-badge"
  >
    {{ text }}
  </el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  level: {
    type: String,
    default: 'available' // available | beta | planned
  },
  effect: {
    type: String,
    default: 'plain'
  }
})

const config = computed(() => {
  switch ((props.level || '').toLowerCase()) {
    case 'beta':
      return { type: 'warning', text: '试运行' }
    case 'planned':
      return { type: 'info', text: '规划中' }
    default:
      return { type: 'success', text: '已上线' }
  }
})

const type = computed(() => config.value.type)
const text = computed(() => config.value.text)
</script>

<style scoped>
.orin-maturity-badge {
  font-weight: 600;
}
</style>
