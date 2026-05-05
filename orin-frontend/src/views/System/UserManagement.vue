<template>
  <div class="user-management fade-in">
    <OrinEntityHeader
      domain="组织权限"
      title="用户管理"
      description="管理企业成员、权限角色、账号状态与组织范围"
      :summary="userHeaderSummary"
    >
      <template #actions>
        <a-button type="primary" class="create-btn" @click="handleCreate">
          创建用户
        </a-button>
      </template>

      <template #filters>
        <div class="user-header-filters">
          <a-input-search
            v-model="searchQuery"
            placeholder="搜索用户名 / 邮箱..."
            class="search-input"
            allow-clear
            @input="handleSearch"
          />
          <a-button class="icon-btn" @click="loadUsers">
            刷新
          </a-button>
        </div>
      </template>
    </OrinEntityHeader>

    <OrinArcoDataTable
      :columns="userColumns"
      :data="filteredUsers"
      :loading="loading"
      row-key="id"
      @row-click="openUserDetail"
    >
      <template #header>
        <div class="table-title">
          <strong>组织用户清单</strong>
          <span>以权限和账号状态为核心维护口径</span>
        </div>
      </template>

      <template #username="{ record }">
        <div class="user-info">
          <a-avatar :size="32" class="user-avatar">
            {{ record.username.charAt(0).toUpperCase() }}
          </a-avatar>
          <span class="username">{{ record.username }}</span>
        </div>
      </template>

      <template #role="{ record }">
        <span :class="['role-badge', getRoleBadgeClass(record.role)]">
          {{ getRoleName(record.role) }}
        </span>
      </template>

      <template #department="{ record }">
        <span class="department-text">{{ getDepartmentName(record.departmentId) }}</span>
      </template>

      <template #status="{ record }">
        <div class="status-indicator">
          <span :class="['status-dot', record.status === 'active' ? 'active' : 'inactive']" />
          {{ record.status === 'active' ? '激活' : '禁用' }}
        </div>
      </template>

      <template #createdAt="{ record }">
        <span class="time-text">{{ formatDate(record.createdAt) }}</span>
      </template>

      <template #lastLogin="{ record }">
        <span class="time-text">{{ formatDate(record.lastLogin) }}</span>
      </template>

      <template #actions="{ record }">
        <div class="action-buttons" @click.stop>
          <a-button type="text" size="mini" @click="openUserDetail(record)">详情</a-button>
          <a-button type="text" size="mini" @click="handleEdit(record)">编辑</a-button>
          <a-button
            type="text"
            size="mini"
            :status="record.status === 'active' ? 'warning' : 'success'"
            @click="handleToggleStatus(record)"
          >
            {{ record.status === 'active' ? '禁用' : '启用' }}
          </a-button>
          <a-button type="text" size="mini" status="danger" @click="handleDelete(record)">删除</a-button>
        </div>
      </template>

      <template #empty>
        <OrinEmptyState
          description="暂无组织用户，请创建用户或调整搜索条件"
          action-label="创建用户"
          @action="handleCreate"
        />
      </template>

      <template #footer>
        <a-pagination
          v-model:current="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="totalUsers"
          show-total
          show-page-size
          size="small"
          @page-size-change="handleSizeChange"
          @change="handlePageChange"
        />
      </template>
    </OrinArcoDataTable>

    <OrinArcoDetailDrawer
      v-model="detailVisible"
      title="用户详情"
      :width="420"
    >
      <OrinDetailPanel
        v-if="selectedUser"
        :title="selectedUser.username"
        :eyebrow="getRoleName(selectedUser.role)"
      >
        <dl class="user-detail-list">
          <div>
            <dt>邮箱</dt>
            <dd>{{ selectedUser.email || '-' }}</dd>
          </div>
          <div>
            <dt>部门</dt>
            <dd>{{ getDepartmentName(selectedUser.departmentId) }}</dd>
          </div>
          <div>
            <dt>账号状态</dt>
            <dd>{{ selectedUser.status === 'active' ? '已激活' : '已禁用' }}</dd>
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
          <a-button @click="detailVisible = false">关闭</a-button>
          <a-button type="primary" @click="handleEdit(selectedUser)">编辑用户</a-button>
        </div>
      </OrinDetailPanel>
    </OrinArcoDetailDrawer>

    <!-- 创建/编辑用户对话框 -->
    <OrinArcoFormDialog
      ref="formRef"
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '创建用户'"
      :width="480"
      :model="formData"
      :rules="formRules"
    >
      <a-form-item label="用户名" field="username">
        <a-input v-model="formData.username" placeholder="请输入用户名" />
      </a-form-item>

      <a-form-item label="邮箱" field="email">
        <a-input v-model="formData.email" placeholder="请输入邮箱" />
      </a-form-item>

      <a-form-item v-if="!isEdit" label="密码" field="password">
        <a-input-password v-model="formData.password" placeholder="设置初始密码" />
      </a-form-item>

      <div class="form-row">
        <a-form-item label="角色" field="role" class="half-width">
          <a-select v-model="formData.role" placeholder="选择角色">
            <a-option label="超级管理员" value="ROLE_SUPER_ADMIN" />
            <a-option label="平台管理员" value="ROLE_PLATFORM_ADMIN" />
            <a-option label="业务运营" value="ROLE_OPERATOR" />
            <a-option label="管理员" value="ROLE_ADMIN" />
            <a-option label="普通用户" value="ROLE_USER" />
          </a-select>
        </a-form-item>

        <a-form-item label="部门" field="departmentId" class="half-width">
          <a-select v-model="formData.departmentId" placeholder="选择部门" allow-clear>
            <a-option
              v-for="dept in departments"
              :key="dept.departmentId"
              :label="dept.departmentName"
              :value="dept.departmentId"
            />
          </a-select>
        </a-form-item>
      </div>

      <a-form-item label="状态" field="status">
        <div class="status-switch-wrapper">
          <a-switch
            v-model="formData.status"
            checked-value="active"
            unchecked-value="inactive"
          />
          <span class="status-text">{{ formData.status === 'active' ? '已激活' : '已禁用' }}</span>
        </div>
      </a-form-item>

      <template #footer>
        <div class="dialog-footer">
          <a-button @click="dialogVisible = false">
            取消
          </a-button>
          <a-button type="primary" :loading="submitting" @click="handleSubmit">
            确认{{ isEdit ? '更新' : '创建' }}
          </a-button>
        </div>
      </template>
    </OrinArcoFormDialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, createUser, updateUser, deleteUser, toggleUserStatus, getRoles } from '@/api/userManage'
