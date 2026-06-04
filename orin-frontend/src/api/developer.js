import request from '@/utils/request'

/**
 * 开发者工作台接口
 */
export function getDeveloperSummary(config = {}) {
  return request({
    url: '/developer/summary',
    method: 'get',
    ...config
  })
}
