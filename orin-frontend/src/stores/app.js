import { defineStore } from 'pinia';
import { ref, watch } from 'vue';

const MENU_MODE_KEY = 'orin_menu_mode';
const MENU_COLLAPSE_KEY = 'orin_menu_collapse';
const DEFAULT_MENU_MODE = 'sidebar';
const VALID_MENU_MODES = new Set(['topbar', 'sidebar']);

// 获取本地存储的菜单模式
const getStoredMenuMode = () => {
    const stored = localStorage.getItem(MENU_MODE_KEY);
    return VALID_MENU_MODES.has(stored) ? stored : DEFAULT_MENU_MODE;
};

// 获取本地存储的折叠状态
const getStoredCollapse = () => {
    const stored = localStorage.getItem(MENU_COLLAPSE_KEY);
    return stored === 'true';
};

export const useAppStore = defineStore('app', () => {
    const menuMode = ref(getStoredMenuMode()); // 'topbar' | 'sidebar'
    const isCollapse = ref(getStoredCollapse());

    // 切换菜单模式
    const toggleMenuMode = () => {
        menuMode.value = menuMode.value === 'topbar' ? 'sidebar' : 'topbar';
    };

    const setMenuMode = (mode) => {
        if (!VALID_MENU_MODES.has(mode)) return;
        menuMode.value = mode;
    };

    const toggleSidebar = () => {
        isCollapse.value = !isCollapse.value;
    };

    // 监听变化并持久化
    watch(menuMode, (newMode) => {
        localStorage.setItem(MENU_MODE_KEY, newMode);
    });

    watch(isCollapse, (newCollapse) => {
        localStorage.setItem(MENU_COLLAPSE_KEY, String(newCollapse));
    });

    return {
        menuMode,
        isCollapse,
        setMenuMode,
        toggleMenuMode,
        toggleSidebar
    };
});