import { getDepartmentList } from '@/api/department'
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue'
import OrinEmptyState from '@/components/orin/OrinEmptyState.vue'
import OrinDetailPanel from '@/components/orin/OrinDetailPanel.vue'
import OrinArcoDataTable from '@/ui/arco/OrinArcoDataTable.vue'
import OrinArcoDetailDrawer from '@/ui/arco/OrinArcoDetailDrawer.vue'
import OrinArcoFormDialog from '@/ui/arco/OrinArcoFormDialog.vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const departments = ref([])
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalUsers = ref(0)

// 对话框状态
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

// 表单验证规则
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

const userColumns = [
  { title: '用户名', dataIndex: 'username', minWidth: 170, slotName: 'username', fixed: 'left' },
  { title: '邮箱', dataIndex: 'email', minWidth: 220 },
  { title: '角色', dataIndex: 'role', width: 150, slotName: 'role' },
  { title: '部门', dataIndex: 'departmentId', width: 160, slotName: 'department' },
  { title: '状态', dataIndex: 'status', width: 120, slotName: 'status' },
  { title: '创建时间', dataIndex: 'createdAt', width: 180, slotName: 'createdAt' },
  { title: '最后登录', dataIndex: 'lastLogin', width: 180, slotName: 'lastLogin' },
  { title: '操作', dataIndex: 'actions', width: 220, align: 'center', fixed: 'right', slotName: 'actions' }
]

