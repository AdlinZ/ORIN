import { describe, expect, it } from 'vitest'
import {
  toKnowledgeListViewModel,
  toKnowledgeAssetListViewModel,
  toKnowledgeDocumentListViewModel,
  toKnowledgeSummaryViewModel,
  toRetrievalResultViewModel,
  workflowStatusLabel,
  toWorkflowDslValidationViewModel,
  toWorkflowInstanceViewModel,
  toWorkflowListViewModel,
  toWorkflowTaskViewModel,
  toWorkflowStatsViewModel
} from '@/viewmodels'

describe('revamp viewmodel adapters', () => {
  it('normalizes knowledge payload', () => {
    const rows = toKnowledgeListViewModel([
      { id: 1, name: 'KB-A', type: 'DOCUMENT', docCount: 2 },
      { id: 2, name: 'KB-B', type: 'STRUCTURED', stats: { tableCount: 3 } }
    ])
    expect(rows[0].type).toBe('UNSTRUCTURED')
    expect(rows[0].stats.documentCount).toBe(2)
    expect(rows[1].stats.tableCount).toBe(3)
  })

  it('builds knowledge summary by type', () => {
    const summary = toKnowledgeSummaryViewModel([
      { type: 'UNSTRUCTURED' },
      { type: 'STRUCTURED' },
      { type: 'PROCEDURAL' },
      { type: 'META_MEMORY' }
    ])
    expect(summary.total).toBe(4)
    expect(summary.unstructured).toBe(1)
    expect(summary.structured).toBe(1)
    expect(summary.procedural).toBe(1)
    expect(summary.memory).toBe(1)
  })

  it('normalizes knowledge assets, documents, and retrieval results', () => {
    const [asset] = toKnowledgeAssetListViewModel(
      [{ id: 7, name: 'Docs', type: 'DOCUMENT', docCount: 3 }],
      [{ id: 'g-7', knowledgeBaseId: 7, name: 'Docs Graph', buildStatus: 'SUCCESS', entityCount: '5' }]
    )
    expect(asset.kb.type).toBe('UNSTRUCTURED')
    expect(asset.graph.id).toBe('g-7')
    expect(asset.graph.entityCount).toBe(5)

    const [doc] = toKnowledgeDocumentListViewModel({
      records: [{ documentId: 'd-1', originalFilename: 'guide.md', vectorStatus: 'SUCCESS', charCount: '42' }]
    })
    expect(doc.id).toBe('d-1')
    expect(doc.fileName).toBe('guide.md')
    expect(doc.charCount).toBe(42)

    const [result] = toRetrievalResultViewModel({
      results: [{ text: 'answer', similarity: '0.82', metadata: { chunk_id: 'c-1', fileName: 'guide.md' } }],
      traceId: 'trace-kb'
    })
    expect(result.content).toBe('answer')
    expect(result.score).toBe(0.82)
    expect(result.chunkId).toBe('c-1')
    expect(result.traceId).toBe('trace-kb')
  })

  it('normalizes workflow payload and computes stats', () => {
    const rows = toWorkflowListViewModel([
      { workflowId: 'wf-1', name: 'Flow-1', status: 'PUBLISHED' },
      { workflowId: 'wf-2', name: 'Flow-2', status: 'DRAFT' }
    ])
    expect(rows[0].workflowName).toBe('Flow-1')
    expect(rows[1].status).toBe('DRAFT')
    const stats = toWorkflowStatsViewModel(rows)
    expect(stats.total).toBe(2)
    expect(stats.published).toBe(1)
    expect(stats.draft).toBe(1)
  })

  it('treats backend ACTIVE workflows as published', () => {
    const stats = toWorkflowStatsViewModel([
      { status: 'ACTIVE' },
      { status: 'DRAFT' }
    ])
    expect(stats.published).toBe(1)
    expect(stats.draft).toBe(1)
    expect(workflowStatusLabel('ACTIVE')).toBe('已发布')
  })

  it('normalizes workflow execution instances and tasks', () => {
    const [instance] = toWorkflowInstanceViewModel({
      records: [{
        instanceId: 'inst-1',
        state: 'COMPLETED',
        startTime: '2026-05-22T10:00:00Z',
        trace_id: 'trace-inst'
      }]
    })
    const [task] = toWorkflowTaskViewModel({
      content: [{
        taskId: 'task-1',
        instanceId: 'inst-1',
        status: 'FAILED',
        retryCount: '1',
        maxRetries: '3',
        error: 'timeout'
      }]
    })

    expect(instance.id).toBe('inst-1')
    expect(instance.traceId).toBe('trace-inst')
    expect(task.taskId).toBe('task-1')
    expect(task.workflowInstanceId).toBe('inst-1')
    expect(task.retryCount).toBe(1)
    expect(task.errorMessage).toBe('timeout')
  })

  it('normalizes workflow DSL validation result', () => {
    expect(toWorkflowDslValidationViewModel([])).toMatchObject({
      level: 'FULL',
      issueCount: 0,
      publishable: true
    })
    expect(toWorkflowDslValidationViewModel(['missing start', 'missing end', 'bad edge'])).toMatchObject({
      level: 'BLOCKED',
      issueCount: 3,
      publishable: false
    })
  })
})
