<template>
  <div class="top-navbar">
    <!-- Logo 区域 -->
    <div class="navbar-logo" @click="goHome">
      <BrandingLogo :height="44" />
    </div>

    <!-- 中间菜单区域 -->
    <nav class="navbar-menu">
      <div 
        v-for="menu in visibleMenus" 
        :key="menu.id"
        class="menu-item"
        :class="{ active: activeMenuId === menu.id }"
        @mouseenter="handleMenuHover(menu.id)"
        @mouseleave="handleMenuLeave"
        @click="handleMenuClick(menu)"
      >
        <el-icon>
          <component :is="getIconComponent(menu.icon)" />
        </el-icon>
        <span class="menu-title">{{ menu.title }}</span>
        
        <!-- 下拉二级菜单 -->
        <transition name="dropdown">
          <div 
            v-show="activeDropdown === menu.id" 
            class="dropdown-menu"
            @mouseenter="keepDropdownOpen(menu.id)"
            @mouseleave="handleMenuLeave"
          >
            <router-link 
              v-for="child in menu.children"
              :key="child.path"
              :to="child.path"
              class="dropdown-item"
              @click="closeDropdown"
            >
              <el-icon v-if="child.icon">
                <component :is="getIconComponent(child.icon)" />
              </el-icon>
              <span>{{ child.title }}</span>
            </router-link>
          </div>
        </transition>
      </div>
    </nav>

    <!-- 右侧操作区 -->
    <div class="navbar-actions">
      <!-- 动态插槽容器 (Teleport 目标) -->
      <div id="navbar-actions" class="navbar-page-actions"></div>
      <!-- 刷新按钮 -->
      <div class="action-item">
        <el-tooltip content="刷新页面" placement="bottom">
          <el-button text :icon="Refresh" @click="handleRefresh" class="action-btn" />
        </el-tooltip>
      </div>

      <!-- 主题切换按钮 -->
      <div class="action-item">
        <el-tooltip :content="isDarkMode ? '切换到浅色模式' : '切换到深色模式'" placement="bottom">
          <el-button 
            text 
            :icon="isDarkMode ? Sunny : Moon" 
            @click="toggleTheme" 
            class="action-btn" 
          />
        </el-tooltip>
      </div>

      <!-- 通知图标 -->
      <div class="action-item">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="notification-badge">
          <el-tooltip content="通知中心" placement="bottom">
            <el-button text :icon="Bell" @click="showNotifications" class="action-btn" />
          </el-tooltip>
        </el-badge>
      </div>

      <!-- 分隔线 -->
      <div class="action-divider"></div>

      <!-- 系统 AI 按钮 -->
      <el-tooltip content="系统 AI 助手" placement="bottom">
        <div class="system-ai-btn" @click="showSystemAI">
          <el-icon><DataAnalysis /></el-icon>
          <span>AI 助手</span>
        </div>
      </el-tooltip>

      <!-- 用户下拉菜单 -->
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-info">
          <el-avatar :src="userInfo.avatar" :size="36">
            {{ userInfo.name?.charAt(0) }}
          </el-avatar>
          <span class="user-name">{{ userInfo.name }}</span>
          <el-icon class="dropdown-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              <span>个人资料</span>
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon>
              <span>账号设置</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="dev_hub">
              <el-icon><Link /></el-icon>
              <span>开发者服务百宝箱</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              <span>退出登录</span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 移动端汉堡菜单 -->
    <div class="mobile-menu-toggle" @click="toggleMobileMenu">
      <el-icon><Menu /></el-icon>
    </div>
  </div>

  <!-- 通知中心抽屉 -->
  <NotificationCenter 
    v-model="showNotificationCenter" 
    @update:unreadCount="handleUnreadCountUpdate"
  />

  <!-- 开发者传送门百宝箱 -->
  <DeveloperHub v-model="showDevHub" />

  <!-- 移动端侧边抽屉 -->
  <el-drawer
    v-model="showMobileMenu"
    direction="ltr"
    size="280px"
    :show-close="false"
  >
    <template #header>
      <div class="mobile-drawer-header">
        <BrandingLogo :height="32" class="drawer-logo" />
      </div>
    </template>
    
    <div class="mobile-menu-content">
      <div v-for="menu in visibleMenus" :key="menu.id" class="mobile-menu-group">
        <div class="mobile-menu-title" :style="{ color: menu.color }">
          <el-icon><component :is="getIconComponent(menu.icon)" /></el-icon>
          <span>{{ menu.title }}</span>
        </div>
        <router-link
          v-for="child in menu.children"
          :key="child.path"
          :to="child.path"
          class="mobile-menu-item"
          @click="closeMobileMenu"
        >
          {{ child.title }}
        </router-link>
      </div>
    </div>
  </el-drawer>

  <!-- System AI Assistant Dialog (ORIN CORE) -->
  <el-dialog
      v-model="showSystemEval"
      :show-close="false"
      :header="null"
      width="1000px"
      class="orin-core-dialog"
      :close-on-click-modal="false"
      append-to-body
      @open="handleSystemDialogOpen"
      align-center
  >
      <template #header>
          <div class="core-header">
              <div class="header-left">
                  <div class="core-logo">
                      <el-icon><Cpu /></el-icon>
                  </div>
                  <div class="core-title">ORIN CORE</div>
                  <div class="header-tabs">
                      <div class="core-tab-btn" :class="{ active: currentDialogTab === 'ai' }" @click="currentDialogTab = 'ai'">
                          <el-icon><MagicStick /></el-icon>
                          <span>AI 助手</span>
                      </div>
                      <div class="core-tab-btn" :class="{ active: currentDialogTab === 'zeroclaw' }" @click="currentDialogTab = 'zeroclaw'">
                          <el-icon><Operation /></el-icon>
                          <span>ZeroClaw</span>
                          <div class="zc-status-dot" :class="{ connected: zeroClawStatus?.connected }"></div>
                      </div>
                  </div>
              </div>
              <div class="header-right">
                  <div class="sys-status">
                      <div class="status-dot"></div>
                      <span>SYSTEM_OK</span>
                  </div>
                  <el-icon class="close-icon" @click="showSystemEval = false"><Close /></el-icon>
              </div>
          </div>
      </template>

      <div class="orin-terminal-container">
          <!-- AI Assistant Tab -->
          <template v-if="currentDialogTab === 'ai'">
              <div class="terminal-body" ref="chatScrollRef">
                  <div v-for="(msg, index) in systemMessages" :key="index" class="terminal-msg-row" :class="msg.role">
                      <div class="t-avatar" v-if="msg.role === 'ai'">
                          <el-icon><HelpFilled /></el-icon>
                      </div>
                      <div class="t-content-group">
                          <div class="t-sender-name" v-if="msg.role === 'ai'">ORIN AI ASSISTANT</div>
                          <div class="t-msg-card">
                              <div class="marketing-text" v-if="msg.role === 'ai' && index === 0">
                                  系统初始化已完成。我是您的系统级 AI 管理助手。<br/><br/>
                                  您可以询问关于系统硬件、软件配置的问题，或者要求我执行脚本、清理日志等操作。
                              </div>
                              <div class="markdown-body" v-else v-html="renderMarkdown(msg.content)"></div>
                              <div class="quick-actions" v-if="msg.role === 'ai' && index === 0">
                                  <div class="q-btn" @click="quickCommand('查看系统资源占用')">
                                      <el-icon><Odometer /></el-icon>
                                      <span>查看系统资源占用</span>
                                  </div>
                                  <div class="q-btn" @click="quickCommand('分析最近的错误日志')">
                                      <el-icon><DocumentChecked /></el-icon>
                                      <span>分析最近的错误日志</span>
                                  </div>
                              </div>
                          </div>
                      </div>
                  </div>
                  <div v-if="systemLoading" class="terminal-msg-row ai">
                      <div class="t-avatar"><el-icon class="is-loading"><Loading /></el-icon></div>
                      <div class="t-content-group">
                          <div class="t-sender-name">ORIN AI ASSISTANT</div>
                          <div class="t-msg-card typing">
                              _COMPUTING...
                          </div>
                      </div>
                  </div>
              </div>
              <div class="terminal-footer">
                  <div class="command-bar">
                      <div class="cmd-prompt">$</div>
                      <input
                          v-model="systemInput"
                          class="cmd-input"
                          placeholder="输入管理指令或询问系统状态 ..."
                      @keyup.enter="sendSystemMessage"
                      :disabled="systemLoading"
                      />
                      <div class="cmd-actions">
                          <span class="cmd-hint">ENTER</span>
                          <button class="run-btn" @click="sendSystemMessage" :disabled="systemLoading">
                              RUN
                          </button>
                      </div>
                  </div>
                  <div class="cmd-statusbar">
                      <span>READY_FOR_COMMAND</span>
                      <span class="secure"><el-icon><Lock /></el-icon> ENCRYPTED</span>
                      <span>ROOT_ACCESS</span>
                  </div>
              </div>
          </template>

          <!-- ZeroClaw Tab -->
          <template v-else-if="currentDialogTab === 'zeroclaw'">
              <div class="zeroclaw-container">
                  <!-- Tab Navigation -->
                  <div class="zc-tabs">
                      <div class="zc-tab" :class="{ active: zeroClawTab === 'status' }" @click="zeroClawTab = 'status'">
                          <el-icon><Operation /></el-icon>
                          <span>状态监控</span>
                      </div>
                      <div class="zc-tab" :class="{ active: zeroClawTab === 'analysis' }" @click="zeroClawTab = 'analysis'">
                          <el-icon><TrendChartsIcon /></el-icon>
                          <span>智能分析</span>
                      </div>
                      <div class="zc-tab" :class="{ active: zeroClawTab === 'healing' }" @click="zeroClawTab = 'healing'">
                          <el-icon><Tools /></el-icon>
                          <span>主动维护</span>
                      </div>
                  </div>

                  <!-- Status Tab -->
                  <div v-if="zeroClawTab === 'status'" class="zc-content">
                      <div class="zc-status-card">
                          <div class="zc-status-header">
                              <div class="zc-status-indicator" :class="{ connected: zeroClawStatus?.connected }">
                                  <div class="zc-dot"></div>
                                  <span>{{ zeroClawStatus?.connected ? '已连接' : '未连接' }}</span>
                              </div>
                              <el-button size="small" :icon="Refresh" @click="fetchZeroClawStatus" :loading="zeroClawLoading">刷新</el-button>
                          </div>
                          <div class="zc-status-info" v-if="zeroClawStatus">
                              <div class="info-item" v-if="zeroClawStatus.configName">
                                  <span class="label">配置名称:</span>
                                  <span class="value">{{ zeroClawStatus.configName }}</span>
                              </div>
                              <div class="info-item">
                                  <span class="label">智能分析:</span>
                                  <span class="value" :class="{ active: zeroClawStatus.analysisEnabled }">
                                      {{ zeroClawStatus.analysisEnabled ? '已启用' : '已禁用' }}
                                  </span>
                              </div>
                              <div class="info-item">
                                  <span class="label">主动维护:</span>
                                  <span class="value" :class="{ active: zeroClawStatus.selfHealingEnabled }">
                                      {{ zeroClawStatus.selfHealingEnabled ? '已启用' : '已禁用' }}
                                  </span>
                              </div>
                          </div>
                          <div class="zc-status-message" v-else>
                              <p v-if="!zeroClawLoading">暂无ZeroClaw配置，请先在系统设置中添加ZeroClaw连接配置。</p>
                              <p v-else>正在加载状态...</p>
                          </div>
                      </div>
                  </div>

                  <!-- Analysis Tab -->
                  <div v-if="zeroClawTab === 'analysis'" class="zc-content">
                      <div class="zc-actions-row">
                          <el-button type="primary" :icon="TrendChartsIcon" @click="runZeroClawAnalysis('PERFORMANCE')" :loading="zeroClawLoading" :disabled="!zeroClawStatus?.connected">
                              性能分析
                          </el-button>
                          <el-button type="primary" :icon="TrendChartsIcon" @click="runZeroClawAnalysis('ANOMALY')" :loading="zeroClawLoading" :disabled="!zeroClawStatus?.connected">
                              异常检测
                          </el-button>
                          <el-button type="primary" :icon="TrendChartsIcon" @click="runZeroClawAnalysis('TREND_FORECAST')" :loading="zeroClawLoading" :disabled="!zeroClawStatus?.connected">
                              趋势预测
                          </el-button>
                      </div>
                      <div class="zc-reports-list">
                          <div class="zc-reports-title">最近分析报告</div>
                          <div v-if="zeroClawReports.length === 0" class="zc-empty">暂无分析报告</div>
                          <div v-for="report in zeroClawReports" :key="report.id" class="zc-report-card">
                              <div class="report-header">
                                  <span class="report-title">{{ report.title }}</span>
                                  <el-tag size="small" :type="getSeverityType(report.severity)">{{ report.severity }}</el-tag>
                              </div>
                              <div class="report-summary">{{ report.summary }}</div>
                              <div class="report-time">
                                  <el-icon><Clock /></el-icon>
                                  <span>{{ formatDate(report.createdAt) }}</span>
                              </div>
                          </div>
                      </div>
                  </div>

                  <!-- Healing Tab -->
                  <div v-if="zeroClawTab === 'healing'" class="zc-content">
                      <div class="zc-actions-row">
                          <el-button type="warning" :icon="Tools" @click="runZeroClawSelfHealing('MEMORY_OPTIMIZATION')" :loading="healingLoading" :disabled="!zeroClawStatus?.connected">
                              内存优化
                          </el-button>
                          <el-button type="warning" :icon="Tools" @click="runZeroClawSelfHealing('LOG_CLEANUP')" :loading="healingLoading" :disabled="!zeroClawStatus?.connected">
                              日志清理
                          </el-button>
                          <el-button type="warning" :icon="Tools" @click="runZeroClawSelfHealing('CACHE_CLEAR')" :loading="healingLoading" :disabled="!zeroClawStatus?.connected">
                              缓存清理
                          </el-button>
                          <el-button type="danger" :icon="Tools" @click="runZeroClawSelfHealing('EMERGENCY_RECOVERY')" :loading="healingLoading" :disabled="!zeroClawStatus?.connected">
                              紧急恢复
                          </el-button>
                      </div>
                      <div class="zc-healing-info">
                          <el-alert type="info" :closable="false" show-icon>
                              主动维护功能可在无需人工干预的情况下自动修复系统问题。请确保ZeroClaw已正确配置。
                          </el-alert>
                      </div>
                  </div>
              </div>
          </template>
      </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useDark } from '@vueuse/core'