// 过滤后的用户列表
const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value

  const query = searchQuery.value.toLowerCase()
  return users.value.filter(user =>
    user.username.toLowerCase().includes(query) ||
    user.email.toLowerCase().includes(query)
  )
})

const userHeaderSummary = computed(() => [
  {
    label: '用户总数',
    value: totalUsers.value || users.value.length
  },
  {
    label: '已启用',
    value: users.value.filter(user => user.status === 'active').length
  },
  {
    label: '禁用',
    value: users.value.filter(user => user.status !== 'active').length
  },
  {
    label: '权限角色',
    value: new Set(users.value.map(user => user.role).filter(Boolean)).size
  }
])

const openUserDetail = (row) => {
  selectedUser.value = row
  detailVisible.value = true
}

// 获取角色名称
const getRoleName = (role) => {
  const roleMap = {
    'ROLE_SUPER_ADMIN': '超级管理员',
    'ROLE_PLATFORM_ADMIN': '平台管理员',
    'ROLE_OPERATOR': '业务运营',
    'ROLE_ADMIN': '管理员',
    'ROLE_USER': '普通用户'
  }
  return roleMap[role] || role
}

const getRoleBadgeClass = (role) => {
  if (['ROLE_SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'ROLE_ADMIN'].includes(role)) {
    return 'role-admin'
  }
  return 'role-user'
}

// 获取部门名称
const getDepartmentName = (departmentId) => {
  if (!departmentId) return '-'
  const dept = departments.value.find(d => d.departmentId === departmentId)
  return dept ? dept.departmentName : '-'
}

// 格式化日期
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

// 加载用户列表
const loadUsers = async () => {
  loading.value = true
  try {
    // 加载用户列表和部门列表
    const [userRes, deptRes] = await Promise.all([
      getUserList({
        page: currentPage.value - 1,
        size: pageSize.value,
        search: searchQuery.value
      }),
      getDepartmentList()
    ])

    // 设置部门列表
    departments.value = deptRes.data || []

    // 映射后端字段到前端字段
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

// 搜索处理
const handleSearch = () => {
  currentPage.value = 1
}

// 分页处理
const handleSizeChange = (size) => {
  pageSize.value = size
  loadUsers()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadUsers()
}

// 创建用户
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

// 编辑用户
const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, {
    id: row.id,
    username: row.username,
    email: row.email,
    password: '', // 编辑时不显示密码
    role: row.role,
    status: row.status,
    departmentId: row.departmentId
  })
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  const errors = await formRef.value.validate()
  if (errors) return

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

// 切换用户状态
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
    ElMessage.success(`${action}成功`)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(`${action}失败`)
    }
  }
}

// 删除用户
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
  padding: 32px;
  max-width: 1600px;
  margin: 0 auto;
  background: #ffffff;
  min-height: 100vh;
}

.fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.user-header-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.create-btn {
  height: 32px;
  padding: 0 14px;
  border-radius: 5px;
  font-weight: 600;
  transition: transform 0.15s;
}

.create-btn:active {
  transform: scale(0.98);
}

.search-input {
  width: 260px;
}

