import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(__dirname, '../..')

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8')
}

describe('unified frontend 1.0 guardrails', () => {
  it('keeps sidebar as the default enterprise navigation mode', () => {
    const source = read('src/stores/app.js')
    expect(source).toContain("const DEFAULT_MENU_MODE = 'sidebar'")
  })

  it('does not expand Arco usage outside the quarantined ui adapter layer', () => {
    const files = []
    const walk = (dir) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) {
          walk(full)
        } else if (/\.(vue|js|ts)$/.test(entry.name)) {
          files.push(full)
        }
      }
    }

    walk(path.join(root, 'src'))
    const offenders = files
      .filter((file) => !file.includes(`${path.sep}src${path.sep}ui${path.sep}arco${path.sep}`))
      .filter((file) => fs.readFileSync(file, 'utf8').includes('@arco-design/web-vue'))

    expect(offenders).toEqual([])
  })

  it('keeps UI libraries on demand at the app entry', () => {
    const main = read('src/main.js')
    expect(main).not.toContain("app.use(ElementPlus)")
    expect(main).not.toContain("app.use(ArcoVue)")
    expect(main).not.toContain("element-plus/dist/index.css")
    expect(main).not.toContain("@arco-design/web-vue/dist/arco.css")
    expect(main).toContain('app.use(ElLoading)')
  })

  it('keeps chart pages on the curated ECharts runtime', () => {
    const files = []
    const walk = (dir) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) {
          walk(full)
        } else if (/\.(vue|js)$/.test(entry.name)) {
          files.push(full)
        }
      }
    }

    walk(path.join(root, 'src/views'))
    walk(path.join(root, 'src/components'))
    const offenders = files
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8')
        return source.includes("from 'echarts'") || source.includes('from "echarts"')
      })
      .map((file) => path.relative(root, file))

    expect(offenders).toEqual([])
    expect(read('src/utils/echarts.js')).toContain("from 'echarts/core'")
  })

  it('keeps business request calls inside api modules', () => {
    const files = []
    const walk = (dir) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) {
          walk(full)
        } else if (/\.(vue|js|ts)$/.test(entry.name)) {
          files.push(full)
        }
      }
    }

    for (const dir of ['src/components', 'src/layout', 'src/stores', 'src/views']) {
      walk(path.join(root, dir))
    }
    const offenders = files
      .filter((file) => {
        const source = fs.readFileSync(file, 'utf8')
        return source.includes("from '@/utils/request'")
          || source.includes('from "@/utils/request"')
          || /from ['"]\.\.?\/.*\/?api(?:\/|['"])/.test(source)
          || /\bfetch\s*\(/.test(source)
      })
      .map((file) => path.relative(root, file))

    expect(offenders).toEqual([])
    expect(fs.existsSync(path.join(root, 'src/views/Playground/api.js'))).toBe(false)
    expect(read('src/api/playground.js')).not.toContain('VITE_PLAYGROUND_API_URL')
    expect(read('src/stores/knowledgeStore.js')).not.toContain('simulateProgress')
    expect(read('src/stores/knowledgeStore.js')).not.toContain('Mock Task ID')
  })

  it('provides a single dashboard summary API entry and adapter', () => {
    expect(read('src/api/dashboard.js')).toContain("url: '/dashboard/summary'")
    expect(read('src/viewmodels/index.js')).toContain("export * from './adapters/dashboard'")
  })

  it('keeps raw Element Plus tables behind unified table wrappers', () => {
    const allowedRawTableFiles = new Set([
      path.join(root, 'src/components/ResizableTable.vue'),
      path.join(root, 'src/components/orin/OrinAuditTable.vue')
    ])
    const files = []
    const walk = (dir) => {
      for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
        const full = path.join(dir, entry.name)
        if (entry.isDirectory()) {
          walk(full)
        } else if (entry.name.endsWith('.vue')) {
          files.push(full)
        }
      }
    }

    walk(path.join(root, 'src/views'))
    walk(path.join(root, 'src/components'))
    const offenders = files
      .filter((file) => !allowedRawTableFiles.has(file))
      .flatMap((file) => {
        const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/)
        let dataTableDepth = 0
        return lines.flatMap((line, index) => {
          if (line.includes('<OrinDataTable')) dataTableDepth += 1
          const tableIsOutsideWrapper = /<el-table(?:\s|>|$)/.test(line) && dataTableDepth === 0
          if (line.includes('</OrinDataTable>')) dataTableDepth = Math.max(0, dataTableDepth - 1)
          return tableIsOutsideWrapper ? [`${path.relative(root, file)}:${index + 1}`] : []
        })
      })

    expect(offenders).toEqual([])
  })

  it('keeps first-run setup as a public guarded route', () => {
    const router = read('src/router/index.js')
    expect(router).toContain("path: '/setup'")
    expect(router).toContain("getSetupStatusCached")
    expect(router).toContain("'/setup'")
    expect(router).toContain("next(ROUTES.SETUP)")
  })

  it('keeps setup wizard on the unified request client', () => {
    const setupApi = read('src/api/setup.js')
    const setupView = read('src/views/SetupWizard.vue')
    expect(setupApi).toContain("from '@/utils/request'")
    expect(setupView).toContain("initializeSetup")
    expect(setupView).toContain('密码至少 8 位')
    expect(setupView).toContain('CLIENT_ACCESS Key')
    expect(setupView).not.toContain('axios')
  })

  it('keeps Wave 2 application pages on Orin shell and table primitives', () => {
    const wave2Pages = [
      'src/views/revamp/agents/AgentListV2.vue',
      'src/views/Agent/ChatLogs.vue',
      'src/views/ModelConfig/ModelList.vue',
      'src/views/Agent/AgentExtensions.vue',
      'src/views/Skill/SkillManagement.vue',
      'src/views/revamp/collaboration/CollaborationDashboardV2.vue',
      'src/views/Playground/PlaygroundContainer.vue',
      'src/views/Playground/PlaygroundOverview.vue'
    ]

    for (const file of wave2Pages) {
      const source = read(file)
      expect(source, `${file} should use OrinPageShell`).toContain('OrinPageShell')
      expect(source, `${file} should not use legacy PageHeader`).not.toContain("from '@/components/PageHeader.vue'")
    }

    const tablePages = [
      'src/views/revamp/agents/AgentListV2.vue',
      'src/views/Agent/ChatLogs.vue',
      'src/views/ModelConfig/ModelList.vue',
      'src/views/Skill/SkillManagement.vue',
      'src/views/revamp/collaboration/CollaborationDashboardV2.vue'
    ]

    for (const file of tablePages) {
      const source = read(file)
      expect(source, `${file} should wrap tables with OrinDataTable`).toContain('OrinDataTable')
      expect(source, `${file} should expose async states`).toContain('OrinAsyncState')
    }
  })

  it('documents Wave 2 fullscreen and embedded application exceptions', () => {
    const fullscreenWorkbench = read('src/views/Agent/AgentWorkspace.vue')
    const playgroundRun = read('src/views/Playground/PlaygroundRun.vue')
    const playgroundWorkflows = read('src/views/Playground/PlaygroundWorkflows.vue')

    expect(fullscreenWorkbench).toContain('class="agent-workspace"')
    expect(playgroundRun).toContain('PlaygroundContainer')
    expect(playgroundWorkflows).toContain('PlaygroundContainer')
  })

  it('routes application domain pages through standard viewmodel adapters', () => {
    const index = read('src/viewmodels/index.js')
    expect(index).toContain("export * from './adapters/application'")
    expect(read('src/views/Agent/ChatLogs.vue')).toContain('toSessionListViewModel')
    expect(read('src/views/ModelConfig/ModelList.vue')).toContain('toModelListViewModel')
    expect(read('src/views/Skill/SkillManagement.vue')).toContain('toSkillListViewModel')
    expect(read('src/views/Playground/PlaygroundOverview.vue')).toContain('toPlaygroundSummaryViewModel')
  })

  it('keeps model onboarding behind backend model APIs', () => {
    const addModel = read('src/views/ModelConfig/AddModel.vue')
    expect(addModel).toContain('fetchModels')
    expect(addModel).toContain('OrinDataTable')
    expect(addModel).not.toContain('https://api.siliconflow.cn/v1/models?')
    expect(addModel).not.toContain('await fetch(')
  })

  it('keeps Wave 3 workflow pages on Orin shell and state primitives', () => {
    const wave3Pages = [
      'src/views/Workflow/WorkflowList.vue',
      'src/views/Workflow/WorkflowExecution.vue',
      'src/views/Workflow/WorkflowEditor.vue',
      'src/views/Workflow/VisualWorkflowEditor.vue'
    ]

    for (const file of wave3Pages) {
      const source = read(file)
      expect(source, `${file} should use OrinPageShell`).toContain('OrinPageShell')
      expect(source, `${file} should not use legacy PageHeader`).not.toContain("from '@/components/PageHeader.vue'")
    }

    for (const file of ['src/views/Workflow/WorkflowList.vue', 'src/views/Workflow/WorkflowExecution.vue']) {
      const source = read(file)
      expect(source, `${file} should wrap workflow tables with OrinDataTable`).toContain('OrinDataTable')
      expect(source, `${file} should expose workflow async states`).toContain('OrinAsyncState')
    }
  })

  it('routes workflow domain pages through standard viewmodel adapters', () => {
    const workflowIndex = read('src/viewmodels/index.js')
    const workflowAdapter = read('src/viewmodels/adapters/workflow.js')

    expect(workflowIndex).toContain("export * from './adapters/workflow'")
    expect(workflowAdapter).toContain('toWorkflowInstanceViewModel')
    expect(workflowAdapter).toContain('toWorkflowTaskViewModel')
    expect(workflowAdapter).toContain('toWorkflowDslValidationViewModel')
    expect(read('src/views/Workflow/WorkflowList.vue')).toContain('toWorkflowListViewModel')
    expect(read('src/views/Workflow/WorkflowExecution.vue')).toContain('toWorkflowTaskViewModel')
    expect(read('src/views/Workflow/WorkflowExecution.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Workflow/components/WorkflowHistory.vue')).toContain('OrinDataTable')
  })

  it('keeps Wave 4 knowledge pages on Orin shell primitives', () => {
    const wave4Pages = [
      'src/views/Knowledge/KnowledgeAssets.vue',
      'src/views/Knowledge/KBCreate.vue',
      'src/views/Knowledge/KBDetail.vue',
      'src/views/Knowledge/DocumentDetail.vue',
      'src/views/Knowledge/EmbeddingLab.vue',
      'src/views/Knowledge/RetrievalTestPage.vue',
      'src/views/Knowledge/AssetSchema.vue',
      'src/views/Knowledge/KnowledgeGraphDetail.vue'
    ]

    for (const file of wave4Pages) {
      const source = read(file)
      expect(source, `${file} should use OrinPageShell`).toContain('OrinPageShell')
      expect(source, `${file} should not use legacy PageHeader`).not.toContain("from '@/components/PageHeader.vue'")
    }

    const dataStatePages = [
      'src/views/Knowledge/KnowledgeAssets.vue',
      'src/views/Knowledge/KBDetail.vue',
      'src/views/Knowledge/RetrievalTestPage.vue'
    ]

    for (const file of dataStatePages) {
      const source = read(file)
      expect(source, `${file} should expose knowledge async states`).toContain('OrinAsyncState')
    }

    const tablePage = read('src/views/Knowledge/KBDetail.vue')
    expect(tablePage).toContain('OrinDataTable')
  })

  it('routes knowledge domain pages through standard viewmodel adapters', () => {
    const knowledgeIndex = read('src/viewmodels/index.js')
    const knowledgeAdapter = read('src/viewmodels/adapters/knowledge.js')

    expect(knowledgeIndex).toContain("export * from './adapters/knowledge'")
    expect(knowledgeAdapter).toContain('toKnowledgeAssetListViewModel')
    expect(knowledgeAdapter).toContain('toKnowledgeDocumentListViewModel')
    expect(knowledgeAdapter).toContain('toRetrievalResultViewModel')
    expect(read('src/views/Knowledge/KnowledgeAssets.vue')).toContain('toKnowledgeAssetListViewModel')
    expect(read('src/views/Knowledge/KBCreate.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/KBDetail.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/DocumentDetail.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/EmbeddingLab.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/components/DocumentList.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/components/RetrievalTest.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/components/RagEvaluation.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Knowledge/components/KnowledgeMeta.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/components/DocumentManager.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/components/DocumentManager.vue')).toContain('OrinDataTable')
    expect(read('src/views/Knowledge/RetrievalTestPage.vue')).toContain('toRetrievalResultViewModel')
  })

  it('keeps multimodal upload and file management behind api modules', () => {
    const files = [
      'src/views/System/FileManagement.vue',
      'src/components/MultimodalUpload.vue',
      'src/views/Agent/components/VideoGenerator.vue'
    ]

    for (const file of files) {
      expect(read(file), `${file} should not call request directly`).not.toContain("from '@/utils/request'")
    }
  })

  it('keeps embedded system panels on unified table primitives', () => {
    expect(read('src/views/System/ClientSync.vue')).toContain('OrinDataTable')
  })

  it('keeps unified gateway child tabs on unified table primitives', () => {
    const gatewayTabs = [
      'src/views/System/components/gateway/UnifiedGatewayAclTab.vue',
      'src/views/System/components/gateway/UnifiedGatewayOverviewTab.vue',
      'src/views/System/components/gateway/UnifiedGatewayPoliciesTab.vue',
      'src/views/System/components/gateway/UnifiedGatewayRoutesTab.vue',
      'src/views/System/components/gateway/UnifiedGatewayServicesTab.vue'
    ]

    for (const file of gatewayTabs) {
      expect(read(file), `${file} should wrap gateway tables with OrinDataTable`).toContain('OrinDataTable')
    }
  })

  it('keeps shared agent management widgets behind api modules', () => {
    expect(read('src/components/AgentVersionManager.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/components/AgentVersionManager.vue')).toContain('OrinDataTable')
  })

  it('keeps Wave 5 runtime monitor pages on Orin shell and state primitives', () => {
    const wave5Pages = [
      'src/views/Home/HomeDashboard.vue',
      'src/views/Trace/TraceViewer.vue',
      'src/views/Monitor/TaskQueue.vue',
      'src/views/Monitor/ServerMonitor.vue',
      'src/views/Monitor/ServerNodeDetail.vue',
      'src/views/Monitor/TokenStats.vue',
      'src/views/Monitor/LatencyStats.vue',
      'src/views/Monitor/ErrorStats.vue',
      'src/views/Monitor/AlertsLogsCenter.vue',
      'src/views/Monitor/CostStats.vue',
      'src/views/System/AlertManagement.vue',
      'src/views/Monitor/LogArchive.vue',
      'src/views/Monitor/DataFlow.vue',
      'src/views/Monitor/RateLimit.vue'
    ]

    for (const file of wave5Pages) {
      const source = read(file)
      expect(source, `${file} should use OrinPageShell`).toContain('OrinPageShell')
      expect(source, `${file} should not use legacy PageHeader`).not.toContain("from '@/components/PageHeader.vue'")
    }

    const tablePages = [
      'src/views/Trace/TraceViewer.vue',
      'src/views/Monitor/TaskQueue.vue',
      'src/views/Monitor/ServerMonitor.vue',
      'src/views/Monitor/ServerNodeDetail.vue',
      'src/views/Monitor/LatencyStats.vue',
      'src/views/Monitor/ErrorStats.vue',
      'src/views/Monitor/CostStats.vue',
      'src/views/System/AlertManagement.vue',
      'src/views/Monitor/LogArchive.vue',
      'src/views/Monitor/RateLimit.vue'
    ]

    for (const file of tablePages) {
      const source = read(file)
      expect(source, `${file} should wrap runtime tables with OrinDataTable`).toContain('OrinDataTable')
    }

    const statePages = [
      'src/views/Trace/TraceViewer.vue',
      'src/views/Monitor/TaskQueue.vue',
      'src/views/Monitor/ServerMonitor.vue',
      'src/views/Monitor/ServerNodeDetail.vue',
      'src/views/Monitor/LatencyStats.vue',
      'src/views/Monitor/ErrorStats.vue',
      'src/views/System/AlertManagement.vue',
      'src/views/Monitor/LogArchive.vue',
      'src/views/Monitor/DataFlow.vue'
    ]

    for (const file of statePages) {
      const source = read(file)
      expect(source, `${file} should expose runtime async states`).toContain('OrinAsyncState')
    }

    expect(read('src/views/System/AlertManagement.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/Monitor/AlertRuleBuilder.vue')).not.toContain("from '@/utils/request'")
  })

  it('keeps Wave 6 system control pages on Orin shell and table primitives', () => {
    const wave6Pages = [
      'src/views/System/UserManagement.vue',
      'src/views/System/DepartmentManagement.vue',
      'src/views/System/RoleManagement.vue',
      'src/views/System/ApiKeyManagement.vue',
      'src/views/System/DataAssets.vue',
      'src/views/System/FileManagement.vue',
      'src/views/System/MonitorSettings.vue',
      'src/views/System/UnifiedGateway.vue',
      'src/views/System/McpService.vue',
      'src/views/System/PricingConfig.vue',
      'src/views/System/Statistics.vue',
      'src/views/System/SystemMaintenance.vue',
      'src/views/Mail/MailSetup.vue',
      'src/views/Mail/MailWorkbench.vue',
      'src/views/revamp/system/AuditCenterV2.vue'
    ]

    for (const file of wave6Pages) {
      const source = read(file)
      expect(source, `${file} should use OrinPageShell`).toContain('OrinPageShell')
      expect(source, `${file} should not use legacy PageHeader`).not.toContain("from '@/components/PageHeader.vue'")
      expect(source, `${file} should not use legacy entity header`).not.toContain('OrinEntityHeader')
    }

    const tablePages = [
      'src/views/System/UserManagement.vue',
      'src/views/System/RoleManagement.vue',
      'src/views/System/ApiKeyManagement.vue',
      'src/views/System/UnifiedGateway.vue',
      'src/views/System/McpService.vue',
      'src/views/System/PricingConfig.vue',
      'src/views/System/Statistics.vue',
      'src/views/System/SystemMaintenance.vue',
      'src/views/System/FileManagement.vue',
      'src/views/Mail/MailWorkbench.vue',
      'src/views/revamp/system/AuditCenterV2.vue'
    ]

    for (const file of tablePages) {
      const source = read(file)
      expect(source, `${file} should wrap system control tables with OrinDataTable`).toContain('OrinDataTable')
    }

    const statePages = [
      'src/views/System/UserManagement.vue',
      'src/views/System/DepartmentManagement.vue',
      'src/views/System/RoleManagement.vue',
      'src/views/System/UnifiedGateway.vue',
      'src/views/System/McpService.vue',
      'src/views/System/PricingConfig.vue',
      'src/views/System/Statistics.vue',
      'src/views/System/SystemMaintenance.vue',
      'src/views/revamp/system/AuditCenterV2.vue'
    ]

    for (const file of statePages) {
      const source = read(file)
      expect(source, `${file} should expose system control async states`).toContain('OrinAsyncState')
    }

    expect(read('src/views/revamp/system/AuditCenterV2.vue')).not.toContain("from '@/utils/request'")
    expect(read('src/views/System/MonitorSettings.vue')).not.toContain("from '@/utils/request'")
  })

  it('keeps media components off direct browser fetch calls', () => {
    const mediaComponents = [
      'src/views/Agent/components/AudioGenerator.vue',
      'src/views/Agent/components/VideoGenerator.vue'
    ]

    for (const file of mediaComponents) {
      const source = read(file)
      expect(source, `${file} should use shared media fetch helpers`).not.toContain('await fetch(')
    }
  })

  it('documents Wave 6 full-page system exceptions', () => {
    const apiDocs = read('src/views/System/UnifiedApiDocs.vue')
    expect(apiDocs).toContain('class="api-doc-page"')
    expect(apiDocs).not.toContain("from '@/components/PageHeader.vue'")
    expect(apiDocs).not.toContain("from '@/utils/request'")
    expect(apiDocs).not.toContain("from 'axios'")
  })

  it('keeps Wave 7 special pages on unified shells or documented full-page exceptions', () => {
    const profile = read('src/views/Profile.vue')
    expect(profile).toContain('OrinPageShell')
    expect(profile).not.toContain("from '@/components/PageHeader.vue'")

    const login = read('src/views/Login.vue')
    expect(login).toContain('class="login-container"')
    expect(login).not.toContain("from '@/components/PageHeader.vue'")

    const portal = read('src/views/UserPortal.vue')
    expect(portal).toContain('class="service-portal"')
    expect(portal).not.toContain("from '@/components/PageHeader.vue'")

    const dataWall = read('src/views/DataWall.vue')
    expect(dataWall).toContain('class="light-datawall"')
    expect(dataWall).toContain('getServerHardware')
    expect(dataWall).not.toContain("from '@/utils/request'")

    expect(read('src/components/ServerHardwareCard.vue')).not.toContain("from '@/utils/request'")

    const notFound = read('src/views/Error/NotFound.vue')
    expect(notFound).toContain('class="bsod-container"')
    expect(notFound).not.toContain("from '@/components/PageHeader.vue'")

    const workspace = read('src/views/Agent/AgentWorkspace.vue')
    expect(workspace).toContain('class="agent-workspace"')
    expect(workspace).not.toContain("from '@/components/PageHeader.vue'")

    const playgroundRun = read('src/views/Playground/PlaygroundRun.vue')
    expect(playgroundRun).toContain('PlaygroundContainer')
    expect(playgroundRun).not.toContain("from '@/components/PageHeader.vue'")

    const visualEditor = read('src/views/Workflow/VisualWorkflowEditor.vue')
    expect(visualEditor).toContain('OrinPageShell')
    expect(visualEditor).not.toContain("from '@/components/PageHeader.vue'")
  })
})
