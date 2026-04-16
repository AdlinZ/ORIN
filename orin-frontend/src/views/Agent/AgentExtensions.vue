<template>
  <div class="agent-extensions-container">
    <PageHeader
      title="智能体扩展"
      description="管理智能体的核心能力扩展，包括技能绑定和 MCP 服务集成"
      icon="MagicStick"
    />

    <el-tabs v-model="activeTab" class="extensions-tabs">
      <!-- 技能管理 -->
      <el-tab-pane label="技能管理" name="skills" :lazy="true">
        <SkillManagementPanel />
      </el-tab-pane>

      <!-- MCP 服务 -->
      <el-tab-pane label="MCP 服务" name="mcp" :lazy="true">
        <McpServicePanel />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import SkillManagementPanel from '@/views/Skill/SkillManagement.vue'
import McpServicePanel from '@/views/System/McpService.vue'

const activeTab = ref('skills')
</script>

<style scoped>
.agent-extensions-container {
  padding: 20px;
}

.extensions-tabs {
  margin-top: 20px;
}
</style>
