import { describe, expect, it } from 'vitest'
import dataAssetsSource from '@/views/System/DataAssets.vue?raw'

describe('DataAssets canonical navigation', () => {
  it('keeps tab changes on the admin canonical route', () => {
    expect(dataAssetsSource).toContain("path: '/admin/data-assets'")
    expect(dataAssetsSource).not.toContain("path: '/dashboard/control/data-assets'")
  })
})