import { ROUTES } from '@/router/routes'
import { TOP_MENU_CONFIG, getVisibleMenus, getActiveMenuId } from '@/router/topMenuConfig'
import NotificationCenter from './NotificationCenter.vue'
import DeveloperHub from './DeveloperHub.vue'
import {
  Bell, ArrowDown, User, Setting, Sunny, Moon, SwitchButton, Menu, Refresh, DataAnalysis,
  Box, Monitor, Collection, Setting as SettingIcon,
  List, ChatDotRound, Cpu, MagicStick, Connection,
  DataLine, TrendCharts, Share, Warning,
  Document, Picture, Histogram, Search, View, Grid,
  Notebook, Link, Coin, Loading, Close, HelpFilled, Odometer, DocumentChecked, Lock,
  Operation, TrendCharts as TrendChartsIcon, Tools, CircleCheck, CircleClose, Clock
} from '@element-plus/icons-vue'
import BrandingLogo from '@/components/BrandingLogo.vue'
import { v4 as uuidv4 } from 'uuid'
import { marked } from 'marked'
import { chatAgent, getAgentList } from '@/api/agent'
import { getServerHardware, getTokenHistory } from '@/api/monitor'
import { getModelConfig } from '@/api/modelConfig'
import { getKnowledgeList } from '@/api/knowledge'
import { getZeroClawStatus, performZeroClawAnalysis, executeZeroClawSelfHealing, getZeroClawReports } from '@/api/zeroclaw'
import { ElMessage, ElMessageBox } from 'element-plus'
import Cookies from 'js-cookie'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// Icon mapping
const iconMap = {
  Box, Monitor, Collection, Setting: SettingIcon,
  List, ChatDotRound, Cpu, MagicStick, Connection,
  DataLine, TrendCharts, Share, Warning,
  Document, Picture, Histogram, Search, View, Grid,
  User, Notebook, Link, Coin, Operation, TrendChartsIcon, Tools, Clock
}

