import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { getSetupStatus, initializeSetup, testSetupProvider } from '@/api/setup'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

describe('setup api', () => {
  beforeEach(() => {
    request.mockReset()
  })

  it('uses first-run setup endpoints under the /api/v1 baseURL', () => {
    getSetupStatus()
    testSetupProvider({ provider: 'ollama' })
    initializeSetup({ admin: { username: 'admin' } })

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/setup/status',
      method: 'get',
      noRetry: true,
      silentError: true,
      skipAuthRefresh: true
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/setup/provider/test',
      method: 'post',
      data: { provider: 'ollama' },
      noRetry: true,
      skipAuthRefresh: true
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/setup/initialize',
      method: 'post',
      data: { admin: { username: 'admin' } },
      noRetry: true,
      skipAuthRefresh: true
    })
  })
})
