import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UserManagement from '@/views/System/UserManagement.vue'
import userManagementSource from '@/views/System/UserManagement.vue?raw'

const mocks = vi.hoisted(() => ({
  getUserList: vi.fn(),
  getRoles: vi.fn(),
  getDepartmentList: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  toggleUserStatus: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  confirm: vi.fn()
}))

vi.mock('@/api/userManage', () => ({
  getUserList: mocks.getUserList,
  getRoles: mocks.getRoles,
  createUser: mocks.createUser,
  updateUser: mocks.updateUser,
  deleteUser: mocks.deleteUser,
  toggleUserStatus: mocks.toggleUserStatus
}))

vi.mock('@/api/department', () => ({
  getDepartmentList: mocks.getDepartmentList
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: {
      success: (...args) => mocks.messageSuccess(...args),
      error: (...args) => mocks.messageError(...args)
    },
    ElMessageBox: {
      confirm: (...args) => mocks.confirm(...args)
    }
  }
})

const users = [
  {
    id: 1,
    username: 'alice',
    email: 'alice@orin.test',
    role: 'ROLE_ADMIN',
    departmentId: 7,
    status: 'ENABLED',
    createTime: '2026-07-01T09:00:00Z',
    lastLoginTime: '2026-07-15T09:00:00Z'
  },
  {
    id: 2,
    username: 'bob',
    email: 'bob@orin.test',
    role: 'ROLE_USER',
    departmentId: null,
    status: 'active'
  },
  {
    id: 3,
    username: 'carol',
    email: 'carol@orin.test',
    role: 'ROLE_USER',
    departmentId: null,
    status: 'disabled'
  }
]

const stubs = {
  OrinPageShell: {
    props: ['title', 'description'],
    template: `
      <header class="page-shell-stub">
        <h1>{{ title }}</h1>
        <p>{{ description }}</p>
        <slot name="actions" />
        <slot name="filters" />
      </header>
    `
  },
  OrinDataTable: {
    template: '<section class="data-table-stub"><slot /><footer><slot name="footer" /></footer></section>'
  },
  OrinAsyncState: {
    template: '<div class="async-state-stub"><slot /></div>'
  },
  ElInput: {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'input'],
    template: `
      <input
        :value="modelValue"
        :placeholder="placeholder"
        @input="$emit('update:modelValue', $event.target.value); $emit('input', $event.target.value)"
      >
    `
  },
  ElSelect: {
    props: ['modelValue', 'placeholder'],
    emits: ['update:modelValue', 'change'],
    template: `
      <select
        :value="modelValue"
        :aria-label="placeholder"
        @change="$emit('update:modelValue', $event.target.value); $emit('change', $event.target.value)"
      >
        <option value="">全部</option>
        <slot />
      </select>
    `
  },
  ElOption: {
    props: ['label', 'value'],
    template: '<option :value="value">{{ label }}</option>'
  },
  ElSegmented: {
    props: ['modelValue', 'options'],
    emits: ['update:modelValue', 'change'],
    template: `
      <div class="segmented-stub">
        <button
          v-for="option in options"
          :key="option.value"
          :data-status="option.value"
          @click="$emit('update:modelValue', option.value); $emit('change', option.value)"
        >
          {{ option.label }}
        </button>
      </div>
    `
  },
  ElButton: {
    template: '<button @click="$emit(\'click\')"><slot /></button>'
  },
  ElIcon: { template: '<span><slot /></span>' },
  ElTable: {
    props: ['data'],
    emits: ['row-click'],
    template: `
      <div class="table-stub">
        <button
          v-for="row in data"
          :key="row.id"
          class="user-row"
          @click="$emit('row-click', row)"
        >
          {{ row.username }}
        </button>
        <slot />
      </div>
    `
  },
  ElTableColumn: true,
  ElPagination: {
    props: ['total', 'currentPage', 'pageSize', 'size'],
    template: '<div class="pagination-stub" :data-total="total" :data-size="size" />'
  },
  ElDrawer: {
    props: ['modelValue', 'title', 'size'],
    template: `
      <aside v-if="modelValue" class="drawer-stub" :data-size="size">
        <h2>{{ title }}</h2>
        <slot />
      </aside>
    `
  },
  ElDialog: {
    props: ['modelValue', 'title', 'width'],
    template: `
      <section v-if="modelValue" class="dialog-stub" :data-width="width">
        <h2>{{ title }}</h2>
        <slot />
        <slot name="footer" />
      </section>
    `
  },
  ElTag: { template: '<span><slot /></span>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElSwitch: true,
  ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
  ElDropdownMenu: { template: '<div><slot /></div>' },
  ElDropdownItem: { template: '<button><slot /></button>' }
}

