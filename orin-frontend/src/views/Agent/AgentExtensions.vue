<template>
  <div class="agent-extensions-page">
    <section class="extensions-console">
      <header class="extensions-hero">
        <div class="extensions-hero-main">
          <div class="extensions-icon">
            <el-icon><MagicStick /></el-icon>
          </div>
          <div class="extensions-title-block">
            <h1>智能体扩展中心</h1>
            <p>统一管理 Skills、MCP 服务与模型工具能力，集中完成配置、启停和维护。</p>
          </div>
        </div>

        <div class="extensions-summary">
          <button
            v-for="item in tabSummaries"
            :key="item.key"
            type="button"
            :class="['summary-card', { active: activeTab === item.key }]"
            @click="activeTab = item.key"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>
              <strong>{{ item.title }}</strong>
              <small>{{ item.description }}</small>
            </span>
          </button>
        </div>

      </header>

      <section
        ref="contentPanelRef"
        class="content-panel"
      >
        <div v-if="switchLoading" class="tab-skeleton-wrap">
          <el-skeleton :rows="7" animated />
        </div>
        <div v-else class="tab-content">
          <el-alert
            v-if="tabRenderError"
            class="tab-error"
            type="error"
            :title="`页面渲染失败：${tabRenderError}`"
            description="已自动回退到默认标签页，请点击重试。"
            show-icon
            :closable="false"
          >
            <template #default>
              <el-button type="primary" text @click="recoverFromTabError">
                重试
              </el-button>
            </template>
          </el-alert>
          <keep-alive>
            <component
              :is="currentTabComponent"
              :embedded="true"
            />
          </keep-alive>
        </div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onErrorCaptured, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Service, Setting } from '@element-plus/icons-vue'
import SkillManagementPanel from '@/views/Skill/SkillManagement.vue'
import McpServicePanel from '@/views/System/McpService.vue'
import AgentToolsBindingPanel from '@/views/Agent/AgentToolsBindingPanel.vue'

const TAB_KEYS = ['skills', 'mcp', 'bindings']
const TAB_DEFAULT = 'skills'
const TAB_SWITCH_SKELETON_MS = 260

const route = useRoute()
const router = useRouter()

const activeTab = ref(TAB_DEFAULT)
const lastTab = ref(TAB_DEFAULT)
const switchLoading = ref(false)
const contentPanelRef = ref(null)
const tabRenderError = ref('')

const tabScrollState = ref({
  skills: 0,
  mcp: 0,
  bindings: 0
})

const tabComponentMap = {
  skills: SkillManagementPanel,
  mcp: McpServicePanel,
  bindings: AgentToolsBindingPanel
}

const tabSummaries = [
  {
    key: 'skills',
    title: 'Skills',
    description: '脚本、API 与知识检索能力',
    icon: MagicStick
  },
  {
    key: 'mcp',
    title: 'MCP 服务',
    description: '外部服务连接与工具安装',
    icon: Service
  },
  {
    key: 'bindings',
    title: '模型工具',
    description: 'Function calling 工具目录',
    icon: Setting
  }
]

const currentTabComponent = computed(() => tabComponentMap[activeTab.value] || tabComponentMap[TAB_DEFAULT])

const getSanitizedTab = (value) => {
  const resolved = Array.isArray(value) ? value[0] : value
  return TAB_KEYS.includes(resolved) ? resolved : null
}

const saveCurrentScroll = () => {
  if (!contentPanelRef.value) return
  tabScrollState.value[lastTab.value] = contentPanelRef.value.scrollTop || 0
}

const restoreCurrentScroll = () => {
  if (!contentPanelRef.value) return
  const nextTop = tabScrollState.value[activeTab.value] || 0
  contentPanelRef.value.scrollTo({ top: nextTop, behavior: 'auto' })
}

const updateRouteTab = (tab) => {
  const nextQuery = { ...route.query, tab }
  router.replace({ query: nextQuery })
}

const recoverFromTabError = () => {
  tabRenderError.value = ''
  activeTab.value = TAB_DEFAULT
}

watch(
  () => route.query.tab,
  (queryTab) => {
    const validTab = getSanitizedTab(queryTab)
    if (validTab) {
      if (activeTab.value !== validTab) {
        activeTab.value = validTab
      }
      return
    }
    if (queryTab) {
      ElMessage.warning('标签参数无效，已回退到“技能”')
    }
    if (route.query.tab !== TAB_DEFAULT) {
      updateRouteTab(TAB_DEFAULT)
    }
    if (activeTab.value !== TAB_DEFAULT) {
      activeTab.value = TAB_DEFAULT
    }
  },
  { immediate: true }
)

