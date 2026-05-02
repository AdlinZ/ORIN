<template>
  <div class="department-management fade-in">
    <OrinEntityHeader
      domain="组织治理"
      title="部门管理"
      description="管理系统部门组织结构"
      :summary="departmentHeaderSummary"
    >
      <template #actions>
        <el-button type="primary" @click="handleCreateRoot">
          <el-icon class="mr-1">
            <Plus />
          </el-icon>
          创建顶级部门
        </el-button>
      </template>
      <template #filters>
        <div class="header-toolbar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索部门名称/编码"
            clearable
            class="search-input"
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button-group>
            <el-button title="展开全部" @click="expandAll">
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <el-button title="收起全部" @click="collapseAll">
              <el-icon><ArrowUp /></el-icon>
            </el-button>
          </el-button-group>
          <el-button title="刷新" @click="loadDepartments">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </template>
    </OrinEntityHeader>

    <div class="content-wrapper">
      <!-- 左侧部门树 -->
      <div class="tree-panel premium-card">
        <div class="panel-header">
          <span>组织结构</span>
        </div>

        <!-- 统计胶囊 -->
        <div v-if="stats.total > 0" class="stats-capsules">
          <div class="capsule">
            <span class="capsule-value">{{ stats.total }}</span>
            <span class="capsule-label">部门</span>
          </div>
          <div class="capsule enabled">
            <span class="capsule-value">{{ stats.enabled }}</span>
            <span class="capsule-label">启用</span>
          </div>
          <div class="capsule root">
            <span class="capsule-value">{{ stats.root }}</span>
            <span class="capsule-label">顶级</span>
          </div>
        </div>

        <div v-loading="loading" class="tree-content">
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
                <div class="node-info">
                  <span class="status-dot" :class="data.status === 'ENABLED' ? 'enabled' : 'disabled'" />
                  <span class="node-label">{{ data.departmentName }}</span>
                  <span v-if="data.children && data.children.length > 0" class="child-count">
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

          <el-empty v-if="!loading && (!treeData || treeData.length === 0)" description="暂无部门数据">
            <template #description>
              <div class="empty-tip">
                <p>暂无部门组织</p>
                <el-button type="primary" size="small" @click="handleCreateRoot">
                  <el-icon class="mr-1">
                    <Plus />
                  </el-icon>
                  创建第一个部门
                </el-button>
              </div>
            </template>
          </el-empty>
        </div>
      </div>

      <!-- 右侧部门详情 -->
      <div v-if="selectedDepartment" class="detail-panel premium-card">
        <div class="panel-header">
          <div class="header-title">
            <span class="status-dot large" :class="selectedDepartment.status === 'ENABLED' ? 'enabled' : 'disabled'" />
            <span>{{ selectedDepartment.departmentName }}</span>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="handleAddChild(selectedDepartment)">
              <el-icon class="mr-1">
                <Plus />
              </el-icon>
              新增子部门
            </el-button>
            <el-button @click="handleEdit(selectedDepartment)">
              <el-icon class="mr-1">
                <Edit />
              </el-icon>
              编辑
            </el-button>
          </div>
        </div>

        <div class="detail-content">
          <!-- 概览卡片 -->
          <div class="overview-card">
            <div class="overview-item">
              <el-icon class="overview-icon">
                <OfficeBuilding />
              </el-icon>
              <div class="overview-info">
                <span class="overview-value">{{ selectedDepartment.departmentCode }}</span>
                <span class="overview-label">部门编码</span>
              </div>
            </div>
            <div class="overview-item">
              <el-icon class="overview-icon">
                <Clock />
              </el-icon>
              <div class="overview-info">
                <span class="overview-value">{{ formatDate(selectedDepartment.createTime) }}</span>
                <span class="overview-label">创建时间</span>
              </div>
            </div>
            <div class="overview-item">
              <el-tag :type="selectedDepartment.status === 'ENABLED' ? 'success' : 'danger'" size="large">
                {{ selectedDepartment.status === 'ENABLED' ? '启用' : '禁用' }}
              </el-tag>
              <span class="overview-label">状态</span>
            </div>
          </div>

          <!-- 基础信息 -->
          <div class="info-section">
            <div class="section-title">
              基础信息
            </div>
            <div class="info-grid">
              <div class="info-item">
                <span class="info-label">部门负责人</span>
                <span class="info-value">{{ selectedDepartment.leader || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">联系电话</span>
                <span class="info-value">{{ selectedDepartment.phone || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">排序号</span>
                <span class="info-value">{{ selectedDepartment.orderNum || 0 }}</span>
              </div>
              <div class="info-item">
                <span class="info-label">父级部门</span>
                <span class="info-value">{{ getParentName(selectedDepartment.parentId) }}</span>
              </div>
            </div>
          </div>

          <!-- 描述/元信息 -->
          <div v-if="selectedDepartment.description" class="info-section">
            <div class="section-title">
              部门描述
            </div>
            <div class="description-content">
              {{ selectedDepartment.description }}
            </div>
          </div>

          <!-- 子部门信息 -->
          <div v-if="selectedDepartment.children && selectedDepartment.children.length > 0" class="info-section">
            <div class="section-title">
              下级部门
              <span class="section-count">{{ selectedDepartment.children.length }}</span>
            </div>
            <div class="children-list">
              <div
                v-for="child in selectedDepartment.children"
                :key="child.departmentId"
                class="child-item"
                @click="handleNodeClick(child)"
              >
                <span class="status-dot small" :class="child.status === 'ENABLED' ? 'enabled' : 'disabled'" />
                <span class="child-name">{{ child.departmentName }}</span>
                <el-tag size="small" :type="child.status === 'ENABLED' ? 'success' : 'danger'">
                  {{ child.status === 'ENABLED' ? '启用' : '禁用' }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="detail-panel premium-card empty">
        <el-empty>
          <template #description>
            <div class="empty-detail">
              <p class="empty-title">
                请选择部门查看详情
              </p>
              <p class="empty-tip">
                可从左侧选择部门，或创建顶级部门开始搭建组织结构
              </p>
            </div>
          </template>
        </el-empty>
      </div>
    </div>

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
import { Plus, Edit, Delete, Search, Refresh, ArrowDown, ArrowUp, OfficeBuilding, Clock } from '@element-plus/icons-vue'
import { getDepartmentList, getAllDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'
import { getUserList } from '@/api/userManage'
import OrinEntityHeader from '@/components/orin/OrinEntityHeader.vue'

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
    root: all.filter(d => !d.parentId || d.parentId === 0).length
  }
})

const departmentHeaderSummary = computed(() => ([
  { label: '部门总数', value: String(stats.value.total) },
  { label: '已启用', value: String(stats.value.enabled) },
  { label: '顶级部门', value: String(stats.value.root) }
]))

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
  padding: 32px;
  max-width: none;
  width: 100%;
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

/* Header Toolbar */
.header-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-input {
  width: 240px;
}

.content-wrapper {
  display: flex;
  gap: 24px;
}

.tree-panel {
  width: 380px;
  flex-shrink: 0;
}

.detail-panel {
  flex: 1;
  min-width: 0;
}

.premium-card {
  background: var(--card-bg, var(--el-bg-color));
  border-radius: 12px;
  border: 1px solid var(--border-color, var(--el-border-color-light));
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
  padding: 20px;
  transition: all 0.3s ease;
}

.premium-card:hover {
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05), 0 4px 6px -2px rgba(0, 0, 0, 0.025);
}

.premium-card.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  font-weight: 600;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

/* Stats Capsules */
.stats-capsules {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 12px;
  background: var(--orin-primary-50);
  border-radius: 8px;
}

.capsule {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px;
  background: var(--el-bg-color);
  border-radius: 6px;
}

.capsule.enabled {
  background: var(--success-50);
}

.capsule.root {
  background: var(--info-50);
}

.capsule-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.capsule-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tree-content {
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
}

/* Tree Node */
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s;
}

.tree-node.is-selected {
  background: var(--el-color-primary-light-9);
}

.tree-node:hover {
  background: var(--el-fill-color-light);
}

.node-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-dot.enabled {
  background: var(--success-500);
}

.status-dot.disabled {
  background: var(--error-500);
}

.status-dot.large {
  width: 12px;
  height: 12px;
}

.status-dot.small {
  width: 6px;
  height: 6px;
}

.node-label {
  font-size: 14px;
}

.child-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color);
  padding: 2px 6px;
  border-radius: 10px;
}

