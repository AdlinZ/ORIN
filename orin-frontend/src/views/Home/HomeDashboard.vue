<template>
  <div class="command-center-root" :class="{ 'theme-dark': isDark, 'with-sidebar': isSidebarMode, 'is-collapsed': appStore.isCollapse }">
    <!-- Loading Overlay -->
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner">
        <div class="spinner-ring"></div>
        <span class="loading-text">正在加载监控数据...</span>
      </div>
    </div>

    <!-- TOP BAR: Readable & High-End -->
    <header class="cc-header-glass">
      <div class="h-brand">
        <h1 class="logo-text">ORIN<span class="logo-dot">.</span>COMMAND</h1>
        <div class="status-indicator" :class="{ 'is-load': summary.highLoadAgents > 0 }">
          <span class="dot"></span>
          <span class="txt">{{ summary.highLoadAgents > 0 ? '负载过高' : '运行正常' }}</span>
        </div>
      </div>
      
      <div class="h-utility">
        <div class="uptime-orb">
          <el-icon class="orb-icon"><Timer /></el-icon>
          <div class="orb-content">
            <span class="orb-val" v-html="formattedUptime"></span>
            <span class="orb-lbl">稳定运行时长</span>
          </div>
        </div>
        <div class="h-divider"></div>
        <div class="h-clock">
          <span class="c-time">{{ currentTime }}</span>
          <span class="c-date">{{ currentDate }}</span>
        </div>
      </div>
    </header>

    <main class="cc-layout-grid">
      <!-- LEFT: Telemetry -->
      <aside class="col-telemetry">
        <!-- Assets -->
        <div class="premium-card">
          <div class="card-glow"></div>
          <h3 class="card-head clickable" @click="goToPage(ROUTES.RESOURCES.KNOWLEDGE)">资产中枢<span class="head-line"></span></h3>
          <div class="asset-bubbles">
            <div class="bubble-item clickable" @click="goToPage(ROUTES.RESOURCES.KNOWLEDGE)">
              <el-icon><Collection /></el-icon>
              <div class="b-text"><span class="b-num">{{ summary.total_knowledge || 0 }}</span><span class="b-lbl">知识库数量</span></div>
            </div>
            <div class="bubble-item">
              <el-icon><Connection /></el-icon>
              <div class="b-text"><span class="b-num">{{ summary.total_documents || 0 }}</span><span class="b-lbl">文档总数</span></div>
            </div>
          </div>
        </div>

        <!-- Resources -->
        <div class="premium-card clickable" @click="goToPage(ROUTES.MONITOR.TOKENS)">
          <div class="card-glow"></div>
          <h3 class="card-head">资源矩阵<span class="head-line"></span></h3>
          <div class="matrix-grid">
            <div class="m-box">
              <span class="m-lbl">今日消耗 TOKEN</span>
              <div class="m-val-group">
                <span class="m-num primary-text">{{ formatK(summary.total_tokens) }}</span>
                <span class="m-trend" :class="summary.total_tokens_trend >= 0 ? 'up' : 'down'">
                  {{ summary.total_tokens_trend >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.total_tokens_trend || 0) }}%
                </span>
              </div>
            </div>
            <div class="m-box">
              <span class="m-lbl">估算成本 COST</span>
              <div class="m-val-group">
                <span class="m-num">¥{{ (summary.todayCost || 0).toFixed(2) }}</span>
                <span class="m-trend" :class="summary.today_cost_trend >= 0 ? 'up' : 'down'">
                  {{ summary.today_cost_trend >= 0 ? '↑' : '↓' }} {{ Math.abs(summary.today_cost_trend || 0) }}%
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Load -->
        <div class="premium-card flex-grow shadow-soft clickable" @click="goToPage(ROUTES.MONITOR.SERVER)">
          <div class="card-glow"></div>
          <h3 class="card-head">物理负载<span class="head-line"></span></h3>
          <div class="load-bars">
            <div class="l-item">
              <div class="l-info"><span>CPU 负载</span><span class="l-val">{{ (hardware.cpuUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.cpuUsage + '%', background: getBarColor(hardware.cpuUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>GPU 负载</span><span class="l-val">{{ (hardware.gpuUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.gpuUsage + '%', background: getBarColor(hardware.gpuUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>内存 负载</span><span class="l-val">{{ (hardware.memoryUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.memoryUsage + '%', background: getBarColor(hardware.memoryUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>磁盘 负载</span><span class="l-val">{{ (hardware.diskUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.diskUsage + '%', background: getBarColor(hardware.diskUsage) }"></div></div>
            </div>
          </div>
        </div>
      </aside>

      <!-- CENTER: Integrated Operations Hub -->
      <article class="col-insight">
        <div class="hub-container">
          <!-- Main Chart Panel -->
          <div class="hub-visual-panel">
            <div class="panel-top">
              <div class="p-meta">
                <div class="pm-item"><span class="p-lbl">今日请求</span><span class="p-val accent-blue">{{ summary.daily_requests || 0 }}</span></div>
                <div class="pm-item"><span class="p-lbl">平均延时</span><span class="p-val accent-green">{{ summary.avg_latency || '0ms' }}</span></div>
              </div>
              <div class="p-chart-controls">
                <div class="ctrl-group main-tabs">
                  <button :class="{ active: chartType === 'tokens' }" @click="chartType = 'tokens'">令牌</button>
                  <button :class="{ active: chartType === 'latency' }" @click="chartType = 'latency'">延时</button>
                  <button :class="{ active: chartType === 'hardware' }" @click="chartType = 'hardware'">硬件</button>
                </div>
                <div class="ctrl-group sub-tabs" v-if="chartType === 'hardware'">
                  <button :class="{ active: hardwareMetric === 'cpuUsage' }" @click="hardwareMetric = 'cpuUsage'">CPU</button>
                  <button :class="{ active: hardwareMetric === 'memoryUsage' }" @click="hardwareMetric = 'memoryUsage'">内存</button>
                  <button :class="{ active: hardwareMetric === 'diskUsage' }" @click="hardwareMetric = 'diskUsage'">磁盘</button>
                  <button :class="{ active: hardwareMetric === 'gpuUsage' }" @click="hardwareMetric = 'gpuUsage'">GPU</button>
                </div>
                <div class="ctrl-group time-range" v-else>
                  <span
                    v-for="r in ranges"
                    :key="r"
                    class="r-pill-v2"
                    :class="{ active: r === currentRange }"
                    @click="handleRangeChange(r)"
                  >
                    {{ r }}
                  </span>
                </div>
              </div>
            </div>
            <div class="p-chart-wrap">
                <LineChart
                :data="trendData"
                :title="chartType === 'tokens' ? 'Token 消耗趋势' : chartType === 'latency' ? '响应延时趋势' : getHardwareTitle()"
                :yAxisName="chartType === 'tokens' ? 'Tokens' : chartType === 'latency' ? 'ms' : '%'"
                :maxPoints="30"
                height="100%"
                :color="isDark ? '#26FFDF' : '#00BFA5'"
              />
            </div>
          </div>

          <!-- Semantic Cloud -->
          <div class="hub-semantic-panel">
            <div class="semantic-hdr">
              <span class="sh-txt">语义联想与决策空间</span>
              <span class="sh-code">实时获取中</span>
            </div>
            <div class="semantic-world">
              <div class="tags-container">
                <div
                v-for="(tag, idx) in intentTags"
                :key="tag.label + keywordPage"
                class="s-tag-v4"
                :style="{
                  '--f-size': (tag.size + 2) + 'px',
                  '--op': tag.opacity,
                  '--c': tag.color,
                  'animation-delay': tag.delay + 's'
                }"
              >
                #{{ tag.label }}
              </div>
              </div>
            </div>
          </div>

          <footer class="hub-footer">
             <div class="f-left">
                <span class="f-status-dot"></span>
                <span class="f-tag">映射引擎 // 系统监控开启</span>
             </div>
             <div class="f-right">
                <span class="f-code-tag">BUILD: {{ buildDate }}</span>
             </div>
          </footer>
        </div>
      </article>

      <!-- RIGHT: Tactical Board -->
      <aside class="col-battle">
        <!-- Top KPIs -->
        <div class="premium-card clickable" @click="goToPage(ROUTES.MONITOR.DASHBOARD)">
          <div class="card-glow"></div>
          <div class="mini-kpi-grid">
             <div class="mk-box">
                <span class="mk-lbl">活跃智能体数</span>
                <span class="mk-val blue">{{ summary.online_agents || 0 }}<small>智能体</small></span>
             </div>
             <div class="mk-box">
                <span class="mk-lbl">运行健康评分</span>
                <span class="mk-val green">{{ summary.averageHealthScore || 0 }}<small>%</small></span>
             </div>
          </div>
        </div>

        <!-- Rank List -->
        <div class="premium-card flex-grow battle-card clickable" @click="goToPage(ROUTES.MONITOR.TOKENS)">
          <div class="card-glow"></div>
          <div class="battle-header">
            <h3 class="b-title">资源分布（按智能体）</h3>
            <span class="b-count">{{ distribution.length }} 活跃</span>
          </div>

          <div class="rank-list-v4">
            <div class="rk-item" v-for="(item, idx) in topAgents" :key="item.name">
              <div class="rk-top">
                <span class="rk-idx">0{{ idx+1 }}</span>
                <span class="rk-name" :title="item.name">{{ item.name }}</span>
                <span class="rk-num">{{ formatK(item.value) }}</span>
              </div>
              <div class="rk-bar-bg"><div class="rk-bar-fill" :style="{ width: getRkWidth(item.value) + '%' }"></div></div>
            </div>
          </div>

          <div class="recent-events-v3">
            <h4 class="re-title">实时审计日志</h4>
            <div class="re-list">
              <div class="re-node" v-for="(ev, idx) in recentLogs.slice(0, 3)" :key="idx">
                <div class="re-line" :class="ev.status"></div>
                <div class="re-body">
                  <span class="re-txt">{{ ev.text }}</span>
                  <div class="re-tm-row">
                    <span class="re-tm">{{ ev.time }}</span>
                    <span class="re-status-dot" :class="ev.status"></span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useDark } from '@vueuse/core'
import { useAppStore } from '@/stores/app'
import { Timer, Collection, Connection } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import {
  getGlobalSummary,
  getAgentList,
  getServerHardware,
  getTokenHistory,
  getTokenTrend,
  getLatencyTrend,
  getTokenDistribution,
  getServerHardwareTrend
} from '@/api/monitor'
import { getSemanticKeywords } from '@/api/knowledge'
import { ROUTES } from '@/router/routes'
import LineChart from '@/components/LineChart.vue'

const router = useRouter()
const appStore = useAppStore()

// 构建日期（Vite 构建时注入）
const buildDate = __BUILD_DATE__ || new Date().toISOString().split('T')[0]

const isDark = useDark()

// 是否使用侧边栏模式
const isSidebarMode = computed(() => appStore.menuMode === 'sidebar')
const loading = ref(true)

const currentTime = ref('')
const currentDate = ref('')
const uptimeDays = ref(0)
const uptimeHours = ref(24)
const uptimeMinutes = ref(15)
const currentRange = ref('1H')
const ranges = ['5M', '1H', '24H', '7D']

// 格式化运行时长显示
const formattedUptime = computed(() => {
  if (uptimeDays.value > 0) {
    return `${uptimeDays.value}<small>d</small> ${uptimeHours.value}<small>h</small>`
  } else if (uptimeHours.value > 0) {
    return `${uptimeHours.value}<small>h</small> ${uptimeMinutes.value}<small>m</small>`
  } else {
    return `${uptimeMinutes.value}<small>m</small>`
  }
})

const chartType = ref('tokens')
const hardwareMetric = ref('cpuUsage') // 硬件指标: cpuUsage, memoryUsage, diskUsage, gpuUsage
const trendData = ref([])

const summary = ref({})
const agents = ref([])
const hardware = ref({ cpuUsage: 0, gpuUsage: 0, memoryUsage: 0, diskUsage: 0, gpuModel: '' })
const recentLogs = ref([])
const distribution = ref([])
const intentTags = ref([])
const allKeywords = ref([]) // 保存所有关键词
const keywordPage = ref(0)
const KEYWORDS_PER_PAGE = 15 // 每页显示数量
let keywordInterval = null

// 静态后备标签（API失败时使用）
const fallbackTags = [
  { label: '知识库检索', size: 14, opacity: 1, delay: 0.1, color: '#00BFA5' },
  { label: '语义理解', size: 11, opacity: 0.7, delay: 0.5, color: '#94a3b8' },
  { label: '逻辑推理', size: 13, opacity: 0.9, delay: 0.8, color: '#26FFDF' },
  { label: 'DeepSeek-R1', size: 16, opacity: 1, delay: 0.3, color: '#10b981' },
  { label: 'Agent_Thinking', size: 10, opacity: 0.5, delay: 1.5, color: '#64748b' },
  { label: '文本纠错', size: 11, opacity: 0.8, delay: 0.9, color: '#3b82f6' },
  { label: '跨库关联', size: 12, opacity: 0.8, delay: 0.4, color: '#00BFA5' },
  { label: '用户建模', size: 11, opacity: 0.6, delay: 1.8, color: '#94a3b8' }
]

// 获取语义关键词
const fetchKeywords = async () => {
  try {
    const res = await getSemanticKeywords({ limit: 30 })
    if (res && res.length > 0) {
      // 转换后端数据格式以匹配前端展示
      allKeywords.value = res.map((tag, idx) => ({
        label: tag.label,
        size: tag.size || 12,
        opacity: tag.opacity || 0.8,
        delay: tag.delay || (idx * 0.2),
        color: tag.color || '#00BFA5'
      }))
      rotateKeywords()
    } else {
      allKeywords.value = fallbackTags
      intentTags.value = fallbackTags
    }
  } catch (e) {
    console.error('获取关键词失败，使用默认标签', e)
    allKeywords.value = fallbackTags
    intentTags.value = fallbackTags
  }
}

// 轮换显示关键词
const rotateKeywords = () => {
  if (!allKeywords.value || allKeywords.value.length <= KEYWORDS_PER_PAGE) {
    intentTags.value = allKeywords.value
    return
  }
  const start = keywordPage.value * KEYWORDS_PER_PAGE
  const end = start + KEYWORDS_PER_PAGE
  intentTags.value = allKeywords.value.slice(start, end)
  keywordPage.value = (keywordPage.value + 1) % Math.ceil(allKeywords.value.length / KEYWORDS_PER_PAGE)
}

const formatK = (v) => {
  if (!v) return '0'
  return v >= 1000 ? (v / 1000).toFixed(1) + 'k' : v
}

const getRkWidth = (v) => {
  if (distribution.value.length === 0) return 0
  const max = Math.max(...distribution.value.map(d => d.value))
  return (v / max) * 100
}

const topAgents = computed(() => distribution.value.slice(0, 3))

const getBarColor = (v) => v > 80 ? '#ef4444' : v > 60 ? '#f59e0b' : '#3b82f6'

const getHardwareTitle = () => {
  const titles = {
    cpuUsage: 'CPU 使用率',
    memoryUsage: '内存使用率',
    diskUsage: '磁盘使用率',
    gpuUsage: 'GPU 使用率'
  }
  return titles[hardwareMetric.value] || '硬件使用率'
}

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
  currentDate.value = now.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', weekday: 'short' })
}

const handleRangeChange = (r) => {
  currentRange.value = r
  fetchTrend()
}

const goToPage = (path) => {
  router.push(path)
}

const fetchTrend = async () => {
  try {
    const period = currentRange.value.toLowerCase()
    let res = []
    if (chartType.value === 'tokens') {
      res = await getTokenTrend(period)
      trendData.value = res.map(i => ({
        timestamp: i.timestamp,
        value: i.tokens || 0
      }))
    } else if (chartType.value === 'latency') {
      res = await getLatencyTrend(period)
      trendData.value = res.map(i => ({
        timestamp: i.timestamp,
        value: i.latency || 0
      }))
    } else if (chartType.value === 'hardware') {
      res = await getServerHardwareTrend(period)
      trendData.value = res.map(i => ({
        timestamp: i.timestamp,
        value: i[hardwareMetric.value] || 0
      }))
    }
  } catch (e) { console.error(e) }
}

const fetchData = async () => {
  try {
    const [s, a, h, l, d] = await Promise.allSettled([
      getGlobalSummary(), getAgentList(), getServerHardware(), getTokenHistory({ size: 10 }), getTokenDistribution()
    ])
    if (s.status === 'fulfilled') {
      summary.value = s.value
      if (s.value.system_uptime) {
        const totalMinutes = Math.floor(s.value.system_uptime / (1000 * 60))
        uptimeDays.value = Math.floor(totalMinutes / (60 * 24))
        uptimeHours.value = Math.floor((totalMinutes % (60 * 24)) / 60)
        uptimeMinutes.value = totalMinutes % 60
      }
    }
    if (a.status === 'fulfilled') agents.value = a.value
    if (h.status === 'fulfilled') hardware.value = h.value
    if (l.status === 'fulfilled' && l.value.content) {
      recentLogs.value = l.value.content.map(i => {
        let name = i.agentName || i.providerId || 'SYSTEM';
        if (name.length > 12) name = name.substring(0, 8) + '...';

        return {
          // 使用 dayjs 解析时间，确保时区正确
          time: dayjs(i.createdAt).format('HH:mm'),
          text: `${name} | ${i.endpoint?.split('/').pop() || 'Processing'}`,
          status: i.success ? 'healthy' : 'critical'
        };
      })
    }
    if (d.status === 'fulfilled') distribution.value = d.value.sort((a,b) => b.value - a.value)
    await fetchTrend()
  } catch (e) { console.error(e) }
  finally {
    loading.value = false
    window.dispatchEvent(new Event('page-refresh-done'))
  }
}

watch([chartType, currentRange, hardwareMetric], fetchTrend)
onMounted(() => {
  updateTime(); fetchData(); fetchKeywords();
  setInterval(updateTime, 1000);
  setInterval(fetchData, 10000);
  // 每5分钟刷新关键词
  setInterval(fetchKeywords, 5 * 60 * 1000);
  // 每8秒轮换显示下一批关键词
  keywordInterval = setInterval(rotateKeywords, 8000);
  window.addEventListener('page-refresh', fetchData);
})
onUnmounted(() => {
  window.removeEventListener('page-refresh', fetchData);
  if (keywordInterval) clearInterval(keywordInterval);
})
</script>

<style scoped>
.command-center-root {
  height: calc(100vh - 84px);
  padding: 16px;
  background-color: var(--neutral-gray-50);
  color: var(--neutral-gray-900);
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
  font-family: 'Outfit', 'Inter', system-ui, sans-serif;
  position: relative;
}

/* 侧边栏模式适配 - 核心修复：确保宽度自适应，不溢出 */
.command-center-root.with-sidebar {
  height: calc(100vh - 24px); 
  padding: 12px;
  width: 100%;
  min-width: 0;
}

/* 当侧边栏展开时，进一步压缩两侧支柱宽度以保护中间核心图表 */
.command-center-root.with-sidebar:not(.is-collapsed) .cc-layout-grid {
  grid-template-columns: minmax(180px, 240px) 1fr minmax(220px, 280px);
  gap: 12px;
}

/* 当侧边栏收起时，恢复较宽的侧边栏 */
.command-center-root.with-sidebar.is-collapsed .cc-layout-grid {
  grid-template-columns: 280px 1fr 320px;
  gap: 16px;
}

/* 确保所有网格项都能缩小 */
.col-telemetry, .col-insight, .col-battle {
  min-width: 0;
}

/* Loading Overlay */
.loading-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  border-radius: 14px;
}

.theme-dark .loading-overlay {
  background: rgba(19, 23, 31, 0.95);
}

.loading-spinner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.spinner-ring {
  width: 48px;
  height: 48px;
  border: 4px solid rgba(0, 0, 0, 0.1);
  border-top-color: var(--orin-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.theme-dark .spinner-ring {
  border-color: rgba(255, 255, 255, 0.1);
  border-top-color: var(--orin-primary);
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  font-size: 14px;
  font-weight: 600;
  color: var(--neutral-gray-600);
}

.theme-dark .loading-text {
  color: var(--neutral-gray-300);
}
.theme-dark { background-color: var(--neutral-gray-50); color: var(--neutral-gray-900); }

/* HEADER */
.cc-header-glass {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border-radius: 14px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .cc-header-glass { background: rgba(21, 26, 35, 0.85); box-shadow: none; border: 1px solid rgba(255,255,255,0.06); }

.h-brand { display: flex; align-items: center; gap: 24px; }
.logo-text { font-size: 20px; font-weight: 900; letter-spacing: -0.5px; }
.logo-dot { color: var(--orin-primary); }

.status-indicator { display: flex; align-items: center; gap: 10px; font-size: 11px; font-weight: 800; color: #10b981; padding: 6px 14px; background: rgba(16, 185, 129, 0.1); border-radius: 8px; }
.status-indicator.is-load { color: #f59e0b; background: rgba(245, 158, 11, 0.1); }
.status-indicator .dot { width: 7px; height: 7px; background: currentColor; border-radius: 50%; animation: breathe 2s infinite; }

.h-utility { display: flex; align-items: center; gap: 32px; }
.uptime-orb { display: flex; align-items: center; gap: 14px; }
.orb-icon { font-size: 24px; color: var(--orin-primary); }
.orb-content { display: flex; flex-direction: column; line-height: 1.1; }
.orb-val { font-size: 18px; font-weight: 800; }
.orb-val small { font-size: 11px; opacity: 0.5; margin: 0 1px; }
.orb-lbl { font-size: 9px; font-weight: 700; color: #94a3b8; }

.h-clock { text-align: right; display: flex; flex-direction: column; }
.c-time { font-size: 18px; font-weight: 700; line-height: 1; }
.c-date { font-size: 10px; color: #94a3b8; font-weight: 700; margin-top: 4px; }

/* GRID */
.cc-layout-grid {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 16px;
  flex: 1;
  min-height: 0;
}

.col-telemetry, .col-battle { display: flex; flex-direction: column; gap: 16px; min-height: 0; }
.flex-grow { flex: 1; }

/* CARD SYSTEM */
.premium-card {
  position: relative;
  background: #ffffff;
  border-radius: 14px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(0,0,0,0.05);
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
}

.premium-card.clickable {
  cursor: pointer;
  transition: all 0.3s ease;
}

.premium-card.clickable:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0,0,0,0.08);
  border-color: var(--orin-primary);
}
.theme-dark .premium-card { background: rgba(19, 23, 31, 0.6); border-color: rgba(255,255,255,0.06); box-shadow: none; }

.card-head { font-size: 11px; font-weight: 800; color: #64748b; text-transform: uppercase; margin-bottom: 12px; display: flex; align-items: center; gap: 10px; }
.head-line { flex: 1; height: 1px; background: rgba(148, 163, 184, 0.15); }

/* CONTENT */
.asset-bubbles { display: flex; flex-direction: column; gap: 10px; }
.bubble-item { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 12px; background: #f8fafc; }
.theme-dark .bubble-item { background: rgba(255,255,255,0.03); }
.bubble-item .el-icon { font-size: 20px; color: var(--orin-primary); }
.b-num { font-size: 18px; font-weight: 800; display: block; }
.b-lbl { font-size: 11px; font-weight: 700; color: #94a3b8; }

.matrix-grid { display: grid; gap: 8px; }
.m-lbl { font-size: 11px; font-weight: 700; color: #94a3b8; }
.m-num { font-size: 18px; font-weight: 800; }
.m-num.primary-text { color: var(--orin-primary); }
.m-trend { font-size: 11px; font-weight: 800; margin-left: 8px; }
.m-trend.up { color: #ef4444; } .m-trend.down { color: #10b981; }

.load-bars { display: flex; flex-direction: column; gap: 10px; }
.l-item { display: flex; flex-direction: column; gap: 4px; }
.l-info { display: flex; justify-content: space-between; font-size: 11px; font-weight: 800; color: #64748b; }
.l-val { color: #1a1c21; }
.theme-dark .l-val { color: #f9fafb; }
.l-rail { height: 6px; background: #f1f5f9; border-radius: 3px; overflow: hidden; }
.theme-dark .l-rail { background: rgba(255,255,255,0.06); }
.l-fill { height: 100%; border-radius: 3px; transition: width 0.8s cubic-bezier(0.17, 0.67, 0.83, 0.67); }

/* INSIGHT HUB */
.col-insight { display: flex; flex-direction: column; min-height: 0; }
.hub-container {
  flex: 1; display: flex; flex-direction: column;
  background: #ffffff;
  border-radius: 18px; padding: 24px; gap: 24px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.02);
  border: 1px solid rgba(0,0,0,0.04);
}
.theme-dark .hub-container { background: rgba(19, 23, 31, 0.7); border-color: rgba(255,255,255,0.05); }

.hub-visual-panel { flex: 1.5; display: flex; flex-direction: column; min-height: 0; }
.p-title-compact { 
  font-size: 13px; 
  font-weight: 800; 
  margin: 0; 
  color: #475569; 
  opacity: 0.8; 
  white-space: nowrap; 
  flex-shrink: 0;
}
.theme-dark .p-title-compact { color: #cbd5e1; }
.panel-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.p-chart-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.ctrl-group {
  display: flex;
  align-items: center;
  background: rgba(0,0,0,0.03);
  border: 1px solid rgba(0,0,0,0.04);
  border-radius: 100px;
  padding: 3px;
}

.ctrl-group button,
.ctrl-group .r-pill-v2 {
  font-size: 10px;
  font-weight: 700;
  border: none;
  padding: 5px 12px;
  border-radius: 100px;
  cursor: pointer;
  transition: all 0.2s;
  background: transparent;
  color: #94a3b8;
  white-space: nowrap;
}

.ctrl-group button.active,
.ctrl-group .r-pill-v2.active {
  background: var(--orin-primary);
  color: #fff;
  box-shadow: 0 2px 6px var(--primary-glow);
}

.ctrl-group button:hover:not(.active),
.ctrl-group .r-pill-v2:hover:not(.active) {
  color: var(--orin-primary);
}

.ctrl-group.main-tabs {
  gap: 2px;
}

.ctrl-group.sub-tabs,
.ctrl-group.time-range {
  gap: 2px;
}

.theme-dark .ctrl-group {
  background: rgba(0,0,0,0.3);
  border-color: rgba(255,255,255,0.06);
}

.p-meta { display: flex; gap: 32px; }
.p-lbl { font-size: 11px; font-weight: 700; color: #94a3b8; display: block; margin-bottom: 4px; }
.p-val { font-size: 24px; font-weight: 900; }
.accent-blue { color: var(--orin-primary); } .accent-green { color: #10b981; }

.p-chart-wrap { 
  flex: 1; 
  min-height: 0; 
  min-width: 0; 
  width: 100%; 
  overflow: hidden; /* 防止图表内容溢出 */
}

.hub-semantic-panel { flex: 0.6; background: #f8fafc; border-radius: 14px; padding: 18px; display: flex; flex-direction: column; }
.theme-dark .hub-semantic-panel { background: rgba(255,255,255,0.02); }
.sh-txt { font-size: 12px; font-weight: 800; color: var(--orin-primary); }
.sh-code { font-size: 10px; font-weight: 900; color: #cbd5e1; opacity: 0.6; }

.semantic-world { flex: 1; display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-start; align-content: flex-start; padding: 8px; max-height: 100px; overflow: hidden; position: relative; }
.tags-container { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.semantic-world::after { content: ''; position: absolute; bottom: 0; left: 0; right: 0; height: 20px; background: linear-gradient(transparent, #f8fafc); pointer-events: none; }
.theme-dark .semantic-world::after { background: linear-gradient(transparent, rgba(19,23,31,0.85)); }
.s-tag-v4 {
  font-size: var(--f-size); opacity: var(--op); color: var(--c);
  background: #fff; border: 1px solid rgba(0,0,0,0.06);
  padding: 4px 12px; border-radius: 16px; font-weight: 600;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
  animation: fadeInUp 0.4s ease-out forwards, sFloat 4s ease-in-out infinite;
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: var(--op); transform: translateY(0); }
}
.theme-dark .s-tag-v4 { background: rgba(255,255,255,0.05); border-color: transparent; }

.hub-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #f1f5f9; padding: 12px 0 0 0; margin-top: auto; }
.theme-dark .hub-footer { border-color: rgba(255,255,255,0.06); }
.f-left { display: flex; align-items: center; gap: 8px; }
.f-status-dot { width: 6px; height: 6px; background: #10b981; border-radius: 50%; box-shadow: 0 0 8px #10b981; }
.f-tag, .f-code-tag { font-size: 10px; font-weight: 900; color: #cbd5e1; letter-spacing: 0.5px; }
.f-code-tag { opacity: 0.5; background: rgba(0,0,0,0.05); padding: 2px 8px; border-radius: 4px; }
.theme-dark .f-code-tag { background: rgba(255,255,255,0.05); }

/* BATTLE BOARD */
.mini-kpi-grid { 
  display: grid; 
  grid-template-columns: 1fr 1fr; 
  gap: 8px; /* 减小间距 */
}
.mk-box { display: flex; flex-direction: column; gap: 4px; }
.mk-lbl { font-size: 10px; font-weight: 800; color: #94a3b8; }
.mk-val { font-size: 22px; font-weight: 900; }
.mk-val small { font-size: 11px; font-weight: 400; margin-left: 4px; }
.mk-val.blue { color: var(--orin-primary); } .mk-val.green { color: #10b981; }

.battle-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.b-title { font-size: 14px; font-weight: 900; margin: 0; }
.b-count { font-size: 10px; font-weight: 800; color: var(--orin-primary); background: var(--orin-primary-soft); padding: 4px 12px; border-radius: 6px; }

.rank-list-v4 { display: flex; flex-direction: column; gap: 12px; flex: 1; }
.rk-item { padding: 14px; background: #f8fafc; border-radius: 12px; border: 1px solid transparent; transition: 0.2s; }
.rk-item:hover { background: #fff; border-color: var(--orin-primary-fade); box-shadow: 0 4px 12px rgba(0,0,0,0.04); }
.theme-dark .rk-item { background: rgba(255,255,255,0.03); }
.theme-dark .rk-item:hover { background: rgba(255,255,255,0.06); }
.theme-dark .rk-item:hover { background: rgba(255,255,255,0.05); }

.rk-top { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.rk-idx { font-size: 14px; font-weight: 900; color: #cbd5e1; }
.rk-name { flex: 1; font-size: 13px; font-weight: 700; color: #475569; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.theme-dark .rk-name { color: #cbd5e1; }
.rk-num { font-size: 14px; font-weight: 900; color: var(--orin-primary); }
.rk-bar-bg { height: 4px; background: rgba(0,0,0,0.05); border-radius: 2px; }
.theme-dark .rk-bar-bg { background: rgba(255,255,255,0.06); }
.rk-bar-fill { height: 100%; background: var(--orin-primary); border-radius: 2px; }

.recent-events-v3 { margin-top: 24px; border-top: 1px solid #f1f5f9; padding-top: 20px; }
.theme-dark .recent-events-v3 { border-color: rgba(255,255,255,0.06); }
.re-title { font-size: 11px; font-weight: 800; color: #94a3b8; margin: 0 0 16px 0; }
.re-list { display: flex; flex-direction: column; gap: 14px; }
.re-node { display: flex; gap: 14px; position: relative; }
.re-line { width: 3px; height: 100%; position: absolute; left: 0; border-radius: 2px; }
.re-line.healthy { background: var(--orin-primary); }
.re-line.critical { background: #ef4444; }
.re-body { padding-left: 14px; display: flex; flex-direction: column; gap: 4px; }
.re-txt { font-size: 12px; font-weight: 700; color: #334155; line-height: 1.4; }
.theme-dark .re-txt { color: #cbd5e1; }
.re-tm-row { display: flex; align-items: center; gap: 8px; }
.re-tm { font-size: 10px; font-weight: 700; color: #94a3b8; }
.re-status-dot { width: 4px; height: 4px; border-radius: 50%; }
.re-status-dot.healthy { background: var(--orin-primary); box-shadow: 0 0 4px var(--orin-primary); }
.re-status-dot.critical { background: #ef4444; box-shadow: 0 0 4px #ef4444; }

@keyframes breathe { 0%, 100% { opacity: 0.6; transform: scale(1); } 50% { opacity: 1; transform: scale(1.15); } }
@keyframes sFloat { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-4px); } }

@media (max-width: 1440px) {
  .h-brand { gap: 12px; }
  .h-utility { gap: 16px; }
  .p-meta { gap: 16px; }
}

@media (max-width: 1280px) {
  .logo-text { font-size: 16px; }
  .status-indicator { padding: 4px 8px; }
  .orb-val { font-size: 14px; }
  .c-time { font-size: 14px; }
  .p-val { font-size: 18px; }
}

@media (max-width: 1680px) { 
  .cc-layout-grid { grid-template-columns: 240px 1fr 280px; } 
}
@media (max-width: 1440px) { 
  .cc-layout-grid { grid-template-columns: 200px 1fr 240px; } 
}
@media (max-width: 1280px) { 
  .cc-layout-grid { grid-template-columns: 180px 1fr 220px; gap: 10px; } 
  .p-meta { display: none; } /* 空间不足时隐藏次要元数据 */
}
@media (max-width: 1100px) {
  .cc-layout-grid { grid-template-columns: 1fr; }
  .col-battle { display: none; }
}
/* Unify semantic cloud colors in dark mode */
html.dark .s-tag-v4 {
  background: var(--neutral-gray-100);
}

html.dark .rk-item {
  background: var(--neutral-gray-50);
}

html.dark .rk-item:hover {
  background: var(--neutral-gray-100);
}

/* 黑夜模式全局适配 - 卡片和背景颜色 */
html.dark .premium-card {
  background: rgba(19, 23, 31, 0.8) !important;
}

html.dark .hub-container {
  background: rgba(19, 23, 31, 0.85) !important;
}

html.dark .hub-semantic-panel {
  background: rgba(255, 255, 255, 0.03) !important;
}

html.dark .bubble-item {
  background: rgba(255, 255, 255, 0.05) !important;
}

html.dark .l-rail {
  background: rgba(255, 255, 255, 0.1) !important;
}

html.dark .rk-item {
  background: rgba(255, 255, 255, 0.05) !important;
}

html.dark .p-chart-tabs {
  background: rgba(0, 0, 0, 0.4) !important;
}

/* 黑夜模式 - 文字颜色 */
html.dark .card-head,
html.dark .l-info,
html.dark .b-lbl,
html.dark .m-lbl,
html.dark .p-lbl,
html.dark .mk-lbl,
html.dark .re-title,
html.dark .re-tm,
html.dark .orb-lbl,
html.dark .c-date,
html.dark .pill-group button {
  color: #94a3b8 !important;
}

html.dark .p-title-compact,
html.dark .rk-name,
html.dark .re-txt,
html.dark .l-val {
  color: #e2e8f0 !important;
}

html.dark .sh-code,
html.dark .rk-idx {
  color: #64748b !important;
}

/* 黑夜模式 - 边框颜色 */
html.dark .hub-footer,
html.dark .recent-events-v3 {
  border-color: rgba(255, 255, 255, 0.1) !important;
}

html.dark .head-line {
  background: rgba(148, 163, 184, 0.2) !important;
}

/* 黑夜模式 - 悬停效果 */
html.dark .rk-item:hover {
  background: rgba(255, 255, 255, 0.08) !important;
  border-color: rgba(255, 255, 255, 0.1) !important;
}

html.dark .pill-group button:hover:not(.active) {
  color: var(--orin-primary) !important;
}
</style>
