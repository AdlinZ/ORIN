import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

const projectRoot = path.resolve(__dirname, '..', '..')

function exists(relativePath) {
  return fs.existsSync(path.join(projectRoot, relativePath))
}

function listFiles(dir, matcher) {
  const root = path.join(projectRoot, dir)
  if (!fs.existsSync(root)) {
    return []
  }

  const out = []
  const stack = [root]
  while (stack.length) {
    const current = stack.pop()
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const abs = path.join(current, entry.name)
      if (entry.isDirectory()) {
        stack.push(abs)
        continue
      }
      const rel = path.relative(projectRoot, abs)
      if (matcher(rel)) {
        out.push(rel)
      }
    }
  }
  return out
}

describe('dead file guardrails', () => {
  it('keeps removed scaffolding files absent', () => {
    const removed = [
      'src/services/api.ts',
      'src/stores/app.ts',
      'src/stores/loading.ts',
      'src/types/api.d.ts'
    ]

    for (const file of removed) {
      expect(exists(file)).toBe(false)
    }
  })

  it('keeps removed directories absent', () => {
    expect(exists('src/services')).toBe(false)
    expect(exists('src/types')).toBe(false)
  })

  it('blocks backup files under views', () => {
    const backupFiles = listFiles('src/views', (rel) => rel.endsWith('.backup'))
    expect(backupFiles).toEqual([])
  })
})
