import { describe, expect, it } from 'vitest'
import rateLimitSource from '@/views/Monitor/RateLimit.vue?raw'

describe('RateLimit settings surface', () => {
  it('keeps configuration grouped in lightweight panels below the shared page header', () => {
    expect(rateLimitSource).toContain('<div class="rate-limit-grid">')
    expect(rateLimitSource).toContain('class="settings-panel"')
    expect(rateLimitSource).toContain('class="reference-panel"')
    expect(rateLimitSource).not.toContain('<el-card')
  })

  it('gives response-header reference data one table surface instead of nesting it in a card', () => {
    expect(rateLimitSource).toContain('<OrinDataTable compact class="response-header-table">')
    expect(rateLimitSource).toContain('响应头信息')
  })
})
