<template>
  <div class="chart-wrapper">
    <div ref="chartRef" class="chart-container" :style="{ height: height }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { useDark } from '@vueuse/core';

const isDark = useDark();

const props = defineProps({
  title: String,
  color: { type: String, default: 'var(--orin-primary)' },
  data: { type: Array, default: () => [] }, 
  height: { type: String, default: '300px' },
  yAxisName: String,
  yAxisMax: Number
});

const chartRef = ref(null);
let chartInstance = null;
let resizeObserver = null;

const initChart = async () => {
  if (!chartRef.value) return;
  
  await nextTick();
  
  if (chartInstance) {
    chartInstance.dispose();
  }
  
  chartInstance = echarts.init(chartRef.value, isDark.value ? 'dark' : '');
  updateOption();
  
  // Use ResizeObserver for more reliable resizing (handles drawer transitions)
  if (resizeObserver) resizeObserver.disconnect();
  resizeObserver = new ResizeObserver(() => {
    if (chartInstance) {
      chartInstance.resize();
    }
  });
  resizeObserver.observe(chartRef.value);
};

const updateOption = () => {
  if (!chartInstance) return;

  const timestamps = props.data.map(d => {
    const date = new Date(d.timestamp);
    return isNaN(date.getTime()) ? '-' : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  });
  const values = props.data.map(d => d.value || 0);

  const option = {
    backgroundColor: 'transparent',
    title: { 
      text: props.title, 
      left: 10,
      top: 10,
      textStyle: { 
        fontSize: 14, 
        fontWeight: '700',
        color: isDark.value ? '#e5e7eb' : '#1f2937'
      }
    },
    tooltip: { 
      trigger: 'axis',
      backgroundColor: isDark.value ? '#1f2937' : '#ffffff',
      borderColor: isDark.value ? '#374151' : '#e5e7eb',
      textStyle: { color: isDark.value ? '#f3f4f6' : '#1f2937' }
    },
    grid: { 
      top: '60',
      left: '40', 
      right: '20', 
      bottom: '30',
      containLabel: true 
    },
    xAxis: { 
      type: 'category', 
      boundaryGap: false, 
      data: timestamps,
      axisLine: { lineStyle: { color: isDark.value ? '#4b5563' : '#e5e7eb' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    yAxis: { 
      type: 'value', 
      name: props.yAxisName, 
      max: props.yAxisMax,
      splitLine: { lineStyle: { color: isDark.value ? '#374151' : '#f3f4f6', type: 'dashed' } },
      axisLabel: { color: '#9ca3af', fontSize: 11 }
    },
    series: [
      {
        name: props.title,
        type: 'line',
        smooth: true,
        showSymbol: false,
        symbol: 'circle',
        symbolSize: 8,
        emphasis: {
          focus: 'series',
          itemStyle: {
            borderWidth: 2,
            borderColor: '#fff'
          }
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: props.color + '66' },
            { offset: 0.5, color: props.color + '22' },
            { offset: 1, color: props.color + '00' }
          ])
        },
        lineStyle: { 
          width: 4, 
          color: props.color,
          shadowBlur: 10,
          shadowColor: props.color + '66',
          shadowOffsetY: 5
        },
        itemStyle: { color: props.color },
        data: values
      }
    ]
  };
  chartInstance.setOption(option);
};

watch(() => props.data, () => {
  if (chartInstance) updateOption();
  else initChart();
}, { deep: true });

watch(isDark, () => {
  initChart();
});

onMounted(() => {
  initChart();
});

onBeforeUnmount(() => {
  if (resizeObserver) resizeObserver.disconnect();
  chartInstance?.dispose();
});
</script>

<style scoped>
.chart-wrapper {
  width: 100%;
  position: relative;
  background: transparent;
}
.chart-container {
  width: 100%;
  min-height: 200px;
}
</style>
