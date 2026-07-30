import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = process.cwd()
const readView = (file) => fs.readFileSync(path.join(projectRoot, 'src/views/workspace', file), 'utf8')

describe('core loop detail-page product hierarchy', () => {
  it('keeps Agent essentials visible and implementation details out of the primary UI', () => {
    const source = readView('agents/AgentDraftPage.vue')

    expect(source).toContain('核心配置')
    expect(source).toContain('高级执行参数')
    expect(source).toContain('凭据绑定')
    expect(source).toContain('冻结准备')
    expect(source).not.toContain('RFC 8785 JCS')
    expect(source).not.toContain('digest 公式')
    expect(source).not.toContain('MVP 暂无 secret refs')
  })

  it('shows the Run result before collapsed diagnostics', () => {
    const source = readView('runs/RunDetailPage.vue')

    expect(source.indexOf('class="result-card"')).toBeLessThan(source.indexOf('class="diagnostics"'))
    expect(source).toContain('执行过程')
    expect(source).toContain('技术信息')
    expect(source).not.toContain('<strong>基本信息</strong>')
  })

  it('starts a Run from a task description while keeping version and Runner secondary', () => {
    const source = readView('runs/RunListPage.vue')

    expect(source).toContain('运行中心')
    expect(source).toContain('运行结果概览')
    expect(source).toContain('进度与结果')
    expect(source).toContain('需要处理')
    expect(source).not.toContain('<span>当前记录</span>')
    expect(source).not.toContain('<el-table-column label="执行节点"')
    expect(source).not.toContain('<el-table-column label="Agent / 版本"')
    expect(source).not.toContain('{{ compactId(row.id) }}')
    expect(source).toContain('选择一个可运行的 Agent，描述这次要完成的任务')
    expect(source).toContain('描述任务')
    expect(source).toContain('高级运行设置')
    expect(source).toContain('系统已自动选择，按需调整')
    expect(source).toContain('chooseDeliverableVersion')
    expect(source).toContain('chooseRunRunner')
    expect(source).toContain('form.input.trim()')
    expect(source).toContain('router.push(`/workspace/runs/${createdId}`)')
  })

  it('presents an Agent version as a runnable capability, not a digest inspector', () => {
    const source = readView('agents/AgentVersionDetailPage.vue')

    expect(source).toContain('这是一个不可变的运行版本')
    expect(source).toContain('开始运行')
    expect(source).toContain('发布服务')
    expect(source).toContain('运行凭据')
    expect(source).toContain('技术信息')
    expect(source).toContain('normalizeAgentVersionDetail')
    expect(source).not.toContain('snapshotSchemaVersion=undefined')
    expect(source).not.toContain('AgentVersion v')
  })

  it('presents version history as evolution and delivery actions', () => {
    const source = readView('agents/AgentVersionListPage.vue')

    expect(source).toContain('版本历史')
    expect(source).toContain('当前使用版本是新运行的默认选择')
    expect(source).toContain('可运行版本')
    expect(source).toContain('运行</el-button>')
    expect(source).toContain('发布</el-button>')
    expect(source).toContain('版本管理')
    expect(source).not.toContain('label="digest"')
    expect(source).not.toContain('切到 active')
  })

  it('publishes the specifically selected immutable version through a task-oriented dialog', () => {
    const source = readView('endpoints/EndpointListPage.vue')

    expect(source).toContain('发布中心')
    expect(source).toContain('已交付服务')
    expect(source).toContain('查看调用方式')
    expect(source).toContain('选择已经验证的 Agent，并决定外部系统如何调用它')
    expect(source).toContain('选择要发布的 Agent')
    expect(source).toContain('选择调用方式')
    expect(source).toContain('MCP 工具')
    expect(source).toContain('chooseDeliverableVersion')
    expect(source).toContain('route.query.versionId')
  })

  it('requires API key acknowledgement before revealing delivery methods', () => {
    const source = readView('endpoints/EndpointListPage.vue')

    expect(source).toContain('我已将密钥保存到安全位置')
    expect(source).toContain(':disabled="!deliveryKeySaved"')
    expect(source).toContain('查看调用方式')
    expect(source).toContain("delivery?.endpointType === 'MCP_SERVER'")
    expect(source).not.toContain('<el-tab-pane label="REST API"')
    expect(source).not.toContain('<el-tab-pane label="MCP 客户端"')
    expect(source).toContain('YOUR_API_KEY')
    expect(source).toContain('高级密钥管理')
  })
})
