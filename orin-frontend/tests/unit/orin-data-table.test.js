import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'

const componentSource = readFileSync(
  path.resolve(__dirname, '../../src/components/orin/OrinDataTable.vue'),
  'utf8'
)

const global = {
  stubs: {
    ElCard: {
      name: 'ElCard',
      template: `
        <article class="el-card-stub">
          <header v-if="$slots.header" class="el-card-header-stub"><slot name="header" /></header>
          <div class="el-card-body-stub"><slot /></div>
        </article>
      `
    }
  }
}

const mountTable = ({ props = {}, slots = {} } = {}) => mount(OrinDataTable, {
  props,
  slots,
  global
})

describe('OrinDataTable', () => {
  it('keeps card as the compatible default surface', () => {
    const wrapper = mountTable({
      slots: { default: '<div data-test="content">表格内容</div>' }
    })

    expect(wrapper.props('surface')).toBe('card')
    expect(wrapper.find('.el-card-stub').exists()).toBe(true)
    expect(wrapper.classes()).toContain('orin-data-table--card')
    expect(wrapper.get('[data-test="content"]').text()).toBe('表格内容')
  })

  it('renders a bare surface without an Element card or outer card treatment', () => {
    const wrapper = mountTable({
      props: { surface: 'bare' },
      slots: {
        header: '<div data-test="header">自定义表头</div>',
        default: '<div data-test="content">表格内容</div>',
        footer: '<div data-test="footer">分页</div>'
      }
    })

    expect(wrapper.find('.el-card-stub').exists()).toBe(false)
    expect(wrapper.classes()).toContain('orin-data-table--bare')
    expect(wrapper.get('[data-test="header"]').text()).toBe('自定义表头')
    expect(wrapper.get('[data-test="content"]').text()).toBe('表格内容')
    expect(wrapper.get('[data-test="footer"]').text()).toBe('分页')
  })

  it.each(['card', 'bare'])('renders title and description on the %s surface', (surface) => {
    const wrapper = mountTable({
      props: {
        surface,
        title: '模型资源清单',
        description: '按供应商维护模型'
      }
    })

    expect(wrapper.get('.data-table-titlebar strong').text()).toBe('模型资源清单')
    expect(wrapper.get('.data-table-titlebar span').text()).toBe('按供应商维护模型')
  })

  it.each(['card', 'bare'])('gives the header slot precedence on the %s surface', (surface) => {
    const wrapper = mountTable({
      props: {
        surface,
        title: '不应重复的标题',
        description: '不应重复的描述'
      },
      slots: {
        header: '<div data-test="header">唯一表头</div>'
      }
    })

    expect(wrapper.findAll('[data-test="header"]')).toHaveLength(1)
    expect(wrapper.text()).toContain('唯一表头')
    expect(wrapper.text()).not.toContain('不应重复的标题')
    expect(wrapper.text()).not.toContain('不应重复的描述')
    expect(wrapper.find('.data-table-titlebar').exists()).toBe(false)
  })

  it('keeps table backgrounds theme-token driven', () => {
    expect(componentSource).toContain('--orin-data-table-surface: var(--orin-surface, var(--el-bg-color))')
    expect(componentSource).not.toMatch(/(?:background|--el-table-[\w-]*bg-color):\s*(?:#[\da-f]{3,8}|rgba?\()/i)
  })
})
