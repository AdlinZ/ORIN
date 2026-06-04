import request from '@/utils/request'

export function getDashboardSummary(config = {}) {
  return request({
    url: '/dashboard/summary',
    method: 'get',
    ...config
  })
}
