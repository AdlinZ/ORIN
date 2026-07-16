<template>
  <div :class="['mcp-service-container', { 'is-embedded': embedded }]">
    <OrinPageShell
      v-if="!embedded"
      domain="系统控制"
      title="MCP 服务管理"
      description="管理 MCP（Model Context Protocol）服务配置、连接健康与工具安装。"
      icon="Service"
    >
      <template #actions>
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          添加服务
        </el-button>
      </template>
      <template #filters>
        <div class="mcp-collection-workbar">
          <div class="workbar-heading">
            <h2>{{ collectionTitle }}</h2>
            <span>{{ activeResultCount }} 个结果</span>
          </div>
          <div class="workbar-controls">
            <div class="collection-mode-switch" role="tablist" aria-label="MCP 内容模式">
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
            <el-input
              v-if="activeTab === 'list'"
              v-model="searchQuery"
              placeholder="搜索服务名称"
              class="workbar-search"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button
              :loading="activeTab === 'list' ? loading : toolsLoading"
              @click="refreshActiveCollection"
            >
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
    </OrinPageShell>

    <div v-if="embedded" class="mcp-collection-workbar embedded-toolbar">
      <div class="workbar-heading">
        <h2>{{ collectionTitle }}</h2>
        <span>{{ activeResultCount }} 个结果</span>
      </div>
      <div class="workbar-controls">
        <div class="collection-mode-switch" role="tablist" aria-label="MCP 内容模式">
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
        <el-input
          v-if="activeTab === 'list'"
          v-model="searchQuery"
          placeholder="搜索服务名称"
          class="workbar-search"
          clearable
          @input="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button
          :loading="activeTab === 'list' ? loading : toolsLoading"
          @click="refreshActiveCollection"
        >
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          添加服务
        </el-button>
      </div>
    </div>

    <OrinDataTable compact class="mcp-collection-surface">
      <el-tabs v-model="activeTab" class="mcp-tabs">
        <!-- MCP 服务列表 -->
        <el-tab-pane label="服务列表" name="list">
          <OrinAsyncState
            :status="loadError ? 'error' : loading ? 'loading' : mcpServices.length > 0 ? 'success' : 'empty'"
            empty-text="暂无 MCP 服务，点击“添加服务”创建"
            empty-action-label="添加服务"
            :error-text="loadError || '请稍后重试'"
            @retry="loadMcpServices"
            @empty-action="openAddDialog"
          >
            <div v-if="embedded" class="service-card-grid">
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

            <template v-else>
              <el-table
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
            </template>
          </OrinAsyncState>
        </el-tab-pane>

        <!-- MCP 工具市场 -->
        <el-tab-pane label="工具市场" name="market" :lazy="true">
          <OrinAsyncState
            :status="toolsLoading ? 'loading' : availableTools.length > 0 ? 'success' : 'empty'"
            empty-text="暂无可用工具"
            @retry="loadAvailableTools"
          >
            <div class="tools-grid">
              <article
                v-for="tool in availableTools"
                :key="tool.id"
                class="tool-card"
              >
                <div class="tool-header">
                  <span class="tool-name">{{ tool.name }}</span>
                  <el-tag size="small" :type="tool.installed ? 'success' : 'info'">
                    {{ tool.installed ? '已安装' : '未安装' }}
                  </el-tag>
                </div>
                <p class="tool-desc">
                  {{ tool.description || '暂无描述' }}
                </p>
                <dl class="tool-command-list">
                  <div>
                    <dt>{{ tool.installed ? '当前' : 'Docker' }}</dt>
                    <dd>{{ tool.command || tool.url || '-' }}</dd>
                  </div>
                  <div v-if="tool.localCommand">
                    <dt>本机</dt>
                    <dd>{{ formatLocalCommand(tool) }}</dd>
                  </div>
                </dl>
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
              </article>
            </div>
          </OrinAsyncState>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <span v-if="activeTab === 'list'">共 {{ totalServices }} 个服务</span>
        <span v-else>共 {{ availableTools.length }} 个工具</span>
      </template>
    </OrinDataTable>

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
          <div class="env-secret-insert">
            <el-select
              v-model="secretRefToInsert"
              size="small"
              placeholder="插入密钥引用"
              clearable
              @change="insertSecretRef"
            >
              <el-option
                v-for="s in mcpSecrets"
                :key="s.secretId"
                :label="`${s.name}（${s.maskedSecret}）`"
                :value="s.secretId"
              />
            </el-select>
            <el-button size="small" text @click="openCreateSecretDialog">
              + 新建密钥
            </el-button>
          </div>
          <div class="form-tip">
            每行一个环境变量，格式: KEY=VALUE。敏感变量（*_TOKEN/_KEY/_SECRET）只能用
            <code>KEY=${secret:xxx}</code> 引用，明文会被拒绝。
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

    <!-- 市场安装对话框 -->
    <el-dialog
      v-model="installDialogVisible"
      title="安装 MCP 工具"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="installFormRef"
        :model="installForm"
        :rules="installFormRules"
        label-width="100px"
        label-position="top"
      >
        <el-form-item label="工具">
          <div class="install-tool-summary">
            <strong>{{ installForm.tool?.name || '-' }}</strong>
            <span>{{ installForm.tool?.description || '暂无描述' }}</span>
          </div>
        </el-form-item>
        <el-form-item label="安装模式" prop="mode">
          <el-radio-group v-model="installForm.mode">
            <el-radio-button value="docker">
              Docker
            </el-radio-button>
            <el-radio-button value="local">
              本机
            </el-radio-button>
          </el-radio-group>
          <div class="form-tip">
            Docker 使用容器内默认路径；本机模式使用本地命令模板。
          </div>
        </el-form-item>
        <el-form-item
          v-if="requiresLocalPath(installForm.tool) && installForm.mode === 'local'"
          :label="localPathLabel(installForm.tool)"
          prop="localPath"
        >
          <el-input
            v-model="installForm.localPath"
            :placeholder="localPathPlaceholder(installForm.tool)"
            clearable
          />
          <div class="form-tip">
            {{ localPathTip(installForm.tool) }}
          </div>
        </el-form-item>
        <el-form-item label="最终命令">
          <el-input :model-value="installCommandPreview" readonly />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="installDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="installingToolKey === installForm.tool?.key" @click="confirmInstallTool">
          安装
        </el-button>
      </template>
    </el-dialog>

    <!-- 新建 MCP 密钥对话框 -->
    <el-dialog
      v-model="secretDialogVisible"
      title="新建 MCP 密钥"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="secretFormRef"
        :model="secretForm"
        :rules="secretFormRules"
        label-width="80px"
        label-position="top"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="secretForm.name" placeholder="例如 github-token" />
        </el-form-item>
        <el-form-item label="密钥值" prop="secret">
          <el-input
            v-model="secretForm.secret"
            type="password"
            show-password
            placeholder="粘贴真实 token / key，仅加密存储，不会回显"
          />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="secretForm.description" placeholder="可选" />
        </el-form-item>
        <div class="form-tip">
          创建后通过 <code>${secret:xxx}</code> 引用，明文只在后端解析下发给 AI Engine。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="secretDialogVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="creatingSecret" @click="confirmCreateSecret">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import OrinAsyncState from '@/components/orin/OrinAsyncState.vue'
