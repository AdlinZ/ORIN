<template>
  <div class="department-management fade-in">
    <section class="department-shell">
      <OrinEntityHeader
        domain="组织权限"
        title="部门管理"
        description="维护组织架构、部门负责人和上下级关系。"
      >
        <template #actions>
          <el-button :icon="Refresh" @click="loadDepartments">
            刷新
          </el-button>
          <el-button type="primary" :icon="Plus" @click="handleCreateRoot">
            创建顶级部门
          </el-button>
        </template>
        <template #filters>
          <div class="governance-filterbar">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索部门名称/编码"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button-group>
              <el-button title="展开全部" @click="expandAll">
                <el-icon><ArrowDown /></el-icon>
                展开
              </el-button>
              <el-button title="收起全部" @click="collapseAll">
                <el-icon><ArrowUp /></el-icon>
                收起
              </el-button>
            </el-button-group>
          </div>
        </template>
      </OrinEntityHeader>

      <OrinStatusSummary :items="departmentStatusItems" class="governance-summary" />

      <section class="department-workspace">
        <aside class="directory-panel">
          <div class="directory-head">
            <div>
              <h2>组织目录</h2>
              <p>{{ stats.total }} 个部门节点</p>
            </div>
            <el-button
              circle
              :icon="Plus"
              type="primary"
              @click="handleCreateRoot"
            />
          </div>

          <div v-loading="loading" class="directory-tree">
            <el-tree
              ref="treeRef"
              :data="filteredTreeData"
              :props="treeProps"
              node-key="departmentId"
              :default-expanded-keys="expandedKeys"
              :expand-on-click-node="false"
              :filter-node-method="filterNode"
              @node-click="handleNodeClick"
            >
              <template #default="{ node, data }">
                <div class="tree-node" :class="{ 'is-selected': selectedDepartment?.departmentId === data.departmentId }">
                  <div class="node-main">
                    <span class="status-dot" :class="data.status === 'ENABLED' ? 'enabled' : 'disabled'" />
                    <span class="node-name">{{ data.departmentName }}</span>
                    <span v-if="data.children && data.children.length > 0" class="node-count">
                      {{ data.children.length }}
                    </span>
                  </div>
                  <div class="node-actions">
                    <el-tooltip content="新增子部门" placement="top">
                      <el-button
                        link
                        type="primary"
                        size="small"
                        @click.stop="handleAddChild(data)"
                      >
                        <el-icon><Plus /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="编辑" placement="top">
                      <el-button
                        link
                        type="primary"
                        size="small"
                        @click.stop="handleEdit(data)"
                      >
                        <el-icon><Edit /></el-icon>
                      </el-button>
                    </el-tooltip>
                    <el-tooltip
                      :content="data.children && data.children.length > 0 ? '存在子部门，无法删除' : '删除'"
                      placement="top"
                    >
                      <el-button
                        link
                        type="danger"
                        size="small"
                        :disabled="data.children && data.children.length > 0"
                        @click.stop="handleDelete(data)"
                      >
                        <el-icon><Delete /></el-icon>
                      </el-button>
                    </el-tooltip>
                  </div>
                </div>
              </template>
            </el-tree>

            <div v-if="!loading && (!treeData || treeData.length === 0)" class="empty-directory">
              <el-empty description="暂无部门数据" :image-size="72">
                <el-button
                  type="primary"
                  size="small"
                  :icon="Plus"
                  @click="handleCreateRoot"
                >
                  创建第一个部门
                </el-button>
              </el-empty>
            </div>
          </div>
        </aside>

        <template v-if="selectedDepartment">
          <main class="dossier-panel">
            <section class="dossier-hero">
              <div class="dossier-avatar">
                <el-icon><OfficeBuilding /></el-icon>
              </div>
              <div class="dossier-title">
                <el-tag
                  size="small"
                  :type="selectedDepartment.status === 'ENABLED' ? 'success' : 'danger'"
                  effect="light"
                >
                  {{ selectedDepartment.status === 'ENABLED' ? '启用中' : '已禁用' }}
                </el-tag>
                <h2>{{ selectedDepartment.departmentName }}</h2>
                <p>{{ selectedDepartmentPath }}</p>
              </div>
              <div class="dossier-actions">
                <el-button type="primary" :icon="Plus" @click="handleAddChild(selectedDepartment)">
                  新增子部门
                </el-button>
                <el-button :icon="Edit" @click="handleEdit(selectedDepartment)">
                  编辑
                </el-button>
                <el-button
                  :icon="Delete"
                  :disabled="selectedDepartment.children && selectedDepartment.children.length > 0"
                  @click="handleDelete(selectedDepartment)"
                >
                  删除
                </el-button>
              </div>
            </section>

            <section class="field-grid">
              <div class="field-cell">
                <span>部门编码</span>
                <strong>{{ selectedDepartment.departmentCode }}</strong>
              </div>
              <div class="field-cell">
                <span>部门负责人</span>
                <strong>{{ selectedDepartment.leader || '-' }}</strong>
              </div>
              <div class="field-cell">
                <span>联系电话</span>
                <strong>{{ selectedDepartment.phone || '-' }}</strong>
              </div>
              <div class="field-cell">
                <span>创建时间</span>
                <strong>{{ formatDate(selectedDepartment.createTime) }}</strong>
              </div>
            </section>

            <section class="content-card">
              <div class="section-heading">
                <h3>部门描述</h3>
                <span>Profile</span>
              </div>
              <p class="description-text" :class="{ muted: !selectedDepartment.description }">
                {{ selectedDepartment.description || '暂无部门描述。' }}
              </p>
            </section>

            <section class="content-card">
              <div class="section-heading">
                <h3>下级部门</h3>
                <span>{{ selectedDepartment.children?.length || 0 }} 个</span>
              </div>
              <div v-if="selectedDepartment.children && selectedDepartment.children.length > 0" class="children-list">
                <button
                  v-for="child in selectedDepartment.children"
                  :key="child.departmentId"
                  type="button"
                  class="child-item"
                  @click="handleNodeClick(child)"
                >
                  <span class="status-dot small" :class="child.status === 'ENABLED' ? 'enabled' : 'disabled'" />
                  <span class="child-name">{{ child.departmentName }}</span>
                  <el-tag size="small" :type="child.status === 'ENABLED' ? 'success' : 'danger'">
                    {{ child.status === 'ENABLED' ? '启用' : '禁用' }}
                  </el-tag>
                </button>
              </div>
              <div v-else class="empty-children">
                暂无下级部门
              </div>
            </section>
          </main>
        </template>

        <main v-else class="no-selection-panel">
          <div class="no-selection-copy">
            <el-icon><OfficeBuilding /></el-icon>
            <h2>选择一个部门查看详情</h2>
            <p>从左侧组织目录选择部门，或创建顶级部门开始搭建组织结构。</p>
            <el-button type="primary" :icon="Plus" @click="handleCreateRoot">
              创建顶级部门
            </el-button>
          </div>
        </main>
      </section>
    </section>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑部门' : (isRoot ? '创建顶级部门' : '创建子部门')"
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
import { Plus, Edit, Delete, Search, Refresh, ArrowDown, ArrowUp, OfficeBuilding } from '@element-plus/icons-vue'
import { getDepartmentList, getAllDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'
import { getUserList } from '@/api/userManage'
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue'
import OrinStatusSummary from '@/components/orin/OrinStatusSummary.vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const treeData = ref([])
const flatData = ref([])
const selectedDepartment = ref(null)
const searchKeyword = ref('')
const treeRef = ref(null)
const expandedKeys = ref([])
const userList = ref([])
const userLoading = ref(false)

// 树形配置
const treeProps = {
  children: 'children',
  label: 'departmentName'
}

// 统计计算
const stats = computed(() => {
  const all = flatData.value
  return {
    total: all.length,
    enabled: all.filter(d => d.status === 'ENABLED').length,
    disabled: all.filter(d => d.status !== 'ENABLED').length,
    root: all.filter(d => !d.parentId || d.parentId === 0).length
  }
})

const departmentStatusItems = computed(() => [
  { label: '部门总数', value: String(stats.value.total), meta: '组织架构中的全部节点' },
  { label: '启用部门', value: String(stats.value.enabled), meta: '可参与权限和业务归属', intent: 'success' },
  { label: '禁用部门', value: String(stats.value.disabled), meta: '暂不参与日常业务流转', intent: stats.value.disabled > 0 ? 'warning' : '' },
  { label: '顶级部门', value: String(stats.value.root), meta: '组织架构第一层节点' }
])

const selectedDepartmentPath = computed(() => {
  if (!selectedDepartment.value) return ''

  const path = []
  const walk = (nodes, targetId, parents = []) => {
    for (const node of nodes) {
      const nextParents = [...parents, node.departmentName]
      if (node.departmentId === targetId) {
        path.push(...nextParents)
        return true
      }
      if (node.children?.length && walk(node.children, targetId, nextParents)) {
        return true
      }
    }
    return false
  }

  walk(treeData.value, selectedDepartment.value.departmentId)
  return path.length ? path.join(' / ') : getParentName(selectedDepartment.value.parentId)
})

// 过滤后的树数据
const filteredTreeData = computed(() => {
  if (!searchKeyword.value) return treeData.value
  return treeData.value
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

// 获取初始展开的keys（只展开前两级）
const getInitialExpandedKeys = (data) => {
  const keys = []
  data.forEach(item => {
    keys.push(item.departmentId)
    if (item.children && item.children.length > 0 && keys.length < 10) {
      item.children.slice(0, 3).forEach(child => {
        keys.push(child.departmentId)
      })
    }
  })
  return keys
}

// 加载部门列表
const loadDepartments = async () => {
  loading.value = true
  try {
    const [treeRes, flatRes] = await Promise.all([
      getDepartmentList(),
      getAllDepartments()
    ])

    treeData.value = treeRes.data || []
    flatData.value = flatRes.data || []
    expandedKeys.value = getInitialExpandedKeys(treeData.value)
  } catch (error) {
    ElMessage.error('加载部门列表失败')
    console.error(error)
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

// 搜索过滤
const handleSearch = (value) => {
  treeRef.value?.filter(value)
}

const filterNode = (value, data) => {
  if (!value) return true
  const keyword = value.toLowerCase()
  return data.departmentName.toLowerCase().includes(keyword) ||
    (data.departmentCode && data.departmentCode.toLowerCase().includes(keyword))
}

// 展开/收起全部
const expandAll = () => {
  const allKeys = []
  const getAllKeys = (data) => {
    data.forEach(item => {
      allKeys.push(item.departmentId)
      if (item.children) {
        getAllKeys(item.children)
      }
    })
  }
  getAllKeys(treeData.value)
  expandedKeys.value = allKeys
}

const collapseAll = () => {
  expandedKeys.value = []
}

// 点击树节点
const handleNodeClick = (data) => {
  selectedDepartment.value = data
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
    selectedDepartment.value = null
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
  min-height: 100vh;
  padding: 22px 26px;
  background: #f6f8fb;
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
  gap: 14px;
  max-width: 1600px;
  margin: 0 auto;
}

.governance-filterbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto;
  gap: 10px;
  width: 100%;
  align-items: center;
}

.governance-summary {
  margin-bottom: 2px;
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
  grid-template-columns: minmax(320px, 390px) minmax(0, 1fr);
  gap: 14px;
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
  min-height: 610px;
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
  gap: 12px;
  align-content: start;
  padding: 16px;
}

.dossier-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 112px;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}

.dossier-avatar {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 8px;
  background: #ffffff;
  color: #0f766e;
  font-size: 28px;
  box-shadow: none;
}

.dossier-title {
  display: grid;
  align-content: start;
  gap: 8px;
  min-width: 0;
}

.dossier-title h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
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

.dossier-title p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  overflow-wrap: anywhere;
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
  border-radius: 8px;
  background: #ffffff;
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

@media (max-width: 1320px) {
  .department-workspace {
    grid-template-columns: minmax(300px, 360px) minmax(0, 1fr);
  }
}

@media (max-width: 980px) {
  .department-topbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-grid,
  .department-workspace,
  .field-grid,
  .governance-filterbar {
    grid-template-columns: 1fr;
  }

  .no-selection-panel {
    grid-column: auto;
  }

  .directory-panel,
  .no-selection-panel {
    min-height: 420px;
  }

  .directory-tree {
    max-height: 420px;
  }

  .field-cell {
    border-right: 0;
    border-bottom: 1px solid #e2e8f0;
  }

  .field-cell:last-child {
    border-bottom: 0;
  }
}

@media (max-width: 640px) {
  .department-management {
    padding: 16px;
  }

  .department-topbar,
  .topbar-actions,
  .directory-tools,
  .dossier-hero {
    grid-template-columns: 1fr;
  }

  .topbar-actions,
  .topbar-actions :deep(.el-button) {
    width: 100%;
  }

  .directory-tools {
    display: grid;
  }

  .dossier-title h2 {
    font-size: 24px;
  }

  .dossier-actions,
  .dossier-actions :deep(.el-button) {
    width: 100%;
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }
}

html.dark .department-management {
  background: var(--bg-color);
}

html.dark .directory-panel,
html.dark .dossier-panel,
html.dark .no-selection-panel,
html.dark .summary-card,
html.dark .content-card,
html.dark .field-cell {
  background: var(--card-bg);
  border-color: var(--border-color);
  box-shadow: none;
}

html.dark .directory-tools,
html.dark .dossier-hero,
html.dark .child-item {
  background: var(--el-fill-color-dark);
}

html.dark .topbar-copy h1,
html.dark .summary-card strong,
html.dark .directory-head h2,
html.dark .section-heading h3,
html.dark .dossier-title h2,
html.dark .field-cell strong,
html.dark .no-selection-copy h2 {
  color: var(--text-primary);
}

html.dark .description-text,
html.dark .node-name,
html.dark .child-item {
  color: var(--text-secondary);
}

html.dark .custom-dialog :deep(.el-dialog) {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
}

html.dark .custom-dialog :deep(.el-dialog__title) {
  color: var(--text-primary);
}

html.dark .custom-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
}
</style>
