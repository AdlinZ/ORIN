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
    const allowedExistingFiles = new Set([
      path.join(root, 'src/main.js')
    ])
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
      .filter((file) => !allowedExistingFiles.has(file))
      .filter((file) => !file.includes(`${path.sep}src${path.sep}ui${path.sep}arco${path.sep}`))
      .filter((file) => fs.readFileSync(file, 'utf8').includes('@arco-design/web-vue'))

    expect(offenders).toEqual([])
  })

  it('provides a single dashboard summary API entry and adapter', () => {
    expect(read('src/api/dashboard.js')).toContain("url: '/dashboard/summary'")
    expect(read('src/viewmodels/index.js')).toContain("export * from './adapters/dashboard'")
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
})
