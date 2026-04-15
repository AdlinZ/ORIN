import fs from 'node:fs'
import path from 'node:path'

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

const lines = []
lines.push('# ORIN Frontend Route Rollback Drill')
lines.push('')
lines.push(`Generated at: ${new Date().toISOString()}`)
lines.push('')
lines.push('## 1. Preconditions')
lines.push('- [ ] `npm test` and `npm run smoke:revamp` pass on current branch')
lines.push('- [ ] Confirm target rollback scope (single module vs full router map)')
lines.push('')
lines.push('## 2. Single-Module Rollback')
lines.push('- [ ] Revert only the target route/component mapping in `src/router/index.js`')
lines.push('- [ ] Keep legacy redirects intact in `src/router/routes.js`')
lines.push('- [ ] Run `npm test` and verify no new dead-route/duplicate-route failures')
lines.push('')
lines.push('## 3. Full Rollback')
lines.push('- [ ] Restore previous router and route constants commit')
lines.push('- [ ] Re-run `npm run smoke:revamp` and confirm redirected legacy URLs still resolve')
lines.push('- [ ] Re-run `npm run build` before merge')
lines.push('')
lines.push('## 4. Post-Rollback Validation')
lines.push('- [ ] `/dashboard/control/gateway` and `/dashboard/runtime/overview` functional smoke')
lines.push('- [ ] `/dashboard/applications/workflows` imports/exports and diagnostics smoke')
lines.push('- [ ] `/dashboard/resources/knowledge` list actions smoke')

writeIfNeeded(getArgValue('--out'), lines.join('\n'))
