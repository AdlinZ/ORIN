<script setup>
import "./playground.css"
import { onMounted, provide, ref } from "vue"

import {
  createAgent,
  createWorkflow,
  deleteAgent,
  deleteWorkflow,
  fetchAppSettings,
  fetchAgents,
  fetchConversation,
  fetchSkills,
  fetchTemplates,
  fetchWorkflowGraph,
  fetchWorkflows,
  updateAgent,
  updateAppSettings,
  updateWorkflow,
  runWorkflowStream,
  runWorkflow,
} from "./api"
import { useWorkflowRunner } from "./composables/useWorkflowRunner"
import { I18N_KEY, createUiI18n } from "./i18n"
import AgentsPage from "./pages/AgentsPage.vue"
import OverviewPage from "./pages/OverviewPage.vue"
import PlaygroundPage from "./pages/PlaygroundPage.vue"
import SettingsPage from "./pages/SettingsPage.vue"
import WorkflowsPage from "./pages/WorkflowsPage.vue"

const props = defineProps({
  initialPage: {
    type: String,
    default: "overview",
  },
  themeStyle: {
    type: String,
    default: "legacy",
  },
  standalone: {
    type: Boolean,
    default: false,
  },
})

const templates = ref([])
const skills = ref([])
const agents = ref([])
const workflows = ref([])
const currentPage = ref(props.initialPage)
const skillSyncStatus = ref("")
const appSettings = ref(null)
const savingSettings = ref(false)

const i18n = createUiI18n()
provide(I18N_KEY, i18n)
const t = i18n?.t || ((key) => key)

const {
  selectedWorkflowId,
  selectedWorkflow,
  selectedGraph,
  loading,
  errorMessage,
  chatMessages,
  replayingTrace,
  activeNodeId,
  traceForView,
  chooseInitialWorkflow,
  loadGraph,
  run: handleRun,
  clear: handleClearRun,
  stop: handleStopRun,
} = useWorkflowRunner({
  workflows,
  fetchWorkflowGraph,
  fetchConversation,
  runWorkflow,
  runWorkflowStream,
  t,
})

async function loadInitialData() {
  [templates.value, skills.value, agents.value, workflows.value, appSettings.value] =
    await Promise.all([
      fetchTemplates(),
      fetchSkills(),
      fetchAgents(),
      fetchWorkflows(),
      fetchAppSettings(),
    ])

  chooseInitialWorkflow()
}

