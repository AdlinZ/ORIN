import fs from 'node:fs'
import path from 'node:path'
import { FEATURE_FLAG_KEYS } from '../src/config/featureFlags.js'
import { REVAMP_ROLLOUT_ITEMS } from '../src/config/revampRollout.js'

const REVAMP_FLAGS = FEATURE_FLAG_KEYS.filter((key) => key.startsWith('revamp'))

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
  console.log(`Rollback drill guide written to ${resolved}`)
}

const rolloutFlags = REVAMP_ROLLOUT_ITEMS.map((item) => item.key)
const missingFlags = rolloutFlags.filter((flag) => !REVAMP_FLAGS.includes(flag))

const localStorageSnippet = [
  "(() => {",
  ...REVAMP_FLAGS.map((flag) => `  localStorage.setItem('orin_ff_${flag}', 'false')`),
  "  console.log('revamp flags disabled')",
  "})()"
].join('\n')

const lines = []
lines.push('# ORIN Revamp Rollback Drill')
lines.push('')
lines.push(`Generated at: ${new Date().toISOString()}`)
lines.push('')
lines.push('## 1. Preconditions')
lines.push('- [ ] `npm run smoke:revamp` currently passes')
lines.push('- [ ] Operator has admin access to `/dashboard/control/revamp-rollout`')
lines.push('')
lines.push('## 2. Full Rollback Steps')
lines.push('- [ ] In rollout console, click `全部关闭`')
lines.push('- [ ] Run `npm run smoke:revamp` and verify all routes still resolve')
lines.push('- [ ] Verify core pages fallback to legacy views')
lines.push('')
lines.push('## 3. Browser Console Fallback Script')
lines.push('```js')
lines.push(localStorageSnippet)
lines.push('```')
lines.push('')
lines.push('## 4. Recovery Steps')
lines.push('- [ ] Re-enable by stage order in rollout console')
lines.push('- [ ] Re-run `npm run smoke:revamp && npm run test:revamp`')
lines.push('- [ ] Confirm audit logs include rollback operator and timestamp')
lines.push('')
lines.push('## 5. Consistency Check')
if (missingFlags.length === 0) {
  lines.push('- Rollout flags and feature flag registry are consistent')
} else {
  lines.push(`- Missing flags in registry: ${missingFlags.join(', ')}`)
}

writeIfNeeded(getArgValue('--out'), lines.join('\n'))
