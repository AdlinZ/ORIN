<template>
  <div class="department-management fade-in">
    <PageHeader
      title="部门管理"
      description="管理系统部门组织结构"
      icon="OfficeBuilding"
    />

    <div class="content-wrapper">
      <!-- 左侧部门树 -->
      <div class="tree-panel premium-card">
        <div class="panel-header">
          <span>部门结构</span>
          <el-button type="primary" size="small" @click="handleCreateRoot">
            <el-icon class="mr-1"><Plus /></el-icon>
            新增部门
          </el-button>
        </div>

        <div class="tree-content" v-loading="loading">
          <el-tree
            :data="treeData"
            :props="treeProps"
            node-key="departmentId"
            default-expand-all
            :expand-on-click-node="false"
            @node-click="handleNodeClick"
          >
            <template #default="{ node, data }">
              <div class="tree-node">
                <span class="node-label">{{ data.departmentName }}</span>
                <div class="node-actions">
                  <el-button link type="primary" size="small" @click.stop="handleAddChild(data)">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                  <el-button link type="primary" size="small" @click.stop="handleEdit(data)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                  <el-button link type="danger" size="small" @click.stop="handleDelete(data)" :disabled="data.children && data.children.length > 0">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </template>
          </el-tree>
        </div>
      </div>

      <!-- 右侧部门详情 -->
      <div class="detail-panel premium-card" v-if="selectedDepartment">
        <div class="panel-header">
          <span>部门详情</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleEdit(selectedDepartment)">
              <el-icon class="mr-1"><Edit /></el-icon>
              编辑
            </el-button>
          </div>
        </div>

        <div class="detail-content">
          <div class="detail-row">
            <span class="label">部门名称：</span>
            <span class="value">{{ selectedDepartment.departmentName }}</span>
          </div>
          <div class="detail-row">
            <span class="label">部门编码：</span>
            <span class="value code">{{ selectedDepartment.departmentCode }}</span>
          </div>
          <div class="detail-row">
            <span class="label">部门状态：</span>
            <el-tag :type="selectedDepartment.status === 'ENABLED' ? 'success' : 'danger'">
              {{ selectedDepartment.status === 'ENABLED' ? '启用' : '禁用' }}
            </el-tag>
          </div>
          <div class="detail-row">
            <span class="label">部门负责人：</span>
            <span class="value">{{ selectedDepartment.leader || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">联系电话：</span>
            <span class="value">{{ selectedDepartment.phone || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">部门描述：</span>
            <span class="value">{{ selectedDepartment.description || '-' }}</span>
          </div>
          <div class="detail-row">
            <span class="label">创建时间：</span>
            <span class="value">{{ formatDate(selectedDepartment.createTime) }}</span>
          </div>
        </div>
      </div>

      <div class="detail-panel premium-card empty" v-else>
        <el-empty description="请选择部门查看详情" />
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
        <el-form-item label="上级部门" v-if="!isRoot && !isEdit">
          <el-input :value="parentDepartmentName" disabled />
        </el-form-item>

        <el-form-item label="部门名称" prop="departmentName">
          <el-input v-model="formData.departmentName" placeholder="请输入部门名称" />
        </el-form-item>

        <el-form-item label="部门编码" prop="departmentCode">
          <el-input v-model="formData.departmentCode" placeholder="如: DEPT_001" :disabled="isEdit" />
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
          <el-input v-model="formData.leader" placeholder="请输入负责人姓名" />
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
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getDepartmentList, getAllDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/department'
import PageHeader from '@/components/PageHeader.vue'

// 数据状态
const loading = ref(false)
const submitting = ref(false)
const treeData = ref([])
const flatData = ref([])
const selectedDepartment = ref(null)

// 树形配置
const treeProps = {
  children: 'children',
  label: 'departmentName'
}

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
  } catch (error) {
    ElMessage.error('加载部门列表失败')
    console.error(error)
  } finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

// 点击树节点
const handleNodeClick = (data) => {
  selectedDepartment.value = data
}

// 创建根部门
const handleCreateRoot = () => {
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
  dialogVisible.value = true
}

// 创建子部门
const handleAddChild = (data) => {
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
  dialogVisible.value = true
}

// 编辑部门
const handleEdit = (data) => {
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
  dialogVisible.value = true
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

.content-wrapper {
  display: flex;
  gap: 24px;
}

.tree-panel {
  width: 400px;
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

.header-actions {
  display: flex;
  gap: 8px;
}

.tree-content {
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
}

.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  padding-right: 8px;
}

.node-label {
  font-size: 14px;
}

.node-actions {
  display: none;
  gap: 4px;
}

.tree-node:hover .node-actions {
  display: flex;
}

/* Detail Panel Styles */
.detail-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-row {
  display: flex;
  align-items: center;
}

.detail-row .label {
  width: 100px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.detail-row .value {
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.detail-row .value.code {
  font-family: monospace;
  padding: 2px 8px;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}

/* Form Styles */
.form-row {
  display: flex;
  gap: 16px;
}

.half-width {
  flex: 1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Dark Mode */
html.dark .department-management {
  background: var(--bg-color);
}

html.dark .premium-card {
  background: var(--card-bg);
  border-color: var(--border-color);
}

html.dark .detail-row .label {
  color: var(--text-secondary);
}

html.dark .detail-row .value {
  color: var(--text-primary);
}

html.dark .detail-row .value.code {
  background: rgba(38, 255, 223, 0.1);
  color: var(--orin-primary);
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