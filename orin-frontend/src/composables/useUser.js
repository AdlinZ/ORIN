/**
 * useUser — 统一用户信息管理
 * 替代 Home.vue / Sidebar.vue / TopNavbar.vue 各自重复的 checkLoginStatus 逻辑
 */
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import Cookies from 'js-cookie'

const roleNameMap = {
  ROLE_ADMIN: '管理员',
  ROLE_USER: '用户',
  ADMIN: 'ADMIN',
  USER: 'USER',
}

export function useUser() {
  const userStore = useUserStore()
  const router = useRouter()

  const userInfo = reactive({
    name: '',
    role: '',
    avatar: '',
  })

  /**
   * 从 store / cookie 同步用户信息到 userInfo
   */
  const checkLoginStatus = () => {
    if (!userStore.isLoggedIn) {
      userStore.restoreFromCookies()
    }

    if (userStore.isLoggedIn && userStore.userInfo) {
      userInfo.name =
        userStore.userInfo.nickname ||
        userStore.userInfo.username ||
        '用户'
      userInfo.avatar = userStore.userInfo.avatar || ''
      userInfo.role =
        userStore.roles?.length
          ? roleNameMap[userStore.roles[0]] ?? userStore.roles[0]
          : '用户'
    } else {
      userInfo.name = ''
      userInfo.role = ''
      userInfo.avatar = ''
    }
  }

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
