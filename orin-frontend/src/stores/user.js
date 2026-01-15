import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import Cookies from 'js-cookie'
import { isTokenExpired, getTokenRemainingTime, formatRemainingTime } from '@/utils/jwt'

/**
 * 用户状态管理Store
 * 管理用户信息、Token和角色权限
 */
export const useUserStore = defineStore('user', () => {
    // 状态
    const token = ref(Cookies.get('orin_token') || '')
    const userInfo = ref(null)
    const roles = ref([]) // 用户角色列表: ['ROLE_ADMIN', 'ROLE_USER']

    // Getters
    const isLoggedIn = computed(() => !!token.value)
    const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
    const isUser = computed(() => roles.value.includes('ROLE_USER'))
    const username = computed(() => userInfo.value?.username || '')
    const userId = computed(() => userInfo.value?.userId || null)

    // Actions
    /**
     * 登录
     */
    function login(loginToken, user, userRoles = []) {
        token.value = loginToken
        userInfo.value = user
        roles.value = userRoles

        // 保存到Cookie
        Cookies.set('orin_token', loginToken, { expires: 7 }) // 7天过期
        Cookies.set('orin_userInfo', JSON.stringify(user), { expires: 7 })
        Cookies.set('orin_roles', JSON.stringify(userRoles), { expires: 7 })
    }

    /**
     * 登出
     */
    function logout() {
        token.value = ''
        userInfo.value = null
        roles.value = []

        // 清除Cookie
        Cookies.remove('orin_token')
        Cookies.remove('orin_userInfo')
        Cookies.remove('orin_roles')
    }

    /**
     * 更新用户信息
     */
    function updateUserInfo(user) {
        userInfo.value = user
        Cookies.set('orin_userInfo', JSON.stringify(user), { expires: 7 })
    }

    /**
     * 设置角色
     */
    function setRoles(userRoles) {
        roles.value = userRoles
        Cookies.set('orin_roles', JSON.stringify(userRoles), { expires: 7 })
    }

    /**
     * 检查是否拥有指定角色
     */
    function hasRole(role) {
        return roles.value.includes(role)
    }

    /**
     * 检查是否拥有任意一个指定角色
     */
    function hasAnyRole(roleList) {
        return roleList.some(role => roles.value.includes(role))
    }

    /**
     * 检查是否拥有所有指定角色
     */
    function hasAllRoles(roleList) {
        return roleList.every(role => roles.value.includes(role))
    }

    /**
     * 从Cookie恢复状态
     */
    function restoreFromCookies() {
        const savedToken = Cookies.get('orin_token')
        const savedUserInfo = Cookies.get('orin_userInfo')
        const savedRoles = Cookies.get('orin_roles')

        if (savedToken) {
            // 检查 Token 是否过期
            if (isTokenExpired(savedToken)) {
                console.warn('Token has expired, clearing user data')
                logout() // 清除过期的 Token
                return
            }

            token.value = savedToken

            // 输出 Token 剩余有效时间（用于调试）
            const remaining = getTokenRemainingTime(savedToken)
            console.log(`Token is valid, remaining time: ${formatRemainingTime(remaining)}`)
        }

        if (savedUserInfo) {
            try {
                userInfo.value = JSON.parse(savedUserInfo)
            } catch (e) {
                console.error('Failed to parse userInfo from cookie:', e)
            }
        }

        if (savedRoles) {
            try {
                roles.value = JSON.parse(savedRoles)
            } catch (e) {
                console.error('Failed to parse roles from cookie:', e)
            }
        }
    }

    /**
     * 检查当前 Token 是否有效
     */
    function isTokenValid() {
        if (!token.value) return false
        return !isTokenExpired(token.value)
    }

    /**
     * 获取 Token 剩余时间信息
     */
    function getTokenInfo() {
        if (!token.value) {
            return { valid: false, remaining: 0, formatted: '无 Token' }
        }

        const expired = isTokenExpired(token.value)
        const remaining = getTokenRemainingTime(token.value)
        const formatted = formatRemainingTime(remaining)

        return {
            valid: !expired,
            remaining,
            formatted
        }
    }

    // 初始化时从Cookie恢复
    restoreFromCookies()

    return {
        // State
        token,
        userInfo,
        roles,

        // Getters
        isLoggedIn,
        isAdmin,
        isUser,
        username,
        userId,

        // Actions
        login,
        logout,
        updateUserInfo,
        setRoles,
        hasRole,
        hasAnyRole,
        hasAllRoles,
        restoreFromCookies,
        isTokenValid,
        getTokenInfo
    }
})
