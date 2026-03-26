import request from '@/utils/request'

export function getStatisticsOverview() {
  return request({
    url: '/api/v1/statistics/overview',
    method: 'get'
  })
}

export function getStatisticsUsers(params) {
  return request({
    url: '/api/v1/statistics/users',
    method: 'get',
    params
  })
}

export function getStatisticsAgents(params) {
  return request({
    url: '/api/v1/statistics/agents',
    method: 'get',
    params
  })
}

export function getStatisticsKnowledge(params) {
  return request({
    url: '/api/v1/statistics/knowledge',
    method: 'get',
    params
  })
}

export function getStatisticsTokens(params) {
  return request({
    url: '/api/v1/statistics/tokens',
    method: 'get',
    params
  })
}

export function getStatisticsTasks(params) {
  return request({
    url: '/api/v1/statistics/tasks',
    method: 'get',
    params
  })
}

export function exportStatistics(params) {
  return request({
    url: '/api/v1/statistics/export',
    method: 'get',
    params,
    responseType: 'blob'
  })
}
