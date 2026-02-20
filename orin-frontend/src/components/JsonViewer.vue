<template>
  <div class="json-viewer-container" :class="{ 'dark-theme': dark }">
    <div class="json-viewer-header" v-if="title || showCopy">
      <span class="json-title">{{ title }}</span>
      <el-button 
        v-if="showCopy" 
        link 
        size="small" 
        :icon="CopyDocument" 
        @click="copyJson"
      >
        复制
      </el-button>
    </div>
    <div class="json-content">
      <json-view-item 
        :data="parsedData" 
        :is-last="true" 
        :depth="0"
        :expand-all="expandAll"
      />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { CopyDocument } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import JsonViewItem from './JsonViewItem.vue';

const props = defineProps({
  data: {
    type: [Object, Array, String],
    required: true
  },
  title: {
    type: String,
    default: ''
  },
  showCopy: {
    type: Boolean,
    default: true
  },
  expandAll: {
    type: Boolean,
    default: false
  },
  dark: {
    type: Boolean,
    default: false
  }
});

const parsedData = computed(() => {
  if (typeof props.data === 'string') {
    try {
      return JSON.parse(props.data);
    } catch (e) {
      return props.data;
    }
  }
  return props.data;
});

const copyJson = () => {
  const text = typeof props.data === 'string' ? props.data : JSON.stringify(props.data, null, 2);
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板');
  });
};
</script>

<style scoped>
.json-viewer-container {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-family: 'Fira Code', 'Monaco', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow: hidden;
}

.json-viewer-container.dark-theme {
  background: #1e293b;
  border-color: #334155;
  color: #e2e8f0;
}

.json-viewer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: rgba(0, 0, 0, 0.03);
  border-bottom: 1px solid rgba(0, 0, 0, 0.05);
}

.dark-theme .json-viewer-header {
  background: rgba(255, 255, 255, 0.05);
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.json-title {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.json-content {
  padding: 12px;
  max-height: 400px;
  overflow-y: auto;
}

/* Custom Scrollbar */
.json-content::-webkit-scrollbar {
  width: 6px;
}
.json-content::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}
.dark-theme .json-content::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
}
</style>
