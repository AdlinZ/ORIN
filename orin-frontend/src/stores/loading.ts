/**
 * Loading 状态管理 Store
 */

import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useLoadingStore = defineStore('loading', () => {
  // 全局 Loading 状态
  const globalLoading = ref(false);
  const globalLoadingText = ref('');

  // 各模块 Loading 状态
  const modules = ref<Record<string, boolean>>({});
  const moduleTexts = ref<Record<string, string>>({});

  // 当前是否有任何 Loading
  const isLoading = computed(() => {
    return globalLoading.value || Object.values(modules.value).some(v => v);
  });

  // 获取当前 Loading 文本
  const currentText = computed(() => {
    if (globalLoading.value && globalLoadingText.value) {
      return globalLoadingText.value;
    }
    // 返回第一个正在加载的模块文本
    for (const [key, loading] of Object.entries(modules.value)) {
      if (loading && moduleTexts.value[key]) {
        return moduleTexts.value[key];
      }
    }
    return '';
  });

  // ========== 全局 Loading ==========

  const startGlobalLoading = (text = '') => {
    globalLoading.value = true;
    globalLoadingText.value = text;
  };

  const stopGlobalLoading = () => {
    globalLoading.value = false;
    globalLoadingText.value = '';
  };

  // ========== 模块 Loading ==========

  const startLoading = (module: string, text = '') => {
    modules.value[module] = true;
    moduleTexts.value[module] = text;
  };

  const stopLoading = (module: string) => {
    modules.value[module] = false;
    moduleTexts.value[module] = '';
  };

  const setLoading = (module: string, loading: boolean, text = '') => {
    modules.value[module] = loading;
    moduleTexts.value[module] = text;
  };

  // ========== 批量操作 ==========

  const startMultipleLoading = (moduleList: string[], text = '') => {
    moduleList.forEach(m => {
      modules.value[m] = true;
      moduleTexts.value[m] = text;
    });
  };

  const stopMultipleLoading = (moduleList: string[]) => {
    moduleList.forEach(m => {
      modules.value[m] = false;
      moduleTexts.value[m] = '';
    });
  };

  const stopAllLoading = () => {
    globalLoading.value = false;
    globalLoadingText.value = '';
    Object.keys(modules.value).forEach(key => {
      modules.value[key] = false;
    });
    Object.keys(moduleTexts.value).forEach(key => {
      moduleTexts.value[key] = '';
    });
  };

  return {
    // State
    globalLoading,
    globalLoadingText,
    modules,
    moduleTexts,
    // Getters
    isLoading,
    currentText,
    // Global Loading Actions
    startGlobalLoading,
    stopGlobalLoading,
    // Module Loading Actions
    startLoading,
    stopLoading,
    setLoading,
    // Batch Actions
    startMultipleLoading,
    stopMultipleLoading,
    stopAllLoading
  };
});
