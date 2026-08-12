import { describe, expect, it } from 'vitest'
import { buildAgentKbBindingPayload } from '@/utils/agentToolBinding'

describe('agent KB binding updates', () => {
  const existingBinding = {
    toolIds: ['query_kb', 'custom-tool'],
    kbIds: ['kb-existing'],
    skillIds: [11],
    mcpIds: [22],
    enableSuggestions: false,
    showRetrievedContext: true,
    autoRenameSession: false,
  }

  it('adds a KB without replacing other agent binding fields', () => {
    expect(buildAgentKbBindingPayload(existingBinding, 'kb-new', true)).toEqual({
      ...existingBinding,
      kbIds: ['kb-existing', 'kb-new'],
    })
  })

  it('removes only the selected KB', () => {
    expect(buildAgentKbBindingPayload({
      ...existingBinding,
      kbIds: ['kb-existing', 'kb-remove'],
    }, 'kb-remove', false)).toEqual(existingBinding)
  })
})
