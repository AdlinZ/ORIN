import { describe, expect, it } from 'vitest'
import {
  toModelListViewModel,
  toPlaygroundSummaryViewModel,
  toSessionListViewModel,
  toSkillListViewModel
} from '@/viewmodels'

describe('application domain viewmodels', () => {
  it('normalizes session records without exposing transport shape', () => {
    const [session] = toSessionListViewModel({
      content: [{
        conversationId: 'conv-1',
        agentId: 'agent-1',
        model: 'gpt-test',
        query: 'hello',
        cumulativeTokens: '42',
        responseTime: '120',
        createdAt: [2026, 5, 22, 10, 30, 1],
        success: false,
        trace_id: 'trace-1'
      }]
    })

    expect(session.sessionId).toBe('conv-1')
    expect(session.tokens).toBe(42)
    expect(session.responseTime).toBe(120)
    expect(session.success).toBe(false)
    expect(session.traceId).toBe('trace-1')
    expect(session.time).toBe('2026-05-22 10:30:01')
  })

  it('normalizes model and skill lists for page consumption', () => {
    const [model] = toModelListViewModel({
      records: [{ modelId: 'deepseek-chat', providerName: 'DeepSeek', enabled: false }]
    })
    const [skill] = toSkillListViewModel({
      data: [{ skillId: 'skill-1', name: 'SearchDocs', type: 'KNOWLEDGE', enabled: true }]
    })

    expect(model.name).toBe('deepseek-chat')
    expect(model.provider).toBe('DeepSeek')
    expect(model.status).toBe('DISABLED')
    expect(skill.skillName).toBe('SearchDocs')
    expect(skill.skillType).toBe('KNOWLEDGE')
    expect(skill.status).toBe('ACTIVE')
  })

  it('builds playground metrics from standard collections', () => {
    const vm = toPlaygroundSummaryViewModel({
      agents: [{ id: 1 }, { id: 2 }],
      workflows: [{ id: 'wf-1' }],
      templates: []
    })

    expect(vm.metrics.map((item) => item.value)).toEqual([2, 1, 0])
  })
})
