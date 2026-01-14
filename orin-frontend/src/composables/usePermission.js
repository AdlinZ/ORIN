import { ref } from 'vue';

// Mock user permissions
const permissions = ref(['agent:view', 'agent:add', 'agent:edit', 'agent:delete', 'knowledge:view']);

export const usePermission = () => {
    const hasPermission = (permission) => {
        // Super admin check or specific permission
        return permissions.value.includes('*') || permissions.value.includes(permission);
    };

    return {
        hasPermission
    };
};