// State
const activeDropdown = ref(null)
const showMobileMenu = ref(false)
const showNotificationCenter = ref(false)
const showDevHub = ref(false)
const unreadCount = ref(0) // 初始未读数量为 0

// Dark mode logic (from original Navbar)
const isDarkMode = useDark({
  onChanged(dark) {
    if (dark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }
})

// Computed
const userInfo = computed(() => ({
  name: userStore.userInfo?.username || 'Admin',
  avatar: userStore.userInfo?.avatar || ''
}))

const isAdmin = computed(() => {
  // 使用 store 中的管理员判断，但为了开发方便暂时默认返回 true 以确保“控制”菜单可见
  return userStore.isAdmin || true
})

const visibleMenus = computed(() => {
  return getVisibleMenus(isAdmin.value)
})

const activeMenuId = computed(() => {
  return getActiveMenuId(route.path)
})

// Methods
const getIconComponent = (iconName) => {
  return iconMap[iconName] || Box
}

const goHome = () => {
  router.push(ROUTES.HOME)
}

const handleRefresh = () => {
  // 触发页面刷新事件
  window.dispatchEvent(new Event('page-refresh'))
  
  ElMessage({
    message: '正在刷新页面数据...',
    type: 'info',
    duration: 1500
  })
}

const toggleTheme = () => {
  isDarkMode.value = !isDarkMode.value
}

const handleMenuHover = (menuId) => {
  activeDropdown.value = menuId
}

const handleMenuLeave = () => {
  // 延迟关闭，给用户时间移动到下拉菜单
  setTimeout(() => {
    if (activeDropdown.value) {
      activeDropdown.value = null
    }
  }, 200)
}

const keepDropdownOpen = (menuId) => {
  activeDropdown.value = menuId
}

const handleMenuClick = (menu) => {
  // 只切换下拉菜单，不导航到父路由
  if (activeDropdown.value === menu.id) {
    activeDropdown.value = null
  } else {
    activeDropdown.value = menu.id
  }
}

const closeDropdown = () => {
  activeDropdown.value = null
}

const showNotifications = () => {
  showNotificationCenter.value = true
}

// System AI Logic
const showSystemEval = ref(false)
const systemInput = ref('')
const systemLoading = ref(false)
const chatScrollRef = ref(null)
const systemMessages = ref([
  { role: 'ai', content: '系统内核已就绪。我是 ORIN 全局监控 AI，已接入实时硬件监控、日志系统与知识库索引。请问有什么可以帮您？', time: 'Now' }
])
const currentKernelAgent = ref(null)
const currentConversationId = ref(null)

// ZeroClaw Logic
const zeroClawTab = ref('status') // 'status' | 'analysis' | 'healing'
const zeroClawStatus = ref(null)
const zeroClawLoading = ref(false)
const zeroClawReports = ref([])
const recentHealingActions = ref([])
const healingLoading = ref(false)

// Dialog Tab
const currentDialogTab = ref('ai')

// Handle dialog open
const handleSystemDialogOpen = () => {
  if (currentDialogTab.value === 'ai') {
    initSystemAI()
  } else {
    fetchZeroClawStatus()
    fetchZeroClawReports()
  }
}

// Watch tab change
watch(currentDialogTab, (newTab) => {
  if (newTab === 'zeroclaw') {
    fetchZeroClawStatus()
    fetchZeroClawReports()
  }
})

const renderMarkdown = (text) => {
  try {
    return marked.parse(text || '')
  } catch (e) {
    return text
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (chatScrollRef.value) {
    chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
  }
}

const initSystemAI = async () => {
  // Scroll to bottom
  scrollToBottom()
  // Load available agents to act as "Kernel"
  if (!currentKernelAgent.value) {
    try {
      // 1. Fetch System Configuration
      const configRes = await getModelConfig().catch(() => ({}))
      const preferredModel = configRes.systemModel

      // 2. Fetch Agents
      const res = await getAgentList()
      const agents = res.data || res
      
      if (agents.length > 0) {
        let foundAgent = null

        // 3. Try to match preferred model
        if (preferredModel) {
          foundAgent = agents.find(a => a.modelName === preferredModel || a.modelId === preferredModel)
        }

        // 4. If not found, fallback to 'System' name, then first available
        if (!foundAgent) {
          foundAgent = agents.find(a => a.name.includes('System')) || agents[0]
        }
        
        currentKernelAgent.value = foundAgent
      }
    } catch (e) {
      console.error('Failed to load kernel agent', e)
    }
  }
}

const sendSystemMessage = async () => {
  if (!systemInput.value.trim() || !currentKernelAgent.value) return

  const userMsg = systemInput.value
  systemInput.value = ''
  systemMessages.value.push({ role: 'user', content: userMsg, time: new Date().toLocaleTimeString() })
  scrollToBottom()
  systemLoading.value = true

  try {
    // [PHASE] 1. Gather Real System Data
    const [hardwareRes, agentsRes, kbRes, logsRes] = await Promise.allSettled([
      getServerHardware().catch(e => ({ error: 'Hardware fetch failed' })),
      getAgentList().catch(e => []),
      getKnowledgeList().catch(e => ({ list: [] })),
      getTokenHistory({ size: 5 }).catch(e => ({ content: [] })) // Last 5 tasks
    ])

    // Format Hardware
    const hdw = hardwareRes.status === 'fulfilled' ? hardwareRes.value : { cpuUser: 'N/A', memoryAvailable: 'N/A' }
    const cpuInfo = `Process CPU: ${(hdw.processCpuLoad * 100).toFixed(1)}%, System CPU: ${(hdw.systemCpuLoad * 100).toFixed(1)}%`
    const memInfo = `Available Memory: ${(hdw.memoryAvailable / 1024 / 1024 / 1024).toFixed(2)} GB / ${(hdw.memoryTotal / 1024 / 1024 / 1024).toFixed(2)} GB`
    
    // Format Agents
    const agents = agentsRes.status === 'fulfilled' ? (agentsRes.value.data || agentsRes.value) : []
    const activeAgentsCount = agents.filter(a => a.enabled).length
    const agentSummary = agents.slice(0, 10).map(a => `- [${a.enabled ? 'ON' : 'OFF'}] ${a.name} (${a.modelName})`).join('\n') 
      + (agents.length > 10 ? `\n... and ${agents.length - 10} more` : '')

    // Format Knowledge Bases
    const kbs = kbRes.status === 'fulfilled' ? (kbRes.value.data?.list || []) : []
    const kbSummary = kbs.slice(0, 10).map(k => `- ${k.name} (${k.docCount} docs)`).join('\n')
      + (kbs.length > 10 ? `\n... and ${kbs.length - 10} more` : '')

    // Format Recent Logs
    const logs = logsRes.status === 'fulfilled' ? (logsRes.value.content || []) : []
    const logSummary = logs.map(l => {
      const time = l.createdAt || 'N/A'
      const action = l.endpoint ? l.endpoint.split('/').pop() : 'Request' 
      let detail = l.errorMessage || (l.success ? 'Success' : 'Failed')
      if (detail.length > 50) detail = detail.substring(0, 50) + '...'
      return `- [${time}] ${action}: ${detail}`
    }).join('\n').slice(0, 2000)

    // Construct System Persona Prompt
    const systemPersona = `
You are the **ORIN System Core AI** (ORIN 系统中枢 AI).
You have access to REAL-TIME system metrics. Use them to answer user questions.

### REAL-TIME SYSTEM STATUS:

**1. Hardware Topology:**
- CPU: ${cpuInfo}
- Memory: ${memInfo}

**2. Active Agents (${activeAgentsCount} Active / ${agents.length} Total):**
${agentSummary}

**3. Knowledge Bases:**
${kbSummary}

**4. Recent System Events (logs):**
${logSummary}

### Instructions:
- **Role**: System Administrator & DevOps AI.
- **Tone**: Professional, precise, technical (Cyberpunk style preferred).
- **Language**: Chinese (Simplified).
- **Conciseness**: **Be extremely concise.** Do NOT repeat the full lists of agents or logs unless explicitly asked. Summarize status instead.
- **Output Limit**: Ensure your response fits within standard token limits.

### User Query:
${userMsg}
`

    // Ensure we have a conversation ID for this session
    if (!currentConversationId.value) {
      currentConversationId.value = uuidv4()
    }

    const res = await chatAgent(currentKernelAgent.value.agentId, userMsg, null, systemPersona, currentConversationId.value)
    
    let aiResponse = 'System Busy...'
    
    if (res) {
      if (res.choices && res.choices.length > 0 && res.choices[0].message) {
        aiResponse = res.choices[0].message.content
      } else if (res.data && res.data.choices && res.data.choices[0]) {
        aiResponse = res.data.choices[0].message.content
      } else if (typeof res === 'string') {
        aiResponse = res
      } else if (res.data && typeof res.data === 'string') {
        aiResponse = res.data
      }
    }
    
    systemMessages.value.push({ 
      role: 'ai', 
      content: aiResponse, 
      time: new Date().toLocaleTimeString() 
    })
    scrollToBottom()

  } catch (e) {
    systemMessages.value.push({ role: 'ai', content: 'System Internal Error: ' + e.message })
  } finally {
    systemLoading.value = false
    scrollToBottom()
  }
}

const quickCommand = (cmd) => {
  systemInput.value = cmd
  sendSystemMessage()
}

// ZeroClaw Helpers
const getSeverityType = (severity) => {
  const map = {
    'CRITICAL': 'danger',
    'HIGH': 'danger',
    'MEDIUM': 'warning',
    'LOW': 'info',
    'INFO': 'info'
  }
  return map[severity] || 'info'
}

const formatDate = (dateStr) => {
  if (!dateStr) return 'N/A'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

const showSystemAI = () => {
  showSystemEval.value = true
}

// ZeroClaw Methods
const fetchZeroClawStatus = async () => {
  zeroClawLoading.value = true
  try {
    const res = await getZeroClawStatus()
    zeroClawStatus.value = res
  } catch (e) {
    zeroClawStatus.value = { connected: false, message: 'Failed to fetch status' }
  } finally {
    zeroClawLoading.value = false
  }
}

const fetchZeroClawReports = async () => {
  try {
    const res = await getZeroClawReports({ page: 0, size: 5 })
    zeroClawReports.value = res.content || res.data?.content || []
  } catch (e) {
    zeroClawReports.value = []
  }
}

const runZeroClawAnalysis = async (type = 'PERFORMANCE') => {
  if (!zeroClawStatus.value?.connected) {
    ElMessage.warning('ZeroClaw 未连接，无法执行分析')
    return
  }

  zeroClawLoading.value = true
  try {
    const res = await performZeroClawAnalysis({
      analysisType: type,
      context: `Quick ${type} analysis requested from AI Assistant`
    })
    if (res) {
      ElMessage.success('分析完成，请查看报告')
      await fetchZeroClawReports()
    }
  } catch (e) {
    ElMessage.error('分析失败: ' + e.message)
  } finally {
    zeroClawLoading.value = false
  }
}

const runZeroClawSelfHealing = async (actionType = 'MEMORY_OPTIMIZATION') => {
  if (!zeroClawStatus.value?.connected) {
    ElMessage.warning('ZeroClaw 未连接，无法执行维护')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要执行 "${actionType}" 维护操作吗？此操作可能影响系统运行。`,
      '确认执行',
      { confirmButtonText: '执行', cancelButtonText: '取消', type: 'warning' }
    )

    healingLoading.value = true
    const res = await executeZeroClawSelfHealing({
      actionType,
      targetResource: 'SYSTEM',
      reason: 'Manual trigger from AI Assistant',
      forceExecute: true
    })

    if (res) {
      ElMessage.success(`维护操作 ${res.status === 'SUCCESS' ? '成功' : '失败'}`)
    }
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('维护失败: ' + e.message)
    }
  } finally {
    healingLoading.value = false
  }
}

const handleUnreadCountUpdate = (count) => {
  unreadCount.value = count
}

const handleUserCommand = (command) => {
  switch (command) {
    case 'profile':
      router.push(ROUTES.PROFILE)
      break
    case 'settings':
      router.push(ROUTES.PROFILE)
      break
    case 'dev_hub':
      showDevHub.value = true
      break
    case 'logout':
      handleLogout()
      break
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    Cookies.remove('token')
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}

const toggleMobileMenu = () => {
  showMobileMenu.value = !showMobileMenu.value
}

const closeMobileMenu = () => {
  showMobileMenu.value = false
}

onMounted(() => {
})
</script>

<style scoped>
.top-navbar {
  height: 64px;
  background: white;
  border-bottom: 1px solid var(--neutral-gray-200);
  display: flex;
  align-items: center;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* Logo 区域 */
.navbar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding-left: 12px;
}

.navbar-logo:hover {
  opacity: 0.8;
}

.navbar-logo img {
  height: 44px;
  width: auto;
  display: block;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

/* 一级菜单 - 始终居中 */
.navbar-menu {
  display: flex;
  gap: 8px;
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  user-select: none;
}

.menu-item .el-icon {
  color: var(--neutral-gray-600);
  font-size: 18px;
}

.menu-item:hover {
  background: var(--neutral-gray-50);
}

.menu-item:hover .el-icon,
.menu-item:hover .menu-title {
  color: var(--orin-primary);
}

.menu-item.active {
  background: var(--orin-primary-soft);
}

.menu-item.active .el-icon,
.menu-item.active .menu-title {
  color: var(--orin-primary);
  font-weight: 600;
}

.menu-title {
  font-size: 14px;
  color: var(--neutral-gray-700);
  transition: all 0.3s;
}

/* 下拉菜单 */
.dropdown-menu {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  min-width: 180px;
  padding: 8px;
  z-index: 1001;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 6px;
  color: var(--neutral-gray-700);
  text-decoration: none;
  transition: all 0.2s;
  font-size: 14px;
}

.dropdown-item:hover {
  background: var(--neutral-gray-50);
  color: var(--orin-primary);
}

.dropdown-item .el-icon {
  font-size: 16px;
}

/* 下拉动画 */
.dropdown-enter-active,
.dropdown-leave-active {
  transition: all 0.3s ease;
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 右侧操作区域 */
.navbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.action-item {
  display: flex;
  align-items: center;
}

.action-btn {
  width: 36px;
  height: 36px;
  color: var(--neutral-gray-600);
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
}

.action-btn:hover {
  color: var(--orin-primary);
  background: var(--neutral-gray-50);
}

.notification-badge {
  display: inline-flex;
  align-items: center;
}

.notification-badge :deep(.el-badge__content) {
  transform: translateY(-50%) translateX(50%);
  right: 8px;
  top: 8px;
}

.action-divider {
  width: 1px;
  height: 20px;
  background: var(--neutral-gray-300);
  margin: 0 4px;
}

.system-ai-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  height: 36px;
  border-radius: 8px;
  background: var(--neutral-gray-50);
  border: 1px solid var(--neutral-gray-200);
  color: var(--orin-primary);
  cursor: pointer;
  transition: all 0.3s;
  font-size: 13px;
  font-weight: 600;
}

.system-ai-btn:hover {
  background: var(--orin-primary);
  color: white;
  border-color: var(--orin-primary);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(21, 94, 239, 0.2);
}

.system-ai-btn .el-icon {
  font-size: 16px;
}

.system-ai-btn span {
  font-size: 12px;
  letter-spacing: 0.5px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.user-info:hover {
  background: var(--neutral-gray-50);
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--neutral-gray-900);
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-icon {
  font-size: 12px;
  color: var(--neutral-gray-500);
  transition: transform 0.3s;
}

.user-info:hover .dropdown-icon {
  transform: rotate(180deg);
}

/* 移动端汉堡菜单 */
.mobile-menu-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.mobile-menu-toggle:hover {
  background: var(--neutral-gray-50);
}

.mobile-menu-toggle .el-icon {
  font-size: 24px;
}

/* 移动端抽屉 */
.mobile-drawer-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.drawer-logo {
  width: 32px;
  height: 32px;
}

.drawer-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--neutral-gray-900);
}

.mobile-menu-content {
  padding: 16px 0;
}

.mobile-menu-group {
  margin-bottom: 24px;
}

.mobile-menu-title {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
}

.mobile-menu-item {
  display: block;
  padding: 10px 16px 10px 48px;
  color: var(--neutral-gray-700);
  text-decoration: none;
  font-size: 14px;
  border-radius: 6px;
  margin: 4px 8px;
  transition: all 0.2s;
}

.mobile-menu-item:hover {
  background: var(--neutral-gray-50);
  color: var(--orin-primary);
}

/* 响应式 */
@media (max-width: 1024px) {
  .navbar-menu {
    display: none;
  }
  
  .mobile-menu-toggle {
    display: flex;
  }
  
  .user-name {
    display: none;
  }
}

@media (max-width: 768px) {
  .top-navbar {
    padding: 0 16px;
  }
  
  .navbar-logo {
    margin-right: 16px;
  }
  
  .logo-text {
    font-size: 18px;
  }
}

/* 深色模式适配 */
html.dark .top-navbar {
  background: #1a1a1a;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

html.dark .logo-text {
  color: #ffffff;
}

html.dark .menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .menu-item.active {
  background: rgba(21, 94, 239, 0.2);
}

html.dark .menu-title {
  color: #e0e0e0;
}

html.dark .dropdown-menu {
  background: #2a2a2a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
}

html.dark .dropdown-item {
  color: #e0e0e0;
}

html.dark .dropdown-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #ffffff;
}

html.dark .user-info {
  background: rgba(255, 255, 255, 0.05);
}

html.dark .user-info:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .user-name {
  color: #ffffff;
}

html.dark .mobile-menu-toggle:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .mobile-menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
}

html.dark .drawer-title {
  color: #ffffff;
}

html.dark .mobile-menu-title {
  color: #e0e0e0;
}

html.dark .mobile-menu-item {
  color: #b0b0b0;
}

html.dark .action-divider {
  background: rgba(255, 255, 255, 0.2);
}

html.dark .system-ai-btn {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255, 255, 255, 0.1);
  color: #667eea;
}

html.dark .system-ai-btn:hover {
  background: #667eea;
  color: white;
  border-color: #667eea;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.4);
}

/* --- ORIN CORE (Adaptive Style) --- */
:global(.orin-core-dialog.el-dialog) {
  background: var(--el-bg-color) !important;
  border-radius: 16px !important;
  overflow: hidden;
  box-shadow: var(--el-box-shadow-dark);
}
:global(.orin-core-dialog .el-dialog__header) {
  padding: 0;
  margin: 0;
  background: transparent;
}
:global(.orin-core-dialog .el-dialog__body) {
  padding: 0 !important;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.core-header {
  height: 72px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color-overlay);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.core-logo {
  width: 40px;
  height: 40px;
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 8px;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.core-title {
  font-family: 'Inter', ui-monospace, monospace;
  font-weight: 800;
  font-size: 20px;
  color: var(--el-text-color-primary);
  letter-spacing: -0.5px;
  margin-right: 8px;
}

.header-tags {
  display: flex;
  gap: 8px;
  align-items: center;
}

.core-tag {
  font-family: monospace;
  font-size: 11px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
  font-weight: 600;
}
.core-tag.da {
  background: var(--el-fill-color-lighter);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.sys-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: monospace;
  font-size: 11px;
  color: #484b51;
}
.status-dot {
  width: 6px;
  height: 6px;
  background: #00ff9d;
  border-radius: 50%;
  box-shadow: 0 0 5px #00ff9d;
}

.close-icon {
  font-size: 20px;
  color: #484b51;
  cursor: pointer;
  transition: color 0.2s;
}
.close-icon:hover { color: var(--el-color-primary); }

.orin-terminal-container {
  height: 550px;
  display: flex;
  flex-direction: column;
  position: relative;
  background: var(--el-bg-color);
}

.terminal-body {
  flex: 1;
  padding: 30px 40px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 30px;
  scrollbar-width: none; 
}
.terminal-body::-webkit-scrollbar { display: none; }

.terminal-msg-row {
  display: flex;
  gap: 16px;
  animation: slideIn 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.terminal-msg-row.user {
  flex-direction: row-reverse;
}

.t-avatar {
  width: 36px;
  height: 36px;
  background: var(--el-color-info-light-9);
  color: var(--el-color-info);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.terminal-msg-row.user .t-avatar {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.t-sender-name {
  font-size: 11px;
  font-weight: 700;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}
.terminal-msg-row.user .t-sender-name { text-align: right; }

.t-content-group {
  max-width: 80%;
}

.t-msg-card {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  border-top-left-radius: 2px;
  padding: 16px 20px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  line-height: 1.6;
}
.terminal-msg-row.user .t-msg-card {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-8);
  border-top-left-radius: 12px;
  border-top-right-radius: 2px;
}

.quick-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
  flex-wrap: wrap;
}

.q-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--el-text-color-regular);
  white-space: nowrap;
}
.q-btn:hover {
  background: var(--el-fill-color);
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}

.terminal-footer {
  padding: 20px 40px 30px 40px;
  background: linear-gradient(to top, var(--el-bg-color) 60%, transparent);
  border-top: 1px solid var(--el-border-color-lighter);
}

.command-bar {
  background: var(--el-fill-color-darker);
  border: 1px solid var(--el-border-color);
  border-radius: 12px;
  display: flex;
  align-items: center;
  padding: 10px 16px;
  transition: border-color 0.2s;
}
.command-bar:focus-within {
  border-color: var(--el-color-primary);
}

.cmd-prompt {
  font-family: monospace;
  font-weight: bold;
  color: var(--el-color-primary);
  font-size: 18px;
  margin-right: 12px;
}

.cmd-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  color: var(--el-text-color-primary);
  font-family: 'Menlo', 'Consolas', monospace;
  font-size: 14px;
}
.cmd-input::placeholder { color: var(--el-text-color-placeholder); }

.cmd-hint {
  font-size: 10px;
  color: var(--el-text-color-secondary);
  font-weight: 700;
  border: 1px solid var(--el-border-color);
  padding: 2px 6px;
  border-radius: 4px;
}

.run-btn {
  background: var(--el-color-primary);
  border: none;
  border-radius: 6px;
  padding: 6px 16px;
  color: white;
  font-weight: 800;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.1s;
}
.run-btn:active { opacity: 0.8; }
.run-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.cmd-statusbar {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  padding-left: 10px;
  font-family: monospace;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  letter-spacing: 1px;
}
.cmd-statusbar .secure {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-success);
}

@keyframes slideIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* ZeroClaw Styles */
.header-tabs {
  display: flex;
  gap: 8px;
  margin-left: 24px;
}

.core-tab-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--el-text-color-secondary);
  background: transparent;
}

.core-tab-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.core-tab-btn.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.zc-status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-color-danger);
}

.zc-status-dot.connected {
  background: #00ff9d;
  box-shadow: 0 0 5px #00ff9d;
}

.zeroclaw-container {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
}

.zc-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  padding-bottom: 12px;
}

.zc-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: var(--el-text-color-secondary);
}

.zc-tab:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.zc-tab.active {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-weight: 600;
}

.zc-content {
  flex: 1;
  overflow-y: auto;
}

.zc-status-card {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 20px;
}

.zc-status-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.zc-status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: var(--el-color-danger);
}

.zc-status-indicator.connected {
  color: #00ff9d;
}

.zc-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.zc-status-info {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-item {
  display: flex;
  gap: 12px;
  font-size: 14px;
}

.info-item .label {
  color: var(--el-text-color-secondary);
  min-width: 80px;
}

.info-item .value {
  color: var(--el-text-color-primary);
}

.info-item .value.active {
  color: var(--el-color-success);
}

.zc-status-message {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.zc-actions-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.zc-reports-list {
  margin-top: 16px;
}

.zc-reports-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--el-text-color-primary);
}

.zc-empty {
  text-align: center;
  color: var(--el-text-color-secondary);
  padding: 40px;
}

.zc-report-card {
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}

.report-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.report-title {
  font-weight: 600;
  font-size: 14px;
}

.report-summary {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
  line-height: 1.5;
}

.report-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.zc-healing-info {
  margin-top: 16px;
}

/* Deep mode for ZeroClaw */
html.dark .zc-status-indicator.connected {
  color: #00ff9d;
}
</style>
