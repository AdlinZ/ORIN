import { defineStore } from 'pinia';
import { ref } from 'vue';

/**
 * 全局刷新管理 Store
 * 用于管理所有页面的刷新操作，支持取消正在进行的刷新
 */
export const useRefreshStore = defineStore('refresh', () => {
    // 存储所有活跃的 AbortController
    const activeControllers = ref(new Map());

    // 刷新状态：idle, refreshing, cancelling
    const refreshStatus = ref('idle');

    // 当前正在刷新的页面数量
    const activeRefreshCount = ref(0);

    /**
     * 注册一个新的刷新操作
     * @param {string} key - 唯一标识符（通常是页面名称）
     * @returns {AbortController} - 用于取消请求的控制器
     */
    const registerRefresh = (key) => {
        // 如果该 key 已存在，先取消旧的
        if (activeControllers.value.has(key)) {
            const oldController = activeControllers.value.get(key);
            oldController.abort();
        }

        const controller = new AbortController();
        activeControllers.value.set(key, controller);
        activeRefreshCount.value = activeControllers.value.size;

        if (refreshStatus.value !== 'cancelling') {
            refreshStatus.value = 'refreshing';
        }

        return controller;
    };

    /**
     * 取消注册的刷新操作
     * @param {string} key - 唯一标识符
     */
    const unregisterRefresh = (key) => {
        activeControllers.value.delete(key);
        activeRefreshCount.value = activeControllers.value.size;

        if (activeControllers.value.size === 0) {
            refreshStatus.value = 'idle';
        }
    };

    /**
     * 取消所有正在进行的刷新操作
     */
    const cancelAllRefresh = () => {
        if (activeControllers.value.size === 0) {
            return;
        }

        refreshStatus.value = 'cancelling';

        // 取消所有活跃的请求
        activeControllers.value.forEach((controller, key) => {
            controller.abort();
        });

        // 清空所有控制器
        activeControllers.value.clear();
        activeRefreshCount.value = 0;
        refreshStatus.value = 'idle';
    };

    /**
     * 触发全局刷新事件
     */
    const triggerGlobalRefresh = () => {
        // 如果正在刷新，则取消
        if (refreshStatus.value === 'refreshing') {
            cancelAllRefresh();
            return 'cancelled';
        }

        // 否则触发新的刷新
        refreshStatus.value = 'refreshing';
        window.dispatchEvent(new CustomEvent('global-refresh'));
        return 'started';
    };

    return {
        activeControllers,
        refreshStatus,
        activeRefreshCount,
        registerRefresh,
        unregisterRefresh,
        cancelAllRefresh,
        triggerGlobalRefresh
    };
});
