import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PageHeader from '@/components/PageHeader.vue'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(__dirname, '../..')

const read = (file) => fs.readFileSync(path.join(root, file), 'utf8')

describe('dashboard visual foundation', () => {
  it('renders the shared page heading, description and metadata slot', () => {
    const wrapper = mount(PageHeader, {
      props: {
        title: '智能体列表',
        description: '管理可用智能体与运行状态'
      },
      slots: {
        'tag-content': '<span data-testid="page-meta">智能体管理</span>'
      }
    })

    expect(wrapper.get('h1').text()).toBe('智能体列表')
    expect(wrapper.get('.header-description').text()).toBe('管理可用智能体与运行状态')
    expect(wrapper.get('[data-testid="page-meta"]').text()).toBe('智能体管理')
  })

  it('isolates the user-approved reference canvases from dashboard overrides', () => {
    const layout = read('src/layout/MainLayout.vue')
    const foundation = read('src/assets/styles/dashboard.css')

    expect(layout).toContain("'ApplicationWorkspace'")
    expect(layout).toContain("'VisualWorkflowCreate'")
    expect(layout).toContain("'VisualWorkflowEdit'")
    expect(layout).toContain("'is-reference-page': isReferenceCanvasRoute")
    expect(foundation).toContain('.dashboard-redesign:not(.is-reference-canvas)')
    expect(foundation).toContain('.content-area:not(.is-reference-page)')
  })

  it('keeps the login and reference page implementations untouched by the shared shell', () => {
    const publicFoundation = read('src/assets/styles/public.css')

    expect(read('src/views/Login.vue')).not.toContain('OrinPageShell')
    expect(read('src/views/Agent/AgentWorkspace.vue')).not.toContain('OrinPageShell')
    expect(read('src/views/Workflow/VisualWorkflowEditor.vue')).not.toContain('OrinPageShell')
    expect(publicFoundation).not.toContain('.login-container')
  })
})
