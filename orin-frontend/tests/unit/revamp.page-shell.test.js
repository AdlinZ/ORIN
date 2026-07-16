import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'

const global = {
  stubs: {
    ElIcon: {
      template: '<i class="el-icon-stub"><slot /></i>'
    },
    ElTag: {
      props: ['type', 'effect', 'size', 'round'],
      template: '<span class="el-tag-stub" :data-type="type"><slot /></span>'
    }
  }
}

const mountHeader = ({ props = {}, slots = {} } = {}) => mount(PageHeader, {
  props: { title: '测试标题', ...props },
  slots,
  global
})

const mountShell = ({ props = {}, slots = {} } = {}) => mount(OrinPageShell, {
  props: {
    title: '测试标题',
    description: '测试描述',
    ...props
  },
  slots,
  global
})

describe('Orin page shell', () => {
  it.each([
    ['legacy', 'is-legacy'],
    ['plain', 'is-plain']
  ])('maps the %s variant to the page header', (variant, className) => {
    expect(mountHeader({ props: { variant } }).classes()).toContain(className)
  })

  it('forwards variant and flat without leaking them onto the shell root', () => {
    const wrapper = mountShell({ props: { variant: 'plain', flat: true } })
    const header = wrapper.getComponent(PageHeader)

    expect(header.props('variant')).toBe('plain')
    expect(header.props('flat')).toBe(true)
    expect(wrapper.attributes('variant')).toBeUndefined()
    expect(wrapper.attributes('flat')).toBeUndefined()
  })

  it('does not synthesize empty action or filter rows', () => {
    const wrapper = mountShell()

    expect(wrapper.find('.header-actions').exists()).toBe(false)
    expect(wrapper.find('.header-filters').exists()).toBe(false)
  })

  it('renders supplied actions and filters once', () => {
    const wrapper = mountShell({
      slots: {
        actions: '<button data-test="action">保存</button>',
        filters: '<input data-test="filter">'
      }
    })

    expect(wrapper.get('[data-test="action"]').text()).toBe('保存')
    expect(wrapper.get('[data-test="filter"]').exists()).toBe(true)
    expect(wrapper.findAll('.header-actions')).toHaveLength(1)
    expect(wrapper.findAll('.header-filters')).toHaveLength(1)
  })

  it('renders metadata only when it is explicitly enabled', () => {
    const hidden = mountShell({ props: { domain: '组织权限', maturity: 'beta' } })
    const visible = mountShell({
      props: {
        domain: '组织权限',
        maturity: 'beta',
        showMeta: true
      }
    })

    expect(hidden.text()).not.toContain('组织权限')
    expect(hidden.text()).not.toContain('试运行')
    expect(visible.text()).toContain('组织权限')
    expect(visible.text()).toContain('试运行')
  })

  it('can suppress the standard header while preserving page content', () => {
    const wrapper = mountShell({
      props: { variant: 'none' },
      slots: { default: '<main data-test="content">页面内容</main>' }
    })

    expect(wrapper.findComponent(PageHeader).exists()).toBe(false)
    expect(wrapper.get('[data-test="content"]').text()).toBe('页面内容')
  })
})
