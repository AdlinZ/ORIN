<template>
  <el-button 
    v-if="hasPermission"
    v-bind="$attrs"
  >
    <slot />
  </el-button>
</template>

<script setup>
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  roles: {
    type: Array,
    default: () => []
  }
})

const userStore = useUserStore()

const hasPermission = computed(() => {
  if (!props.roles || props.roles.length === 0) {
    return true
  }
  return userStore.hasAnyRole(props.roles)
})
</script>
