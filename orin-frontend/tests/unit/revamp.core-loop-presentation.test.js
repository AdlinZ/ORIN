import { describe, expect, it } from 'vitest'
import {
  chooseDeliverableVersion,
  chooseRunRunner,
  chooseRunVersion,
  compactId,
  formatWorkspaceTime,
  getAgentDeliveryState,
  getAgentVersionState,
  getEndpointStatusMeta,
  getRunnerReadiness,
  getRunnerStatusMeta,
  getRunOutcomeGroup,
  getRunStatusMeta,
  normalizeAgentVersionDetail,
} from '@/views/workspace/coreLoopPresentation'

describe('core loop presentation language', () => {
  it('translates execution states into user-facing Chinese', () => {
    expect(getRunStatusMeta('QUEUED')).toEqual({ label: '排队中', type: 'warning' })
    expect(getRunStatusMeta('RUNNING')).toEqual({ label: '运行中', type: 'primary' })
    expect(getRunStatusMeta('COMPLETED')).toEqual({ label: '已完成', type: 'success' })
    expect(getRunStatusMeta('FAILED')).toEqual({ label: '失败', type: 'danger' })
  })

  it('groups Runs by the next product action instead of raw runtime state', () => {
    expect(getRunOutcomeGroup('QUEUED')).toBe('ACTIVE')
    expect(getRunOutcomeGroup('LEASED')).toBe('ACTIVE')
    expect(getRunOutcomeGroup('RUNNING')).toBe('ACTIVE')
    expect(getRunOutcomeGroup('COMPLETED')).toBe('SUCCEEDED')
    expect(getRunOutcomeGroup('FAILED')).toBe('NEEDS_ACTION')
    expect(getRunOutcomeGroup('CANCELLED')).toBe('STOPPED')
  })

  it('expresses Agent state as delivery readiness', () => {
    expect(getAgentDeliveryState({}).key).toBe('DRAFT')
    expect(getAgentDeliveryState({ activeVersionStatus: 'FROZEN' })).toMatchObject({
      key: 'READY',
      label: '可运行',
      action: '运行',
    })
    expect(getAgentDeliveryState({ activeVersionStatus: 'DEPRECATED' })).toMatchObject({
      key: 'DEPRECATED',
      label: '已退役',
    })
  })

  it('expresses Endpoint state as service availability', () => {
    expect(getEndpointStatusMeta('ACTIVE')).toEqual({ label: '服务中', type: 'success' })
    expect(getEndpointStatusMeta('INACTIVE')).toEqual({ label: '已下线', type: 'info' })
  })

  it('expresses Runner state as execution readiness', () => {
    expect(getRunnerStatusMeta('ONLINE')).toMatchObject({
      label: '可运行',
      type: 'success',
    })
    expect(getRunnerStatusMeta('DRAINING').label).toBe('暂停接单')
    expect(getRunnerStatusMeta('OFFLINE').description).toBe('当前无法接收任务')
    expect(getRunnerReadiness([
      { status: 'ONLINE' },
      { status: 'OFFLINE' },
      { status: 'DEGRADED' },
      { status: 'DRAINING' },
    ])).toEqual({
      online: 1,
      paused: 1,
      needsAttention: 2,
      ready: true,
    })
    expect(getRunnerReadiness([{ status: 'REVOKED' }]).ready).toBe(false)
  })

  it('keeps technical identifiers secondary and compact', () => {
    expect(compactId('run_1234567890abcdef')).toBe('run_1234…cdef')
    expect(compactId('short')).toBe('short')
    expect(formatWorkspaceTime(1785228532489)).toMatch(/\d{2}\/\d{2}.*\d{2}:\d{2}/)
  })

  it('defaults a Run to the active version and first online Runner', () => {
    const versions = [
      { agent_version_id: 'version-1', version_number: 1 },
      { agent_version_id: 'version-2', version_number: 2 },
    ]
    expect(chooseRunVersion({ activeVersionId: 'version-1' }, versions)).toBe('version-1')
    expect(chooseDeliverableVersion(
      { activeVersionId: 'version-1' },
      versions.map((version) => ({ ...version, status: 'FROZEN' })),
      'version-2'
    )).toBe('version-2')
    expect(chooseRunVersion({}, versions)).toBe('version-2')
    expect(chooseRunRunner([{ id: 'runner-1' }, { id: 'runner-2' }])).toBe('runner-1')
    expect(chooseRunRunner([{ id: 'runner-1' }], 'runner-1')).toBe('runner-1')
  })

  it('expresses immutable version state as delivery readiness', () => {
    expect(getAgentVersionState({ status: 'FROZEN', isActive: true })).toEqual({
      key: 'CURRENT',
      label: '当前使用',
      type: 'primary',
    })
    expect(getAgentVersionState({ status: 'FROZEN' }).label).toBe('可运行')
    expect(getAgentVersionState({ status: 'DEPRECATED' }).label).toBe('已退役')
  })

  it('normalizes the real snake_case Agent version response', () => {
    expect(normalizeAgentVersionDetail({
      agent_version_id: 'version-1',
      agent_id: 'agent-1',
      version_number: 3,
      status: 'FROZEN',
      is_active: true,
      change_description: 'ready for delivery',
      snapshot_schema_version: 1,
      content_digest: 'abc',
      frozen_at: '2026-07-28T10:00:00Z',
      secret_refs: [{ alias: 'model', inject_as: 'env', required: true }],
    })).toMatchObject({
      id: 'version-1',
      agentId: 'agent-1',
      versionNumber: 3,
      status: 'FROZEN',
      isActive: true,
      changeDescription: 'ready for delivery',
      snapshotSchemaVersion: 1,
      contentDigest: 'abc',
      secretRefs: [{ alias: 'model', injectAs: 'env', required: true }],
    })
  })
})
