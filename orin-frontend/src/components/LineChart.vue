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

  // No manual formatting needed for time axis
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
      backgroundColor: 'rgba(255, 255, 255, 0.9)',
      borderWidth: 0,
      padding: 0,
      extraCssText: 'box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1), 0 4px 6px -2px rgba(0,0,0,0.05); border-radius: 12px; backdrop-filter: blur(8px);',
      formatter: (params) => {
        const p = params[0];
        const date = new Date(p.value[0]);
        const timeStr = date.toLocaleString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' });
        const dateStr = (date.getMonth() + 1) + '月' + date.getDate() + '日';
        return `
          <div style="padding: 12px; min-width: 180px; font-family: Inter, system-ui, sans-serif;">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px;">
              <span style="font-weight: 700; color: #1f2937; font-size: 14px;">${props.title}</span>
              <span style="font-size: 11px; color: #6b7280;">${dateStr}</span>
            </div>
            <div style="color: #4b5563; font-size: 12px; margin-bottom: 10px;">执行节点监测数据采样</div>
            <div style="display: flex; align-items: center; gap: 8px;">
              <div style="width: 8px; height: 8px; border-radius: 50%; background: ${props.color};"></div>
              <span style="font-weight: 600; font-size: 16px; color: #111827;">${p.value[1]}</span>
              <span style="font-size: 11px; color: #9ca3af; margin-top: 4px;">${props.yAxisName || ''}</span>
            </div>
            <div style="margin-top: 8px; font-size: 10px; color: #9ca3af; border-top: 1px solid #f3f4f6; padding-top: 6px;">
              采样时间: ${timeStr}
            </div>
          </div>
        `;
      }
    },
    grid: { 
      top: '40',
      left: '20', 
      right: '20', 
      bottom: '70',
      containLabel: true 
    },
    xAxis: { 
      type: 'time', 
      boundaryGap: false,
      axisLine: { show: false },
      axisLabel: { 
        color: isDark.value ? '#9ca3af' : '#6b7280', 
        fontSize: 10,
        margin: 15
      },
      axisTick: { show: false },
      splitLine: { 
        show: true, 
        lineStyle: { color: isDark.value ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', width: 1 } 
      }
    },
    yAxis: { 
      type: 'value', 
      name: '', 
      splitLine: { lineStyle: { color: isDark.value ? 'rgba(255,255,255,0.05)' : 'rgba(0,0,0,0.03)', type: 'dashed' } },
      axisLabel: { color: '#9ca3af', fontSize: 10 },
      axisLine: { show: false }
    },
    dataZoom: [
      {
        type: 'slider',
        show: true,
        height: 35,
        bottom: 10,
        handleIcon: 'path://M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
        handleSize: '80%',
        handleStyle: {
          color: '#fff',
          shadowBlur: 10,
          shadowColor: 'rgba(0, 0, 0, 0.1)',
          shadowOffsetX: 2,
          shadowOffsetY: 2
        },
        textStyle: { color: '#9ca3af', fontSize: 10 },
        fillerColor: props.color + '15',
        borderColor: 'transparent',
        backgroundColor: isDark.value ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
        borderRadius: 20,
        dataBackground: {
          lineStyle: { color: props.color, width: 1, opacity: 0.3 },
          areaStyle: { color: props.color, opacity: 0.1 }
        },
        selectedDataBackground: {
          lineStyle: { color: props.color, width: 2 },
          areaStyle: { color: props.color, opacity: 0.3 }
        }
      },
      { type: 'inside' }
    ],
    series: [
      {
        name: props.title,
        type: 'line',
        smooth: 0.4,
        showSymbol: true,
        symbol: 'circle',
        symbolSize: (val) => Math.min(Math.max(val[1] / 100, 6), 12),
        itemStyle: { 
          color: props.color,
          borderWidth: 2,
          borderColor: '#fff',
          shadowBlur: 5,
          shadowColor: 'rgba(0,0,0,0.1)'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: props.color + '33' },
            { offset: 1, color: props.color + '00' }
          ])
        },
        lineStyle: { width: 3, color: props.color },
        data: props.data.map(d => [d.timestamp, d.value])
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
  height: 100%;
  position: relative;
  background: transparent;
}
.chart-container {
  width: 100%;
  min-height: 200px;
}
</style>
