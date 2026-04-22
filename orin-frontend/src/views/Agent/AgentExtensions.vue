<template>
  <div class="agent-extensions-page">
    <OrinPageShell
      title="智能体扩展中心"
      description="管理 Skills、MCP 服务与模型工具（Tools）能力，统一在本页面完成配置。"
      :icon="MagicStick"
    >
      <template #filters>
        <el-tabs v-model="activeTab" class="extensions-tabs">
          <el-tab-pane name="skills">
            <template #label>
              <span class="tab-label">
                <el-icon><MagicStick /></el-icon>
                技能管理
              </span>
            </template>
          </el-tab-pane>

          <el-tab-pane name="mcp">
            <template #label>
              <span class="tab-label">
                <el-icon><Service /></el-icon>
                MCP 服务
              </span>
            </template>
          </el-tab-pane>

          <el-tab-pane name="bindings">
            <template #label>
              <span class="tab-label">
                <el-icon><Setting /></el-icon>
                模型工具（Tools）
              </span>
            </template>
          </el-tab-pane>
        </el-tabs>
      </template>

      <section class="content-panel">
        <div v-if="activeTab === 'skills'" class="tab-content">
          <SkillManagementPanel :embedded="true" />
        </div>
        <div v-else-if="activeTab === 'mcp'" class="tab-content">
          <McpServicePanel :embedded="true" />
        </div>
        <div v-else class="tab-content">
          <AgentToolsBindingPanel />
        </div>
      </section>
    </OrinPageShell>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MagicStick, Service, Setting } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import SkillManagementPanel from '@/views/Skill/SkillManagement.vue'
import McpServicePanel from '@/views/System/McpService.vue'
import AgentToolsBindingPanel from '@/views/Agent/AgentToolsBindingPanel.vue'

const activeTab = ref('skills')
</script>

<style scoped>
.agent-extensions-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.content-panel {
  padding: 12px 18px 18px;
  border-radius: 14px;
  background: var(--neutral-white);
  border: 1px solid var(--orin-border);
  box-shadow: 0 8px 30px -24px rgba(15, 23, 42, 0.45);
}

.extensions-tabs {
  margin: 0;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.tab-content {
  padding-top: 4px;
}

@media (max-width: 720px) {
  .content-panel {
    padding: 12px;
  }
}
</style>
