<template>
  <div class="user-management page-container fade-in">
    <section class="user-shell">
      <header class="user-topbar">
        <div class="topbar-copy">
          <span class="topbar-eyebrow">组织权限</span>
          <h1>用户管理</h1>
          <p>维护企业成员、账号状态、部门归属和系统访问角色。</p>
        </div>
        <div class="topbar-actions">
          <el-button :icon="Refresh" @click="loadUsers">
            刷新
          </el-button>
          <el-button type="primary" :icon="Plus" @click="handleCreate">
            创建用户
          </el-button>
        </div>
      </header>

      <section class="summary-grid">
        <article class="summary-card primary">
          <span>用户总数</span>
          <strong>{{ userStats.total }}</strong>
          <p>当前组织中的全部账号</p>
        </article>
        <article class="summary-card">
          <span>已启用</span>
          <strong>{{ userStats.active }}</strong>
          <p>可正常登录和访问系统</p>
        </article>
        <article class="summary-card">
          <span>已禁用</span>
          <strong>{{ userStats.inactive }}</strong>
          <p>暂时阻止访问的账号</p>
        </article>
        <article class="summary-card">
          <span>权限角色</span>
          <strong>{{ userStats.roles }}</strong>
          <p>当前账号覆盖的角色类型</p>
        </article>
      </section>

      <section class="user-workspace">
        <div class="workspace-head">
          <div>
            <h2>组织用户清单</h2>
            <p>以部门归属、权限角色和账号状态为维护口径。</p>
          </div>
          <div class="workspace-tools">
            <el-input
              v-model="searchQuery"
              placeholder="搜索用户名 / 邮箱"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="filteredUsers"
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

          <el-table-column label="创建时间" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ formatDate(row.createdAt) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="最后登录" width="180">
            <template #default="{ row }">
              <span class="time-text">{{ formatDate(row.lastLogin) }}</span>
            </template>
          </el-table-column>

          <el-table-column
            label="操作"
            width="210"
            align="right"
            fixed="right"
          >
            <template #default="{ row }">
              <div class="action-buttons" @click.stop>
                <el-tooltip content="查看详情" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="View"
                    @click="openUserDetail(row)"
                  />
                </el-tooltip>
                <el-tooltip content="编辑用户" placement="top">
                  <el-button
                    link
                    type="primary"
                    :icon="Edit"
                    @click="handleEdit(row)"
                  />
                </el-tooltip>
                <el-tooltip :content="row.status === 'active' ? '禁用用户' : '启用用户'" placement="top">
                  <el-button
                    link
                    :type="row.status === 'active' ? 'warning' : 'success'"
                    :icon="row.status === 'active' ? Lock : Unlock"
                    @click="handleToggleStatus(row)"
                  />
                </el-tooltip>
                <el-tooltip content="删除用户" placement="top">
                  <el-button
                    link
                    type="danger"
                    :icon="Delete"
                    @click="handleDelete(row)"
                  />
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="table-footer">
          <span>共 {{ totalUsers || filteredUsers.length }} 个用户</span>
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50]"
            :total="totalUsers || filteredUsers.length"
            layout="sizes, prev, pager, next"
            small
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </section>
    </section>

    <el-drawer
      v-model="detailVisible"
      title="用户详情"
      size="420px"
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
            <h2>{{ selectedUser.username }}</h2>
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
      width="520px"
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Lock, Plus, Refresh, Search, Unlock, View } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser, toggleUserStatus } from '@/api/userManage'
import { getDepartmentList } from '@/api/department'

const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const departments = ref([])
const searchQuery = ref('')
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

const roleOptions = [
  { label: '超级管理员', value: 'ROLE_SUPER_ADMIN' },
  { label: '平台管理员', value: 'ROLE_PLATFORM_ADMIN' },
  { label: '业务运营', value: 'ROLE_OPERATOR' },
  { label: '管理员', value: 'ROLE_ADMIN' },
  { label: '普通用户', value: 'ROLE_USER' }
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

const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value

  const query = searchQuery.value.toLowerCase()
  return users.value.filter(user =>
    (user.username || '').toLowerCase().includes(query) ||
    (user.email || '').toLowerCase().includes(query)
  )
})

const userStats = computed(() => ({
  total: totalUsers.value || users.value.length,
  active: users.value.filter(user => user.status === 'active').length,
  inactive: users.value.filter(user => user.status !== 'active').length,
  roles: new Set(users.value.map(user => user.role).filter(Boolean)).size
}))

const openUserDetail = (row) => {
  selectedUser.value = row
  detailVisible.value = true
}

const getRoleName = (role) => {
  const roleMap = {
    ROLE_SUPER_ADMIN: '超级管理员',
    ROLE_PLATFORM_ADMIN: '平台管理员',
    ROLE_OPERATOR: '业务运营',
    ROLE_ADMIN: '管理员',
    ROLE_USER: '普通用户'
  }
  return roleMap[role] || role || '-'
}

const isAdminRole = (role) => {
  return ['ROLE_SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN'].includes(role)
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

const loadUsers = async () => {
  loading.value = true
  try {
    const [userRes, deptRes] = await Promise.all([
      getUserList({
        page: currentPage.value - 1,
        size: pageSize.value,
        search: searchQuery.value
      }),
      getDepartmentList()
    ])

    departments.value = deptRes.data || []
    users.value = (userRes.data || []).map(user => ({
      ...user,
      createdAt: user.createTime,
      lastLogin: user.lastLoginTime || user.lastLogin,
      status: user.status === 'ENABLED' ? 'active' : 'inactive'
    }))
    totalUsers.value = userRes.total || 0
  } catch (error) {
    ElMessage.error('加载用户列表失败')
    console.error(error)
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

const handleSearch = () => {
  currentPage.value = 1
}

const handleSizeChange = (size) => {
  pageSize.value = size
  loadUsers()
}

const handlePageChange = (page) => {
  currentPage.value = page
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
  loadUsers()
  window.addEventListener('page-refresh', loadUsers)
})

onUnmounted(() => {
  window.removeEventListener('page-refresh', loadUsers)
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

.user-shell {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.user-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.topbar-copy {
  min-width: 0;
}

.topbar-eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}

.topbar-copy h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 26px;
  font-weight: 760;
  line-height: 1.2;
}

.topbar-copy p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  padding: 18px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.04);
}

.summary-card.primary {
  border-color: var(--el-color-primary-light-7);
  background: linear-gradient(180deg, var(--el-color-primary-light-9), var(--el-bg-color));
}

.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 700;
}

.summary-card strong {
  display: block;
  margin-top: 10px;
  color: var(--el-text-color-primary);
  font-size: 28px;
  line-height: 1;
}

.summary-card p {
  margin: 10px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.user-workspace {
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.05);
}

.workspace-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.workspace-head h2 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 16px;
  font-weight: 720;
}

.workspace-head p {
  margin: 6px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.workspace-tools {
  width: min(340px, 100%);
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

.action-buttons {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid var(--el-border-color-lighter);
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

.drawer-profile h2 {
  margin: 10px 0 4px;
  color: var(--el-text-color-primary);
  font-size: 20px;
}

.drawer-profile p {
  margin: 0;
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

html.dark .summary-card,
html.dark .user-workspace {
  box-shadow: none;
}

@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .user-topbar,
  .workspace-head,
  .table-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .topbar-actions {
    justify-content: flex-start;
  }

  .summary-grid,
  .form-row {
    grid-template-columns: 1fr;
  }

  .workspace-tools {
    width: 100%;
  }
}
</style>
