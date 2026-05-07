import { describe, expect, it } from 'vitest'
import {
  DEFAULT_SUPPORTED_WORKFLOW_NODE_TYPES,
  findUnsupportedWorkflowNodes,
  isSupportedWorkflowNodeType,
  normalizeWorkflowNodeType
} from '@/utils/workflowCapabilities'

describe('workflow capabilities', () => {
  it('normalizes aliases before support checks', () => {
    expect(normalizeWorkflowNodeType('if-else')).toBe('if_else')
    expect(isSupportedWorkflowNodeType('knowledge-retrieval')).toBe(true)
    expect(isSupportedWorkflowNodeType('answer')).toBe(false)
  })

  it('finds nodes that cannot be published for execution', () => {
    const unsupported = findUnsupportedWorkflowNodes([
      { id: 'start_1', type: 'start', data: { label: 'Start' } },
      { id: 'answer_1', type: 'answer', data: { label: 'Reply' } },
      { id: 'e-start-answer', source: 'start_1', target: 'answer_1' }
    ], DEFAULT_SUPPORTED_WORKFLOW_NODE_TYPES)

    expect(unsupported).toEqual([
      { id: 'answer_1', type: 'answer', label: 'Reply' }
    ])
  })
})
