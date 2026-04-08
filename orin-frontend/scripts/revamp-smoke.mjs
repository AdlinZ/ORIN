import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { FEATURE_FLAG_KEYS } from '../src/config/featureFlags.js'
import { ROUTES } from '../src/router/routes.js'
import { REVAMP_ROLLOUT_ITEMS, REVAMP_ROLLOUT_STAGES } from '../src/config/revampRollout.js'
import { buildSmokeReport, toConsoleLines, toMarkdownSummary } from '../src/utils/revampSmokeReport.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const projectRoot = path.resolve(__dirname, '..')

const read = (relativePath) => fs.readFileSync(path.join(projectRoot, relativePath), 'utf8')
const exists = (relativePath) => fs.existsSync(path.join(projectRoot, relativePath))
const ensureDir = (targetPath) => fs.mkdirSync(path.dirname(targetPath), { recursive: true })

function getArgValue(name) {
  const idx = process.argv.indexOf(name)
  if (idx === -1 || idx === process.argv.length - 1) {
    return ''
  }
  return process.argv[idx + 1]
}

function writeOutput(targetPath, content) {
  if (!targetPath) {
    return
  }
  const resolved = path.isAbsolute(targetPath) ? targetPath : path.resolve(process.cwd(), targetPath)
  ensureDir(resolved)
  fs.writeFileSync(resolved, content, 'utf8')
}

const checks = [
  ...REVAMP_ROLLOUT_ITEMS.map((item) => ({
    flag: item.key,
    route: item.route,
    v2File: item.v2View.replace('@/', 'src/'),
    routerNeedle: `resolveDomainView(\n                            '${item.key}'`
  }))
]

const routerIndex = read('src/router/index.js')
const missingFlags = checks.filter((item) => !FEATURE_FLAG_KEYS.includes(item.flag))
const missingRoutes = checks.filter((item) => !item.route || typeof item.route !== 'string')
const missingFiles = checks.filter((item) => !exists(item.v2File))
const missingRouterBindings = checks.filter((item) => !routerIndex.includes(item.routerNeedle))

const failedSet = new Set()
for (const item of missingFlags) failedSet.add(item.flag)
for (const item of missingRoutes) failedSet.add(item.flag)
for (const item of missingFiles) failedSet.add(item.flag)
for (const item of missingRouterBindings) failedSet.add(item.flag)

const rows = checks.map((item) => {
  const errors = []
  if (missingFlags.some((entry) => entry.flag === item.flag)) {
    errors.push('Missing feature flag key')
  }
  if (missingRoutes.some((entry) => entry.flag === item.flag)) {
    errors.push('Missing route constant binding')
  }
  if (missingFiles.some((entry) => entry.flag === item.flag)) {
    errors.push('Missing V2 file')
  }
  if (missingRouterBindings.some((entry) => entry.flag === item.flag)) {
    errors.push('Missing resolveDomainView binding')
  }
  return {
    key: item.flag,
    flag: item.flag,
    route: item.route,
    v2File: item.v2File,
    passed: !failedSet.has(item.flag),
    reason: errors.length > 0 ? errors.join('; ') : '通过'
  }
})

if (ROUTES.SYSTEM.REVAMP_ROLLOUT !== '/dashboard/control/revamp-rollout') {
  rows.push({
    key: 'ROUTES.SYSTEM.REVAMP_ROLLOUT',
    flag: 'ROUTES.SYSTEM.REVAMP_ROLLOUT',
    route: ROUTES.SYSTEM.REVAMP_ROLLOUT,
    v2File: '-',
    passed: false,
    reason: 'Route constant mismatch'
  })
}

const report = buildSmokeReport({
  source: 'ci-script',
  rows,
  stageMatrix: REVAMP_ROLLOUT_STAGES,
  lastRunAt: new Date().toISOString()
})

const consoleOutput = toConsoleLines(report).join('\n')
console.log(consoleOutput)

const jsonOut = getArgValue('--json-out')
const summaryOut = getArgValue('--summary-out')
writeOutput(jsonOut, `${JSON.stringify(report, null, 2)}\n`)
writeOutput(summaryOut, `${toMarkdownSummary(report)}\n`)

if (!report.summary.passed) {
  process.exit(1)
}
