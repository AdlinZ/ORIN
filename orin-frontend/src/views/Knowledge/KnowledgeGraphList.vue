<template>
  <div class="page-container">
    <PageHeader
      title="知识图谱"
      description="管理和可视化知识图谱，进行实体检索与关系探索。"
      icon="Connection"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="fetchGraphs">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="handleCreate">创建图谱</el-button>
      </template>
    </PageHeader>

    <!-- 图谱列表 -->
    <div v-loading="loading" class="graph-grid-container">
      <div class="graph-grid">
        <!-- 创建图谱卡片 -->
        <div class="graph-grid-item create-card" @click="handleCreate">
          <div class="create-content">
            <div class="create-icon">
              <el-icon><Plus /></el-icon>
            </div>
            <span class="create-text">创建图谱</span>
          </div>
        </div>

        <!-- 图谱列表 -->
        <div
          v-for="graph in graphs"
          :key="graph.id"
          class="graph-grid-item"
          @click="openGraphDetail(graph)"
        >
          <el-card shadow="hover" class="graph-card">
            <div class="graph-header">
              <div class="icon-wrapper graph-icon">
                <el-icon><Connection /></el-icon>
              </div>
              <div class="graph-info">
                <h3 class="graph-name text-ellipsis">{{ graph.name }}</h3>
              </div>
              <div class="graph-more" @click.stop>
                <el-dropdown trigger="click">
                  <el-icon class="more-btn"><MoreFilled /></el-icon>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="handleBuild(graph)">构建</el-dropdown-item>
                      <el-dropdown-item @click="handleEdit(graph)">编辑</el-dropdown-item>
                      <el-dropdown-item @click="handleDelete(graph)" divided class="text-danger">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
            </div>

            <div class="graph-body">
              <p class="graph-desc">{{ graph.description || '暂无描述' }}</p>
            </div>

            <div class="graph-footer">
              <div class="graph-tags">
                <el-tag size="small" effect="plain" :type="getStatusType(graph.buildStatus)">
                  {{ getStatusText(graph.buildStatus) }}
                </el-tag>
              </div>
              <div class="graph-stats">
                <span>{{ graph.entityCount || 0 }} 实体</span>
                <span class="separator">|</span>
                <span>{{ graph.relationCount || 0 }} 关系</span>
              </div>
            </div>
          </el-card>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="!loading && graphs.length === 0" class="empty-state-container">
        <el-empty description="暂无图谱">
          <el-button type="primary" @click="handleCreate">创建第一个图谱</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建图谱' : '编辑图谱'"
      width="500px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="图谱名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入图谱名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入图谱描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Close, MoreFilled, Connection } from '@element-plus/icons-vue'
import { getGraphList, createGraph, updateGraph, deleteGraph, buildGraph } from '@/api/knowledge'

const loading = ref(false)
const graphs = ref([])
const dialogVisible = ref(false)
const dialogMode = ref('create')
const formRef = ref(null)
const selectedGraph = ref(null)

const form = reactive({
  name: '',
  description: ''
})

const rules = {
  name: [{ required: true, message: '请输入图谱名称', trigger: 'blur' }]
}

const getStatusType = (status) => {
  const map = {
    'PENDING': 'info',
    'BUILDING': 'warning',
    'ENTITY_EXTRACTING': 'warning',
    'RELATION_EXTRACTING': 'warning',
    'SUCCESS': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = {
    'PENDING': '待构建',
    'BUILDING': '构建中',
    'ENTITY_EXTRACTING': '实体抽取',
    'RELATION_EXTRACTING': '关系抽取',
    'SUCCESS': '已完成',
    'FAILED': '构建失败'
  }
  return map[status] || status
}

const fetchGraphs = async () => {
  loading.value = true
  try {
    // TODO: 替换为真实的 API 调用
    // graphs.value = await getGraphList()
    graphs.value = []
  } catch (error) {
    console.error('获取图谱列表失败:', error)
    ElMessage.error('获取图谱列表失败')
  } finally {
    loading.value = false
  }
}

const openGraphDetail = (graph) => {
  // TODO: 导航到图谱详情页
  console.log('打开图谱详情:', graph)
  ElMessage.info('图谱详情功能开发中')
}

const handleCreate = () => {
  dialogMode.value = 'create'
  form.name = ''
  form.description = ''
  dialogVisible.value = true
}

const handleEdit = (graph) => {
  dialogMode.value = 'edit'
  selectedGraph.value = graph
  form.name = graph.name
  form.description = graph.description
  dialogVisible.value = true
}

const handleBuild = async (graph) => {
  try {
    await ElMessageBox.confirm(`确定要构建图谱「${graph.name}」吗？这将触发图谱构建任务。`, '构建图谱', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
    // TODO: 调用构建 API
    // await buildGraph(graph.id)
    ElMessage.success('图谱构建任务已触发')
    fetchGraphs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('构建图谱失败:', error)
      ElMessage.error('构建图谱失败')
    }
  }
}

const handleDelete = async (graph) => {
  try {
    await ElMessageBox.confirm(`确定要删除图谱「${graph.name}」吗？此操作不可恢复。`, '删除图谱', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    // TODO: 调用删除 API
    // await deleteGraph(graph.id)
    ElMessage.success('图谱已删除')
    fetchGraphs()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除图谱失败:', error)
      ElMessage.error('删除图谱失败')
    }
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()

    if (dialogMode.value === 'create') {
      // TODO: 调用创建 API
      // await createGraph(form)
      ElMessage.success('图谱创建成功')
    } else {
      // TODO: 调用更新 API
      // await updateGraph(selectedGraph.value.id, form)
      ElMessage.success('图谱更新成功')
    }

    dialogVisible.value = false
    fetchGraphs()
  } catch (error) {
    console.error('提交失败:', error)
  }
}

onMounted(() => {
  fetchGraphs()
})
</script>

<style scoped>
.graph-grid-container {
  min-height: 400px;
}

.graph-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.graph-grid-item {
  cursor: pointer;
}

.create-card {
  border: 2px dashed var(--el-border-color);
  border-radius: var(--radius-base);
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.3s;
}

.create-card:hover {
  border-color: var(--primary-color);
  background: rgba(var(--primary-color-rgb), 0.05);
}

.create-content {
  text-align: center;
}

.create-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: var(--el-fill-color-light);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: var(--el-text-color-secondary);
}

.create-card:hover .create-icon {
  background: var(--primary-color);
  color: white;
}

.create-text {
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.create-card:hover .create-text {
  color: var(--primary-color);
}

.graph-card {
  height: 100%;
}

.graph-header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.graph-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f3e8ff 0%, #ede9fe 100%);
  color: #7c3aed;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin-right: 12px;
}

.graph-info {
  flex: 1;
  min-width: 0;
}

.graph-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--el-text-color-primary);
}

.graph-body {
  margin-bottom: 12px;
}

.graph-desc {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.graph-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 12px;
  border-top: 1px solid var(--el-fill-color-light);
}

.graph-stats {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.graph-stats .separator {
  margin: 0 6px;
}

.more-btn {
  cursor: pointer;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

.more-btn:hover {
  color: var(--primary-color);
}
</style>
