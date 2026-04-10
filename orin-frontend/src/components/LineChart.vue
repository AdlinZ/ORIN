<template>
  <div class="chart-wrapper">
    <div ref="chartRef" class="chart-container" :style="{ height: height }" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onBeforeUnmount, nextTick, computed } from 'vue';
import * as echarts from 'echarts';
import { useDark } from '@vueuse/core';

const isDark = useDark();

const props = defineProps({
  title: String,
  color: { type: String, default: 'var(--orin-primary)' },
  data: { type: Array, default: () => [] },
  height: { type: String, default: '300px' },
  yAxisName: String,
  yAxisMax: Number,
  // 限制显示的点数
  maxPoints: { type: Number, default: 0 },
  // 支持多系列数据
  series: { type: Array, default: () => [] },
  // 是否应用主题色解析 (如果是 CSS 变量则自动取值)
  resolveColor: { type: Boolean, default: true },
  // 是否显示缩放滑块 (数据量大时建议关闭以提升性能)
  showDataZoom: { type: Boolean, default: true }
});

// 检查是否为多系列数据模式
const isMultiSeries = computed(() => {
  return props.series && props.series.length > 0;
});

// 解析实际颜色 (处理 CSS 变量)
const actualColor = computed(() => {
  if (props.color && props.color.startsWith('var(') && props.resolveColor) {
    try {
      const varName = props.color.match(/var\((--[^)]+)\)/)?.[1];
      if (varName) {
        const val = getComputedStyle(document.documentElement).getPropertyValue(varName).trim();
        if (val) return val;
      }
    } catch (e) {
      console.warn('Failed to resolve color variable:', props.color);
    }
  }
  return props.color || '#0d9488';
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

// LTTB (Largest Triangle Three Buckets) downsampling algorithm
// Preserves visual shape of the data while reducing points
const lttbDownsample = (data, threshold) => {
  if (threshold >= data.length || threshold <= 2) return data;

  const sampled = [];
  const bucketSize = (data.length - 2) / (threshold - 2);

  // Always add first point
  sampled.push(data[0]);

  for (let i = 0; i < threshold - 2; i++) {
    const avgRangeStart = Math.floor((i + 1) * bucketSize) + 1;
    const avgRangeEnd = Math.min(Math.floor((i + 2) * bucketSize) + 1, data.length);
    const avgRangeLength = avgRangeEnd - avgRangeStart;

    let avgX = 0, avgY = 0;
    for (let j = avgRangeStart; j < avgRangeEnd; j++) {
      avgX += data[j].timestamp;
      avgY += data[j].value;
    }
    avgX /= avgRangeLength;
    avgY /= avgRangeLength;

    const rangeStart = Math.floor(i * bucketSize) + 1;
    const rangeEnd = Math.floor((i + 1) * bucketSize) + 1;

    const pointAX = sampled[sampled.length - 1].timestamp;
    const pointAY = sampled[sampled.length - 1].value;

    let maxArea = -1;
    let maxAreaPoint = data[rangeStart];

    for (let j = rangeStart; j < rangeEnd; j++) {
      const area = Math.abs(
        (pointAX - avgX) * (data[j].value - pointAY) -
        (pointAX - data[j].timestamp) * (avgY - pointAY)
      ) * 0.5;
      if (area > maxArea) {
        maxArea = area;
        maxAreaPoint = data[j];
      }
    }
    sampled.push(maxAreaPoint);
  }

  // Always add last point
  sampled.push(data[data.length - 1]);

  return sampled;
};

const updateOption = () => {
  if (!chartInstance) return;

  // 限制显示的点数，使用 LTTB 降采样保留视觉特征
  let displayData = props.data;
  if (props.maxPoints > 0 && props.data.length > props.maxPoints) {
    displayData = lttbDownsample(props.data, props.maxPoints);
  }
  const isDenseData = displayData.length > 160;
  const disableAnimation = displayData.length > 240;

  const option = {
    backgroundColor: 'transparent',
    animation: !disableAnimation,
    animationDuration: disableAnimation ? 0 : 300,
    animationDurationUpdate: disableAnimation ? 0 : 300,
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
              <div style="width: 8px; height: 8px; border-radius: 50%; background: ${actualColor.value};"></div>
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
    dataZoom: props.showDataZoom ? [
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
        fillerColor: actualColor.value + '15',
        borderColor: 'transparent',
        backgroundColor: isDark.value ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
        borderRadius: 20,
        dataBackground: {
          lineStyle: { color: actualColor.value, width: 1, opacity: 0.3 },
          areaStyle: { color: actualColor.value, opacity: 0.1 }
        },
        selectedDataBackground: {
          lineStyle: { color: actualColor.value, width: 2 },
          areaStyle: { color: actualColor.value, opacity: 0.3 }
        }
      },
      { type: 'inside' }
    ] : [{ type: 'inside' }],
    series: [
      {
        name: props.title,
        type: 'line',
        smooth: isDenseData ? 0.15 : 0.4,
        showSymbol: !isDenseData,
        symbol: 'circle',
        symbolSize: isDenseData ? 0 : 6,
        itemStyle: { 
          color: actualColor.value,
          borderWidth: isDenseData ? 0 : 2,
          borderColor: '#fff',
          shadowBlur: isDenseData ? 0 : 5,
          shadowColor: 'rgba(0,0,0,0.1)'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: actualColor.value + '33' },
            { offset: 1, color: actualColor.value + '00' }
          ])
        },
        lineStyle: { width: 3, color: actualColor.value },
        data: displayData.map(d => [d.timestamp, d.value]),
        // 大数据量优化：启用 large 模式
        large: displayData.length > 100,
        largeThreshold: 100,
        sampling: 'lttb',
        progressive: 300,
        progressiveThreshold: 500
      }
    ]
  };
  chartInstance.setOption(option);
};

watch([() => props.data, () => props.color, isDark], () => {
  if (chartInstance) updateOption();
  else initChart();
}, { deep: true });

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
