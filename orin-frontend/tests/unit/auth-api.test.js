import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { getRegistrationStatus, login, register } from '@/api/auth'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

describe('auth api', () => {
  beforeEach(() => {
    request.mockReset()
  })

  it('uses auth endpoints under the /api/v1 baseURL', () => {
    login({ username: 'admin', password: 'redacted' })
    register({ username: 'new-user', password: 'redacted' })
    getRegistrationStatus()

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/auth/login',
      method: 'post',
      data: { username: 'admin', password: 'redacted' }
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/auth/register',
      method: 'post',
      data: { username: 'new-user', password: 'redacted' }
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/auth/registration-status',
      method: 'get'
    })
  })
})
