/**
 * 权限指令
 * 用于在模板中控制元素的显示/隐藏
 * 
 * 使用方法:
 * v-permission="['ROLE_ADMIN']"
 * v-permission="'ROLE_ADMIN'"
 */
import { useUserStore } from '@/stores/user'

export default {
    mounted(el, binding) {
        const { value } = binding
        const userStore = useUserStore()

        if (value) {
            const requiredRoles = Array.isArray(value) ? value : [value]
            const hasPermission = userStore.hasAnyRole(requiredRoles)

            if (!hasPermission) {
                el.parentNode && el.parentNode.removeChild(el)
            }
        } else {
            throw new Error('需要指定角色! 例如: v-permission="[\'ROLE_ADMIN\']"')
        }
    }
}
