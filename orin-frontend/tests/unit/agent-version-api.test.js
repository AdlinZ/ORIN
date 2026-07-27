import { beforeEach, describe, expect, it, vi } from 'vitest'
import request from '@/utils/request'
import { getAgentVersions } from '@/domains/agent/api'

vi.mock('@/utils/request', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn() }
}))

describe('Agent version domain API', () => {
  beforeEach(() => request.get.mockReset())

  it('normalizes the immutable-version list contract for every consumer', async () => {
    request.get.mockResolvedValue([{
      agent_version_id: 'ver_001',
      version_number: 3,
      version_tag: 'v3',
      content_digest: 'digest',
      snapshot_schema_version: 1,
      frozen_at: '2026-07-27T00:00:00Z',
      created_by: 'admin',
      is_active: true,
      status: 'FROZEN'
    }])

    await expect(getAgentVersions('ag_001')).resolves.toEqual([expect.objectContaining({
      id: 'ver_001',
      agentVersionId: 'ver_001',
      versionNumber: 3,
      versionTag: 'v3',
      contentDigest: 'digest',
      snapshotSchemaVersion: 1,
      frozenAt: '2026-07-27T00:00:00Z',
      createdBy: 'admin',
      isActive: true,
      status: 'FROZEN'
    })])
    expect(request.get).toHaveBeenCalledWith('/agents/ag_001/versions')
  })
})