import OrinDataTable from '@/components/orin/OrinDataTable.vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import {
  getMcpServices,
  createMcpService,
  updateMcpService,
  deleteMcpService,
  testMcpConnection,
  getMcpTools,
  installMcpTool,
  setMcpServiceEnabled,
  getMcpSecrets,
  createMcpSecret
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
const installFormRef = ref(null)
const loadError = ref('')

// MCP env 密钥引用
const mcpSecrets = ref([])
const secretRefToInsert = ref('')
const secretDialogVisible = ref(false)
const secretFormRef = ref(null)
const creatingSecret = ref(false)
const secretForm = reactive({
  name: '',
  secret: '',
  description: ''
})
const secretFormRules = {
  name: [{ required: true, message: '请填写密钥名称', trigger: 'blur' }],
  secret: [{ required: true, message: '请填写密钥值', trigger: 'blur' }]
}

// 服务搜索与结果口径
const searchQuery = ref('')
const totalServices = ref(0)
let searchTimer = null

const collectionTitle = computed(() => activeTab.value === 'list' ? '全部服务' : '工具市场')
const activeResultCount = computed(() => (
  activeTab.value === 'list' ? totalServices.value : availableTools.value.length
))

const serviceForm = reactive({
  id: null,
  name: '',
  type: 'STDIO',
  command: '',
  url: '',
  envVars: '',
  description: ''
})

