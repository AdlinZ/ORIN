import { describe, expect, it } from 'vitest'
import pricingSource from '@/views/System/PricingConfig.vue?raw'

describe('PricingConfig collection surfaces', () => {
  it('uses one table surface per tab without wrapping it in another card', () => {
    expect(pricingSource).toContain('<OrinDataTable compact class="tab-wrapper-card">')
    expect(pricingSource).toContain('<OrinDataTable v-else compact class="tab-wrapper-card">')
    expect(pricingSource).not.toContain('<el-card shadow="never" class="tab-wrapper-card">')
    expect(pricingSource).not.toContain('<OrinDataTable compact>\n            <el-table')
  })

  it('keeps cost filters and pricing management actions visible in their table headers', () => {
    expect(pricingSource).toContain('v-model="dateRange"')
    expect(pricingSource).toContain('v-model="filterKeyword"')
    expect(pricingSource).toContain('@click="handleQuickImportPricing"')
    expect(pricingSource).toContain('@click="handleAdd"')
  })
})
