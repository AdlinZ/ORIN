<template>
  <div class="json-item">
    <div class="item-line" :style="{ paddingLeft: depth * 20 + 'px' }">
      <!-- Key and Toggle -->
      <span v-if="isExpandable" class="toggle-btn" @click="toggle">
        <el-icon :class="{ 'is-rotated': expanded }"><CaretRight /></el-icon>
      </span>
      <span v-else class="toggle-placeholder"></span>

      <span v-if="name" class="json-key">"{{ name }}": </span>

      <!-- Values -->
      <template v-if="!isExpandable">
        <span :class="['json-value', valueType]">{{ formattedValue }}{{ isLast ? '' : ',' }}</span>
      </template>
      <template v-else>
        <span class="json-bracket">{{ startBracket }}</span>
        <span v-if="!expanded" class="json-summary" @click="toggle">
          {{ summaryText }}
          <span class="json-bracket">{{ endBracket }}</span>{{ isLast ? '' : ',' }}
        </span>
      </template>
    </div>

    <!-- Recursive children -->
    <div v-if="isExpandable && expanded" class="json-children">
      <json-view-item
        v-for="(val, key, index) in data"
        :key="key"
        :data="val"
        :name="Array.isArray(data) ? '' : key"
        :is-last="index === Object.keys(data).length - 1"
        :depth="depth + 1"
        :expand-all="expandAll"
      />
      <div class="item-line" :style="{ paddingLeft: depth * 20 + 'px' }">
        <span class="toggle-placeholder"></span>
        <span class="json-bracket">{{ endBracket }}</span>{{ isLast ? '' : ',' }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue';
import { CaretRight } from '@element-plus/icons-vue';

const props = defineProps({
  data: [Object, Array, String, Number, Boolean, null],
  name: {
    type: String,
    default: ''
  },
  isLast: Boolean,
  depth: Number,
  expandAll: Boolean
});

const expanded = ref(props.expandAll || props.depth < 2);

const isExpandable = computed(() => {
  return props.data !== null && typeof props.data === 'object';
});

const valueType = computed(() => {
  if (props.data === null) return 'null';
  if (Array.isArray(props.data)) return 'array';
  return typeof props.data;
});

const formattedValue = computed(() => {
  if (valueType.value === 'string') return `"${props.data}"`;
  return String(props.data);
});

const startBracket = computed(() => Array.isArray(props.data) ? '[' : '{');
const endBracket = computed(() => Array.isArray(props.data) ? ']' : '}');

const summaryText = computed(() => {
  if (Array.isArray(props.data)) {
    return ` ... ${props.data.length} items `;
  }
  return ` ... ${Object.keys(props.data).length} keys `;
});

const toggle = () => {
  expanded.value = !expanded.value;
};
</script>

<style scoped>
.json-item {
  user-select: text;
}

.item-line {
  display: flex;
  align-items: flex-start;
  white-space: nowrap;
}

.toggle-btn {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  margin-right: 4px;
  color: #94a3b8;
  transition: color 0.2s;
}

.toggle-btn:hover {
  color: #3b82f6;
}

.toggle-btn .el-icon {
  transition: transform 0.2s;
  font-size: 12px;
}

.toggle-btn .is-rotated {
  transform: rotate(90deg);
}

.toggle-placeholder {
  display: inline-block;
  width: 20px;
}

.json-key {
  color: #a855f7; /* Purple */
  margin-right: 4px;
}

.json-value.string { color: #10b981; } /* Green */
.json-value.number { color: #f59e0b; } /* Orange */
.json-value.boolean { color: #3b82f6; } /* Blue */
.json-value.null { color: #ef4444; } /* Red */

.json-bracket {
  color: #64748b;
}

.json-summary {
  color: #94a3b8;
  font-size: 12px;
  font-style: italic;
  cursor: pointer;
  background: rgba(0, 0, 0, 0.02);
  padding: 0 4px;
  border-radius: 4px;
}

.dark-theme .json-key { color: #d8b4fe; }
.dark-theme .json-value.string { color: #34d399; }
.dark-theme .json-value.number { color: #fbbf24; }
.dark-theme .json-summary { background: rgba(255, 255, 255, 0.05); }
</style>
