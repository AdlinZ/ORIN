<template>
  <div class="empty-state" :class="{ 'is-circle': circle }">
    <div class="empty-icon">
      <el-icon :size="iconSize"><component :is="icon" /></el-icon>
    </div>
    <div class="empty-content">
      <p v-if="title" class="empty-title">{{ title }}</p>
      <p v-if="description" class="empty-description">{{ description }}</p>
      <div v-if="$slots.action" class="empty-action">
        <slot name="action"></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  icon?: any;
  title?: string;
  description?: string;
  iconSize?: number;
  circle?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  icon: 'FolderOpened',
  iconSize: 64,
  circle: false
});
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  text-align: center;
}

.empty-state.is-circle {
  padding: 64px 24px;
}

.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-state.is-circle .empty-icon {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%);
}

html.dark .empty-state.is-circle .empty-icon {
  background: linear-gradient(135deg, #1e293b 0%, #334155 100%);
}

html.dark .empty-icon {
  color: #475569;
}

.empty-content {
  max-width: 360px;
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
  margin: 0 0 8px 0;
}

html.dark .empty-title {
  color: #f1f5f9;
}

.empty-description {
  font-size: 14px;
  color: #64748b;
  margin: 0 0 16px 0;
  line-height: 1.6;
}

html.dark .empty-description {
  color: #94a3b8;
}

.empty-action {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
