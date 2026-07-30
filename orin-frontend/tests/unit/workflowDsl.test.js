import { describe, expect, it } from 'vitest'
import {
  normalizeWorkflowDsl,
  validateWorkflowDsl,
} from '@/views/Workflow/workflowDsl'

describe('workflow DSL normalization', () => {
  it('normalizes the legacy workflow graph wrapper used by saved workflows', () => {
    const definition = normalizeWorkflowDsl({
      workflow: {
        graph: {
          nodes: [
            { id: 'start-1', type: 'start' },
            {
              id: 'agent-1',
              type: 'agent',
              data: { agentId: 'agent-1' },
            },
            {
              id: 'end-1',
              type: 'end',
              data: { outputs: [{ name: 'answer', value: '{{ agent-1.output }}' }] },
            },
          ],
          edges: [
            { source: 'start-1', target: 'agent-1' },
            { source: 'agent-1', target: 'end-1' },
          ],
        },
      },
    })

    expect(definition.graph.nodes).toHaveLength(3)
    expect(definition.graph.edges).toHaveLength(2)
    expect(validateWorkflowDsl(definition)).toEqual([])
  })
})
