<template>
  <div class="user-management page-container fade-in">
    <OrinPageShell
      domain="系统控制"
      title="用户管理"
      description="维护企业成员、账号状态、部门归属和系统访问角色。"
      icon="User"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleCreate">
          创建用户
        </el-button>
      </template>
      <template #filters>
        <div class="user-workbar">
          <div class="workbar-heading">
            <h2>全部用户</h2>
            <span>{{ totalUsers }} 个结果</span>
          </div>

          <div class="workbar-controls">
            <el-input
              v-model="searchQuery"
              class="workbar-search"
              placeholder="搜索用户名 / 邮箱"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select
              v-model="roleFilter"
              placeholder="角色"
              clearable
              @change="handleFilterChange"
            >
              <el-option
                v-for="role in roleOptions"
                :key="role.value"
                :label="role.label"
                :value="role.value"
              />
            </el-select>
            <el-select
              v-model="departmentFilter"
              placeholder="部门"
              clearable
              filterable
              @change="handleFilterChange"
            >
              <el-option
                v-for="dept in departments"
                :key="dept.departmentId"
                :label="dept.departmentName"
                :value="dept.departmentId"
              />
            </el-select>
            <el-segmented
              v-model="statusFilter"
              class="status-filter"
              :options="statusFilterOptions"
              @change="handleFilterChange"
            />
            <el-button :icon="Refresh" :loading="loading" @click="handleRefresh">
              刷新
            </el-button>
          </div>
        </div>
      </template>
    </OrinPageShell>

    <OrinDataTable compact class="user-collection">
      <OrinAsyncState
        :status="loading ? 'loading' : users.length > 0 ? 'success' : 'empty'"
        empty-text="暂无组织用户"
        empty-action-label="创建用户"
        @retry="loadUsers"
        @empty-action="handleCreate"
      >
        <el-table
          :data="users"
          row-key="id"
          class="user-table"
          empty-text="暂无组织用户"
          @row-click="openUserDetail"
        >
          <el-table-column label="用户" min-width="220" fixed>
            <template #default="{ row }">
              <div class="user-cell">
                <div class="user-avatar">
                  {{ getUserInitial(row) }}
                </div>
                <div class="user-copy">
                  <strong>{{ row.username }}</strong>
                  <span>{{ row.email || '未设置邮箱' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="角色" width="160">
            <template #default="{ row }">
              <el-tag
                size="small"
                effect="light"
                :type="isAdminRole(row.role) ? 'danger' : 'primary'"
              >
                {{ getRoleName(row.role) }}
              </el-tag>
            </template>
          </el-table-column>

          <el-table-column label="部门" min-width="150">
            <template #default="{ row }">
              <span class="muted-text">{{ getDepartmentName(row.departmentId) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <span class="status-pill" :class="row.status === 'active' ? 'active' : 'inactive'">
                <span class="status-dot" />
                {{ row.status === 'active' ? '启用中' : '已禁用' }}
              </span>
            </template>
          </el-table-column>

          <el-table-column label="最后登录" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ formatDate(row.lastLogin) }}</span>
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="154"
            align="right"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="row-actions" @click.stop>
                <el-button
                  link
                  type="primary"
                  :icon="Edit"
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-dropdown
                  trigger="click"
                  @command="(command) => handleUserCommand(command, row)"
                >
                  <el-button
                    link
                    :icon="MoreFilled"
                    aria-label="更多用户操作"
                    @click.stop
                  >
                    更多
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        command="toggle"
                        :icon="row.status === 'active' ? Lock : Unlock"
                      >
                        {{ row.status === 'active' ? '禁用用户' : '启用用户' }}
                      </el-dropdown-item>
                      <el-dropdown-item command="delete" :icon="Delete" divided>
                        删除用户
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </OrinAsyncState>

      <template #footer>
        <div class="table-footer">
          <span>共 {{ totalUsers }} 个用户</span>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="totalUsers"
            layout="sizes, prev, pager, next"
            size="small"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </template>
    </OrinDataTable>

    <el-drawer
      v-model="detailVisible"
      :title="selectedUser?.username || '用户详情'"
      size="min(420px, 92vw)"
      class="user-drawer"
    >
      <template v-if="selectedUser">
        <section class="drawer-profile">
          <div class="drawer-avatar">
            {{ getUserInitial(selectedUser) }}
          </div>
          <div>
            <el-tag size="small" :type="isAdminRole(selectedUser.role) ? 'danger' : 'primary'">
              {{ getRoleName(selectedUser.role) }}
            </el-tag>
            <p>{{ selectedUser.email || '未设置邮箱' }}</p>
          </div>
        </section>

        <dl class="user-detail-list">
          <div>
            <dt>部门</dt>
            <dd>{{ getDepartmentName(selectedUser.departmentId) }}</dd>
          </div>
          <div>
            <dt>账号状态</dt>
            <dd>{{ selectedUser.status === 'active' ? '已启用' : '已禁用' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDate(selectedUser.createdAt) }}</dd>
          </div>
          <div>
            <dt>最后登录</dt>
            <dd>{{ formatDate(selectedUser.lastLogin) }}</dd>
          </div>
        </dl>

        <div class="drawer-actions">
          <el-button @click="detailVisible = false">
            关闭
          </el-button>
          <el-button type="primary" :icon="Edit" @click="handleEdit(selectedUser)">
            编辑用户
          </el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '创建用户'"
      width="min(520px, calc(100vw - 32px))"
      class="custom-dialog"
      align-center
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
        class="custom-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="formData.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="设置初始密码"
            show-password
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="角色" prop="role" class="half-width">
            <el-select v-model="formData.role" placeholder="选择角色" class="full-width">
              <el-option
                v-for="role in roleOptions"
                :key="role.value"
                :label="role.label"
                :value="role.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="部门" prop="departmentId" class="half-width">
            <el-select
              v-model="formData.departmentId"
              placeholder="选择部门"
              clearable
              class="full-width"
            >
              <el-option
                v-for="dept in departments"
                :key="dept.departmentId"
                :label="dept.departmentName"
                :value="dept.departmentId"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="状态" prop="status">
          <div class="status-switch-wrapper">
            <el-switch
              v-model="formData.status"
              active-value="active"
              inactive-value="inactive"
            />
            <span>{{ formData.status === 'active' ? '已启用' : '已禁用' }}</span>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">
            取消
          </el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            确认{{ isEdit ? '更新' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Lock, MoreFilled, Plus, Refresh, Search, Unlock } from '@element-plus/icons-vue'
import { getUserList, getRoles, createUser, updateUser, deleteUser, toggleUserStatus } from '@/api/userManage'
import { getDepartmentList } from '@/api/department'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'

const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const departments = ref([])
const roleOptions = ref([
  { label: '管理员', value: 'ROLE_ADMIN' },
  { label: '普通用户', value: 'ROLE_USER' }
])
const searchQuery = ref('')
const roleFilter = ref('')
const departmentFilter = ref(null)
const statusFilter = ref('all')
const currentPage = ref(1)
const pageSize = ref(20)
const totalUsers = ref(0)

const dialogVisible = ref(false)
const detailVisible = ref(false)
const selectedUser = ref(null)
const isEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null,
  username: '',
  email: '',
  password: '',
  role: 'ROLE_USER',
  status: 'active',
  departmentId: null
})

const statusFilterOptions = [
  { label: '全部', value: 'all' },
  { label: '启用', value: 'active' },
  { label: '禁用', value: 'inactive' }
]

const formRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 个字符', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

const openUserDetail = (row) => {
  selectedUser.value = row
  detailVisible.value = true
}

const getRoleName = (role) => {
  return roleOptions.value.find(item => item.value === role)?.label
    || String(role || '-').replace(/^ROLE_/, '').replaceAll('_', ' ')
}

const isAdminRole = (role) => {
  return ['ROLE_ADMIN', 'ROLE_SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN'].includes(role)
}

const getUserInitial = (user) => {
  const source = user?.username || user?.email || '?'
  return source.charAt(0).toUpperCase()
}

const getDepartmentName = (departmentId) => {
  if (!departmentId) return '-'
  const dept = departments.value.find(d => d.departmentId === departmentId)
  return dept ? dept.departmentName : '-'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const normalizeUserStatus = (status) => {
  const normalized = String(status || '').toUpperCase()
  return normalized === 'ENABLED' || normalized === 'ACTIVE' ? 'active' : 'inactive'
}

const getStatusFilterParam = () => {
  if (statusFilter.value === 'active') return 'ENABLED'
  if (statusFilter.value === 'inactive') return 'DISABLED'
  return undefined
}

const loadUsers = async () => {
  loading.value = true
  try {
    const userRes = await getUserList({
      page: currentPage.value - 1,
      size: pageSize.value,
      search: searchQuery.value.trim() || undefined,
      role: roleFilter.value || undefined,
      departmentId: departmentFilter.value || undefined,
      status: getStatusFilterParam()
    })

    users.value = (userRes.data || []).map(user => ({
      ...user,
      createdAt: user.createTime,
      lastLogin: user.lastLoginTime || user.lastLogin,
      status: normalizeUserStatus(user.status)
    }))
    totalUsers.value = Number(userRes.total ?? users.value.length)
  } catch (error) {
    ElMessage.error('加载用户列表失败')
    console.error(error)
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

const loadDepartments = async () => {
  try {
    const response = await getDepartmentList()
    departments.value = response.data || []
  } catch (error) {
    ElMessage.error('加载部门列表失败')
    console.error(error)
  }
}

const loadRoles = async () => {
  try {
    const response = await getRoles()
    const roles = Array.isArray(response) ? response : (response.data || [])
    if (roles.length) {
      roleOptions.value = roles.map(role => ({
        label: role.name || role.roleName || role.id || role.roleCode,
        value: role.id || role.roleCode
      }))
    }
  } catch (error) {
    console.error(error)
  }
}

let searchTimer = null

const cancelPendingSearch = () => {
  if (searchTimer) {
    window.clearTimeout(searchTimer)
    searchTimer = null
  }
}

const handleSearch = () => {
  currentPage.value = 1
  cancelPendingSearch()
  searchTimer = window.setTimeout(() => {
    searchTimer = null
    loadUsers()
  }, 300)
}

const handleFilterChange = () => {
  currentPage.value = 1
  cancelPendingSearch()
  loadUsers()
}

const handleRefresh = () => {
  cancelPendingSearch()
  loadUsers()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  cancelPendingSearch()
  loadUsers()
}

const handlePageChange = (page) => {
  currentPage.value = page
  cancelPendingSearch()
  loadUsers()
}

const handleCreate = () => {
  isEdit.value = false
  Object.assign(formData, {
    id: null,
    username: '',
    email: '',
    password: '',
    role: 'ROLE_USER',
    status: 'active',
    departmentId: null
  })
  dialogVisible.value = true
}

const handleEdit = (row) => {
  detailVisible.value = false
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    email: row.email,
    password: '',
    role: row.role,
    status: row.status,
    departmentId: row.departmentId
  })
  dialogVisible.value = true
}

const handleUserCommand = (command, row) => {
  if (command === 'toggle') {
    handleToggleStatus(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch (error) {
    return
  }

  submitting.value = true
  try {
    const userData = {
      username: formData.username,
      email: formData.email,
      role: formData.role,
      status: formData.status === 'active' ? 'ENABLED' : 'DISABLED',
      departmentId: formData.departmentId
    }

    if (isEdit.value) {
      await updateUser(formData.id, userData)
      ElMessage.success('用户更新成功')
    } else {
      userData.password = formData.password
      await createUser(userData)
      ElMessage.success('用户创建成功')
    }

    dialogVisible.value = false
    loadUsers()
  } catch (error) {
    ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
  } finally {
    submitting.value = false
  }
}

const handleToggleStatus = async (row) => {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  const action = newStatus === 'active' ? '启用' : '禁用'

  try {
    await ElMessageBox.confirm(
      `确定要${action}用户 ${row.username} 吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: newStatus === 'active' ? 'success' : 'warning'
      }
    )

    await toggleUserStatus(row.id, newStatus === 'active')
    row.status = newStatus
    if (selectedUser.value?.id === row.id) {
      selectedUser.value = row
    }
    ElMessage.success(`${action}成功`)
    await loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${action}失败`)
    }
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 ${row.username} 吗？此操作不可恢复！`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await deleteUser(row.id)
    ElMessage.success('删除成功')
    if (selectedUser.value?.id === row.id) {
      detailVisible.value = false
      selectedUser.value = null
    }
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  Promise.all([loadDepartments(), loadRoles(), loadUsers()])
  window.addEventListener('page-refresh', handleRefresh)
})

onUnmounted(() => {
  cancelPendingSearch()
  window.removeEventListener('page-refresh', handleRefresh)
})
</script>

<style scoped>
.user-management {
  min-height: 100vh;
}

.fade-in {
  animation: fadeIn 0.35s ease-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.user-workbar {
  display: grid;
  grid-template-columns: minmax(120px, auto) minmax(0, 1fr);
  gap: 18px;
  width: 100%;
  align-items: center;
}

.workbar-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  white-space: nowrap;
}

.workbar-heading h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 720;
}

.workbar-heading span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 600;
}

.workbar-controls {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(132px, 0.55fr) minmax(150px, 0.65fr) 174px auto;
  gap: 10px;
  min-width: 0;
  align-items: center;
}

.status-filter {
  width: 174px;
}

.status-filter :deep(.el-segmented__group) {
  width: 100%;
}

.status-filter :deep(.el-segmented__item) {
  min-width: 56px;
  justify-content: center;
}

.user-table {
  width: 100%;
}

:deep(.user-table .el-table__header th) {
  background: var(--el-fill-color-extra-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

:deep(.user-table .el-table__row) {
  cursor: pointer;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.user-avatar,
.drawer-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--el-color-primary-light-6);
  border-radius: 8px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 760;
  flex-shrink: 0;
}

.user-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-copy strong {
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy span,
.muted-text,
.time-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 680;
}

.status-pill.active {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.status-pill.inactive {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: currentColor;
}

.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.drawer-profile {
  display: flex;
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.drawer-avatar {
  width: 52px;
  height: 52px;
  font-size: 20px;
}

.drawer-profile p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.user-detail-list {
  margin: 18px 0 0;
}

.user-detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 13px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.user-detail-list dt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.user-detail-list dd {
  margin: 0;
  color: var(--el-text-color-primary);
  text-align: right;
}

.drawer-actions,
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.drawer-actions {
  margin-top: 20px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.full-width {
  width: 100%;
}

.status-switch-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 32px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

html.dark .user-collection :deep(.data-table-footer),
html.dark .user-collection :deep(.el-card__header) {
  border-color: var(--el-border-color-lighter);
  background: var(--el-bg-color);
}

@media (max-width: 1280px) {
  .user-workbar {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .workbar-controls {
    grid-template-columns: minmax(220px, 1fr) minmax(132px, 0.55fr) minmax(150px, 0.65fr) 174px auto;
  }
}

@media (max-width: 900px) {
  .workbar-controls {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workbar-search {
    grid-column: 1 / -1;
  }

  .status-filter {
    width: 100%;
  }
}

@media (max-width: 600px) {
  .workbar-controls {
    grid-template-columns: 1fr;
  }

  .workbar-search {
    grid-column: auto;
  }

  .table-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .table-footer :deep(.el-pagination) {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .form-row {
    grid-template-columns: 1fr;
  }

  .drawer-actions {
    align-items: stretch;
    flex-direction: column-reverse;
  }
}
</style>
