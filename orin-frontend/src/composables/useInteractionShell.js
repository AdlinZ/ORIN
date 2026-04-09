import { computed, onMounted, onUnmounted, ref } from 'vue';

const resolveDrawerMode = (mode, isNarrow) => {
  if (mode === 'always') return true;
  if (mode === 'never') return false;
  return isNarrow;
};

export const useInteractionShell = (options = {}) => {
  const {
    wideMin = 1200,
    narrowMax = 820,
    leftDrawerMode = 'narrow',
    rightDrawerMode = 'narrow'
  } = options;

  const containerRef = ref(null);
  const workspaceWidth = ref(wideMin);

  const isWide = computed(() => workspaceWidth.value >= wideMin);
  const isMedium = computed(() => workspaceWidth.value >= narrowMax && workspaceWidth.value < wideMin);
  const isNarrow = computed(() => workspaceWidth.value < narrowMax);

  const isLeftDrawer = computed(() => resolveDrawerMode(leftDrawerMode, isNarrow.value));
  const isRightDrawer = computed(() => resolveDrawerMode(rightDrawerMode, isNarrow.value));

  let resizeObserver = null;

  onMounted(() => {
    if (!containerRef.value || typeof ResizeObserver === 'undefined') return;
    resizeObserver = new ResizeObserver((entries) => {
      if (!entries?.length) return;
      workspaceWidth.value = entries[0].contentRect.width;
    });
    resizeObserver.observe(containerRef.value);
  });

  onUnmounted(() => {
    if (resizeObserver) resizeObserver.disconnect();
  });

  return {
    containerRef,
    workspaceWidth,
    isWide,
    isMedium,
    isNarrow,
    isLeftDrawer,
    isRightDrawer
  };
};

