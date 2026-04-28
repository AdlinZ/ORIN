<template>
  <div class="orin-row-actions" @click.stop>
    <a-dropdown v-if="menuActions.length" trigger="click" @select="handleSelect">
      <a-button type="text" size="mini" class="manage-action">
        {{ primaryLabel }}
      </a-button>
      <template #content>
        <a-doption
          v-for="action in menuActions"
          :key="action.key"
          :value="action.key"
          :disabled="action.disabled"
        >
          {{ action.label }}
        </a-doption>
      </template>
    </a-dropdown>
    <a-button
      v-else
      type="text"
      size="mini"
      class="manage-action"
      @click="$emit('select', primaryAction)"
    >
      {{ primaryLabel }}
    </a-button>
    <a-button
      v-if="dangerAction"
      type="text"
      size="mini"
      status="danger"
      class="danger-action"
      @click="$emit('select', dangerAction.key)"
    >
      {{ dangerAction.label }}
    </a-button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  actions: { type: Array, default: () => [] },
  primaryLabel: { type: String, default: '管理' },
  primaryAction: { type: String, default: 'detail' }
})

const emit = defineEmits(['select'])

const dangerAction = computed(() => props.actions.find(action => action.danger))
const menuActions = computed(() => props.actions.filter(action => !action.danger))

const handleSelect = (key) => {
  emit('select', key)
}
</script>

<style scoped>
.orin-row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
}

.manage-action {
  color: #2563eb;
  font-weight: 650;
}

.danger-action {
  font-weight: 650;
}
</style>
