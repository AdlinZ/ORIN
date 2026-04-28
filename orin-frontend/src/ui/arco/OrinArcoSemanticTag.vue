<template>
  <span class="orin-semantic-tag" :class="toneClass">
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  label: { type: [String, Number], default: '' },
  tone: { type: String, default: 'neutral' },
  value: { type: [String, Number, Boolean], default: '' },
  family: { type: String, default: '' }
})

const normalizedValue = computed(() => String(props.value || props.tone || '').toLowerCase().replace(/_/g, '-'))

const toneClass = computed(() => {
  if (props.family) return `tone-${props.family}-${normalizedValue.value}`
  return `tone-${props.tone || 'neutral'}`
})
</script>

<style scoped>
.orin-semantic-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 22px;
  max-width: 100%;
  padding: 0 8px;
  border: 1px solid #e3e9ef;
  border-radius: 999px;
  color: #3f4d63;
  background: #ffffff;
  font-size: 12px;
  font-weight: 650;
  line-height: 1.2;
  white-space: nowrap;
}

.tone-primary,
.tone-provider-siliconflow {
  border-color: #99f6e4;
  color: #0f766e;
  background: #ecfdf5;
}

.tone-success,
.tone-status-enabled,
.tone-status-active,
.tone-status-success,
.tone-status-completed {
  border-color: #bbf7d0;
  color: #15803d;
  background: #f0fdf4;
}

.tone-warning,
.tone-status-pending,
.tone-status-building,
.tone-status-running {
  border-color: #fde68a;
  color: #a16207;
  background: #fefce8;
}

.tone-danger,
.tone-risk-high,
.tone-status-failed,
.tone-status-error,
.tone-status-disabled,
.tone-status-inactive {
  border-color: #fecaca;
  color: #dc2626;
  background: #fef2f2;
}

.tone-info,
.tone-type-chat {
  border-color: #bfdbfe;
  color: #1d4ed8;
  background: #eff6ff;
}

.tone-file-document {
  border-color: #c7d2fe;
  color: #4338ca;
  background: #eef2ff;
}

.tone-type-embedding,
.tone-file-image {
  border-color: #99f6e4;
  color: #0f766e;
  background: #ecfdf5;
}

.tone-type-reranker,
.tone-file-video {
  border-color: #ddd6fe;
  color: #7c3aed;
  background: #f5f3ff;
}

.tone-type-text-to-image,
.tone-channel-mail {
  border-color: #fecdd3;
  color: #e11d48;
  background: #fff1f2;
}

.tone-type-text-to-video,
.tone-file-audio {
  border-color: #fed7aa;
  color: #c2410c;
  background: #fff7ed;
}

.tone-type-speech-to-text,
.tone-type-text-to-speech,
.tone-file-other {
  border-color: #fde68a;
  color: #a16207;
  background: #fefce8;
}

.tone-type-llm {
  border-color: #c7d2fe;
  color: #4338ca;
  background: #eef2ff;
}
</style>
