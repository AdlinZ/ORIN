<template>
  <div :class="['mcp-service-container', { 'is-embedded': embedded }]">
    <el-card shadow="never" class="tab-wrapper-card">
      <PageHeader
        v-if="!embedded"
        flat
        title="MCP 服务管理"
        description="管理 MCP (Model Context Protocol) 服务的配置和连接"
        icon="Service"
      />

      <div v-else class="embedded-toolbar">
        <div class="embedded-toolbar-main">
          <div>
            <h2 class="embedded-title">MCP服务</h2>
            <p class="embedded-description">管理 MCP（Model Context Protocol）服务配置、连接健康与工具安装</p>
          </div>
          <el-button type="primary" @click="openAddDialog">
            <el-icon><Plus /></el-icon>
            添加服务
          </el-button>
        </div>
        <div class="embedded-toolbar-bottom">
          <div class="embedded-stats">
            <div class="mcp-stat">
              <span>服务</span>
              <strong>{{ mcpStats.total }}</strong>
            </div>
            <div class="mcp-stat">
              <span>已启用</span>
              <strong>{{ mcpStats.enabled }}</strong>
            </div>
            <div class="mcp-stat">
              <span>已连接</span>
              <strong>{{ mcpStats.connected }}</strong>
            </div>
            <div class="mcp-stat">
              <span>工具</span>
              <strong>{{ availableTools.length }}</strong>
            </div>
          </div>
          <div class="embedded-mode-switch" role="tablist" aria-label="MCP 视图">
            <button
              type="button"
              :class="{ active: activeTab === 'list' }"
              role="tab"
              :aria-selected="activeTab === 'list'"
              @click="activeTab = 'list'"
            >
              服务列表
            </button>
            <button
              type="button"
              :class="{ active: activeTab === 'market' }"
              role="tab"
              :aria-selected="activeTab === 'market'"
              @click="activeTab = 'market'"
            >
              工具市场
            </button>
          </div>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="mcp-tabs">
      <!-- MCP 服务列表 -->
      <el-tab-pane label="服务列表" name="list">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>MCP 服务</span>
              <el-button v-if="!embedded" type="primary" @click="openAddDialog">
                <el-icon><Plus /></el-icon>
                添加服务
              </el-button>
            </div>
          </template>

          <!-- 搜索栏 -->
          <div class="toolbar">
            <el-input
              v-model="searchQuery"
              placeholder="搜索服务名称..."
              class="search-input"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button circle class="icon-btn" @click="loadMcpServices">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
          <el-alert
            v-if="loadError"
            class="load-error"
            type="error"
            show-icon
            :closable="false"
            :title="loadError"
          >
            <template #default>
              <el-button type="primary" text @click="loadMcpServices">重试</el-button>
            </template>
          </el-alert>

          <div v-if="embedded && mcpServices.length" class="service-card-grid">
            <article v-for="service in mcpServices" :key="service.id" class="service-card-item">
              <div class="service-card-head">
                <div class="service-title-wrap">
                  <h3>{{ service.name }}</h3>
                  <span>{{ service.type === 'STDIO' ? service.command : service.url }}</span>
                </div>
                <div class="service-tags">
                  <el-tag>{{ getTypeText(service.type) }}</el-tag>
                  <el-tag :type="getStatusType(service.status)">
                    {{ getStatusText(service.status) }}
                  </el-tag>
                </div>
              </div>
              <div class="service-meta">
                <span>启用：{{ service.enabled ? '是' : '否' }}</span>
                <span>健康分：{{ service.healthScore ?? '-' }}</span>
                <span>最后连接：{{ service.lastConnected ? formatDate(service.lastConnected) : '-' }}</span>
              </div>
              <div class="service-actions">
                <el-button
                  size="small"
                  :loading="testingId === service.id"
                  @click="testConnection(service)"
                >
                  测试
                </el-button>
                <el-button size="small" type="primary" @click="editService(service)">
                  编辑
                </el-button>
                <el-button size="small" type="danger" @click="deleteService(service)">
                  删除
                </el-button>
              </div>
            </article>
          </div>

          <el-empty
            v-else-if="embedded && !loading && !loadError"
            :image-size="72"
            description="暂无 MCP 服务，点击“添加服务”创建"
          />

          <el-table
            v-else-if="!embedded"
            v-loading="loading"
            :data="mcpServices"
            empty-text="暂无 MCP 服务，点击“添加服务”创建"
            stripe
          >
            <el-table-column prop="name" label="服务名称" min-width="150" />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{ row }">
                <el-tag>{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              prop="command"
              label="命令/URL"
              min-width="200"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ row.type === 'STDIO' ? row.command : row.url }}
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="启用" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">
                  {{ row.enabled ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="healthScore" label="健康分" width="80">
              <template #default="{ row }">
                <span :class="['health-score', getHealthClass(row.healthScore)]">
                  {{ row.healthScore ?? '-' }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="lastConnected" label="最后连接" width="180">
              <template #default="{ row }">
                {{ row.lastConnected ? formatDate(row.lastConnected) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  :loading="testingId === row.id"
                  @click="testConnection(row)"
                >
                  测试
                </el-button>
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="editService(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="deleteService(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div v-if="totalServices > 0" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="[10, 20, 50]"
              :total="totalServices"
              layout="total, ->, sizes, prev, pager, next"
              size="small"
              @size-change="handleSizeChange"
              @current-change="handlePageChange"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- MCP 工具市场 -->
      <el-tab-pane label="工具市场" name="market" :lazy="true">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>MCP 工具市场</span>
              <el-button :loading="toolsLoading" @click="refreshTools">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </template>

          <div v-loading="toolsLoading">
            <el-empty v-if="availableTools.length === 0" description="暂无可用工具" />

            <div v-else class="tools-grid">
              <el-card
                v-for="tool in availableTools"
                :key="tool.id"
                class="tool-card"
                shadow="hover"
              >
                <template #header>
                  <div class="tool-header">
                    <span class="tool-name">{{ tool.name }}</span>
                    <el-tag size="small" :type="tool.installed ? 'success' : 'info'">
                      {{ tool.installed ? '已安装' : '未安装' }}
                    </el-tag>
                  </div>
                </template>
                <p class="tool-desc">
                  {{ tool.description || '暂无描述' }}
                </p>
                <div class="tool-actions">
                  <el-button
                    v-if="!tool.installed && tool.key"
                    size="small"
                    type="primary"
                    :loading="installingToolKey === tool.key"
                    @click="installTool(tool)"
                  >
                    安装
                  </el-button>
                  <el-button
                    v-else-if="tool.installed && tool.serviceId"
                    size="small"
                    :type="tool.enabled ? 'warning' : 'success'"
                    :loading="toggleServiceId === tool.serviceId"
                    @click="toggleTool(tool)"
                  >
                    {{ tool.enabled ? '禁用' : '启用' }}
                  </el-button>
                  <el-button size="small" @click="viewToolDetail(tool)">
                    详情
                  </el-button>
                </div>
              </el-card>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    </el-card>

    <!-- 添加/编辑服务对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑 MCP 服务' : '添加 MCP 服务'" width="600px">
      <el-form
        ref="formRef"
        :model="serviceForm"
        :rules="formRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="服务名称" prop="name">
          <el-input v-model="serviceForm.name" placeholder="如: 文件系统服务" />
        </el-form-item>
        <el-form-item label="服务类型" prop="type">
          <el-radio-group v-model="serviceForm.type">
            <el-radio value="STDIO">
              Stdio
            </el-radio>
            <el-radio value="SSE">
              SSE
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="serviceForm.type === 'STDIO'" label="命令" prop="command">
          <el-input v-model="serviceForm.command" placeholder="npx -y @modelcontextprotocol/server-filesystem /path" />
          <div class="form-tip">
            Stdio 类型需要提供启动命令
          </div>
        </el-form-item>
        <el-form-item v-if="serviceForm.type === 'SSE'" label="URL" prop="url">
          <el-input v-model="serviceForm.url" placeholder="http://localhost:3000/sse" />
        </el-form-item>
        <el-form-item label="环境变量" prop="envVars">
          <el-input
            v-model="serviceForm.envVars"
            type="textarea"
            :rows="3"
            placeholder="KEY=VALUE, 每行一个"
          />
          <div class="form-tip">
            每行一个环境变量，格式: KEY=VALUE
          </div>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="serviceForm.description"
            type="textarea"
            :rows="2"
            placeholder="服务描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveService">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  getMcpServices,
  createMcpService,
  updateMcpService,
  deleteMcpService,
  testMcpConnection,
  getMcpTools,
  installMcpTool,
  setMcpServiceEnabled
} from '@/api/mcp'

defineProps({
  embedded: {
    type: Boolean,
    default: false
  }
})

const activeTab = ref('list')
const loading = ref(false)
const mcpServices = ref([])
const availableTools = ref([])
const toolsLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const testingId = ref(null)
const installingToolKey = ref('')
const toggleServiceId = ref(null)
const formRef = ref(null)
const loadError = ref('')

// 分页和搜索
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const totalServices = ref(0)

const mcpStats = computed(() => {
  const rows = Array.isArray(mcpServices.value) ? mcpServices.value : []
  return {
    total: rows.length,
    enabled: rows.filter((item) => item.enabled).length,
    connected: rows.filter((item) => item.status === 'CONNECTED').length
  }
})

const serviceForm = reactive({
  id: null,
  name: '',
  type: 'STDIO',
  command: '',
  url: '',
  envVars: '',
  description: ''
})

// 表单校验规则
const formRules = {
  name: [
    { required: true, message: '请输入服务名称', trigger: 'blur' }
  ],
  command: [
    { required: true, message: '请输入命令', trigger: 'blur', validator: (rule, value, callback) => {
      if (serviceForm.type === 'STDIO' && !value) {
        callback(new Error('请输入命令'))
      } else {
        callback()
      }
    }}
  ],
  url: [
    { required: true, message: '请输入 URL', trigger: 'blur', validator: (rule, value, callback) => {
      if (serviceForm.type === 'SSE' && !value) {
        callback(new Error('请输入 URL'))
      } else {
        callback()
      }
    }}
  ],
  envVars: [
    { validator: (rule, value, callback) => {
      if (value) {
        const lines = value.split('\n').filter(l => l.trim())
        const invalid = lines.filter(l => !l.includes('='))
        if (invalid.length > 0) {
          callback(new Error('环境变量格式错误，每行应为 KEY=VALUE'))
        } else {
          callback()
        }
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ]
}

// 加载 MCP 服务列表
const loadMcpServices = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const params = {
      keyword: searchQuery.value || undefined,
      page: currentPage.value - 1,
      size: pageSize.value
    }
    const res = await getMcpServices(params)
    mcpServices.value = res || []
    totalServices.value = res?.length || 0
  } catch (e) {
    console.error('加载 MCP 服务失败:', e)
    loadError.value = `MCP 服务加载失败：${e?.message || '未知错误'}`
    ElMessage.error('加载失败: ' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  currentPage.value = 1
  loadMcpServices()
}

// 分页
const handlePageChange = (page) => {
  currentPage.value = page
  loadMcpServices()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  loadMcpServices()
}

// 加载可用工具
const loadAvailableTools = async () => {
  toolsLoading.value = true
  try {
    const res = await getMcpTools()
    availableTools.value = res || []
  } catch (e) {
    console.error('加载工具市场失败:', e)
  } finally {
    toolsLoading.value = false
  }
}

// 刷新工具
const refreshTools = () => {
  loadAvailableTools()
  ElMessage.success('已刷新')
}

const installTool = async (tool) => {
  if (!tool?.key) return
  installingToolKey.value = tool.key
  try {
    await installMcpTool(tool.key)
    ElMessage.success(`已安装: ${tool.name}`)
    await Promise.all([loadAvailableTools(), loadMcpServices()])
  } catch (e) {
    ElMessage.error('安装失败: ' + (e.message || e))
  } finally {
    installingToolKey.value = ''
  }
}

const toggleTool = async (tool) => {
  if (!tool?.serviceId) return
  toggleServiceId.value = tool.serviceId
  try {
    const enabled = !tool.enabled
    await setMcpServiceEnabled(tool.serviceId, enabled)
    ElMessage.success(enabled ? '服务已启用' : '服务已禁用')
    await Promise.all([loadAvailableTools(), loadMcpServices()])
  } catch (e) {
    ElMessage.error('更新状态失败: ' + (e.message || e))
  } finally {
    toggleServiceId.value = null
  }
}

// 打开添加对话框
const openAddDialog = async () => {
  isEdit.value = false
  Object.assign(serviceForm, {
    id: null,
    name: '',
    type: 'STDIO',
    command: '',
    url: '',
    envVars: '',
    description: ''
  })
  // 重置表单校验
  if (formRef.value) {
    formRef.value.resetFields()
  }
  dialogVisible.value = true
}

// 编辑服务
const editService = (row) => {
  isEdit.value = true
  Object.assign(serviceForm, {
    id: row.id,
    name: row.name,
    type: row.type || 'STDIO',
    command: row.command || '',
    url: row.url || '',
    envVars: row.envVars || '',
    description: row.description || ''
  })
  dialogVisible.value = true
}

// 保存服务
const saveService = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    saving.value = true
    try {
      const data = {
        name: serviceForm.name,
        type: serviceForm.type,
        command: serviceForm.type === 'STDIO' ? serviceForm.command : null,
        url: serviceForm.type === 'SSE' ? serviceForm.url : null,
        envVars: serviceForm.envVars || null,
        description: serviceForm.description || null
      }

      if (isEdit.value) {
        await updateMcpService(serviceForm.id, data)
        ElMessage.success('服务已更新')
      } else {
        await createMcpService(data)
        ElMessage.success('服务已添加')
      }

      dialogVisible.value = false
      loadMcpServices()
    } catch (e) {
      ElMessage.error('保存失败: ' + (e.message || e))
    } finally {
      saving.value = false
    }
  })
}

// 删除服务
const deleteService = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要删除服务 "${row.name}" 吗?`, '提示', {
      type: 'warning'
    })
    await deleteMcpService(row.id)
    ElMessage.success('服务已删除')
    loadMcpServices()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败: ' + (e.message || e))
    }
  }
}

// 测试连接
const testConnection = async (row) => {
  testingId.value = row.id
  try {
    const res = await testMcpConnection(row.id)
    if (res.success) {
      ElMessage.success('连接成功')
    } else {
      ElMessage.error(res.message + (res.errorDetail ? `\n原因: ${res.errorDetail}` : ''))
    }
    // 刷新列表以获取最新状态
    loadMcpServices()
  } catch (e) {
    ElMessage.error('测试失败: ' + (e.message || e))
  } finally {
    testingId.value = null
  }
}

// 查看工具详情
const viewToolDetail = (tool) => {
  const endpoint = tool.type === 'STDIO' ? (tool.command || '未配置命令') : (tool.url || '未配置 URL')
  ElMessage.info(`工具详情: ${tool.name} (${tool.type}) - ${endpoint}`)
}

const getTypeText = (type) => {
  const map = {
    'STDIO': 'Stdio',
    'SSE': 'SSE'
  }
  return map[type] || type
}

const getStatusText = (status) => {
  const map = {
    'CONNECTED': '已连接',
    'DISCONNECTED': '未连接',
    'ERROR': '错误',
    'TESTING': '测试中'
  }
  return map[status] || status
}

const getStatusType = (status) => {
  const map = {
    'CONNECTED': 'success',
    'DISCONNECTED': 'info',
    'ERROR': 'danger',
    'TESTING': 'warning'
  }
  return map[status] || 'info'
}

const getHealthClass = (score) => {
  if (score === null || score === undefined) return ''
  if (score >= 80) return 'health-good'
  if (score >= 50) return 'health-warning'
  return 'health-error'
}

const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString('zh-CN')
}

onMounted(() => {
  loadMcpServices()
  loadAvailableTools()
})
</script>

<style scoped>
.mcp-service-container {
  padding: 0;
}

.mcp-service-container.is-embedded :deep(.tab-wrapper-card) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
}

.mcp-service-container.is-embedded :deep(.tab-wrapper-card .el-card__body) {
  padding: 0;
}

.embedded-toolbar {
  margin-bottom: 12px;
  padding: 18px;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background:
    linear-gradient(135deg, rgba(239, 246, 255, 0.74), rgba(255, 255, 255, 0.96) 54%),
    var(--neutral-white);
  box-shadow: 0 10px 26px -24px rgba(15, 23, 42, 0.42);
}

.embedded-toolbar-main {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.embedded-toolbar-bottom {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-end;
}

.embedded-title {
  margin: 0;
  font-size: 22px;
  line-height: 1.2;
  color: #0f172a;
  letter-spacing: 0;
}

.embedded-description {
  margin: 7px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.embedded-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(92px, 1fr));
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.mcp-stat {
  padding: 10px 12px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(255, 255, 255, 0.72);
}

.mcp-stat span {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
}

.mcp-stat strong {
  display: block;
  margin-top: 4px;
  color: #2563eb;
  font-size: 19px;
  line-height: 1.1;
}

.embedded-mode-switch {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  padding: 4px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.8);
}

.embedded-mode-switch button {
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  line-height: 32px;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.embedded-mode-switch button:hover,
.embedded-mode-switch button.active {
  background: #ffffff;
  color: var(--orin-primary);
  box-shadow: 0 5px 14px -12px rgba(15, 23, 42, 0.55);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.load-error {
  margin-bottom: 12px;
}

.search-input {
  width: 300px;
}

.icon-btn {
  flex-shrink: 0;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.tool-card {
  margin-bottom: 0;
}

.mcp-service-container :deep(.el-card) {
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
}

.mcp-service-container.is-embedded :deep(.el-tabs__content .el-card__body) {
  padding: 14px;
}

.mcp-service-container.is-embedded :deep(.el-tabs__content .el-card) {
  box-shadow: none;
}

.mcp-service-container.is-embedded :deep(.mcp-tabs > .el-tabs__header) {
  display: none !important;
}

.service-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
}

.service-card-item {
  padding: 14px;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(255, 255, 255, 0.86);
}

.service-card-head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: flex-start;
}

.service-title-wrap {
  min-width: 0;
}

.service-title-wrap h3 {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
  letter-spacing: 0;
}

.service-title-wrap span {
  display: block;
  margin-top: 6px;
  max-width: 100%;
  color: #64748b;
  font-size: 12px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.service-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.service-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  margin-top: 12px;
  color: #64748b;
  font-size: 12px;
}

.service-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.service-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.tool-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tool-name {
  font-weight: 500;
}

.tool-desc {
  font-size: 13px;
  color: #606266;
  margin: 8px 0;
}

.tool-actions {
  margin-top: 12px;
}

.health-score {
  font-weight: 500;
}

.health-good {
  color: var(--success-500);
}

.health-warning {
  color: var(--warning-500);
}

.health-error {
  color: var(--error-500);
}

html.dark .embedded-toolbar {
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.12), rgba(15, 23, 42, 0.94) 56%),
    var(--neutral-gray-900, #0f172a);
  box-shadow: none;
}

html.dark .embedded-title,
html.dark .service-title-wrap h3 {
  color: #f8fafc;
}

html.dark .embedded-description,
html.dark .mcp-stat span,
html.dark .service-title-wrap span,
html.dark .service-meta {
  color: #94a3b8;
}

html.dark .mcp-stat,
html.dark .service-card-item {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.72);
}

html.dark .mcp-stat strong {
  color: #93c5fd;
}

html.dark .embedded-mode-switch {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.7);
}

html.dark .embedded-mode-switch button {
  color: #94a3b8;
}

html.dark .embedded-mode-switch button:hover,
html.dark .embedded-mode-switch button.active {
  background: rgba(255, 255, 255, 0.08);
  color: #93c5fd;
  box-shadow: none;
}

@media (max-width: 720px) {
  .embedded-toolbar-main,
  .embedded-toolbar-bottom,
  .card-header,
  .toolbar,
  .service-card-head {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }

  .embedded-mode-switch {
    width: 100%;
  }

  .embedded-mode-switch button {
    flex: 1;
  }
}
</style>
