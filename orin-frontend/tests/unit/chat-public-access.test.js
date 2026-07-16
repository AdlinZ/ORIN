import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const read = (file) => fs.readFileSync(path.join(frontendRoot, file), 'utf8')

describe('public chat access', () => {
  it('keeps /chat public and does not attach a role requirement to the route', () => {
    const source = read('src/router/index.js')

    expect(source).toContain("'/chat', '/datawall'")
    expect(source).toMatch(/path: '\/chat',[\s\S]*?meta: \{ title: 'ORIN Chat' \}/)
  })

  it('shows a guest-safe sign-in state instead of calling protected chat APIs', () => {
    const source = read('src/views/UserPortal.vue')

    expect(source).toContain('欢迎来到 ORIN Chat')
    expect(source).toContain('if (!isLoggedIn.value) return;')
    expect(source).toContain('if (!isLoggedIn.value) {')
  })
})
