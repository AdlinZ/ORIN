<template>
  <div class="role-management fade-in">
    <PageHeader
      title="角色管理"
      description="管理系统角色及权限分配"
      icon="UserFilled"
    />

    <div class="premium-card">
      <!-- 工具栏 -->
      <div class="toolbar">
        <div class="left-tools">
          <el-button type="primary" class="create-btn" @click="handleCreate">
            <el-icon class="mr-1">
              <Plus />
            </el-icon>
            创建角色
          </el-button>
        </div>

        <div class="right-tools">
          <el-input
            v-model="searchQuery"
            placeholder="搜索角色名称 / 代码..."
            class="search-input"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button circle class="icon-btn" @click="loadRoles">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>

      <!-- 角色列表 -->
      <el-table
        v-loading="loading"
        border
        :data="filteredRoles"
        style="width: 100%"
        class="premium-table"
        :header-cell-style="{ background: 'transparent', color: 'var(--el-text-color-secondary)' }"
      >
        <el-table-column prop="roleCode" label="角色代码" min-width="150">
          <template #default="{ row }">
            <span :class="['role-code', row.roleCode === 'ROLE_ADMIN' ? 'admin' : '']">
              {{ row.roleCode }}
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="roleName" label="角色名称" min-width="150" />

        <el-table-column prop="description" label="描述" min-width="250">
          <template #default="{ row }">
            <span class="description-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="createTime" label="创建时间" width="180">
          <template #default="{ row }">
            <span class="time-text">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column
          label="操作"
          fixed="right"
          width="150"
          align="center"
        >
          <template #default="{ row }">
            <div class="action-buttons">
              <el-tooltip content="编辑" placement="top" :show-after="500">
                <el-button link class="action-btn edit" @click="handleEdit(row)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>

              <el-tooltip :content="isSystemRole(row.roleCode) ? '系统角色不可删除' : '删除'" placement="top" :show-after="500">
                <el-button
                  link
                  class="action-btn delete"
                  :disabled="isSystemRole(row.roleCode)"
                  @click="handleDelete(row)"
                >
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
          :total="totalRoles"
          layout="total, ->, sizes, prev, pager, next"
          background
          small
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <!-- 创建/编辑角色对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑角色' : '创建角色'"
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
        <el-form-item label="角色代码" prop="roleCode">
          <el-input
            v-model="formData.roleCode"
            placeholder="如: ROLE_CUSTOMER"
            :disabled="isEdit"
          >
            <template #prefix>
              <span class="prefix-text">ROLE_</span>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
        </el-form-item>

        <el-form-item label="描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入角色描述"
          />
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
import { Plus, Search, Edit, Delete, Refresh } from '@element-plus/icons-vue'
import { getRoleList, createRole, updateRole, deleteRole } from '@/api/role'
import PageHeader from '@/components/PageHeader.vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const roles = ref([])
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const totalRoles = ref(0)

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const formData = reactive({
  roleId: null,
  roleCode: '',
  roleName: '',
  description: ''
})

// 表单验证规则
const formRules = {
  roleCode: [
    { required: true, message: '请输入角色代码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]+$/, message: '只能包含大写字母、数字和下划线', trigger: 'blur' }
  ],
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { min: 2, max: 20, message: '角色名称长度在 2 到 20 个字符', trigger: 'blur' }
  ]
}

// 过滤后的角色列表
const filteredRoles = computed(() => {
  if (!searchQuery.value) return roles.value

  const query = searchQuery.value.toLowerCase()
  return roles.value.filter(role =>
    role.roleName.toLowerCase().includes(query) ||
    (role.roleCode && role.roleCode.toLowerCase().includes(query)) ||
    (role.description && role.description.toLowerCase().includes(query))
  )
})

// 判断是否为系统预定义角色
const isSystemRole = (roleCode) => {
  return ['ROLE_ADMIN', 'ROLE_USER'].includes(roleCode)
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

// 加载角色列表
const loadRoles = async () => {
  loading.value = true
  try {
    const res = await getRoleList({
      page: currentPage.value - 1,
      size: pageSize.value,
      search: searchQuery.value
    })

    roles.value = res.data || []
    totalRoles.value = res.total || 0
  } catch (error) {
    ElMessage.error('加载角色列表失败')
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
  loadRoles()
}

const handlePageChange = (page) => {
  currentPage.value = page
  loadRoles()
}

// 创建角色
const handleCreate = () => {
  isEdit.value = false
  Object.assign(formData, {
    roleId: null,
    roleCode: '',
    roleName: '',
    description: ''
  })
  dialogVisible.value = true
}

// 编辑角色
const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(formData, {
    roleId: row.roleId,
    roleCode: row.roleCode.replace('ROLE_', ''),
    roleName: row.roleName,
    description: row.description
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
      const roleData = {
        roleCode: formData.roleCode.toUpperCase(),
        roleName: formData.roleName,
        description: formData.description
      }

      if (isEdit.value) {
        await updateRole(formData.roleId, roleData)
        ElMessage.success('角色更新成功')
      } else {
        await createRole(roleData)
        ElMessage.success('角色创建成功')
      }

      dialogVisible.value = false
      loadRoles()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
}

// 删除角色
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除角色 ${row.roleName} 吗？`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'error'
      }
    )

    await deleteRole(row.roleId)
    ElMessage.success('删除成功')
    loadRoles()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadRoles()
  window.addEventListener('page-refresh', loadRoles)
})

onUnmounted(() => {
  window.removeEventListener('page-refresh', loadRoles)
})
</script>

<style scoped>
.role-management {
  padding: 32px;
  max-width: 1600px;
  margin: 0 auto;
  background: var(--bg-color, #f8fafc);
  min-height: 100vh;
}

.fade-in {
  animation: fadeIn 0.5s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.premium-card {
  background: var(--card-bg, var(--el-bg-color));
  border-radius: 12px;
  border: 1px solid var(--border-color, var(--el-border-color-light));
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

.role-code {
  font-family: monospace;
  padding: 4px 8px;
  border-radius: 4px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.role-code.admin {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.description-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
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

.action-btn.delete:hover:not(:disabled) {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination-wrapper {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

/* Dialog Styles */
.prefix-text {
  font-family: monospace;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Dark Mode Overrides */
html.dark .role-management {
  background: var(--bg-color);
}

html.dark .premium-card {
  background: var(--card-bg);
  border-color: var(--border-color);
}

html.dark .search-input :deep(.el-input__wrapper) {
  background: var(--neutral-gray-100);
  box-shadow: 0 0 0 1px var(--border-color) inset;
}

html.dark .icon-btn {
  background: var(--card-bg);
  border-color: var(--border-color);
}

html.dark .role-code {
  background: rgba(38, 255, 223, 0.1);
  color: var(--orin-primary);
}

html.dark .role-code.admin {
  background: rgba(245, 108, 108, 0.15);
  color: #f56c6c;
}

html.dark .description-text,
html.dark .time-text {
  color: var(--text-secondary);
}

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

html.dark .custom-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
}

html.dark .el-pagination {
  --el-pagination-bg-color: var(--card-bg);
  --el-pagination-text-color: var(--text-secondary);
  --el-pagination-hover-color: var(--orin-primary);
}
</style>