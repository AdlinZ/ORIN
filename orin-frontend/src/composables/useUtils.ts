/**
 * ORIN Composable 工具库
 * 提取可复用的 Vue 组合式逻辑
 */

import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import type { Ref } from 'vue';

// ========== 通用 Composables ==========

/**
 * 分页列表 Hook
 */
export function usePagination<T>(
  fetchFn: (params: any) => Promise<any>,
  options?: {
    immediate?: boolean;
    pageSize?: number;
  }
) {
  const { immediate = true, pageSize = 10 } = options || {};

  const data = ref<T[]>([]) as Ref<T[]>;
  const loading = ref(false);
  const total = ref(0);
  const page = ref(1);
  const pageSizeRef = ref(pageSize);

  const params = ref<Record<string, any>>({});

  const load = async () => {
    loading.value = true;
    try {
      const result = await fetchFn({
        page: page.value,
        pageSize: pageSizeRef.value,
        ...params.value
      });
      
      if (result?.list) {
        data.value = result.list;
        total.value = result.total || 0;
      } else if (Array.isArray(result)) {
        data.value = result;
        total.value = result.length;
      }
    } catch (error) {
      console.error('Pagination load error:', error);
      ElMessage.error('加载数据失败');
    } finally {
      loading.value = false;
    }
  };

  const search = (searchParams: Record<string, any> = {}) => {
    params.value = searchParams;
    page.value = 1;
    load();
  };

  const reset = () => {
    params.value = {};
    page.value = 1;
    load();
  };

  const handlePageChange = (newPage: number) => {
    page.value = newPage;
    load();
  };

  const handleSizeChange = (newSize: number) => {
    pageSizeRef.value = newSize;
    page.value = 1;
    load();
  };

  if (immediate) {
    onMounted(load);
  }

  return {
    data,
    loading,
    total,
    page,
    pageSize: pageSizeRef,
    params,
    load,
    search,
    reset,
    handlePageChange,
    handleSizeChange
  };
}

/**
 * 单个数据项 Hook
 */
export function useItem<T>(
  fetchFn: (id: string) => Promise<any>,
  options?: { immediate?: boolean }
) {
  const { immediate = false } = options || {};

  const item = ref<T | null>(null) as Ref<T | null>;
  const loading = ref(false);
  const error = ref<string | null>(null);

  const load = async (id: string) => {
    loading.value = true;
    error.value = null;
    try {
      const result = await fetchFn(id);
      item.value = result;
    } catch (err: any) {
      error.value = err.message || '加载失败';
      ElMessage.error(error.value);
    } finally {
      loading.value = false;
    }
  };

  const reset = () => {
    item.value = null;
    error.value = null;
  };

  return {
    item,
    loading,
    error,
    load,
    reset
  };
}

/**
 * CRUD 操作 Hook
 */
export function useCrud<T>(
  options: {
    createFn?: (data: any) => Promise<any>;
    updateFn?: (id: string, data: any) => Promise<any>;
    deleteFn?: (id: string) => Promise<any>;
    onSuccess?: (action: string, data?: any) => void;
    onError?: (action: string, error: any) => void;
  }
) {
  const loading = ref(false);

  const create = async (data: any) => {
    if (!options.createFn) return;
    loading.value = true;
    try {
      const result = await options.createFn(data);
      ElMessage.success('创建成功');
      options.onSuccess?.('create', result);
      return result;
    } catch (error: any) {
      ElMessage.error(error.message || '创建失败');
      options.onError?.('create', error);
    } finally {
      loading.value = false;
    }
  };

  const update = async (id: string, data: any) => {
    if (!options.updateFn) return;
    loading.value = true;
    try {
      const result = await options.updateFn(id, data);
      ElMessage.success('更新成功');
      options.onSuccess?.('update', result);
      return result;
    } catch (error: any) {
      ElMessage.error(error.message || '更新失败');
      options.onError?.('update', error);
    } finally {
      loading.value = false;
    }
  };

  const remove = async (id: string, confirmMsg = '确定要删除吗？') => {
    if (!options.deleteFn) return;
    
    try {
      await ElMessageBox.confirm(confirmMsg, '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      });
      
      loading.value = true;
      await options.deleteFn(id);
      ElMessage.success('删除成功');
      options.onSuccess?.('delete');
    } catch (error: any) {
      if (error !== 'cancel') {
        ElMessage.error(error.message || '删除失败');
        options.onError?.('delete', error);
      }
    } finally {
      loading.value = false;
    }
  };

  return {
    loading,
    create,
    update,
    remove
  };
}

/**
 * 表单 Hook
 */
export function useForm<T extends Record<string, any>>(initialData: T) {
  const form = ref<T>({ ...initialData }) as Ref<T>;
  const originalForm = ref<T>({ ...initialData });
  const errors = ref<Record<string, string>>({});

  const isDirty = computed(() => {
    return JSON.stringify(form.value) !== JSON.stringify(originalForm.value);
  });

  const reset = () => {
    form.value = { ...originalForm.value };
    errors.value = {};
  };

  const setValues = (values: Partial<T>) => {
    form.value = { ...form.value, ...values };
  };

  const validate = (rules: Record<string, any>): boolean => {
    errors.value = {};
    let isValid = true;

    for (const [key, rule] of Object.entries(rules)) {
      const value = form.value[key];
      
      if (rule.required && !value) {
        errors.value[key] = rule.message || `${key}不能为空`;
        isValid = false;
      }
      
      if (rule.pattern && value && !rule.pattern.test(value)) {
        errors.value[key] = rule.message || `${key}格式不正确`;
        isValid = false;
      }
      
      if (rule.validator && typeof rule.validator === 'function') {
        const error = rule.validator(value, form.value);
        if (error) {
          errors.value[key] = error;
          isValid = false;
        }
      }
    }

    return isValid;
  };

  const clearErrors = () => {
    errors.value = {};
  };

  return {
    form,
    errors,
    isDirty,
    reset,
    setValues,
    validate,
    clearErrors
  };
}

/**
 * 确认对话框 Hook
 */
export function useConfirm() {
  const confirm = async (message: string, title = '提示') => {
    try {
      await ElMessageBox.confirm(message, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      });
      return true;
    } catch {
      return false;
    }
  };

  const prompt = async (message: string, title = '输入', defaultValue = '') => {
    try {
      return await ElMessageBox.prompt(message, title, {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: defaultValue
      });
    } catch {
      return null;
    }
  };

  return {
    confirm,
    prompt
  };
}

