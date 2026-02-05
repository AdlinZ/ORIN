<template>
  <div ref="chartRef" class="gauge-chart" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  value: { type: Number, default: 0 },
  max: { type: Number, default: 100 },
  title: { type: String, default: '' },
  unit: { type: String, default: '%' },
  height: { type: String, default: '200px' },
  color: { type: Array, default: () => ['#67C23A', '#E6A23C', '#F56C6C'] }
});

const chartRef = ref(null);
let chartInstance = null;

const initChart = async () => {
  if (!chartRef.value) return;
  
  await nextTick();
  
  if (chartInstance) {
    chartInstance.dispose();
  }
  
  chartInstance = echarts.init(chartRef.value);
  updateOption();
};

const updateOption = () => {
  if (!chartInstance) return;

  const option = {
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        center: ['50%', '75%'],
        radius: '90%',
        min: 0,
        max: props.max,
        splitNumber: 8,
        axisLine: {
          lineStyle: {
            width: 12,
            color: [
              [0.6, props.color[0]],
              [0.8, props.color[1]],
              [1, props.color[2]]
            ]
          }
        },
        pointer: {
          icon: 'path://M12.8,0.7l12,40.1H0.7L12.8,0.7z',
          length: '60%',
          width: 8,
          offsetCenter: [0, '-60%'],
          itemStyle: {
            color: 'auto'
          }
        },
        axisTick: {
          length: 8,
          lineStyle: {
            color: 'auto',
            width: 1
          }
        },
        splitLine: {
          length: 12,
          lineStyle: {
            color: 'auto',
            width: 2
          }
        },
        axisLabel: {
          color: '#909399',
          fontSize: 10,
          distance: -40,
          rotate: 'tangential',
          formatter: function (value) {
            return value.toFixed(0);
          }
        },
        title: {
          offsetCenter: [0, '-10%'],
          fontSize: 14,
          color: '#606266',
          fontWeight: 600
        },
        detail: {
          fontSize: 32,
          offsetCenter: [0, '10%'],
          valueAnimation: true,
          formatter: function (value) {
            return value.toFixed(0) + props.unit;
          },
          color: 'auto',
          fontWeight: 700
        },
        data: [
          {
            value: props.value,
            name: props.title
          }
        ]
      }
    ]
  };
  
  chartInstance.setOption(option);
};

watch(() => props.value, () => {
  if (chartInstance) updateOption();
}, { deep: true });

onMounted(() => {
  initChart();
});

onBeforeUnmount(() => {
  chartInstance?.dispose();
});
</script>

<style scoped>
.gauge-chart {
  width: 100%;
}
</style>