async function handleCreateAgent(payload) {
  errorMessage.value = ""
  try {
    const createdAgent = await createAgent(payload)
    const latestAgents = await fetchAgents()
    agents.value = [createdAgent, ...latestAgents.filter((a) => a.id !== createdAgent.id)]
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleUpdateAgent(payload) {
  errorMessage.value = ""
  try {
    await updateAgent(payload.id, payload.data)
    agents.value = await fetchAgents()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleDeleteAgent(agentId) {
  errorMessage.value = ""
  try {
    await deleteAgent(agentId)
    agents.value = await fetchAgents()
    workflows.value = await fetchWorkflows()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

function findSingleAgentChatWorkflow(agentId) {
  return (
    workflows.value.find(
      (w) =>
        w.type === "single_agent_chat" &&
        Array.isArray(w.specialist_agent_ids) &&
        w.specialist_agent_ids.length === 1 &&
        w.specialist_agent_ids[0] === agentId
    ) || null
  )
}

async function handleQuickChatAgent(agent) {
  if (!agent?.id) return
  errorMessage.value = ""
  try {
    let targetWorkflow = findSingleAgentChatWorkflow(agent.id)
    if (!targetWorkflow) {
      targetWorkflow = await createWorkflow({
        name: `${agent.name || "Agent"} Chat`,
        type: "single_agent_chat",
        specialist_agent_ids: [agent.id],
        finalizer_enabled: false,
        router_prompt: "Direct single-agent chat workflow.",
      })
      workflows.value = await fetchWorkflows()
      targetWorkflow =
        workflows.value.find((w) => w.id === targetWorkflow.id) ||
        findSingleAgentChatWorkflow(agent.id) ||
        targetWorkflow
    }
    currentPage.value = "playground"
    selectedWorkflowId.value = targetWorkflow.id
    await loadGraph(targetWorkflow.id)
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleCreateWorkflow(payload) {
  errorMessage.value = ""
  try {
    const workflow = await createWorkflow(payload)
    workflows.value = await fetchWorkflows()
    selectedWorkflowId.value = workflow.id
    currentPage.value = "playground"
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleUpdateWorkflow(payload) {
  errorMessage.value = ""
  try {
    const updated = await updateWorkflow(payload.id, payload.data)
    workflows.value = await fetchWorkflows()
    if (selectedWorkflowId.value === updated.id) await loadGraph(updated.id)
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleDeleteWorkflow(workflowId) {
  errorMessage.value = ""
  try {
    await deleteWorkflow(workflowId)
    workflows.value = await fetchWorkflows()
    if (selectedWorkflowId.value === workflowId) {
      selectedWorkflowId.value = workflows.value[0]?.id || ""
      if (selectedWorkflowId.value) {
        await loadGraph(selectedWorkflowId.value)
      } else {
        selectedGraph.value = null
      }
    }
  } catch (error) {
    errorMessage.value = String(error.message || error)
  }
}

async function handleSaveSettings(payload) {
  errorMessage.value = ""
  if (savingSettings.value) return
  savingSettings.value = true
  try {
    appSettings.value = await updateAppSettings(payload)
    skills.value = await fetchSkills()
  } catch (error) {
    errorMessage.value = String(error.message || error)
  } finally {
    savingSettings.value = false
  }
}

onMounted(async () => {
  try {
    await loadInitialData()
  } catch {
    templates.value = []
    skills.value = []
    agents.value = []
    workflows.value = []
    appSettings.value = null
  }
})
</script>

<template>
  <div
    class="playground-scope app-frame"
    :class="{
      'playground-mode': currentPage === 'playground',
      'standalone-run': props.standalone,
      'workflows-page': currentPage === 'workflows',
    }"
    :data-theme="props.themeStyle"
  >
    <main class="shell page-shell">
      <div v-if="errorMessage" class="error-banner">
        {{ errorMessage }}
      </div>

      <Transition name="page-fade" mode="out-in">
        <div
          :key="currentPage"
          class="page-stage"
          :class="{ 'playground-stage': currentPage === 'playground' }"
        >
          <OverviewPage
            v-if="!props.standalone && currentPage === 'overview'"
            :agents="agents"
            :workflows="workflows"
            :templates="templates"
            @navigate="currentPage = $event"
          />

          <AgentsPage
            v-else-if="!props.standalone && currentPage === 'agents'"
            :agents="agents"
            :skills="skills"
            :skill-sync-status="skillSyncStatus"
            @create="handleCreateAgent"
            @update="handleUpdateAgent"
            @delete="handleDeleteAgent"
            @quick-chat="handleQuickChatAgent"
          />

          <WorkflowsPage
            v-else-if="!props.standalone && currentPage === 'workflows'"
            :templates="templates"
            :agents="agents"
            :workflows="workflows"
            :selected-workflow-id="selectedWorkflowId"
            @create="handleCreateWorkflow"
            @update="handleUpdateWorkflow"
            @delete="handleDeleteWorkflow"
            @select="selectedWorkflowId = $event"
          />

          <PlaygroundPage
            v-else-if="currentPage === 'playground'"
            :workflows="workflows"
            :agents="agents"
            :selected-workflow-id="selectedWorkflowId"
            :selected-workflow="selectedWorkflow"
            :selected-graph="selectedGraph"
            :active-node-id="activeNodeId"
            :loading="loading"
            :trace="traceForView"
            :trace-playing="replayingTrace"
            :chat-messages="chatMessages"
            @run="handleRun"
            @clear="handleClearRun"
            @stop="handleStopRun"
            @select-workflow="selectedWorkflowId = $event"
          />

          <SettingsPage
            v-else-if="!props.standalone"
            :settings="appSettings"
            :saving="savingSettings"
            @save="handleSaveSettings"
          />
        </div>
      </Transition>
    </main>
  </div>
</template>

<style scoped>
/* playground-scope provides all base styles via playground.css import */
/* Only add layout overrides needed for ORIN embedding */
.app-frame {
  height: 100%;
  min-height: 0;
}

.app-frame.workflows-page .page-shell {
  width: min(1280px, 100%);
}

.app-frame.workflows-page .page-stage {
  width: min(1180px, 100%);
  margin: 0 auto;
}

.error-banner {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  color: #e11d48;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.15s ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}
</style>
