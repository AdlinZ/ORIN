import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  useChatSelectionPersistence,
  __testables
} from '@/composables/useChatSelectionPersistence'

describe('useChatSelectionPersistence', () => {
  let originalLocalStorage
  let memory = {}

  beforeEach(() => {
    memory = {}
    originalLocalStorage = globalThis.localStorage
    const fakeStore = {
      getItem: (key) => (key in memory ? memory[key] : null),
      setItem: (key, value) => {
        memory[key] = String(value)
      },
      removeItem: (key) => {
        delete memory[key]
      },
      clear: () => {
        memory = {}
      }
    }
    vi.spyOn(globalThis, 'localStorage', 'get').mockReturnValue(fakeStore)
  })

  afterEach(() => {
    if (originalLocalStorage) {
      vi.spyOn(globalThis, 'localStorage', 'get').mockReturnValue(originalLocalStorage)
    }
  })

  it('returns empty values when nothing was persisted', () => {
    const p = useChatSelectionPersistence()
    expect(p.restore()).toEqual({ agentId: '', sessionId: '' })
  })

  it('round-trips agent and session ids through persist/restore', () => {
    const p = useChatSelectionPersistence()
    p.persistAgent('agent-7')
    p.persistSession('sess-42')
    expect(p.restore()).toEqual({ agentId: 'agent-7', sessionId: 'sess-42' })
  })

  it('clearSession removes only the session id and keeps the agent', () => {
    const p = useChatSelectionPersistence()
    p.persistAgent('agent-7')
    p.persistSession('sess-42')
    p.clearSession()
    expect(p.restore()).toEqual({ agentId: 'agent-7', sessionId: '' })
  })

  it('persistAgent with empty value removes the key', () => {
    const p = useChatSelectionPersistence()
    p.persistAgent('agent-7')
    p.persistAgent('')
    expect(memory[__testables.AGENT_KEY]).toBeUndefined()
  })

  it('persistSession with null value removes the key', () => {
    const p = useChatSelectionPersistence()
    p.persistSession('sess-42')
    p.persistSession(null)
    expect(memory[__testables.SESSION_KEY]).toBeUndefined()
  })

  it('clear() wipes both keys', () => {
    const p = useChatSelectionPersistence()
    p.persistAgent('agent-7')
    p.persistSession('sess-42')
    p.clear()
    expect(p.restore()).toEqual({ agentId: '', sessionId: '' })
  })

  it('does not throw when localStorage.setItem raises (e.g. quota exceeded)', () => {
    const fakeStore = {
      getItem: () => null,
      setItem: () => {
        throw new Error('QuotaExceededError')
      },
      removeItem: () => {}
    }
    vi.spyOn(globalThis, 'localStorage', 'get').mockReturnValue(fakeStore)
    const p = useChatSelectionPersistence()
    expect(() => p.persistAgent('a-1')).not.toThrow()
    expect(() => p.persistSession('s-1')).not.toThrow()
    expect(() => p.clearSession()).not.toThrow()
    expect(() => p.clear()).not.toThrow()
    // restore should not throw either
    expect(p.restore()).toEqual({ agentId: '', sessionId: '' })
  })
})