.table-title {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.table-title strong {
  color: #111827;
  font-size: 14px;
  font-weight: 700;
}

.table-title span {
  color: #64748b;
  font-size: 12px;
}

:deep(.search-input .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
}

:deep(.search-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.icon-btn {
  border: 1px solid var(--border-color, var(--el-border-color-lighter));
  background: var(--card-bg, transparent);
}

.icon-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
}

/* Table Styles */
.premium-table {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table th.el-table__cell) {
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

:deep(.el-table tr) {
  transition: background-color 0.2s;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  background: var(--el-color-primary-light-8);
  color: var(--el-color-primary);
  font-weight: 600;
  border: 1px solid var(--el-color-primary-light-5);
}

.username {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.role-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.role-admin {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  border: 1px solid var(--el-color-danger-light-8);
}

.role-user {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  border: 1px solid var(--el-color-primary-light-8);
}

.department-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.active {
  background-color: var(--success-500);
  box-shadow: 0 0 0 2px var(--success-light);
}

html.dark .status-dot.active {
  box-shadow: 0 0 0 2px rgba(19, 206, 102, 0.3);
}

.status-dot.inactive {
  background-color: var(--neutral-gray-400);
  opacity: 0.6;
}

.time-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.action-btn {
  min-width: 32px;
  height: 32px;
  border-radius: 6px;
  padding: 0 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.action-btn.edit:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.action-btn.warning:hover {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning);
}

.action-btn.success:hover {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.action-btn.delete:hover {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.user-detail-list {
  margin: 0;
  padding: 16px;
}

.user-detail-list div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--orin-border-strong, #d8e0e8);
}

.user-detail-list dt {
  color: var(--text-secondary, #64748b);
  font-size: 12px;
  font-weight: 700;
}

.user-detail-list dd {
  margin: 0;
  color: var(--text-primary, #1e293b);
  text-align: right;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid var(--orin-border-strong, #d8e0e8);
}

/* Dialog Styles */
.form-row {
  display: flex;
  gap: 20px;
}

.half-width {
  flex: 1;
}

.status-switch-wrapper {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 32px;
}

.status-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Dark Mode Overrides */
html.dark .user-management {
  background: var(--bg-color);
}

html.dark .search-input :deep(.el-input__wrapper) {
  background: var(--neutral-gray-100);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}

html.dark .icon-btn {
  background: var(--card-bg);
  border-color: var(--border-color);
}

html.dark .icon-btn:hover {
  background: var(--neutral-gray-200);
}

html.dark .role-admin {
  background: var(--error-light);
  border-color: var(--error-100);
  color: var(--error-500);
}

html.dark .role-user {
  background: var(--primary-light);
  border-color: var(--primary-100);
  color: var(--orin-primary);
}

html.dark .status-text {
  color: var(--text-secondary);
}

html.dark .time-text {
  color: var(--text-secondary);
}

html.dark .username {
  color: var(--text-primary);
}

/* Dialog dark mode */
html.dark .custom-dialog :deep(.el-dialog) {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
}

html.dark .custom-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid var(--border-color);
}

html.dark .custom-dialog :deep(.el-dialog__title) {
  color: var(--text-primary);
}

html.dark .custom-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid var(--border-color);
}

html.dark .custom-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
}

html.dark .custom-form :deep(.el-input__wrapper) {
  background: var(--neutral-gray-100);
}

html.dark .custom-form :deep(.el-select .el-input__wrapper) {
  background: var(--neutral-gray-100);
}

/* Pagination dark mode */
html.dark .el-pagination {
  --el-pagination-bg-color: var(--card-bg);
  --el-pagination-text-color: var(--text-secondary);
  --el-pagination-button-bg-color: var(--card-bg);
  --el-pagination-button-color: var(--text-secondary);
  --el-pagination-hover-color: var(--orin-primary);
}

html.dark .el-pagination.is-background .el-pager li:not(.is-disabled).is-active {
  background-color: var(--orin-primary);
  color: #041010;
}

html.dark .el-pagination.is-background .el-pager li:not(.is-disabled):hover {
  color: var(--orin-primary);
}

html.dark .el-pagination.is-background .btn-prev,
html.dark .el-pagination.is-background .btn-next {
  background-color: var(--card-bg);
  color: var(--text-secondary);
}

html.dark .el-pagination.is-background .btn-prev:hover,
html.dark .el-pagination.is-background .btn-next:hover {
  color: var(--orin-primary);
}

html.dark .el-pagination__total {
  color: var(--text-secondary);
}

html.dark .el-pagination .el-select__wrapper {
  background: var(--card-bg);
}
</style>
