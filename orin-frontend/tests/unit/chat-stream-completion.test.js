import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')

describe('chat stream completion', () => {
  it('uses the protocol done event as a terminal signal', () => {
    const source = fs.readFileSync(path.join(frontendRoot, 'src/api/agent-chat.js'), 'utf8')

    expect(source).toContain("if (eventName === 'done')")
    expect(source).toContain('await reader.cancel()')
  })
})
