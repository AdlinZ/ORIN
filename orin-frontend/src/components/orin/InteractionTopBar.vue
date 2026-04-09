<template>
  <header class="interaction-top-bar">
    <div class="top-bar-left">
      <button
        v-for="(chip, index) in chips"
        :key="chip.key || `${index}-${chip.label}`"
        type="button"
        class="top-chip"
        :disabled="chip.disabled"
        @click="$emit('chip-click', chip, index)"
      >
        {{ chip.label }}
      </button>
    </div>
    <el-button
      v-if="showSettings"
      link
      :icon="MoreFilled"
      class="top-settings-btn"
      :class="{ active: settingsOpen }"
      @click="$emit('toggle-settings')"
    >
      {{ settingsLabel }}
    </el-button>
  </header>
</template>

<script setup>
import { MoreFilled } from '@element-plus/icons-vue';

defineProps({
  chips: {
    type: Array,
    default: () => []
  },
  showSettings: {
    type: Boolean,
    default: true
  },
  settingsOpen: {
    type: Boolean,
    default: false
  },
  settingsLabel: {
    type: String,
    default: '配置'
  }
});

defineEmits(['chip-click', 'toggle-settings']);
</script>

<style scoped>
.interaction-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 56px;
  padding: 10px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 0.7);
  background: rgba(255, 255, 255, 0.68);
  backdrop-filter: blur(8px);
}

.top-bar-left {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.top-chip {
  border: 1px solid #d7e3ef;
  background: #f8fbff;
  color: #334155;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  transition: all 0.18s ease;
}

.top-chip:hover {
  border-color: #93c5fd;
  color: #0f766e;
  background: #eff6ff;
}

.top-chip:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.top-settings-btn {
  color: #64748b;
}

.top-settings-btn.active,
.top-settings-btn:hover {
  color: #0f766e;
}

@media (max-width: 820px) {
  .interaction-top-bar {
    padding: 10px 12px;
  }
}
</style>

