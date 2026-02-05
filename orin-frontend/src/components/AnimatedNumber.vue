<template>
  <span class="animated-number">{{ displayValue }}</span>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue';

const props = defineProps({
  value: { type: Number, default: 0 },
  duration: { type: Number, default: 1000 },
  decimals: { type: Number, default: 0 }
});

const displayValue = ref(0);
let animationFrame = null;

const animateValue = (start, end, duration) => {
  const startTime = performance.now();
  
  const animate = (currentTime) => {
    const elapsed = currentTime - startTime;
    const progress = Math.min(elapsed / duration, 1);
    
    // Easing function (easeOutCubic)
    const easeProgress = 1 - Math.pow(1 - progress, 3);
    
    const currentValue = start + (end - start) * easeProgress;
    displayValue.value = props.decimals > 0 
      ? currentValue.toFixed(props.decimals) 
      : Math.floor(currentValue);
    
    if (progress < 1) {
      animationFrame = requestAnimationFrame(animate);
    } else {
      displayValue.value = props.decimals > 0 
        ? end.toFixed(props.decimals) 
        : end;
    }
  };
  
  animationFrame = requestAnimationFrame(animate);
};

watch(() => props.value, (newVal, oldVal) => {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame);
  }
  animateValue(oldVal || 0, newVal, props.duration);
});

onMounted(() => {
  animateValue(0, props.value, props.duration);
});
</script>

<style scoped>
.animated-number {
  font-variant-numeric: tabular-nums;
}
</style>
