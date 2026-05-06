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
  overflow: visible;
  border: 1px solid var(--orin-border);
  border-radius: var(--orin-card-radius, 8px);
  background: var(--neutral-white, #ffffff);
  box-shadow: 0 14px 36px -34px rgba(15, 23, 42, 0.5);
}

.extensions-hero {
  padding: 18px 20px 16px;
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
  gap: 8px;
  margin-top: 16px;
  padding: 4px;
  border: 1px solid rgba(15, 118, 110, 0.12);
  border-radius: 10px;
  background: rgba(248, 250, 252, 0.82);
}

.summary-card {
  min-width: 0;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease, background 0.18s ease, box-shadow 0.18s ease;
}

.summary-card:hover,
.summary-card.active {
  border-color: rgba(15, 118, 110, 0.22);
  background: #ffffff;
  box-shadow: 0 8px 18px -16px rgba(15, 23, 42, 0.45);
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
  padding: 14px;
  background: transparent;
  overflow: visible;
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

.tab-content :deep(.embedded-toolbar),
.tab-content :deep(.header-card) {
  margin-bottom: 12px !important;
  border: 1px solid var(--orin-border, #e2e8f0) !important;
  border-radius: var(--orin-card-radius, 8px) !important;
  background:
    linear-gradient(135deg, rgba(240, 253, 250, 0.76), rgba(255, 255, 255, 0.96) 56%),
    #ffffff !important;
  box-shadow: none !important;
}

.tab-content :deep(.embedded-toolbar),
.tab-content :deep(.header-card .el-card__body) {
  padding: 16px !important;
}

.tab-content :deep(.embedded-toolbar-main),
.tab-content :deep(.header-main) {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.tab-content :deep(.embedded-title),
.tab-content :deep(.title) {
  margin: 0;
  color: #0f172a;
  font-size: 20px;
  line-height: 1.25;
  letter-spacing: 0;
}

.tab-content :deep(.embedded-description),
.tab-content :deep(.desc) {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
}

.tab-content :deep(.embedded-control-row),
.tab-content :deep(.embedded-toolbar-bottom),
.tab-content :deep(.tool-stats) {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(226, 232, 240, 0.76);
}

.tab-content :deep(.embedded-stats),
.tab-content :deep(.tool-stats) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(108px, 1fr));
  gap: 8px;
  min-width: 0;
}

.tab-content :deep(.skill-stat),
.tab-content :deep(.mcp-stat),
.tab-content :deep(.tool-stat) {
  min-width: 0;
  height: auto;
  display: block;
  padding: 10px 12px;
  border: 1px solid rgba(13, 148, 136, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
  text-align: left;
  box-shadow: none;
}

.tab-content :deep(.skill-stat:hover),
.tab-content :deep(.skill-stat.active) {
  border-color: rgba(13, 148, 136, 0.34);
  background: rgba(240, 253, 250, 0.92);
}

.tab-content :deep(.skill-stat span),
.tab-content :deep(.mcp-stat span),
.tab-content :deep(.tool-stat span) {
  display: block;
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
}

.tab-content :deep(.skill-stat strong),
.tab-content :deep(.mcp-stat strong),
.tab-content :deep(.tool-stat strong) {
  display: block;
  margin-top: 4px;
  color: var(--orin-primary, #0d9488);
  font-size: 19px;
  line-height: 1.1;
}

.tab-content :deep(.embedded-filters),
.tab-content :deep(.toolbar) {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}

.tab-content :deep(.list-card),
.tab-content :deep(.mcp-tabs .el-card),
.tab-content :deep(.table-card) {
  border: 1px solid var(--orin-border, #e2e8f0) !important;
  border-radius: var(--orin-card-radius, 8px) !important;
  background: #ffffff !important;
  box-shadow: none !important;
}

.tab-content :deep(.list-card .el-card__header),
.tab-content :deep(.mcp-tabs .el-card__header),
.tab-content :deep(.table-card .el-card__header) {
  padding: 14px 16px;
  border-bottom: 1px solid var(--orin-border, #e2e8f0);
  background: #ffffff;
}

.tab-content :deep(.list-card .el-card__body),
.tab-content :deep(.mcp-tabs .el-card__body),
.tab-content :deep(.table-card .el-card__body) {
  padding: 14px !important;
}

.tab-content :deep(.skill-card-grid),
.tab-content :deep(.service-card-grid),
.tab-content :deep(.tool-card-grid),
.tab-content :deep(.tools-grid) {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 10px;
}

.tab-content :deep(.skill-card-item),
.tab-content :deep(.service-card-item),
.tab-content :deep(.tool-card-item),
.tab-content :deep(.tool-card) {
  border: 1px solid var(--orin-border, #e2e8f0) !important;
  border-radius: var(--orin-card-radius, 8px) !important;
  background: rgba(255, 255, 255, 0.92) !important;
  box-shadow: none !important;
}

.tab-content :deep(.skill-card-title),
.tab-content :deep(.service-title-wrap h3),
.tab-content :deep(.tool-title-wrap h3),
.tab-content :deep(.tool-name) {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.tab-content :deep(.skill-card-description),
.tab-content :deep(.skill-card-meta),
.tab-content :deep(.service-title-wrap span),
.tab-content :deep(.service-meta),
.tab-content :deep(.tool-title-wrap span),
.tab-content :deep(.tool-health),
.tab-content :deep(.tool-desc) {
  color: #64748b;
  font-size: 12px;
}

.tab-content :deep(.embedded-mode-switch) {
  border-color: rgba(13, 148, 136, 0.14);
  background: rgba(248, 250, 252, 0.84);
}

.tab-content :deep(.embedded-mode-switch button:hover),
.tab-content :deep(.embedded-mode-switch button.active) {
  color: var(--orin-primary, #0d9488);
  background: #ffffff;
}

.tab-content :deep(.skill-management.is-embedded .embedded-control-row) {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
}

.tab-content :deep(.skill-management.is-embedded .embedded-stats) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-width: 0;
}

.tab-content :deep(.skill-management.is-embedded .skill-stat) {
  min-width: 96px;
  display: inline-flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
}

.tab-content :deep(.skill-management.is-embedded .skill-stat strong) {
  margin-top: 0;
}

.tab-content :deep(.skill-management.is-embedded .embedded-filters) {
  flex-wrap: nowrap;
  justify-content: flex-end;
  margin-left: 0;
}

.tab-content :deep(.skill-management.is-embedded .skill-search-input) {
  width: min(360px, 34vw);
}

@media (max-width: 720px) {
  .extensions-hero {
    padding: 14px 14px 16px;
  }

  .extensions-summary {
    grid-template-columns: 1fr;
  }

  .content-panel {
    padding: 10px;
  }

  .tab-content :deep(.embedded-toolbar-main),
  .tab-content :deep(.header-main),
  .tab-content :deep(.embedded-control-row),
  .tab-content :deep(.embedded-toolbar-bottom),
  .tab-content :deep(.toolbar) {
    flex-direction: column;
    align-items: stretch;
  }

  .tab-content :deep(.skill-management.is-embedded .embedded-control-row) {
    grid-template-columns: 1fr;
  }

  .tab-content :deep(.skill-management.is-embedded .embedded-filters) {
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .tab-content :deep(.skill-management.is-embedded .skill-search-input) {
    width: 100%;
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

html.dark .tab-content :deep(.embedded-toolbar),
html.dark .tab-content :deep(.header-card),
html.dark .tab-content :deep(.list-card),
html.dark .tab-content :deep(.mcp-tabs .el-card),
html.dark .tab-content :deep(.table-card) {
  border-color: rgba(148, 163, 184, 0.16) !important;
  background:
    linear-gradient(135deg, rgba(15, 118, 110, 0.12), rgba(15, 23, 42, 0.94) 56%),
    var(--neutral-gray-900, #0f172a) !important;
}

html.dark .tab-content :deep(.list-card .el-card__header),
html.dark .tab-content :deep(.mcp-tabs .el-card__header),
html.dark .tab-content :deep(.table-card .el-card__header) {
  border-bottom-color: rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.84);
}

html.dark .tab-content :deep(.embedded-title),
html.dark .tab-content :deep(.title),
html.dark .tab-content :deep(.skill-card-title),
html.dark .tab-content :deep(.service-title-wrap h3),
html.dark .tab-content :deep(.tool-title-wrap h3),
html.dark .tab-content :deep(.tool-name) {
  color: #f8fafc;
}

html.dark .tab-content :deep(.embedded-description),
html.dark .tab-content :deep(.desc),
html.dark .tab-content :deep(.skill-stat span),
html.dark .tab-content :deep(.mcp-stat span),
html.dark .tab-content :deep(.tool-stat span),
html.dark .tab-content :deep(.skill-card-description),
html.dark .tab-content :deep(.skill-card-meta),
html.dark .tab-content :deep(.service-title-wrap span),
html.dark .tab-content :deep(.service-meta),
html.dark .tab-content :deep(.tool-title-wrap span),
html.dark .tab-content :deep(.tool-health),
html.dark .tab-content :deep(.tool-desc) {
  color: #94a3b8;
}

html.dark .tab-content :deep(.skill-stat),
html.dark .tab-content :deep(.mcp-stat),
html.dark .tab-content :deep(.tool-stat),
html.dark .tab-content :deep(.skill-card-item),
html.dark .tab-content :deep(.service-card-item),
html.dark .tab-content :deep(.tool-card-item),
html.dark .tab-content :deep(.tool-card) {
  border-color: rgba(148, 163, 184, 0.16) !important;
  background: rgba(15, 23, 42, 0.72) !important;
}

html.dark .tab-content :deep(.skill-stat strong),
html.dark .tab-content :deep(.mcp-stat strong),
html.dark .tab-content :deep(.tool-stat strong) {
  color: #5eead4;
}

html.dark .tab-content :deep(.embedded-control-row),
html.dark .tab-content :deep(.embedded-toolbar-bottom),
html.dark .tab-content :deep(.tool-stats) {
  border-top-color: rgba(148, 163, 184, 0.16);
}
</style>
