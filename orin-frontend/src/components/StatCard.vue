<template>
  <div class="stat-card" :class="[variant, { clickable }]" @click="handleClick">
    <div class="stat-icon" :style="{ background: iconBg }">
      <el-icon :size="24">
        <component :is="icon" />
      </el-icon>
    </div>
    <div class="stat-content">
      <div class="stat-label">
        {{ label }}
      </div>
      <div class="stat-value">
        <span class="value">{{ displayValue }}</span>
        <span v-if="suffix" class="suffix">{{ suffix }}</span>
      </div>
      <div v-if="trend !== undefined" class="stat-trend" :class="trendClass">
        <el-icon><component :is="trend >= 0 ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
        {{ Math.abs(trend) }}%
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
  label: string;
  value: number | string;
  icon?: any;
  suffix?: string;
  trend?: number;
  variant?: 'default' | 'primary' | 'success' | 'warning' | 'danger';
  loading?: boolean;
  clickable?: boolean;
  precision?: number;
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  loading: false,
  clickable: false,
  precision: 0
});

const emit = defineEmits<{
  click: [event: MouseEvent];
}>();

const displayValue = computed(() => {
  if (props.loading) return '...';
  if (typeof props.value === 'number') {
    return props.value.toLocaleString('zh-CN', {
      minimumFractionDigits: props.precision,
      maximumFractionDigits: props.precision
    });
  }
  return props.value;
});

const iconBg = computed(() => {
  const colors: Record<string, string> = {
    default: 'linear-gradient(135deg, #f1f5f9 0%, #e2e8f0 100%)',
    primary: 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
    success: 'linear-gradient(135deg, #10b981 0%, #34d399 100%)',
    warning: 'linear-gradient(135deg, #f59e0b 0%, #fbbf24 100%)',
    danger: 'linear-gradient(135deg, #ef4444 0%, #f87171 100%)'
  };
  return colors[props.variant];
});

const trendClass = computed(() => ({
  'trend-up': props.trend && props.trend > 0,
  'trend-down': props.trend && props.trend < 0
}));

const handleClick = (e: MouseEvent) => {
  if (props.clickable) {
    emit('click', e);
  }
};
</script>

<style scoped>
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

html.dark .stat-card {
  background: #0f172a;
  border-color: #1e293b;
}

.stat-card.clickable {
  cursor: pointer;
}

.stat-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

html.dark .stat-card.clickable:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-content {
  flex: 1;
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 4px;
}

html.dark .stat-label {
  color: #94a3b8;
}

.stat-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-value .value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

html.dark .stat-value .value {
  color: #f1f5f9;
}

.stat-value .suffix {
  font-size: 14px;
  color: #64748b;
}

.stat-trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  margin-top: 4px;
}

.stat-trend.trend-up {
  color: #10b981;
}

.stat-trend.trend-down {
  color: #ef4444;
}
</style>
