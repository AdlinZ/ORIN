<template>
  <div class="department-management fade-in">
    <OrinPageShell
      title="部门管理"
      description="维护组织架构、部门负责人和上下级关系。"
      :icon="OfficeBuilding"
    >
      <template #actions>
        <el-button type="primary" :icon="Plus" @click="handleCreateRoot">
          创建顶级部门
        </el-button>
      </template>
      <template #filters>
        <div class="gallery-toolbar">
          <div class="gallery-heading">
            <h2>全部部门</h2>
            <span>{{ filteredDepartments.length }} 个结果</span>
          </div>

          <div class="gallery-controls">
            <OrinFilterBar class="department-search">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索部门名称/编码"
                clearable
                aria-label="搜索部门名称或编码"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </OrinFilterBar>

            <div class="gallery-stats" aria-label="组织统计">
              <span><strong>{{ stats.root }}</strong> 顶级</span>
              <span :class="{ warning: stats.disabled > 0 }"><strong>{{ stats.disabled }}</strong> 已禁用</span>
            </div>

            <el-button
              class="department-refresh"
              :icon="Refresh"
              @click="loadDepartments"
            >
              刷新
            </el-button>
          </div>
        </div>
      </template>
    </OrinPageShell>

    <section class="department-shell">
      <section class="department-gallery">
        <OrinAsyncState
          :status="loading ? 'loading' : filteredDepartments.length > 0 ? 'success' : 'empty'"
          empty-text="暂无部门数据"
          empty-action-label="创建第一个部门"
          @retry="loadDepartments"
          @empty-action="handleCreateRoot"
        >
          <div class="department-grid">
            <article
              v-for="department in filteredDepartments"
              :key="department.departmentId"
              class="department-card"
            >
              <header class="department-card-head">
                <div class="department-card-title">
                  <span class="department-icon"><el-icon><OfficeBuilding /></el-icon></span>
                  <div>
                    <h3>{{ department.departmentName }}</h3>
                    <code>{{ department.departmentCode }}</code>
                  </div>
                </div>
                <el-tag size="small" :type="department.status === 'ENABLED' ? 'success' : 'danger'" effect="plain">
                  {{ department.status === 'ENABLED' ? '启用' : '禁用' }}
                </el-tag>
              </header>

              <p class="department-description" :class="{ muted: !department.description }">
                {{ department.description || '暂未填写部门描述' }}
              </p>

              <dl class="department-meta">
                <div><dt>直属上级</dt><dd>{{ getParentName(department.parentId) }}</dd></div>
                <div><dt>负责人</dt><dd>{{ department.leader || '暂未设置' }}</dd></div>
                <div><dt>下级部门</dt><dd>{{ department.children?.length || 0 }} 个</dd></div>
              </dl>

              <footer class="department-card-actions">
                <el-button
                  text
                  type="primary"
                  :icon="Plus"
                  @click="handleAddChild(department)"
                >
                  新增下级
                </el-button>
                <div>
                  <el-button
                    circle
                    text
                    :icon="Edit"
                    aria-label="编辑部门"
                    @click="handleEdit(department)"
                  />
                  <el-button
                    circle
                    text
                    class="delete-action"
                    :icon="Delete"
                    aria-label="删除部门"
                    :disabled="department.children && department.children.length > 0"
                    @click="handleDelete(department)"
                  />
                </div>
              </footer>
            </article>
          </div>
        </OrinAsyncState>
      </section>
    </section>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑部门' : (isRoot ? '创建顶级部门' : '创建子部门')"
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
        <el-form-item v-if="!isRoot && !isEdit" label="上级部门">
          <el-input :value="parentDepartmentName" disabled />
        </el-form-item>

        <el-form-item label="部门名称" prop="departmentName">
          <el-input v-model="formData.departmentName" placeholder="请输入部门名称" />
        </el-form-item>

        <el-form-item label="部门编码" prop="departmentCode">
          <el-input
            v-model="formData.departmentCode"
            placeholder="如: DEPT_001"
            :disabled="isEdit"
            @input="handleCodeInput"
          >
            <template #prefix>
              <el-tag size="small" type="info">
                自动转大写
              </el-tag>
            </template>
          </el-input>
        </el-form-item>

        <div class="form-row">
          <el-form-item label="排序号" prop="orderNum" class="half-width">
            <el-input-number v-model="formData.orderNum" :min="0" :max="999" />
          </el-form-item>

          <el-form-item label="状态" prop="status" class="half-width">
            <el-select v-model="formData.status" placeholder="选择状态">
              <el-option label="启用" value="ENABLED" />
              <el-option label="禁用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="部门负责人" prop="leader">
          <el-select
            v-model="formData.leader"
            placeholder="请选择负责人"
            clearable
            filterable
            :loading="userLoading"
            class="full-width"
          >
            <el-option
              v-for="user in userList"
              :key="user.userId"
              :label="user.nickname || user.username"
              :value="user.nickname || user.username"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系电话" />
        </el-form-item>

        <el-form-item label="部门描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入部门描述"
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
import { Plus, Edit, Delete, Search, Refresh, OfficeBuilding } from '@element-plus/icons-vue'
import { getDepartmentList, getAllDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'
import { getUserList } from '@/api/userManage'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinFilterBar from '@/components/orin/OrinFilterBar.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const treeData = ref([])
const flatData = ref([])
const searchKeyword = ref('')
const userList = ref([])
const userLoading = ref(false)

// 统计计算
const stats = computed(() => {
  const all = flatData.value
  return {
    total: all.length,
    disabled: all.filter(d => d.status !== 'ENABLED').length,
    root: all.filter(d => !d.parentId || d.parentId === 0).length
  }
})

const flattenDepartmentTree = (nodes) => nodes.flatMap((node) => [
  node,
  ...(node.children?.length ? flattenDepartmentTree(node.children) : [])
])

const filteredDepartments = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const departments = flattenDepartmentTree(treeData.value)
  if (!keyword) return departments
  return departments.filter((department) => (
    department.departmentName?.toLowerCase().includes(keyword) ||
    department.departmentCode?.toLowerCase().includes(keyword)
  ))
})

