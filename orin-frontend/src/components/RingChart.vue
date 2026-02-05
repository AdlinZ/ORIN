<template>
  <div ref="chartRef" class="ring-chart" :style="{ height: height }"></div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';

const props = defineProps({
  value: { type: Number, default: 0 },
  max: { type: Number, default: 100 },
  title: { type: String, default: '' },
  subtitle: { type: String, default: '' },
  height: { type: String, default: '180px' },
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

  const percentage = ((props.value / props.max) * 100).toFixed(1);

  const option = {
    series: [
      {
        type: 'pie',
        radius: ['60%', '80%'],
        center: ['50%', '50%'],
        startAngle: 90,
        hoverAnimation: false,
        label: {
          show: true,
          position: 'center',
          formatter: () => {
            return `{value|${props.value}}\n{title|${props.title}}\n{subtitle|${props.subtitle}}`;
          },
          rich: {
            value: {
              fontSize: 32,
              fontWeight: 700,
              color: props.color,
              lineHeight: 40
            },
            title: {
              fontSize: 13,
              color: '#606266',
              lineHeight: 20,
              fontWeight: 600
            },
            subtitle: {
              fontSize: 11,
              color: '#909399',
              lineHeight: 18
            }
          }
        },
        labelLine: {
          show: false
        },
        data: [
          {
            value: props.value,
            itemStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: props.color },
                { offset: 1, color: props.color + 'AA' }
              ]),
              shadowBlur: 10,
              shadowColor: props.color + '66'
            }
          },
          {
            value: props.max - props.value,
            itemStyle: {
              color: '#F0F2F5',
              borderWidth: 0
            },
            label: {
              show: false
            }
          }
        ]
      }
    ]
  };
  
  chartInstance.setOption(option);
};

watch(() => [props.value, props.max], () => {
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
.ring-chart {
  width: 100%;
}
</style>
