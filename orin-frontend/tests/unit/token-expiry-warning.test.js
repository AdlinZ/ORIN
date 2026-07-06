import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TokenExpiryWarning from '@/components/TokenExpiryWarning.vue'

const route = reactive({ path: '/dashboard' })
const push = vi.fn()
const userStore = reactive({
  isLoggedIn: true,
  getTokenInfo: vi.fn(() => ({
    valid: true,
    remaining: 3 * 60 * 1000,
    formatted: '3 分钟'
  })),
  logout: vi.fn()
})

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push })
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => userStore
}))

function mountWarning() {
  return mount(TokenExpiryWarning, {
    global: {
      stubs: {
        ElAlert: {
          template: '<div class="el-alert"><slot /></div>'
        },
        ElButton: {
          template: '<button @click="$emit(\'click\')"><slot /></button>'
        }
      }
    }
  })
}

describe('TokenExpiryWarning', () => {
  beforeEach(() => {
    route.path = '/dashboard'
    userStore.isLoggedIn = true
    userStore.getTokenInfo = vi.fn(() => ({
      valid: true,
      remaining: 3 * 60 * 1000,
      formatted: '3 分钟'
    }))
    userStore.logout = vi.fn()
    push.mockClear()
  })

  it('shows the expiry warning in the admin dashboard', () => {
    const wrapper = mountWarning()

    expect(wrapper.text()).toContain('登录即将过期')
    expect(wrapper.text()).toContain('剩余 3 分钟')
  })

  it('does not show or proactively handle expiry outside the admin dashboard', () => {
    route.path = '/platform'
    userStore.getTokenInfo = vi.fn(() => ({
      valid: false,
      remaining: 0,
      formatted: '0 秒'
    }))

    const wrapper = mountWarning()

    expect(wrapper.text()).not.toContain('登录即将过期')
    expect(userStore.logout).not.toHaveBeenCalled()
    expect(push).not.toHaveBeenCalled()
  })
})