// 对话框状态
const dialogVisible = ref(false)
const isEdit = ref(false)
const isRoot = ref(false)
const parentDepartmentName = ref('')
const formRef = ref(null)
const formData = reactive({
  departmentId: null,
  parentId: null,
  departmentName: '',
  departmentCode: '',
  orderNum: 0,
  status: 'ENABLED',
  leader: '',
  phone: '',
  description: ''
})

// 表单验证规则
const formRules = {
  departmentName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { min: 2, max: 50, message: '部门名称长度在 2 到 50 个字符', trigger: 'blur' }
  ],
  departmentCode: [
    { required: true, message: '请输入部门编码', trigger: 'blur' },
    { pattern: /^[A-Z0-9_]+$/, message: '只能包含大写字母、数字和下划线', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

// 加载用户列表
const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await getUserList({ page: 0, size: 100 })
    userList.value = res.data?.records || res.data || []
  } catch (error) {
    console.error(error)
  } finally {
    userLoading.value = false
  }
}

const unwrapDepartmentList = (response) => {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.content)) return response.content
  if (Array.isArray(response?.records)) return response.records
  return []
}

// 加载部门列表
const loadDepartments = async () => {
  loading.value = true
  try {
    const [treeRes, flatRes] = await Promise.all([
      getDepartmentList(),
      getAllDepartments()
    ])

    const nextTreeData = unwrapDepartmentList(treeRes)
    const nextFlatData = unwrapDepartmentList(flatRes)
    treeData.value = nextTreeData
    flatData.value = nextFlatData
  } catch (error) {
    ElMessage.error('加载部门列表失败')
    console.error(error)
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

// 创建根部门
const handleCreateRoot = async () => {
  isEdit.value = false
  isRoot.value = true
  parentDepartmentName.value = ''
  Object.assign(formData, {
    departmentId: null,
    parentId: null,
    departmentName: '',
    departmentCode: '',
    orderNum: 0,
    status: 'ENABLED',
    leader: '',
    phone: '',
    description: ''
  })
  await loadUsers()
  dialogVisible.value = true
}

// 创建子部门
const handleAddChild = async (data) => {
  isEdit.value = false
  isRoot.value = false
  parentDepartmentName.value = data.departmentName
  Object.assign(formData, {
    departmentId: null,
    parentId: data.departmentId,
    departmentName: '',
    departmentCode: '',
    orderNum: 0,
    status: 'ENABLED',
    leader: '',
    phone: '',
    description: ''
  })
  await loadUsers()
  dialogVisible.value = true
}

// 编辑部门
const handleEdit = async (data) => {
  isEdit.value = true
  isRoot.value = false
  parentDepartmentName.value = getParentName(data.parentId)
  Object.assign(formData, {
    departmentId: data.departmentId,
    parentId: data.parentId,
    departmentName: data.departmentName,
    departmentCode: data.departmentCode,
    orderNum: data.orderNum || 0,
    status: data.status || 'ENABLED',
    leader: data.leader || '',
    phone: data.phone || '',
    description: data.description || ''
  })
  await loadUsers()
  dialogVisible.value = true
}

// 编码自动转大写
const handleCodeInput = (e) => {
  formData.departmentCode = e.target.value.toUpperCase()
}

// 获取父部门名称
const getParentName = (parentId) => {
  if (!parentId || parentId === 0) return '顶级部门'
  const parent = flatData.value.find(d => d.departmentId === parentId)
  return parent ? parent.departmentName : '顶级部门'
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const deptData = {
        departmentName: formData.departmentName,
        departmentCode: formData.departmentCode.toUpperCase(),
        parentId: isEdit.value ? formData.parentId : formData.parentId,
        orderNum: formData.orderNum,
        status: formData.status,
        leader: formData.leader,
        phone: formData.phone,
        description: formData.description
      }

      // 处理根部门
      if (!isEdit.value && isRoot.value) {
        deptData.parentId = 0
      }

      if (isEdit.value) {
        await updateDepartment(formData.departmentId, deptData)
        ElMessage.success('部门更新成功')
      } else {
        await createDepartment(deptData)
        ElMessage.success('部门创建成功')
      }

      dialogVisible.value = false
      loadDepartments()
    } catch (error) {
      ElMessage.error(isEdit.value ? '更新失败' : '创建失败')
    } finally {
      submitting.value = false
    }
  })
}

