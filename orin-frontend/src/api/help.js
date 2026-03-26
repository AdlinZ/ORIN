import request from '@/utils/request'

export function getHelpArticles(params) {
  return request({
    url: '/api/v1/help/articles',
    method: 'get',
    params
  })
}

export function getHelpArticle(id) {
  return request({
    url: `/api/v1/help/articles/${id}`,
    method: 'get'
  })
}

export function searchHelpArticles(keyword, page = 0, size = 20) {
  return request({
    url: '/api/v1/help/articles/search',
    method: 'get',
    params: { keyword, page, size }
  })
}

export function getHelpCategories() {
  return request({
    url: '/api/v1/help/categories',
    method: 'get'
  })
}

export function getHelpCategoryStats() {
  return request({
    url: '/api/v1/help/categories/stats',
    method: 'get'
  })
}

export function createHelpArticle(data) {
  return request({
    url: '/api/v1/help/articles',
    method: 'post',
    data
  })
}

export function updateHelpArticle(id, data) {
  return request({
    url: `/api/v1/help/articles/${id}`,
    method: 'put',
    data
  })
}

export function deleteHelpArticle(id) {
  return request({
    url: `/api/v1/help/articles/${id}`,
    method: 'delete'
  })
}
