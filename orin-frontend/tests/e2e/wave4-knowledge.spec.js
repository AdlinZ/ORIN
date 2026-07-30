import { expect, test } from '@playwright/test'

const now = '2026-05-22T10:00:00Z'

const knowledgeBases = [
  {
    id: 1,
    name: '产品手册知识库',
    description: 'Wave 4 浏览器验收知识库',
    type: 'DOCUMENT',
    docCount: 1,
    status: 'ENABLED',
    createdAt: now,
    updatedAt: now
  }
]

const documents = [
  {
    id: 10,
    fileName: 'guide.md',
    fileType: 'md',
    vectorStatus: 'SUCCESS',
    parseStatus: 'PARSED',
    charCount: 1200,
    uploadTime: now
  }
]

const graph = {
  id: 'graph-1',
  knowledgeBaseId: 1,
  name: '产品图谱',
  description: '知识库图谱',
  buildStatus: 'SUCCESS',
  entityCount: 2,
  relationCount: 1,
  updatedAt: now
}

const json = (body) => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
})

async function authenticate(page) {
  await page.addInitScript(() => {
    const roles = ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_USER']
    window.localStorage.setItem('orin_token', 'wave4-token')
    window.sessionStorage.setItem('orin_setup_completed', 'true')
    document.cookie = 'orin_token=wave4-token; path=/'
    document.cookie = `orin_roles=${encodeURIComponent(JSON.stringify(roles))}; path=/`
    document.cookie = `orin_userInfo=${encodeURIComponent(JSON.stringify({ userId: 1, username: 'admin' }))}; path=/`
  })
}

async function mockBackends(page) {
  await page.route('**/*', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (!path.startsWith('/api/v1/') && !path.startsWith('/api/')) {
      return route.continue()
    }

    if (path === '/api/v1/setup/status') {
      return route.fulfill(json({ completed: true, canInitialize: false }))
    }
    if (path === '/api/v1/knowledge/list' || path === '/api/v1/knowledge/kb/list') {
      return route.fulfill(json(knowledgeBases))
    }
    if (path === '/api/v1/knowledge/1') {
      return route.fulfill(json(knowledgeBases[0]))
    }
    if (path === '/api/v1/knowledge/1/documents') {
      return route.fulfill(json(documents))
    }
    if (path === '/api/v1/knowledge/documents/10') {
      return route.fulfill(json(documents[0]))
    }
    if (path === '/api/v1/knowledge/documents/10/chunks') {
      return route.fulfill(json([
        { id: 'chunk-1', content: '如何接入 Dify 的知识库配置说明', chunkType: 'child', score: 0.88 }
      ]))
    }
    if (path === '/api/v1/knowledge/documents/10/chunks/stats') {
      return route.fulfill(json({ parentCount: 1, childCount: 1, chunkingMode: 'PARENT_CHILD' }))
    }
    if (path === '/api/v1/knowledge/documents/10/content') {
      return route.fulfill(json({ mediaType: 'text', text: '产品手册原文内容', fileName: 'guide.md' }))
    }
    if (path === '/api/v1/knowledge/documents/10/retrieval-info') {
      return route.fulfill(json({
        parseStatus: 'PARSED',
        vectorStatus: 'SUCCESS',
        fullContent: '索引文件内容',
        indexCharCount: 1200
      }))
    }
    if (path === '/api/v1/knowledge/documents/10/history') {
      return route.fulfill(json([{ timestamp: now, type: 'create', description: '创建文档' }]))
    }
    if (path === '/api/v1/knowledge/graphs') {
      return route.fulfill(json([graph]))
    }
    if (path === '/api/v1/knowledge/graphs/graph-1') {
      return route.fulfill(json(graph))
    }
    if (path === '/api/v1/knowledge/graphs/graph-1/visualization') {
      return route.fulfill(json({
        nodes: [
          { id: 'entity-1', name: 'Dify', type: '平台' },
          { id: 'entity-2', name: '知识库', type: '能力' }
        ],
        edges: [
          { source: 'entity-1', target: 'entity-2', relationType: 'USES' }
        ],
        categories: ['平台', '能力']
      }))
    }
    if (path === '/api/v1/knowledge/retrieve/test') {
      return route.fulfill(json({
        traceId: 'trace-wave4',
        results: [{
          content: 'Dify 接入需要配置知识库同步。',
          score: 0.91,
          chunkId: 'chunk-1',
          docId: '10',
          metadata: { fileName: 'guide.md' }
        }]
      }))
    }
    if (path === '/api/v1/knowledge/vector/status') {
      return route.fulfill(json({ healthy: true, connection: 'CONNECTED' }))
    }
    if (path === '/api/v1/models') {
      return route.fulfill(json([
        { id: 1, name: 'BGE-M3', modelId: 'BAAI/bge-m3', type: 'EMBEDDING', status: 'ENABLED' },
        { id: 2, name: 'DeepSeek Chat', modelId: 'deepseek-chat', type: 'CHAT', status: 'ENABLED' }
      ]))
    }
    if (path === '/api/v1/agents') {
      return route.fulfill(json([{ id: 'agent-1', agentId: 'agent-1', name: '客服智能体' }]))
    }

    return route.fulfill(json({}))
  })
}

test.describe('Wave 4 knowledge domain browser smoke', () => {
  test('opens knowledge pages and legacy redirects without blank screens or runtime errors', async ({ page }) => {
    const runtimeErrors = []
    page.on('pageerror', (error) => runtimeErrors.push(`pageerror: ${error.message}`))
    page.on('console', (message) => {
      if (message.type() === 'error') runtimeErrors.push(`console error: ${message.text()}`)
    })

    await authenticate(page)
    await mockBackends(page)

    const paths = [
      '/dashboard/resources/assets',
      '/dashboard/resources/knowledge/create',
      '/dashboard/resources/knowledge/detail/1',
      '/dashboard/resources/knowledge/1/document/10',
      '/dashboard/resources/retrieval',
      '/dashboard/resources/retrieval-test',
      '/dashboard/resources/architecture',
      '/dashboard/resources/graph/graph-1',
      '/dashboard/resources/embedding-lab',
      '/dashboard/resources/graph'
    ]

    for (const path of paths) {
      const startErrorCount = runtimeErrors.length
      await page.goto(path, { waitUntil: 'networkidle' })
      await expect(page.locator('body')).not.toHaveText(/^\\s*$/)
      await expect(page.locator('body')).not.toContainText('登录工作台')
      expect(runtimeErrors.slice(startErrorCount), path).toEqual([])
    }

    await page.goto('/dashboard/resources/embedding-lab', { waitUntil: 'networkidle' })
    await expect(page).toHaveURL(/\/dashboard\/resources\/retrieval/)

    await page.goto('/dashboard/resources/graph', { waitUntil: 'networkidle' })
    await expect(page).toHaveURL(/\/dashboard\/resources\/assets/)
  })
})
