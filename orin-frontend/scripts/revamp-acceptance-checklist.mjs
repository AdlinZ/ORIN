import fs from 'node:fs'
import path from 'node:path'

const canonicalRoutes = [
  '/dashboard/applications/agents',
  '/dashboard/resources/knowledge',
  '/dashboard/applications/collaboration/dashboard',
  '/dashboard/control/audit-logs',
  '/dashboard/applications/workflows',
  '/dashboard/runtime/overview',
  '/dashboard/control/gateway',
  '/dashboard/control/system-env'
]

function getArgValue(name) {
  const idx = process.argv.indexOf(name)
  if (idx === -1 || idx === process.argv.length - 1) {
    return ''
  }
  return process.argv[idx + 1]
}

function writeIfNeeded(target, content) {
  if (!target) {
    console.log(content)
    return
  }
  const resolved = path.isAbsolute(target) ? target : path.resolve(process.cwd(), target)
  fs.mkdirSync(path.dirname(resolved), { recursive: true })
  fs.writeFileSync(resolved, `${content}\n`, 'utf8')
  console.log(`Acceptance checklist written to ${resolved}`)
}

const lines = []
lines.push('# ORIN Frontend Cleanup Acceptance Checklist')
lines.push('')
lines.push(`Generated at: ${new Date().toISOString()}`)
lines.push('')
lines.push('## 1. Build & Test Gates')
lines.push('- [ ] `npm test` passed')
lines.push('- [ ] `npm run build` passed')
lines.push('- [ ] `npm run smoke:revamp` passed')
lines.push('')
lines.push('## 2. Canonical Route Smoke')
for (const route of canonicalRoutes) {
  lines.push(`- [ ] ${route}`)
}
lines.push('')
lines.push('## 3. Redirect Compatibility Smoke')
lines.push('- [ ] /dashboard/applications/collaboration/tasks -> /dashboard/applications/collaboration')
lines.push('- [ ] /dashboard/applications/collaboration/config -> /dashboard/applications/collaboration')
lines.push('- [ ] /dashboard/applications/tools -> /dashboard/applications/mcp')
lines.push('- [ ] /dashboard/runtime/alert-rules -> /dashboard/runtime/alerts')
lines.push('- [ ] /dashboard/control/revamp-rollout -> /dashboard/control')
lines.push('')
lines.push('## 4. Data Integrity')
lines.push('- [ ] Runtime latency page shows empty state on API failure (no fake trend/history rows)')
lines.push('- [ ] No localStorage mock document behavior in knowledge list flow')

writeIfNeeded(getArgValue('--out'), lines.join('\n'))
