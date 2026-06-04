import request from '@/utils/request'

export function getSetupStatus(config = {}) {
  return request({
    url: '/setup/status',
    method: 'get',
    noRetry: true,
    silentError: true,
    skipAuthRefresh: true,
    ...config
  })
}

export function testSetupProvider(data) {
  return request({
    url: '/setup/provider/test',
    method: 'post',
    data,
    noRetry: true,
    skipAuthRefresh: true
  })
}

export function initializeSetup(data) {
  return request({
    url: '/setup/initialize',
    method: 'post',
    data,
    noRetry: true,
    skipAuthRefresh: true
  })
}
