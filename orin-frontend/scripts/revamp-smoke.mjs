import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ROUTES, LEGACY_ROUTE_REDIRECTS } from '../src/router/routes.js'
import { buildSmokeReport, toConsoleLines, toMarkdownSummary } from '../src/utils/revampSmokeReport.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')

function getArgValue(name) {
  const idx = process.argv.indexOf(name)
  if (idx === -1 || idx === process.argv.length - 1) {
    return ''
  }
  return process.argv[idx + 1]
}

function writeOutput(targetPath, content) {
  if (!targetPath) return
  const resolved = path.isAbsolute(targetPath) ? targetPath : path.resolve(process.cwd(), targetPath)
  fs.mkdirSync(path.dirname(resolved), { recursive: true })
  fs.writeFileSync(resolved, content, 'utf8')
}

const checks = [
  {
    key: 'legacy.collaboration.tasks',
    route: '/dashboard/applications/collaboration/tasks',
    passed: LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/tasks'] === ROUTES.AGENTS.COLLABORATION,
    reason: '协作任务历史路由应重定向到协作主入口'
  },
  {
    key: 'legacy.collaboration.config',
    route: '/dashboard/applications/collaboration/config',
    passed: LEGACY_ROUTE_REDIRECTS['/dashboard/applications/collaboration/config'] === ROUTES.AGENTS.COLLABORATION,
    reason: '协作配置历史路由应重定向到协作主入口'
  },
  {
    key: 'legacy.tools',
    route: '/dashboard/applications/tools',
    passed: LEGACY_ROUTE_REDIRECTS['/dashboard/applications/tools'] === ROUTES.MCP.SERVERS,
    reason: 'Tools 历史路由应指向 MCP 入口'
  },
  {
    key: 'legacy.alert-rules',
    route: '/dashboard/runtime/alert-rules',
    passed: LEGACY_ROUTE_REDIRECTS['/dashboard/runtime/alert-rules'] === ROUTES.MONITOR.ALERTS,
    reason: '告警规则历史路由应指向告警中心'
  },
  {
    key: 'route.rollout.removed',
    route: 'ROUTES.SYSTEM.REVAMP_ROLLOUT',
    passed: ROUTES.SYSTEM.REVAMP_ROLLOUT === undefined,
    reason: '灰度开关路由常量应已移除'
  },
  {
    key: 'route.alert.alias',
    route: 'ROUTES.MONITOR.ALERT_RULES',
    passed: ROUTES.MONITOR.ALERT_RULES === ROUTES.MONITOR.ALERTS,
    reason: '告警规则常量应保持与告警中心同一路径'
  },
  {
    key: 'file.system-gateway-v2.removed',
    route: 'src/views/revamp/system/SystemGatewayV2.vue',
    passed: !fs.existsSync(path.join(projectRoot, 'src/views/revamp/system/SystemGatewayV2.vue')),
    reason: '已判定回归的 V2 网关页不应重新出现'
  },
  {
    key: 'file.kb-list.removed',
    route: 'src/views/Knowledge/KBList.vue',
    passed: !fs.existsSync(path.join(projectRoot, 'src/views/Knowledge/KBList.vue')),
    reason: '含本地 mock 文档逻辑的旧知识库页面不应重新出现'
  }
]

const rows = checks.map((item) => ({
  key: item.key,
  flag: item.key,
  route: item.route,
  passed: item.passed,
  reason: item.passed ? '通过' : item.reason
}))

const report = buildSmokeReport({
  source: 'cleanup-smoke',
  rows,
  lastRunAt: new Date().toISOString(),
  stageMatrix: []
})

console.log(toConsoleLines(report).join('\n'))

const jsonOut = getArgValue('--json-out')
const summaryOut = getArgValue('--summary-out')
writeOutput(jsonOut, `${JSON.stringify(report, null, 2)}\n`)
writeOutput(summaryOut, `${toMarkdownSummary(report)}\n`)

if (!report.summary.passed) {
  process.exit(1)
}
