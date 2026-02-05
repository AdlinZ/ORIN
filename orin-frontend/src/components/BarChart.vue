<template>
  <div ref="chartRef" class="bar-chart" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  data: { type: Array, default: () => [] },
  title: { type: String, default: '' },
  height: { type: String, default: '300px' },
  color: { type: String, default: '#409EFF' }
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

  const xData = props.data.map(item => item.name);
  const yData = props.data.map(item => item.value);

  const option = {
    backgroundColor: 'transparent',
    title: {
      text: props.title,
      left: 10,
      top: 10,
      textStyle: {
        fontSize: 14,
        fontWeight: 700,
        color: '#303133'
      }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      backgroundColor: '#ffffff',
      borderColor: '#e4e7ed',
      textStyle: { color: '#303133' }
    },
    grid: {
      top: 60,
      left: 50,
      right: 30,
      bottom: 30,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: xData,
      axisLine: {
        lineStyle: { color: '#e4e7ed' }
      },
      axisLabel: {
        color: '#909399',
        fontSize: 11,
        interval: 0,
        rotate: xData.length > 5 ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      splitLine: {
        lineStyle: { color: '#f5f7fa', type: 'dashed' }
      },
      axisLabel: {
        color: '#909399',
        fontSize: 11
      }
    },
    series: [
      {
        type: 'bar',
        data: yData,
        barWidth: '40%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: props.color },
            { offset: 1, color: props.color + '88' }
          ]),
          borderRadius: [4, 4, 0, 0],
          shadowBlur: 10,
          shadowColor: props.color + '33',
          shadowOffsetY: 5
        },
        emphasis: {
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: props.color },
              { offset: 1, color: props.color }
            ])
          }
        }
      }
    ]
  };
  
  chartInstance.setOption(option);
};

watch(() => props.data, () => {
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
.bar-chart {
  width: 100%;
}
</style>