const installForm = reactive({
  tool: null,
  mode: 'docker',
  localPath: ''
})

const installDialogVisible = ref(false)
const localPathToolKeys = new Set(['filesystem', 'sqlite'])

const installCommandPreview = computed(() => {
  const tool = installForm.tool
  if (!tool) return ''
  if (installForm.mode === 'local') {
    return buildLocalCommand(tool, installForm.localPath)
  }
  return tool.command || tool.url || ''
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

const installFormRules = {
  localPath: [
    { validator: (rule, value, callback) => {
      const path = String(value || '').trim()
      if (installForm.mode === 'local' && requiresLocalPath(installForm.tool) && !path) {
        callback(new Error('请填写本机路径'))
      } else if (path && !isAbsolutePath(path)) {
        callback(new Error('请填写绝对路径'))
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
      keyword: searchQuery.value.trim() || undefined
    }
    const res = await getMcpServices(params)
    const rows = Array.isArray(res)
      ? res
      : Array.isArray(res?.data) ? res.data : []
    mcpServices.value = rows
    totalServices.value = Number.isFinite(Number(res?.total))
      ? Number(res.total)
      : rows.length
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
  window.clearTimeout(searchTimer)
  searchTimer = window.setTimeout(loadMcpServices, 250)
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

const refreshActiveCollection = () => {
  if (activeTab.value === 'market') {
    refreshTools()
    return
  }
  loadMcpServices()
}

const installTool = (tool) => {
  if (!tool?.key) return
  Object.assign(installForm, {
    tool,
    mode: 'docker',
    localPath: ''
  })
  if (installFormRef.value) {
    installFormRef.value.clearValidate()
  }
  installDialogVisible.value = true
}

const confirmInstallTool = async () => {
  if (!installForm.tool?.key || !installFormRef.value) return
  await installFormRef.value.validate(async (valid) => {
    if (!valid) return

    const tool = installForm.tool
    installingToolKey.value = tool.key
    try {
      const service = await installMcpTool(tool.key, { mode: installForm.mode })
      if (installForm.mode === 'local' && requiresLocalPath(tool)) {
        await updateInstalledLocalCommand(service, tool)
      }
      ElMessage.success(`已安装: ${tool.name}`)
      installDialogVisible.value = false
      await Promise.all([loadAvailableTools(), loadMcpServices()])
    } catch (e) {
      ElMessage.error('安装失败: ' + (e.message || e))
    } finally {
      installingToolKey.value = ''
    }
  })
}

const updateInstalledLocalCommand = async (service, tool) => {
  const serviceId = service?.id || service?.serviceId
  if (!serviceId) return
  await updateMcpService(serviceId, {
    name: service.name || tool.name,
    type: service.type || tool.type || 'STDIO',
    command: buildLocalCommand(tool, installForm.localPath),
    url: service.url || tool.url || null,
    envVars: service.envVars || null,
    description: service.description || tool.description || null,
    toolKey: service.toolKey || tool.key,
    enabled: service.enabled ?? true
  })
}

const requiresLocalPath = (tool) => {
  return Boolean(tool?.key && localPathToolKeys.has(tool.key))
}

const localPathLabel = (tool) => {
  return tool?.key === 'sqlite' ? 'SQLite 文件路径' : '允许访问目录'
}

const localPathPlaceholder = (tool) => {
  return tool?.key === 'sqlite'
    ? '/path/to/your/database.sqlite'
    : '/path/to/your/project'
}

const localPathTip = (tool) => {
  return tool?.key === 'sqlite'
    ? '填写本机可访问的 .sqlite/.db 文件绝对路径。'
    : '填写本机允许 MCP filesystem 访问的目录绝对路径。'
}

const formatLocalCommand = (tool) => {
  if (!tool?.localCommand) return '-'
  return requiresLocalPath(tool) ? `${tool.localCommand} <本机路径>` : tool.localCommand
}

const buildLocalCommand = (tool, localPath) => {
  const base = tool?.localCommand || tool?.command || tool?.key || ''
  if (!requiresLocalPath(tool)) return base
  const path = String(localPath || '').trim()
  return path ? `${base} ${quoteCommandArg(path)}` : `${base} <本机路径>`
}

const quoteCommandArg = (value) => {
  const text = String(value || '').trim()
  if (!text) return ''
  return /[\s'"\\]/.test(text) ? `'${text.replace(/'/g, "'\\''")}'` : text
}

const isAbsolutePath = (value) => {
  return value.startsWith('/') || /^[A-Za-z]:[\\/]/.test(value)
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

const loadMcpSecrets = async () => {
  try {
    const data = await getMcpSecrets()
    mcpSecrets.value = Array.isArray(data) ? data : []
  } catch (e) {
    mcpSecrets.value = []
  }
}

const insertSecretRef = (secretId) => {
  if (!secretId) return
  const ref = `\${secret:${secretId}}`
  const current = serviceForm.envVars || ''
  serviceForm.envVars = current && !current.endsWith('\n')
    ? `${current}\n${ref}`
    : `${current}${ref}`
  secretRefToInsert.value = ''
}

const openCreateSecretDialog = () => {
  Object.assign(secretForm, { name: '', secret: '', description: '' })
  if (secretFormRef.value) {
    secretFormRef.value.clearValidate()
  }
  secretDialogVisible.value = true
}

const confirmCreateSecret = async () => {
  if (!secretFormRef.value) return
  await secretFormRef.value.validate(async (valid) => {
    if (!valid) return
    creatingSecret.value = true
    try {
      const created = await createMcpSecret({
        name: secretForm.name,
        secret: secretForm.secret,
        description: secretForm.description
      })
      ElMessage.success('密钥已创建')
      secretDialogVisible.value = false
      await loadMcpSecrets()
      if (created?.secretId) {
        insertSecretRef(created.secretId)
      }
    } catch (e) {
      ElMessage.error('创建密钥失败: ' + (e.message || e))
    } finally {
      creatingSecret.value = false
    }
  })
}

onMounted(() => {
  loadMcpServices()
  loadAvailableTools()
  loadMcpSecrets()
})

onUnmounted(() => {
  window.clearTimeout(searchTimer)
})
</script>

<style scoped>
.mcp-service-container {
  padding: 0;
}

.mcp-collection-workbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  gap: 18px;
}

.embedded-toolbar {
  margin-bottom: 12px;
  padding: 14px;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
}

.workbar-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
  white-space: nowrap;
}

.workbar-heading h2 {
  margin: 0;
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-size: 16px;
  line-height: 1.35;
  font-weight: var(--font-semibold, 600);
}

.workbar-heading span {
  flex: none;
  padding: 3px 8px;
  border-radius: var(--radius-full, 999px);
  background: var(--neutral-gray-100, var(--el-fill-color-light));
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  font-size: 12px;
}

.workbar-controls {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  min-width: 0;
}

.workbar-search {
  width: min(320px, 28vw);
}

.collection-mode-switch {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 4px;
  padding: 3px;
  border: 1px solid var(--orin-border-strong, var(--el-border-color));
  border-radius: 8px;
  background: var(--neutral-gray-100, var(--el-fill-color-light));
}

.collection-mode-switch button {
  height: 30px;
  padding: 0 11px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--neutral-gray-500, var(--el-text-color-secondary));
  cursor: pointer;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 30px;
  transition: background 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.collection-mode-switch button:hover,
.collection-mode-switch button.active {
  background: var(--neutral-white, #ffffff);
  color: var(--orin-primary, var(--el-color-primary));
  box-shadow: 0 5px 14px -12px rgba(15, 23, 42, 0.55);
}

.mcp-service-container :deep(.mcp-tabs > .el-tabs__header) {
  display: none;
}

.mcp-service-container :deep(.mcp-tabs > .el-tabs__content) {
  overflow: visible;
}

.mcp-collection-surface :deep(.orin-async-state > .el-skeleton) {
  padding: 18px;
}

.form-tip {
  font-size: 12px;
  color: var(--neutral-gray-400);
  margin-top: 4px;
}

.form-tip code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 11px;
}

.env-secret-insert {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.env-secret-insert .el-select {
  width: 220px;
}

.tools-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  padding: 14px;
}

.tool-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 176px;
  padding: 14px;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background: color-mix(in srgb, var(--neutral-white, #ffffff) 94%, var(--orin-primary, #0d9488) 6%);
}

.service-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 10px;
  padding: 14px;
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
  color: var(--neutral-gray-900, var(--el-text-color-primary));
  font-weight: 600;
}

.tool-desc {
  font-size: 13px;
  color: #606266;
  margin: 8px 0;
}

.tool-command-list {
  display: grid;
  gap: 6px;
  margin: 10px 0 0;
  padding: 0;
}

.tool-command-list div {
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  gap: 8px;
  align-items: baseline;
}

.tool-command-list dt {
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
}

.tool-command-list dd {
  min-width: 0;
  margin: 0;
  color: #334155;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  font-size: 12px;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tool-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
  padding-top: 12px;
}

.tool-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.install-tool-summary {
  display: grid;
  gap: 5px;
  width: 100%;
}

.install-tool-summary strong {
  color: #0f172a;
  font-size: 15px;
  line-height: 1.35;
}

.install-tool-summary span {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
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
  background: var(--neutral-gray-900, #0f172a);
}

html.dark .workbar-heading h2,
html.dark .service-title-wrap h3,
html.dark .tool-name {
  color: #f8fafc;
}

html.dark .service-title-wrap span,
html.dark .service-meta,
html.dark .tool-command-list dt,
html.dark .install-tool-summary span {
  color: #94a3b8;
}

html.dark .tool-command-list dd {
  color: #cbd5e1;
}

html.dark .install-tool-summary strong {
  color: #f8fafc;
}

html.dark .service-card-item,
html.dark .tool-card {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.72);
}

html.dark .collection-mode-switch {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.7);
}

html.dark .collection-mode-switch button {
  color: #94a3b8;
}

html.dark .collection-mode-switch button:hover,
html.dark .collection-mode-switch button.active {
  background: rgba(255, 255, 255, 0.08);
  color: #93c5fd;
  box-shadow: none;
}

@media (max-width: 980px) {
  .mcp-collection-workbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .workbar-controls {
    justify-content: flex-start;
    width: 100%;
    flex-wrap: wrap;
  }

  .workbar-search {
    width: min(420px, 100%);
    flex: 1 1 260px;
  }
}

@media (max-width: 640px) {
  .workbar-controls,
  .service-card-head {
    flex-direction: column;
    align-items: stretch;
  }

  .workbar-search,
  .collection-mode-switch,
  .workbar-controls > :deep(.el-button) {
    width: 100%;
    flex: none;
  }

  .collection-mode-switch button {
    flex: 1;
  }

  .service-card-grid,
  .tools-grid {
    grid-template-columns: 1fr;
  }

  .env-secret-insert {
    align-items: stretch;
    flex-direction: column;
  }

  .env-secret-insert .el-select {
    width: 100%;
  }
}
</style>
