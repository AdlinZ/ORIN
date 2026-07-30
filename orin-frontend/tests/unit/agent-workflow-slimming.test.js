import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = process.cwd()
const readSource = (relativePath) => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')

describe('Agent and Workflow catalog slimming', () => {
  it('keeps the Agent list focused on model, delivery state and next action', () => {
    const source = readSource('src/views/workspace/agents/AgentListPage.vue')

    expect(source).toContain('label="模型"')
    expect(source).toContain('label="交付状态"')
    expect(source).toContain('@row-click="openAgent"')
    expect(source).toContain('运行</el-button>')
    expect(source).toContain('发布</el-button>')

    expect(source).not.toContain('label="版本 / 状态"')
    expect(source).not.toContain('row.providerType')
    expect(source).not.toContain('row.activeVersionNumber')
    expect(source).not.toContain('>查看版本</el-button>')
    expect(source).not.toContain('（id=${created.agentId}）')
  })

  it('keeps the Workflow list focused on delivery state and next action', () => {
    const source = readSource('src/views/workspace/workflows/WorkflowCatalogPage.vue')

    expect(source).toContain('工作流交付状态概览')
    expect(source).toContain('发布并验证')
    expect(source).toContain('运行验证')
    expect(source).toContain('进入高级管理')

    expect(source).not.toContain('<span>工作流总数</span>')
    expect(source).not.toContain('<el-table-column label="编排"')
    expect(source).not.toContain('v-model="sourceFilter"')
    expect(source).not.toContain('@click="openExecutions"')
    expect(source).not.toContain('ORIN DSL v1')
  })
})
