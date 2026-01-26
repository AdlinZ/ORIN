<template>
  <div class="chart-container">
    <div ref="chartRef" :style="{ width: '100%', height: height }"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount } from 'vue';
import * as echarts from 'echarts';
import { useDark } from '@vueuse/core';

const isDark = useDark();

const props = defineProps({
  title: String,
  data: { type: Array, default: () => [] }, // Array of { name, value }
  height: { type: String, default: '300px' }
});

const chartRef = ref(null);
let chartInstance = null;

const initChart = () => {
  if (chartInstance) chartInstance.dispose();
  chartInstance = echarts.init(chartRef.value, isDark.value ? 'dark' : '');
  chartInstance.setOption({ backgroundColor: 'transparent' });
  updateOption();
};

const updateOption = () => {
  if (!chartInstance) return;

  const option = {
    color: ['#00BFA5', '#26FFDF', '#14B8A6', '#5EEAD4', '#0D9488'],
    title: {
      text: props.title,
      left: 'center',
      textStyle: { 
        fontSize: 14, 
        fontWeight: '600',
        fontFamily: "'Outfit', sans-serif"
      }
    },
    tooltip: { 
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.8)',
      backdropFilter: 'blur(10px)',
      borderWidth: 0,
      shadowBlur: 10,
      shadowColor: 'rgba(0,0,0,0.1)'
    },
    legend: { 
      bottom: '0%', 
      left: 'center',
      icon: 'circle',
      textStyle: { fontFamily: "'Inter', sans-serif" }
    },
    series: [
      {
        name: props.title,
        type: 'pie',
        radius: ['55%', '75%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 12,
          borderColor: isDark.value ? '#1e293b' : '#fff',
          borderWidth: 4
        },
        label: { 
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 20,
            fontWeight: 'bold',
            formatter: '{d}%'
          },
          itemStyle: {
            shadowBlur: 20,
            shadowOffsetX: 0,
            shadowColor: 'rgba(0, 191, 165, 0.6)'
          }
        },
        data: props.data
      }
    ]
  };
  chartInstance.setOption(option);
};

watch(() => props.data, updateOption, { deep: true });
watch(isDark, () => { initChart(); });

onMounted(() => {
  initChart();
  window.addEventListener('resize', () => chartInstance?.resize());
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', () => chartInstance?.resize());
  chartInstance?.dispose();
});
</script>

<style scoped>
.chart-container { width: 100%; height: 100%; }
</style>
