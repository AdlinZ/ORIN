import { describe, expect, it } from 'vitest'
import { LEGACY_ROUTE_REDIRECTS, ROUTES } from '@/router/routes'

describe('legacy redirects normalization', () => {
  it('contains no self redirect entries', () => {
    const entries = Object.entries(LEGACY_ROUTE_REDIRECTS)
    const hasSelfRedirect = entries.some(([from, to]) => from === to)
    expect(hasSelfRedirect).toBe(false)
  })

  it('keeps required historical paths', () => {
    expect(LEGACY_ROUTE_REDIRECTS['/workflow']).toBe('/dashboard/applications/workflows')
    expect(LEGACY_ROUTE_REDIRECTS['/system/api-keys']).toBe('/dashboard/control/api-keys')
  })

  it('exports system revamp rollout route constant', () => {
    expect(ROUTES.SYSTEM.REVAMP_ROLLOUT).toBe('/dashboard/control/revamp-rollout')
  })
})
