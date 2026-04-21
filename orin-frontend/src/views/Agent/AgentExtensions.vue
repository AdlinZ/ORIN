<template>
  <div class="agent-extensions-page">
    <section class="hero-panel">
      <div class="hero-content">
        <span class="hero-badge">应用管理</span>
        <h1 class="hero-title">智能体扩展中心</h1>
        <p class="hero-desc">
          管理 Skills、MCP 服务与模型工具（Tools）能力，统一在本页面完成配置。
        </p>
      </div>
      <div class="hero-grid">
        <div class="hero-metric">
          <div class="metric-label">能力编排</div>
          <div class="metric-value">Skills</div>
          <div class="metric-sub">API / Knowledge / Shell / Workflow</div>
        </div>
        <div class="hero-metric">
          <div class="metric-label">协议接入</div>
          <div class="metric-value">MCP</div>
          <div class="metric-sub">服务连接 / 工具安装 / 健康监控</div>
        </div>
      </div>
    </section>

    <section class="content-panel">
      <el-tabs v-model="activeTab" class="extensions-tabs">
        <el-tab-pane name="skills" :lazy="true">
          <template #label>
            <span class="tab-label">
              <el-icon><MagicStick /></el-icon>
              技能管理
            </span>
          </template>
          <div class="tab-content">
            <SkillManagementPanel :embedded="true" />
          </div>
        </el-tab-pane>

        <el-tab-pane name="mcp" :lazy="true">
          <template #label>
            <span class="tab-label">
              <el-icon><Service /></el-icon>
              MCP 服务
            </span>
          </template>
          <div class="tab-content">
            <McpServicePanel :embedded="true" />
          </div>
        </el-tab-pane>

        <el-tab-pane name="bindings" :lazy="true">
          <template #label>
            <span class="tab-label">
              <el-icon><Setting /></el-icon>
              模型工具（Tools）
            </span>
          </template>
          <div class="tab-content">
            <AgentToolsBindingPanel />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { MagicStick, Service, Setting } from '@element-plus/icons-vue'
import SkillManagementPanel from '@/views/Skill/SkillManagement.vue'
import McpServicePanel from '@/views/System/McpService.vue'
import AgentToolsBindingPanel from '@/views/Agent/AgentToolsBindingPanel.vue'

const activeTab = ref('skills')
</script>

<style scoped>
.agent-extensions-page {
  padding: 24px;
  background:
    radial-gradient(circle at top right, rgba(16, 185, 129, 0.1), transparent 35%),
    radial-gradient(circle at bottom left, rgba(20, 184, 166, 0.08), transparent 30%);
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 24px 26px;
  border-radius: 16px;
  border: 1px solid rgba(15, 118, 110, 0.16);
  background: linear-gradient(130deg, #f0fdfa 0%, #ffffff 48%, #f0f9ff 100%);
  margin-bottom: 16px;
}

.hero-content {
  max-width: 640px;
}

.hero-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 999px;
  color: #115e59;
  background: #ccfbf1;
  font-size: 12px;
  font-weight: 600;
}

.hero-title {
  margin: 12px 0 0;
  font-size: 34px;
  line-height: 1.15;
  letter-spacing: -0.02em;
  color: #0f172a;
}

.hero-desc {
  margin: 10px 0 0;
  color: #475569;
  font-size: 15px;
}

.hero-grid {
  display: grid;
  gap: 12px;
  width: 340px;
}

.hero-metric {
  border-radius: 12px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid rgba(148, 163, 184, 0.24);
}

.metric-label {
  color: #64748b;
  font-size: 12px;
}

.metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #0f766e;
}

.metric-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #64748b;
}

.content-panel {
  padding: 16px 18px 18px;
  border-radius: 14px;
  background: var(--neutral-white);
  border: 1px solid var(--orin-border);
  box-shadow: 0 8px 30px -24px rgba(15, 23, 42, 0.45);
}

.extensions-tabs {
  margin-top: 0;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
}

.tab-content {
  padding-top: 10px;
}

@media (max-width: 1024px) {
  .agent-extensions-page {
    padding: 16px;
  }

  .hero-panel {
    flex-direction: column;
  }

  .hero-grid {
    width: 100%;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .hero-title {
    font-size: 28px;
  }

  .hero-grid {
    grid-template-columns: 1fr;
  }

  .content-panel {
    padding: 12px;
  }
}
</style>