.node-actions {
  display: none;
  gap: 4px;
}

.tree-node:hover .node-actions {
  display: flex;
}

/* Empty State */
.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty-detail {
  text-align: center;
}

.empty-title {
  font-size: 16px;
  color: var(--el-text-color-primary);
  margin-bottom: 8px;
}

.empty-tip p {
  margin-bottom: 12px;
}

/* Detail Content */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* Overview Card */
.overview-card {
  display: flex;
  gap: 24px;
  padding: 20px;
  background: linear-gradient(135deg, var(--orin-primary-50) 0%, var(--orin-primary-100) 100%);
  border-radius: 12px;
}

.overview-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.overview-icon {
  font-size: 32px;
  color: var(--el-color-primary);
}

.overview-info {
  display: flex;
  flex-direction: column;
}

.overview-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.overview-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* Info Section */
.info-section {
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-count {
  font-size: 12px;
  font-weight: normal;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  padding: 2px 8px;
  border-radius: 10px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.info-value {
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.description-content {
  font-size: 14px;
  color: var(--el-text-color-primary);
  line-height: 1.6;
}

/* Children List */
.children-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.child-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.child-item:hover {
  background: var(--orin-primary-50);
}

.child-name {
  flex: 1;
  font-size: 14px;
}

/* Form Styles */
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

/* Responsive */
@media (max-width: 1200px) {
  .content-wrapper {
    flex-direction: column;
  }

  .tree-panel {
    width: 100%;
  }

  .overview-card {
    flex-wrap: wrap;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }
}

/* Dark Mode */
html.dark .department-management {
  background: var(--bg-color);
}

html.dark .premium-card {
  background: var(--card-bg);
  border-color: var(--border-color);
}

html.dark .stats-capsules {
  background: var(--orin-primary-dark-shallow);
}

html.dark .capsule {
  background: var(--neutral-gray-800);
}

html.dark .capsule.enabled {
  background: var(--success-dark-shallow);
}

html.dark .capsule.root {
  background: var(--info-dark-shallow);
}

html.dark .overview-card {
  background: linear-gradient(135deg, var(--orin-primary-dark-shallow) 0%, var(--orin-primary-dark) 100%);
}

html.dark .info-section {
  background: var(--el-fill-color-dark);
}

html.dark .child-item {
  background: var(--el-bg-color-overlay);
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
