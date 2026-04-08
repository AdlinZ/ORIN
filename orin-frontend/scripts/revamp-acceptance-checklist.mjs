import fs from 'node:fs'
import path from 'node:path'
import { REVAMP_ROLLOUT_ITEMS, REVAMP_ROLLOUT_STAGES } from '../src/config/revampRollout.js'

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

const byDomain = REVAMP_ROLLOUT_ITEMS.reduce((acc, item) => {
  if (!acc[item.domain]) {
    acc[item.domain] = []
  }
  acc[item.domain].push(item)
  return acc
}, {})

const lines = []
lines.push('# ORIN Revamp Acceptance Checklist')
lines.push('')
lines.push(`Generated at: ${new Date().toISOString()}`)
lines.push('')
lines.push('## 1. Smoke & Regression Gates')
lines.push('- [ ] `npm run smoke:revamp` passed')
lines.push('- [ ] `npm run test:revamp` passed')
lines.push('- [ ] `npm run build` passed')
lines.push('')
lines.push('## 2. Staged Rollout Verification')
for (const stage of REVAMP_ROLLOUT_STAGES) {
  lines.push(`- [ ] Stage ${stage.stage}: ${stage.flags.join(' + ')}`)
}
lines.push('')
lines.push('## 3. Domain Core Path Acceptance')
for (const [domain, items] of Object.entries(byDomain)) {
  lines.push(`### ${domain}`)
  for (const item of items) {
    lines.push(`- [ ] ${item.title} (${item.route})`)
  }
  lines.push('')
}
lines.push('## 4. Rollback Readiness')
lines.push('- [ ] Single-module rollback drill completed')
lines.push('- [ ] Full rollback drill completed')
lines.push('- [ ] Audit traceability confirmed after rollback')

writeIfNeeded(getArgValue('--out'), lines.join('\n'))
