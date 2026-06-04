import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import {
  createApiKey,
  disableApiKey,
  enableApiKey,
  getAllApiKeys,
  getApiKeyUsage,
  getApiKeySecret,
  resetQuota,
  rotateApiKey
} from '@/api/apiKey'

vi.mock('@/utils/request', () => ({
  default: vi.fn()
}))

describe('api key api', () => {
  beforeEach(() => {
    request.mockReset()
  })

  it('uses lifecycle endpoints under the /api/v1 baseURL', () => {
    getAllApiKeys()
    createApiKey({ name: 'client' })
    disableApiKey('gsec_1')
    enableApiKey('gsec_1')
    resetQuota('gsec_1')
    rotateApiKey('gsec_1')
    getApiKeySecret('gsec_1', { currentPassword: 'redacted' })
    getApiKeyUsage('gsec_1', { limit: 10 })

    expect(request).toHaveBeenNthCalledWith(1, {
      url: '/api-keys',
      method: 'get'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      url: '/api-keys',
      method: 'post',
      data: { name: 'client' }
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      url: '/api-keys/gsec_1/disable',
      method: 'patch'
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      url: '/api-keys/gsec_1/enable',
      method: 'patch'
    })
    expect(request).toHaveBeenNthCalledWith(5, {
      url: '/api-keys/gsec_1/reset-quota',
      method: 'patch'
    })
    expect(request).toHaveBeenNthCalledWith(6, {
      url: '/api-keys/gsec_1/rotate',
      method: 'post'
    })
    expect(request).toHaveBeenNthCalledWith(7, {
      url: '/api-keys/gsec_1/secret',
      method: 'post',
      data: { currentPassword: 'redacted' }
    })
    expect(request).toHaveBeenNthCalledWith(8, {
      url: '/api-keys/gsec_1/usage',
      method: 'get',
      params: { limit: 10 }
    })
  })
})
