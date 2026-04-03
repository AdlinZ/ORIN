/**
 * useTheme — 统一主题切换管理
 * 替代 Sidebar.vue / TopNavbar.vue 各自重复的 isDarkMode / toggleTheme 逻辑
 */
import { useDark } from '@vueuse/core'

export function useTheme() {
  const isDarkMode = useDark({
    onChanged(dark) {
      if (dark) {
        document.documentElement.classList.add('dark')
      } else {
        document.documentElement.classList.remove('dark')
      }
    },
  })

  const toggleTheme = () => {
    isDarkMode.value = !isDarkMode.value
  }

  return {
    isDarkMode,
    toggleTheme,
  }
}
