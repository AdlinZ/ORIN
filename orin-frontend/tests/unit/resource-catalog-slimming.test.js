import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = process.cwd()
const readSource = (relativePath) => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')

describe('resource catalog slimming', () => {
  it('keeps Runner capacity details in the detail page instead of the main table', () => {
    const source = readSource('src/views/revamp/runners/RunnerListView.vue')

    expect(source).toContain('任务负载')
    expect(source).toContain('最近连接')
    expect(source).not.toContain('<el-table-column label="运行资源"')
    expect(source).not.toContain('resourceSummary')
  })

  it('uses three decision-oriented states instead of total-count cards', () => {
    const modelSource = readSource('src/views/workspace/models/ModelCatalogPage.vue')
    const knowledgeSource = readSource('src/views/workspace/knowledge/KnowledgeLibraryPage.vue')
    const mcpSource = readSource('src/views/workspace/mcp/McpToolCatalogPage.vue')

    expect(modelSource).not.toContain('<span>模型总数</span>')
    expect(knowledgeSource).not.toContain('<span>知识库</span>')
    expect(mcpSource).not.toContain('<span>服务总数</span>')

    expect(modelSource).toContain('待配置凭据')
    expect(knowledgeSource).toContain('待添加内容')
    expect(mcpSource).toContain('待验证')
  })

  it('moves low-frequency knowledge and MCP operations out of the page header', () => {
    const knowledgeSource = readSource('src/views/workspace/knowledge/KnowledgeLibraryPage.vue')
    const mcpSource = readSource('src/views/workspace/mcp/McpToolCatalogPage.vue')

    expect(knowledgeSource).toContain('进入高级资产视图')
    expect(mcpSource).toContain('进入高级管理')
    expect(mcpSource).not.toContain('@click="openTemplates"')
    expect(mcpSource).not.toContain('function openTemplates')
    expect(mcpSource).not.toContain('compactEndpoint')
  })
})
