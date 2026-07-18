const CSS_VARIABLE_COLOR = /^var\(\s*(--[\w-]+)\s*(?:,\s*(.+))?\)$/
const DEFAULT_CHART_COLOR = '#0d9488'

/**
 * Resolve a CSS custom-property color to a canvas-safe value for ECharts.
 * CSS `var()` fallbacks are valid in DOM styles but cannot be consumed by ECharts directly.
 */
export function resolveChartColor(color, resolveCssVariable = true) {
  const normalizedColor = typeof color === 'string' ? color.trim() : ''
  if (!normalizedColor) return DEFAULT_CHART_COLOR
  if (!resolveCssVariable) return normalizedColor

  const match = normalizedColor.match(CSS_VARIABLE_COLOR)
  if (!match) return normalizedColor

  const [, variableName, fallbackColor] = match
  try {
    const resolvedColor = getComputedStyle(document.documentElement)
      .getPropertyValue(variableName)
      .trim()
    if (resolvedColor) return resolvedColor
  } catch {
    // Server-rendered or test environments can lack DOM style APIs; use the CSS fallback below.
  }

  return fallbackColor?.trim() || DEFAULT_CHART_COLOR
}
