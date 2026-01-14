import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAppStore = defineStore('app', () => {
    const isCollapse = ref(false);

    const toggleSidebar = () => {
        isCollapse.value = !isCollapse.value;
    };

    return {
        isCollapse,
        toggleSidebar
    };
});
