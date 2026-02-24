<template>
  <div class="command-center-root" :class="{ 'theme-dark': isDark }">
    <!-- TOP BAR: Readable & High-End -->
    <header class="cc-header-glass">
      <div class="h-brand">
        <h1 class="logo-text">ORIN<span class="logo-dot">.</span>COMMAND</h1>
        <div class="status-indicator" :class="{ 'is-load': summary.highLoadAgents > 0 }">
          <span class="dot"></span>
          <span class="txt">{{ summary.highLoadAgents > 0 ? 'HIGH_LOAD' : 'CORE_ACTIVE' }}</span>
        </div>
      </div>
      
      <div class="h-utility">
        <div class="uptime-orb">
          <el-icon class="orb-icon"><Timer /></el-icon>
          <div class="orb-content">
            <span class="orb-val">{{ uptimeHours }}<small>h</small> {{ uptimeMinutes }}<small>m</small></span>
            <span class="orb-lbl">STABLE_UPTIME</span>
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
          <h3 class="card-head">资产中枢 / ASSET_HUB<span class="head-line"></span></h3>
          <div class="asset-bubbles">
            <div class="bubble-item">
              <el-icon><Collection /></el-icon>
              <div class="b-text"><span class="b-num">{{ summary.total_knowledge || 0 }}</span><span class="b-lbl">知识底座</span></div>
            </div>
            <div class="bubble-item">
              <el-icon><Connection /></el-icon>
              <div class="b-text"><span class="b-num">{{ summary.total_agents || 0 }}</span><span class="b-lbl">受控智能体</span></div>
            </div>
          </div>
        </div>

        <!-- Resources -->
        <div class="premium-card">
          <div class="card-glow"></div>
          <h3 class="card-head">资源矩阵 / RESOURCE<span class="head-line"></span></h3>
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
        <div class="premium-card flex-grow shadow-soft">
          <div class="card-glow"></div>
          <h3 class="card-head">物理负载 / LOAD<span class="head-line"></span></h3>
          <div class="load-bars">
            <div class="l-item">
              <div class="l-info"><span>CPU_LOAD</span><span class="l-val">{{ (hardware.cpuUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.cpuUsage + '%', background: getBarColor(hardware.cpuUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>GPU_LOAD</span><span class="l-val">{{ (hardware.gpuUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.gpuUsage + '%', background: getBarColor(hardware.gpuUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>RAM_LOAD</span><span class="l-val">{{ (hardware.memoryUsage || 0).toFixed(1) }}%</span></div>
              <div class="l-rail"><div class="l-fill" :style="{ width: hardware.memoryUsage + '%', background: getBarColor(hardware.memoryUsage) }"></div></div>
            </div>
            <div class="l-item">
              <div class="l-info"><span>DISK_LOAD</span><span class="l-val">{{ (hardware.diskUsage || 0).toFixed(1) }}%</span></div>
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
              <div class="p-title-group">
                <h2 class="p-title-compact">实时执行动态</h2>
                <div class="p-chart-tabs">
                  <div class="pill-group">
                    <button :class="{ active: chartType === 'tokens' }" @click="chartType = 'tokens'">TOKEN</button>
                    <button :class="{ active: chartType === 'latency' }" @click="chartType = 'latency'">LATENCY</button>
                  </div>
                  <div class="pill-divider"></div>
                  <div class="pill-group">
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
              <div class="p-meta">
                <div class="pm-item"><span class="p-lbl">今日请求 (Today)</span><span class="p-val accent-blue">{{ summary.daily_requests || 0 }}</span></div>
                <div class="pm-item"><span class="p-lbl">平均延时 (Avg Lat)</span><span class="p-val accent-green">{{ summary.avg_latency || '0ms' }}</span></div>
              </div>
            </div>
            <div class="p-chart-wrap">
                <LineChart 
                :data="trendData" 
                :title="chartType === 'tokens' ? 'Token Usage Stream' : 'Response Latency Trend'"
                :yAxisName="chartType === 'tokens' ? 'Tokens' : 'ms'"
                height="100%" 
                :color="isDark ? '#26FFDF' : '#00BFA5'" 
              />
            </div>
          </div>

          <!-- Semantic Cloud -->
          <div class="hub-semantic-panel">
            <div class="semantic-hdr">
              <span class="sh-txt">语义联想与决策空间 / SEMANTIC_ORCHESTRATION</span>
              <span class="sh-code">LIVE_FETCH_ACTIVE</span>
            </div>
            <div class="semantic-world">
              <div 
                v-for="(tag, idx) in intentTags" 
                :key="tag.label" 
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

          <footer class="hub-footer">
             <div class="f-left">
                <span class="f-status-dot"></span>
                <span class="f-tag">MAPPING_ENGINE.B_2 // SYSTEM_MONITOR_ON</span>
             </div>
             <div class="f-right">
                <span class="f-code-tag">ACTIVE_LOGGING_v4.2</span>
             </div>
          </footer>
        </div>
      </article>

      <!-- RIGHT: Tactical Board -->
      <aside class="col-battle">
        <!-- Top KPIs -->
        <div class="premium-card">
          <div class="card-glow"></div>
          <div class="mini-kpi-grid">
             <div class="mk-box">
                <span class="mk-lbl">活跃智能体数</span>
                <span class="mk-val blue">{{ summary.online_agents || 0 }}<small>AGENT</small></span>
             </div>
             <div class="mk-box">
                <span class="mk-lbl">运行健康评分</span>
                <span class="mk-val green">{{ summary.averageHealthScore || 0 }}<small>%</small></span>
             </div>
          </div>
        </div>

        <!-- Rank List -->
        <div class="premium-card flex-grow battle-card">
          <div class="card-glow"></div>
          <div class="battle-header">
            <h3 class="b-title">资源分布 (按Agent)</h3>
            <span class="b-count">{{ distribution.length }} ACTIVE</span>
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
            <h4 class="re-title">实时审计日志 / AUDIT_STREAM</h4>
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
import { ref, onMounted, computed, watch } from 'vue'
import { useDark } from '@vueuse/core'
import { Timer, Collection, Connection } from '@element-plus/icons-vue'
import { 
  getGlobalSummary, 
  getAgentList, 
  getServerHardware, 
  getTokenHistory, 
  getTokenTrend, 
  getLatencyTrend,
  getTokenDistribution
} from '@/api/monitor'
import LineChart from '@/components/LineChart.vue'

const isDark = useDark()

const currentTime = ref('')
const currentDate = ref('')
const uptimeHours = ref(24)
const uptimeMinutes = ref(15)
const currentRange = ref('1H')
const ranges = ['5M', '1H', '24H', '7D']

const chartType = ref('tokens')
const trendData = ref([])

const summary = ref({})
const agents = ref([])
const hardware = ref({ cpuUsage: 0, gpuUsage: 0, memoryUsage: 0, diskUsage: 0 })
const recentLogs = ref([])
const distribution = ref([])

const intentTags = [
  { label: '知识库检索', size: 14, opacity: 1, delay: 0.1, color: '#00BFA5' },
  { label: '语义理解', size: 11, opacity: 0.7, delay: 0.5, color: '#94a3b8' },
  { label: '逻辑推理', size: 13, opacity: 0.9, delay: 0.8, color: '#26FFDF' },
  { label: 'DeepSeek-R1', size: 16, opacity: 1, delay: 0.3, color: '#10b981' },
  { label: 'Agent_Thinking', size: 10, opacity: 0.5, delay: 1.5, color: '#64748b' },
  { label: '文本纠错', size: 11, opacity: 0.8, delay: 0.9, color: '#3b82f6' },
  { label: '跨库关联', size: 12, opacity: 0.8, delay: 0.4, color: '#00BFA5' },
  { label: '用户建模', size: 11, opacity: 0.6, delay: 1.8, color: '#94a3b8' }
]

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

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
  currentDate.value = now.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', weekday: 'short' })
}

const handleRangeChange = (r) => {
  currentRange.value = r
  fetchTrend()
}

const fetchTrend = async () => {
  try {
    const period = currentRange.value.toLowerCase()
    const res = chartType.value === 'tokens' ? await getTokenTrend(period) : await getLatencyTrend(period)
    // res now has numeric timestamp and keys like 'tokens' or 'latency'
    trendData.value = res.map(i => ({ 
      timestamp: i.timestamp, 
      value: chartType.value === 'tokens' ? (i.tokens || 0) : (i.latency || 0)
    }))
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
        uptimeHours.value = Math.floor(totalMinutes / 60)
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
          time: new Date(i.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
          text: `${name} | ${i.endpoint?.split('/').pop() || 'Processing'}`,
          status: i.success ? 'healthy' : 'critical'
        };
      })
    }
    if (d.status === 'fulfilled') distribution.value = d.value.sort((a,b) => b.value - a.value)
    await fetchTrend()
  } catch (e) { console.error(e) }
}

watch([chartType, currentRange], fetchTrend)
onMounted(() => {
  updateTime(); fetchData();
  setInterval(updateTime, 1000);
  setInterval(fetchData, 10000);
})
</script>

<style scoped>
.command-center-root {
  height: calc(100vh - 84px);
  padding: 16px;
  background-color: #f6f8fa;
  color: #1a1c21;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow: hidden;
  font-family: 'Outfit', 'Inter', system-ui, sans-serif;
}
.theme-dark { background-color: #0b0e14; color: #f9fafb; }

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
.p-title-group { display: flex; align-items: center; gap: 16px; flex: 1; min-width: 0; }
.panel-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; gap: 20px; }

.p-chart-tabs { 
  display: flex; 
  align-items: center; 
  gap: 8px; 
  background: rgba(0,0,0,0.03); 
  padding: 2px 4px; 
  border-radius: 100px;
  border: 1px solid rgba(0,0,0,0.04);
  flex-shrink: 0;
}
.theme-dark .p-chart-tabs { background: rgba(0,0,0,0.4); border-color: rgba(255,255,255,0.05); }

.pill-group { display: flex; gap: 2px; flex-shrink: 0; }
.pill-divider { width: 1px; height: 12px; background: rgba(0,0,0,0.1); margin: 0 2px; flex-shrink: 0; }
.theme-dark .pill-divider { background: rgba(255,255,255,0.1); }

.pill-group button, .r-pill-v2 {
  font-size: 10px; font-weight: 800; border: none; padding: 5px 14px; border-radius: 100px;
  cursor: pointer; transition: all 0.3s; background: transparent; color: #94a3b8; 
  white-space: nowrap;
}
.pill-group button.active, .r-pill-v2.active {
  background: var(--orin-primary); color: #fff; box-shadow: 0 2px 8px var(--primary-glow);
}
.r-pill-v2:hover:not(.active) { color: var(--orin-primary); }

.p-meta { display: flex; gap: 32px; }
.p-lbl { font-size: 11px; font-weight: 700; color: #94a3b8; display: block; margin-bottom: 4px; }
.p-val { font-size: 24px; font-weight: 900; }
.accent-blue { color: var(--orin-primary); } .accent-green { color: #10b981; }

.p-chart-wrap { flex: 1; min-height: 0; }

.hub-semantic-panel { flex: 0.6; background: #f8fafc; border-radius: 14px; padding: 18px; display: flex; flex-direction: column; }
.theme-dark .hub-semantic-panel { background: rgba(255,255,255,0.02); }
.sh-txt { font-size: 12px; font-weight: 800; color: var(--orin-primary); }
.sh-code { font-size: 10px; font-weight: 900; color: #cbd5e1; opacity: 0.6; }

.semantic-world { flex: 1; display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; align-content: center; }
.s-tag-v4 {
  font-size: var(--f-size); opacity: var(--op); color: var(--c);
  background: #fff; border: 1px solid rgba(0,0,0,0.06);
  padding: 6px 16px; border-radius: 20px; font-weight: 700;
  box-shadow: 0 1px 2px rgba(0,0,0,0.02);
  animation: sFloat 4s ease-in-out infinite;
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
.mini-kpi-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
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

@media (max-width: 1550px) { .cc-layout-grid { grid-template-columns: 240px 1fr 280px; } }
</style>
