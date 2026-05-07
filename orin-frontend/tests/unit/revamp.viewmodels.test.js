import { describe, expect, it } from 'vitest'
import {
  toKnowledgeListViewModel,
  toKnowledgeSummaryViewModel,
  workflowStatusLabel,
  toWorkflowListViewModel,
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
})
