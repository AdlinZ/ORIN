<template>
  <div class="agent-extensions-page">
    <OrinPageShell
      title="智能体扩展中心"
      description="管理 Skills、MCP 服务与模型工具（Tools）能力，统一在本页面完成配置。"
      :icon="MagicStick"
    >
      <template #filters>
        <div
          class="extensions-tabs-sticky"
          tabindex="0"
          @keydown="handleTabKeydown"
        >
          <el-tabs v-model="activeTab" class="extensions-primary-tabs">
          <el-tab-pane name="skills">
            <template #label>
              <span class="tab-label">
                <el-icon><MagicStick /></el-icon>
                技能
              </span>
            </template>
          </el-tab-pane>

          <el-tab-pane name="mcp">
            <template #label>
              <span class="tab-label">
                <el-icon><Service /></el-icon>
                MCP服务
              </span>
            </template>
          </el-tab-pane>

          <el-tab-pane name="bindings">
            <template #label>
              <span class="tab-label">
                <el-icon><Setting /></el-icon>
                模型工具
              </span>
            </template>
          </el-tab-pane>
          </el-tabs>
        </div>
      </template>

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
    </OrinPageShell>
  </div>
</template>

<script setup>
import { computed, nextTick, onErrorCaptured, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick, Service, Setting } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
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

const handleTabKeydown = (event) => {
  const currentIndex = TAB_KEYS.indexOf(activeTab.value)
  if (currentIndex < 0) return
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    activeTab.value = TAB_KEYS[(currentIndex + 1) % TAB_KEYS.length]
    return
  }
  if (event.key === 'ArrowLeft') {
    event.preventDefault()
    activeTab.value = TAB_KEYS[(currentIndex - 1 + TAB_KEYS.length) % TAB_KEYS.length]
  }
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

.content-panel {
  max-height: calc(100vh - 290px);
  overflow: auto;
  padding: 12px 18px 18px;
  border-radius: 14px;
  background: var(--neutral-white);
  border: 1px solid var(--orin-border);
  box-shadow: 0 8px 30px -24px rgba(15, 23, 42, 0.45);
}

.extensions-tabs-sticky {
  position: sticky;
  top: 0;
  z-index: 8;
  outline: none;
}

.extensions-tabs-sticky:focus-visible {
  border-radius: 10px;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.extensions-primary-tabs :deep(.el-tabs__header) {
  margin: 0;
  border-bottom: 1px solid var(--orin-border);
}

.extensions-primary-tabs :deep(.el-tabs__nav-wrap)::after {
  display: none;
}

.extensions-primary-tabs :deep(.el-tabs__active-bar) {
  height: 2px;
}

.extensions-primary-tabs :deep(.el-tabs__item) {
  height: 42px;
  padding: 0 16px !important;
  color: var(--text-secondary);
  font-weight: 500;
}

.extensions-primary-tabs :deep(.el-tabs__item:hover) {
  color: var(--orin-primary);
}

.extensions-primary-tabs :deep(.el-tabs__item.is-active) {
  color: var(--orin-primary) !important;
  font-weight: 600;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.tab-content {
  padding-top: 4px;
  min-height: 460px;
}

.tab-skeleton-wrap {
  padding: 8px 2px 2px;
}

.tab-error {
  margin-top: 2px;
}

@media (max-width: 720px) {
  .content-panel {
    padding: 12px;
    max-height: calc(100vh - 250px);
  }
}
</style>
