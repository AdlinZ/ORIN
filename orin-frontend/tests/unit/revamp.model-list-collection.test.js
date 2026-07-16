import { describe, expect, it } from 'vitest'
import modelListSource from '@/views/ModelConfig/ModelList.vue?raw'

describe('ModelList collection layout', () => {
  it('keeps page identity actions separate from the collection workbar', () => {
    const actionsStart = modelListSource.indexOf('<template #actions>')
    const filtersStart = modelListSource.indexOf('<template #filters>')
    const shellEnd = modelListSource.indexOf('</OrinPageShell>')
    const actions = modelListSource.slice(actionsStart, filtersStart)
    const filters = modelListSource.slice(filtersStart, shellEnd)

    expect(actions).toContain('添加模型')
    expect(actions).not.toContain('API 密钥')
    expect(actions).not.toContain('刷新')
    expect(filters).toContain('全部模型')
    expect(filters).toContain('API 密钥')
    expect(filters).toContain('刷新')
    expect(filters).toContain('providerFilter')
    expect(filters).toContain('typeFilter')
    expect(filters).toContain('statusFilter')
  })

  it('uses one data surface without duplicated summary or title cards', () => {
    expect(modelListSource.match(/<OrinDataTable/g)).toHaveLength(1)
    expect(modelListSource).not.toContain('OrinMetricStrip')
    expect(modelListSource).not.toContain('OrinFilterBar')
    expect(modelListSource).not.toContain('workspace-head')
    expect(modelListSource).not.toContain('model-workspace')
    expect(modelListSource).not.toContain('title="模型资源清单"')
    expect(modelListSource).toContain('显示 {{ displayedList.length }} / {{ modelList.length }} 个模型')
    expect(modelListSource).toContain("router.push(`${ROUTES.ADMIN_PATHS.MODELS}/add`)")
  })
})
