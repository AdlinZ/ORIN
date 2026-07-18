import { afterEach, describe, expect, it } from 'vitest'
import { resolveChartColor } from '@/utils/chartColor'

const rootStyle = document.documentElement.style

afterEach(() => {
  rootStyle.removeProperty('--accent-500')
})

describe('resolveChartColor', () => {
  it('uses the fallback color when a CSS variable is unavailable to ECharts', () => {
    expect(resolveChartColor('var(--accent-500, #8b5cf6)')).toBe('#8b5cf6')
  })

  it('uses the resolved CSS variable when the theme defines it', () => {
    rootStyle.setProperty('--accent-500', '#7c3aed')

    expect(resolveChartColor('var(--accent-500, #8b5cf6)')).toBe('#7c3aed')
  })

  it('keeps literal colors and can opt out of CSS variable resolution', () => {
    expect(resolveChartColor('#0d9488')).toBe('#0d9488')
    expect(resolveChartColor('var(--accent-500, #8b5cf6)', false)).toBe('var(--accent-500, #8b5cf6)')
  })
})