/**
 * 本地存储 Hook
 */
export function useStorage<T>(key: string, defaultValue: T) {
  const data = ref<T>(defaultValue) as Ref<T>;

  // 初始化时从 localStorage 读取
  const init = () => {
    try {
      const stored = localStorage.getItem(key);
      if (stored) {
        data.value = JSON.parse(stored);
      }
    } catch (e) {
      console.error('Storage init error:', e);
    }
  };

  // 保存到 localStorage
  const save = (value: T) => {
    data.value = value;
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      console.error('Storage save error:', e);
    }
  };

  // 清除
  const clear = () => {
    data.value = { ...defaultValue };
    localStorage.removeItem(key);
  };

  init();

  return {
    data,
    save,
    clear
  };
}

/**
 * 定时刷新 Hook
 */
export function useInterval(fn: () => void, intervalMs: number, options?: { immediate?: boolean }) {
  const { immediate = false } = options || {};
  const isRunning = ref(false);
  let timer: ReturnType<typeof setInterval> | null = null;

  const start = () => {
    if (isRunning.value) return;
    isRunning.value = true;
    if (immediate) fn();
    timer = setInterval(fn, intervalMs);
  };

  const stop = () => {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
    isRunning.value = false;
  };

  const restart = () => {
    stop();
    start();
  };

  onUnmounted(stop);

  return {
    isRunning,
    start,
    stop,
    restart
  };
}

/**
 * 防抖 Hook
 */
export function useDebounce<T extends (...args: any[]) => any>(
  fn: T,
  delay = 300
) {
  let timer: ReturnType<typeof setTimeout> | null = null;

  const debounced = ((...args: any[]) => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  }) as T;

  const cancel = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return {
    debounced,
    cancel
  };
}

/**
 * 节流 Hook
 */
export function useThrottle<T extends (...args: any[]) => any>(
  fn: T,
  delay = 300
) {
  let lastRun = 0;

  const throttled = ((...args: any[]) => {
    const now = Date.now();
    if (now - lastRun >= delay) {
      lastRun = now;
      fn(...args);
    }
  }) as T;

  return {
    throttled
  };
}

/**
 * 快捷键 Hook
 */
export function useShortcut(
  keys: string[],
  callback: () => void,
  options?: { preventDefault?: boolean }
) {
  const { preventDefault = true } = options || {};

  const handler = (event: KeyboardEvent) => {
    const keyParts = keys.map(k => k.toLowerCase());
    const ctrl = keyParts.includes('ctrl') || keyParts.includes('cmd');
    const shift = keyParts.includes('shift');
    const alt = keyParts.includes('alt');
    const key = keyParts.find(k => !['ctrl', 'cmd', 'shift', 'alt'].includes(k));

    const match = 
      event.ctrlKey === ctrl &&
      event.shiftKey === shift &&
      event.altKey === alt &&
      event.key.toLowerCase() === key;

    if (match) {
      if (preventDefault) {
        event.preventDefault();
      }
      callback();
    }
  };

  onMounted(() => window.addEventListener('keydown', handler));
  onUnmounted(() => window.removeEventListener('keydown', handler));
}

// ========== 导出 ==========

export default {
  usePagination,
  useItem,
  useCrud,
  useForm,
  useConfirm,
  useStorage,
  useInterval,
  useDebounce,
  useThrottle,
  useShortcut
};