watch(activeTab, async (nextTab, prevTab) => {
  if (!TAB_KEYS.includes(nextTab)) return
  tabRenderError.value = ''
  if (prevTab && TAB_KEYS.includes(prevTab)) {
    saveCurrentScroll()
    lastTab.value = prevTab
  }
  if (route.query.tab !== nextTab) {
    updateRouteTab(nextTab)
  }
  const start = Date.now()
  switchLoading.value = true
  await nextTick()
  const elapsed = Date.now() - start
  const delay = Math.max(0, TAB_SWITCH_SKELETON_MS - elapsed)
  if (delay > 0) {
    await new Promise((resolve) => setTimeout(resolve, delay))
  }
  switchLoading.value = false
  await nextTick()
  restoreCurrentScroll()
  lastTab.value = nextTab
})

onMounted(() => {
  restoreCurrentScroll()
})

onErrorCaptured((error) => {
  tabRenderError.value = error?.message || '未知渲染错误'
  if (activeTab.value !== TAB_DEFAULT) {
    activeTab.value = TAB_DEFAULT
  }
  return false
})
</script>

<style scoped>
.agent-extensions-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.extensions-console {
  overflow: hidden;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.82), rgba(255, 255, 255, 0.98) 42%),
    var(--neutral-white);
  box-shadow: 0 18px 50px -42px rgba(15, 23, 42, 0.52);
}

.extensions-hero {
  padding: 18px 20px 0;
  border-bottom: 1px solid var(--orin-border);
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.8), rgba(255, 255, 255, 0.96) 48%),
    var(--neutral-white);
}

.extensions-hero-main {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

.extensions-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border: 1px solid rgba(15, 118, 110, 0.16);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(240, 253, 250, 0.78);
  color: var(--orin-primary);
  font-size: 18px;
}

.extensions-title-block {
  min-width: 0;
}

.extensions-title-block h1 {
  margin: 0;
  color: #0f172a;
  font-size: 23px;
  line-height: 1.25;
  letter-spacing: 0;
}

.extensions-title-block p {
  margin: 7px 0 0;
  max-width: 760px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}

.extensions-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.summary-card {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: var(--orin-card-radius, 8px);
  background: rgba(255, 255, 255, 0.66);
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.summary-card:hover,
.summary-card.active {
  border-color: rgba(15, 118, 110, 0.36);
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 10px 30px -26px rgba(15, 23, 42, 0.55);
}

.summary-card .el-icon {
  margin-top: 2px;
  color: var(--orin-primary);
}

.summary-card span {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.summary-card strong {
  color: #0f172a;
  font-size: 14px;
  line-height: 1.2;
}

.summary-card small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.35;
}

.content-panel {
  max-height: calc(100vh - 246px);
  overflow: auto;
  padding: 16px;
  background: transparent;
  scrollbar-gutter: stable;
}

.tab-content {
  min-height: 460px;
}

.tab-skeleton-wrap {
  padding: 8px 2px 2px;
}

.tab-error {
  margin-top: 2px;
}

@media (max-width: 720px) {
  .extensions-hero {
    padding: 14px 14px 0;
  }

  .extensions-summary {
    grid-template-columns: 1fr;
  }

  .content-panel {
    padding: 10px;
    max-height: calc(100vh - 220px);
  }
}

html.dark .extensions-console {
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.74), rgba(15, 23, 42, 0.94)),
    var(--neutral-gray-900, #0f172a);
  box-shadow: none;
}

html.dark .extensions-hero {
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(15, 23, 42, 0.94) 52%),
    var(--neutral-gray-900, #0f172a);
}

html.dark .extensions-title-block h1,
html.dark .summary-card strong {
  color: #f8fafc;
}

html.dark .extensions-title-block p,
html.dark .summary-card small {
  color: #94a3b8;
}

html.dark .summary-card {
  border-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.66);
}

html.dark .summary-card:hover,
html.dark .summary-card.active {
  border-color: rgba(45, 212, 191, 0.32);
  background: rgba(15, 23, 42, 0.9);
  box-shadow: none;
}
</style>