const createWrapper = () => mount(UserManagement, {
  global: {
    stubs
  }
})

describe('UserManagement collection', () => {
  beforeEach(() => {
    mocks.getUserList.mockReset()
    mocks.getRoles.mockReset()
    mocks.getDepartmentList.mockReset()
    mocks.createUser.mockReset()
    mocks.updateUser.mockReset()
    mocks.deleteUser.mockReset()
    mocks.toggleUserStatus.mockReset()
    mocks.messageSuccess.mockReset()
    mocks.messageError.mockReset()
    mocks.confirm.mockReset()

    mocks.getUserList.mockResolvedValue({ data: users, total: 37 })
    mocks.getDepartmentList.mockResolvedValue({
      data: [{ departmentId: 7, departmentName: '技术部' }]
    })
    mocks.getRoles.mockResolvedValue([
      { id: 'ROLE_ADMIN', name: '管理员' },
      { id: 'ROLE_SUPER_ADMIN', name: '超级管理员' },
      { id: 'ROLE_USER', name: '普通用户' }
    ])
    mocks.confirm.mockResolvedValue('confirm')
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('loads the server collection with the complete filter contract and filtered total', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    expect(mocks.getUserList).toHaveBeenCalledWith({
      page: 0,
      size: 20,
      search: undefined,
      role: undefined,
      departmentId: undefined,
      status: undefined
    })
    expect(wrapper.text()).toContain('全部用户')
    expect(wrapper.text()).toContain('37 个结果')
    expect(wrapper.text()).toContain('共 37 个用户')
    expect(wrapper.find('.pagination-stub').attributes('data-total')).toBe('37')
    expect(wrapper.find('.pagination-stub').attributes('data-size')).toBe('small')
  })

  it('debounces search and reloads when role or status filters change', async () => {
    vi.useFakeTimers()
    const wrapper = createWrapper()
    await flushPromises()
    mocks.getUserList.mockClear()

    await wrapper.find('input[placeholder="搜索用户名 / 邮箱"]').setValue('alice')
    expect(mocks.getUserList).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(300)
    await flushPromises()

    expect(mocks.getUserList).toHaveBeenLastCalledWith(expect.objectContaining({
      page: 0,
      search: 'alice'
    }))

    await wrapper.find('select[aria-label="角色"]').setValue('ROLE_ADMIN')
    await flushPromises()
    expect(mocks.getUserList).toHaveBeenLastCalledWith(expect.objectContaining({
      role: 'ROLE_ADMIN',
      search: 'alice'
    }))

    await wrapper.find('[data-status="active"]').trigger('click')
    await flushPromises()
    expect(mocks.getUserList).toHaveBeenLastCalledWith(expect.objectContaining({
      role: 'ROLE_ADMIN',
      status: 'ENABLED'
    }))
  })

  it('uses the username as drawer title and closes the drawer before editing', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.findAll('.user-row')[0].trigger('click')
    expect(wrapper.find('.drawer-stub h2').text()).toBe('alice')

    const editButton = wrapper.findAll('.drawer-stub button')
      .find(button => button.text().includes('编辑用户'))
    expect(editButton).toBeTruthy()
    await editButton.trigger('click')

    expect(wrapper.find('.drawer-stub').exists()).toBe(false)
    expect(wrapper.find('.dialog-stub h2').text()).toBe('编辑用户')
    expect(wrapper.find('.dialog-stub').attributes('data-width')).toContain('100vw')
  })

  it('keeps historical status values compatible in the detail drawer', async () => {
    const wrapper = createWrapper()
    await flushPromises()

    await wrapper.findAll('.user-row')[1].trigger('click')
    expect(wrapper.find('.drawer-stub').text()).toContain('已启用')

    await wrapper.findAll('.user-row')[2].trigger('click')
    expect(wrapper.find('.drawer-stub').text()).toContain('已禁用')
  })

  it('keeps a single table surface and the compact row action contract', () => {
    expect(userManagementSource.match(/<OrinDataTable/g)).toHaveLength(1)
    expect(userManagementSource).not.toContain('OrinStatusSummary')
    expect(userManagementSource).not.toContain('user-workspace')
    expect(userManagementSource).not.toContain('label="创建时间"')
    expect(userManagementSource).not.toContain(':icon="View"')
    expect(userManagementSource).toContain('<template #footer>')
    expect(userManagementSource).toContain('size="small"')
    expect(userManagementSource).toContain('handleUserCommand(command, row)')
  })
})
