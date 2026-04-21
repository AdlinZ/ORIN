/**
 * useUser — 统一用户信息管理
 * 替代 Home.vue / Sidebar.vue / TopNavbar.vue 各自重复的 checkLoginStatus 逻辑
 */
import { reactive, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Cookies from 'js-cookie'
import { UI_TEXT } from '@/constants/uiText'

const roleNameMap = {
  ROLE_ADMIN: UI_TEXT.role.ROLE_ADMIN,
  ROLE_SUPER_ADMIN: UI_TEXT.role.ROLE_SUPER_ADMIN,
  ROLE_PLATFORM_ADMIN: UI_TEXT.role.ROLE_PLATFORM_ADMIN,
  ROLE_OPERATOR: UI_TEXT.role.ROLE_OPERATOR,
  ROLE_USER: UI_TEXT.role.ROLE_USER,
  ADMIN: UI_TEXT.role.ADMIN,
  USER: UI_TEXT.role.USER,
}

const roleDisplayPriority = [
  'ROLE_SUPER_ADMIN',
  'ROLE_PLATFORM_ADMIN',
  'ROLE_OPERATOR',
  'ROLE_ADMIN',
  'ADMIN',
  'ROLE_USER',
  'USER',
]

const getDisplayRole = (roles = [], fallbackRole = '') => {
  const normalizedRoles = Array.isArray(roles) ? roles.filter(Boolean) : []
  if (normalizedRoles.length > 0) {
    const roleSet = new Set(normalizedRoles)
    const matched = roleDisplayPriority.find((roleCode) => roleSet.has(roleCode))
    if (matched) return roleNameMap[matched] ?? matched
    return roleNameMap[normalizedRoles[0]] ?? normalizedRoles[0]
  }

  if (fallbackRole) {
    return roleNameMap[fallbackRole] ?? fallbackRole
  }

  return '用户'
}

export function useUser() {
  const userStore = useUserStore()
  const router = useRouter()

  const userInfo = reactive({
    name: '',
    role: '',
    avatar: '',
  })

  const syncUserInfo = () => {
    if (!userStore.isLoggedIn) {
      userStore.restoreFromCookies()
    }

    if (userStore.isLoggedIn && userStore.userInfo) {
      userInfo.name =
        userStore.userInfo.nickname ||
        userStore.userInfo.username ||
        '用户'
      userInfo.avatar = userStore.userInfo.avatar || ''
      userInfo.role = getDisplayRole(userStore.roles, userStore.userInfo.role)
    } else {
      userInfo.name = ''
      userInfo.role = ''
      userInfo.avatar = ''
    }
  }

  /**
   * 从 store / cookie 同步用户信息到 userInfo
   */
  const checkLoginStatus = () => {
    syncUserInfo()
  }

  // 关键：监听用户 store 变化，确保昵称/头像/角色实时同步到侧边栏与顶栏
  watch(
    () => [userStore.isLoggedIn, userStore.userInfo, userStore.roles],
    () => {
      syncUserInfo()
    },
    { immediate: true, deep: true }
  )

  /**
   * 是否已登录（用于条件渲染）
   */
  const isLoggedIn = () => {
    return !!Cookies.get('orin_token') || userStore.isLoggedIn
  }

  /**
   * 带确认框的退出登录
   */
  const handleLogout = async (withConfirm = false) => {
    const doLogout = () => {
      userStore.logout()
      Cookies.remove('orin_token')
      localStorage.removeItem('orin_user')
      ElMessage.success('已安全退出登录')
      router.push('/login')
    }

    if (withConfirm) {
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
        doLogout()
      } catch {
        // 用户取消
      }
    } else {
      doLogout()
    }
  }

  return {
    userInfo,
    checkLoginStatus,
    isLoggedIn,
    handleLogout,
    roleNameMap,
  }
}
