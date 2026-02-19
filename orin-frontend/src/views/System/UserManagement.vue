<template>
  <div class="user-management fade-in">
    <div class="header-section">
      <div class="header-content">
        <h1 class="page-title">用户权限管理</h1>
        <p class="page-subtitle">管理系统用户、角色分配及账号状态</p>
      </div>
    </div>
    
    <div class="premium-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="left-tools">
          <el-button type="primary" class="create-btn" @click="handleCreate">
            <el-icon class="mr-1"><Plus /></el-icon>
            创建用户
          </el-button>
        </div>
        
        <div class="right-tools">
          <el-input
            v-model="searchQuery"
            placeholder="搜索用户名 / 邮箱..."
            class="search-input"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button circle class="icon-btn" @click="loadUsers">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 用户列表 -->
      <el-table
        :data="filteredUsers"
        style="width: 100%"
        v-loading="loading"
        class="premium-table"
        :header-cell-style="{ background: 'transparent', color: 'var(--el-text-color-secondary)' }"
        :row-class-name="tableRowClassName"
      >
        <el-table-column prop="username" label="用户名" min-width="150">
          <template #default="{ row }">
            <div class="user-info">
              <el-avatar :size="32" class="user-avatar" :src="row.avatar">{{ row.username.charAt(0).toUpperCase() }}</el-avatar>
              <span class="username">{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="email" label="邮箱" min-width="200" />
        
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <span :class="['role-badge', row.role === 'ROLE_ADMIN' ? 'role-admin' : 'role-user']">
              {{ getRoleName(row.role) }}
            </span>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <div class="status-indicator">
              <span :class="['status-dot', row.status === 'active' ? 'active' : 'inactive']"></span>
              {{ row.status === 'active' ? '激活' : '禁用' }}
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        
        <el-table-column prop="lastLogin" label="最后登录" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.lastLogin) }}</span>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" fixed="right" width="180" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip content="编辑" placement="top" :show-after="500">
                <el-button link class="action-btn edit" @click="handleEdit(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              
              <el-tooltip :content="row.status === 'active' ? '禁用账号' : '启用账号'" placement="top" :show-after="500">
                <el-button 
                  link 
                  :class="['action-btn', row.status === 'active' ? 'warning' : 'success']"
                  @click="handleToggleStatus(row)"
                >
                  <el-icon v-if="row.status === 'active'"><Lock /></el-icon>
                  <el-icon v-else><Unlock /></el-icon>
                </el-button>
              </el-tooltip>
              
              <el-tooltip content="删除" placement="top" :show-after="500">
                <el-button link class="action-btn delete" @click="handleDelete(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="totalUsers"
          layout="total, ->, sizes, prev, pager, next"
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
          background
          small
        />
      </div>
    </div>

    <!-- 创建/编辑用户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '创建用户'"
      width="480px"
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
        
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input 
            v-model="formData.password" 
            type="password" 
            placeholder="设置初始密码"
            show-password
          />
        </el-form-item>
        
        <div class="form-row">
          <el-form-item label="角色" prop="role" class="half-width">
            <el-select v-model="formData.role" placeholder="选择角色">
              <el-option label="管理员" value="ROLE_ADMIN" />
              <el-option label="普通用户" value="ROLE_USER" />
            </el-select>
          </el-form-item>
          
          <el-form-item label="状态" prop="status" class="half-width">
            <div class="status-switch-wrapper">
               <el-switch
                v-model="formData.status"
                active-value="active"
                inactive-value="inactive"
                style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              />
              <span class="status-text">{{ formData.status === 'active' ? '已激活' : '已禁用' }}</span>
            </div>
          </el-form-item>
        </div>
      </el-form>
      
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            确认{{ isEdit ? '更新' : '创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Edit, Delete, Lock, Unlock, Refresh } from '@element-plus/icons-vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const users = ref([])
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalUsers = ref(0)

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  id: null,
  username: '',
  email: '',
  password: '',
  role: 'ROLE_USER',
  status: 'active'
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

// 过滤后的用户列表
const filteredUsers = computed(() => {
  if (!searchQuery.value) return users.value
  
  const query = searchQuery.value.toLowerCase()
  return users.value.filter(user => 
    user.username.toLowerCase().includes(query) ||
    user.email.toLowerCase().includes(query)
  )
})

// 获取角色名称
const getRoleName = (role) => {
  const roleMap = {
    'ROLE_ADMIN': '管理员',
    'ROLE_USER': '用户'
  }
  return roleMap[role] || role
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
    // 模拟数据延迟
    await new Promise(resolve => setTimeout(resolve, 500))
    
    // 模拟数据
    users.value = [
      {
        id: 1,
        username: 'admin',
        email: 'admin@orin.com',
        role: 'ROLE_ADMIN',
        status: 'active',
        createdAt: '2024-01-01T10:00:00',
        lastLogin: '2024-02-16T16:00:00'
      },
      {
        id: 2,
        username: 'user1',
        email: 'user1@orin.com',
        role: 'ROLE_USER',
        status: 'active',
        createdAt: '2024-01-15T10:00:00',
        lastLogin: '2024-02-15T14:30:00'
      }
    ]
    totalUsers.value = users.value.length
  } catch (error) {
    ElMessage.error('加载用户列表失败')
    console.error(error)
  } finally {
    loading.value = false
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
    status: 'active'
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
    status: row.status
  })
  dialogVisible.value = true
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitting.value = true
    try {
      // 模拟API调用
      await new Promise(resolve => setTimeout(resolve, 800))
      
      if (isEdit.value) {
        ElMessage.success('用户更新成功')
      } else {
        ElMessage.success('用户创建成功')
      }
      
      dialogVisible.value = false
      loadUsers()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
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
    
    // 模拟API
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
    
    ElMessage.success('删除成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const tableRowClassName = ({ rowIndex }) => {
  // if (rowIndex === 1) {
  //   return 'warning-row'
  // }
  return ''
}

onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.user-management {
  padding: 32px;
  max-width: 1600px;
  margin: 0 auto;
}

.fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.header-section {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.premium-card {
  background: var(--el-bg-color);
  border-radius: 12px;
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  padding: 24px;
  transition: all 0.3s ease;
}

.premium-card:hover {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.025);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.create-btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 8px;
  font-weight: 500;
  transition: transform 0.2s;
}

.create-btn:active {
  transform: scale(0.98);
}

.right-tools {
  display: flex;
  gap: 12px;
  align-items: center;
}

.search-input {
  width: 260px;
}

:deep(.search-input .el-input__wrapper) {
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--el-border-color-lighter) inset;
}

:deep(.search-input .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--el-color-primary) inset;
}

.icon-btn {
  border: 1px solid var(--el-border-color-lighter);
  background: transparent;
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
  background-color: #13ce66;
  box-shadow: 0 0 0 2px rgba(19, 206, 102, 0.2);
}

.status-dot.inactive {
  background-color: #909399;
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
  width: 32px;
  height: 32px;
  border-radius: 6px;
  padding: 0;
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

.pagination-wrapper {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
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
html.dark .premium-card {
  background: rgba(30, 30, 30, 0.6);
  backdrop-filter: blur(10px);
  border-color: rgba(255, 255, 255, 0.08);
}

html.dark .page-title {
  color: #e5eaf3;
}

html.dark .search-input :deep(.el-input__wrapper) {
  background: rgba(0, 0, 0, 0.2);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.1) inset;
}

html.dark .role-admin {
  background: rgba(245, 108, 108, 0.15);
  border-color: rgba(245, 108, 108, 0.2);
}

html.dark .role-user {
  background: rgba(64, 158, 255, 0.15);
  border-color: rgba(64, 158, 255, 0.2);
}
</style>