// 删除部门
const handleDelete = async (data) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除部门 ${data.departmentName} 吗？`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteDepartment(data.departmentId)
    ElMessage.success('删除成功')
    loadDepartments()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadDepartments()
  window.addEventListener('page-refresh', loadDepartments)
})

onUnmounted(() => {
  window.removeEventListener('page-refresh', loadDepartments)
})
</script>

<style scoped>
.department-management {
  min-width: 0;
}

.fade-in {
  animation: fadeIn 0.45s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.department-shell {
  display: grid;
  gap: 16px;
  max-width: 1600px;
  margin: 0 auto;
}

.department-gallery {
  min-width: 0;
}

.gallery-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 18px;
  margin: 0;
  padding: 0;
}

.gallery-heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 10px;
}

.gallery-heading h2 {
  margin: 0;
  color: var(--neutral-gray-900);
  font-size: 16px;
  line-height: 1.35;
  font-weight: var(--font-semibold);
}

.gallery-heading > span {
  flex: none;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  background: var(--neutral-gray-100);
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.gallery-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-width: 0;
  gap: 12px;
}

.department-search {
  width: min(320px, 34vw);
  flex: none;
}

.department-search :deep(.el-input) {
  width: 100%;
}

.department-refresh {
  color: var(--neutral-gray-600);
  border-color: var(--orin-border-strong);
  background: var(--orin-surface);
}

.department-refresh:hover,
.department-refresh:focus-visible {
  color: var(--orin-primary);
  border-color: rgba(var(--orin-primary-rgb), 0.42);
  background: var(--orin-primary-soft);
}

.gallery-stats {
  display: flex;
  flex: none;
  align-items: center;
  gap: 14px;
  color: var(--neutral-gray-500);
  font-size: 12px;
}

.gallery-stats span {
  display: grid;
  gap: 2px;
}

.gallery-stats strong {
  color: var(--neutral-gray-900);
  font-size: 15px;
  line-height: 1;
}

.gallery-stats .warning strong {
  color: #b45309;
}

.department-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  padding: 0;
}

.department-card {
  display: grid;
  min-width: 0;
  min-height: 255px;
  grid-template-rows: auto minmax(52px, 1fr) auto auto;
  gap: 16px;
  padding: 18px;
  border: 1px solid var(--orin-border);
  border-radius: var(--radius-xl, 12px);
  background: var(--orin-surface);
  box-shadow: var(--shadow-xs);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.department-card:hover {
  transform: translateY(-2px);
  border-color: rgba(var(--orin-primary-rgb), 0.42);
  box-shadow: 0 10px 22px rgba(var(--orin-primary-rgb), 0.08);
}

.department-card-head,
.department-card-title,
.department-card-actions,
.department-card-actions > div {
  display: flex;
  align-items: center;
}

.department-card-head,
.department-card-actions {
  justify-content: space-between;
  gap: 12px;
}

.department-card-title {
  min-width: 0;
  gap: 11px;
}

.department-card-title > div {
  min-width: 0;
}

.department-card-head :deep(.el-tag) {
  flex: none;
}

.department-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 10px;
  background: var(--orin-primary-soft);
  color: var(--orin-primary);
  font-size: 18px;
}

.department-card h3 {
  margin: 0 0 4px;
  overflow: hidden;
  color: var(--neutral-gray-900);
  font-size: 16px;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-card code {
  color: var(--neutral-gray-500);
  font-size: 11px;
}

.department-description {
  display: -webkit-box;
  margin: 0;
  overflow: hidden;
  color: var(--neutral-gray-600);
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.department-description.muted {
  color: var(--neutral-gray-400);
}

.department-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 0;
  padding: 12px 0;
  border-top: 1px solid var(--orin-border);
  border-bottom: 1px solid var(--orin-border);
}

.department-meta div {
  min-width: 0;
}

.department-meta dt {
  margin-bottom: 5px;
  color: var(--neutral-gray-400);
  font-size: 11px;
}

.department-meta dd {
  margin: 0;
  overflow: hidden;
  color: var(--neutral-gray-700);
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.department-card-actions > div {
  gap: 2px;
}

.department-card-actions .delete-action {
  color: var(--neutral-gray-400);
}

.department-card-actions .delete-action:hover,
.department-card-actions .delete-action:focus-visible {
  color: var(--error-color, #dc2626);
  background: var(--error-dark-shallow, #fef2f2);
}

.department-topbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  padding: 6px 2px 2px;
}

.topbar-copy {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.topbar-eyebrow {
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.topbar-copy h1 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 720;
  letter-spacing: 0;
}

.topbar-copy p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.topbar-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.summary-card {
  display: grid;
  gap: 5px;
  min-width: 0;
  padding: 13px 16px;
  border: 1px solid #dde5ef;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.035);
}

.summary-card.primary {
  border-color: rgba(15, 118, 110, 0.24);
  background: #f0fdfa;
  color: inherit;
}

.summary-card span {
  color: #64748b;
  font-size: 12px;
}

.summary-card.primary span,
.summary-card.primary p {
  color: #0f766e;
}

.summary-card strong {
  color: #0f172a;
  font-size: 24px;
  line-height: 1;
  font-weight: 720;
}

.summary-card.primary strong {
  color: #0f172a;
}

.summary-card p {
  margin: 0;
  color: #94a3b8;
  font-size: 12px;
}

.department-workspace {
  display: grid;
  grid-template-columns: minmax(300px, 350px) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.directory-panel,
.dossier-panel,
.no-selection-panel {
  min-width: 0;
}

.directory-panel,
.dossier-panel,
.no-selection-panel {
  border: 1px solid #dde5ef;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.04);
}

.directory-panel {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  min-height: 580px;
  overflow: hidden;
}

.directory-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid #e2e8f0;
}

.directory-head h2,
.section-heading h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.25;
  font-weight: 680;
}

.directory-head p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 12px;
}

.directory-stats {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 11px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #64748b;
  font-size: 12px;
}

.directory-stats span {
  padding-right: 8px;
  border-right: 1px solid #dbe4ee;
}

.directory-stats span:last-child {
  padding-right: 0;
  border-right: 0;
}

.directory-stats strong {
  color: #0f172a;
  font-weight: 700;
}

.directory-stats .warning strong {
  color: #b45309;
}

.directory-tools {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  padding: 14px 16px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.directory-tree {
  min-height: 420px;
  max-height: calc(100vh - 360px);
  overflow: auto;
  padding: 10px 10px 16px;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 36px;
  padding: 5px 8px;
  border: 1px solid transparent;
  border-radius: 7px;
  transition: background 0.16s ease, border-color 0.16s ease;
}

.tree-node.is-selected {
  border-color: rgba(15, 118, 110, 0.28);
  background: rgba(15, 118, 110, 0.09);
}

.tree-node:hover {
  border-color: rgba(148, 163, 184, 0.26);
  background: #f8fafc;
}

.node-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.node-name,
.child-name,
.field-cell strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-name {
  color: #1e293b;
  font-size: 13px;
}

.node-count {
  flex: none;
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  background: #e2e8f0;
  color: #475569;
  font-size: 11px;
  text-align: center;
}

.node-actions {
  display: none;
  align-items: center;
  gap: 2px;
  flex: none;
}

.tree-node:hover .node-actions {
  display: inline-flex;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex: none;
}

.status-dot.enabled {
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.status-dot.disabled {
  background: #ef4444;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12);
}

.status-dot.small {
  width: 7px;
  height: 7px;
}

.empty-directory {
  display: grid;
  min-height: 300px;
  place-items: center;
}

.dossier-panel {
  display: grid;
  gap: 16px;
  align-content: start;
  padding: 16px;
}

.dossier-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 118px;
  padding: 22px;
  border: 1px solid #b9e2dc;
  border-radius: 12px;
  background: linear-gradient(135deg, #f0fdfa 0%, #f8fafc 68%);
}

.dossier-title {
  display: grid;
  align-content: start;
  gap: 9px;
  min-width: 0;
}

.dossier-kicker {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}

.dossier-title h2 {
  margin: 0;
  color: #0f172a;
  font-size: 28px;
  line-height: 1.12;
  font-weight: 720;
  letter-spacing: 0;
}

.dossier-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.field-cell {
  display: grid;
  gap: 8px;
  min-width: 0;
  padding: 16px;
  border-right: 1px solid #e2e8f0;
  background: #ffffff;
}

.field-cell:last-child {
  border-right: 0;
}

.field-cell span,
.section-heading span {
  color: #64748b;
  font-size: 12px;
}

.field-cell strong {
  color: #0f172a;
  font-size: 14px;
  font-weight: 650;
}

.content-card {
  display: grid;
  gap: 14px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  min-height: 168px;
  border-radius: 10px;
  background: #ffffff;
}

.dossier-content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 0.9fr);
  gap: 16px;
}

.section-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.description-text {
  margin: 0;
  color: #334155;
  font-size: 14px;
  line-height: 1.8;
}

.description-text.muted {
  color: #94a3b8;
}

.children-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 10px;
}

.child-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #f8fafc;
  color: #1e293b;
  cursor: pointer;
  transition: border-color 0.16s ease, background 0.16s ease;
}

.child-item:hover {
  border-color: rgba(15, 118, 110, 0.34);
  background: rgba(15, 118, 110, 0.07);
}

.child-name {
  flex: 1;
  min-width: 0;
  text-align: left;
}

.empty-children {
  display: grid;
  min-height: 92px;
  place-items: center;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  color: #94a3b8;
  font-size: 13px;
}

.no-selection-panel {
  display: grid;
  min-height: 610px;
  place-items: center;
  padding: 32px;
}

.no-selection-copy {
  display: grid;
  justify-items: center;
  gap: 12px;
  max-width: 360px;
  text-align: center;
}

.no-selection-copy > .el-icon {
  display: grid;
  width: 64px;
  height: 64px;
  place-items: center;
  border-radius: 8px;
  background: #ecfdf5;
  color: #0f766e;
  font-size: 30px;
}

.no-selection-copy h2 {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
}

.no-selection-copy p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.form-row {
  display: flex;
  gap: 16px;
}

.half-width {
  flex: 1;
}

.full-width {
  width: 100%;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 980px) {
  .gallery-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .gallery-controls {
    justify-content: flex-start;
    width: 100%;
    flex-wrap: wrap;
  }

  .department-search {
    width: min(360px, 100%);
    flex: 1 1 280px;
  }
}

@media (max-width: 640px) {
  .gallery-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .department-search {
    width: 100%;
    flex: none;
  }

  .gallery-controls > :deep(.el-button) {
    width: 100%;
  }

  .gallery-stats {
    justify-content: flex-start;
  }

  .department-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .department-card {
    min-height: 0;
  }

  .department-card-actions {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }
}

html.dark .department-card {
  box-shadow: none;
}

html.dark .department-card:hover {
  box-shadow: 0 10px 22px rgba(45, 212, 191, 0.08);
}
</style>
