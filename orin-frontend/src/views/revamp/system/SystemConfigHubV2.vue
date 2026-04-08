<template>
  <div class="page-container">
    <OrinPageShell
      title="配置中心"
      description="统一收敛系统参数、通知、同步、集成与模型配置入口"
      icon="Tools"
      domain="系统与网关"
      maturity="available"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="refreshPage">
          刷新
        </el-button>
      </template>
    </OrinPageShell>

    <el-row :gutter="16">
      <el-col v-for="item in entries" :key="item.title" :xs="24" :sm="12" :lg="8">
        <el-card class="entry-card" shadow="hover" @click="go(item.path)">
          <div class="entry-head">
            <h3>{{ item.title }}</h3>
            <OrinMaturityBadge :level="item.maturity" />
          </div>
          <p>{{ item.description }}</p>
          <el-link type="primary" :underline="false">
            进入配置
          </el-link>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinMaturityBadge from '@/components/orin/OrinMaturityBadge.vue'

const router = useRouter()

const entries = [
  {
    title: '系统环境',
    description: '监控数据源、外部依赖与关键环境参数配置',
    path: '/dashboard/runtime/server',
    maturity: 'available'
  },
  {
    title: '通知中心',
    description: '邮件、IM、Webhook 通道配置与联通验证',
    path: '/dashboard/control/notification-channels',
    maturity: 'available'
  },
  {
    title: '端侧同步',
    description: '端侧知识同步策略、回放与检查点管理',
    path: '/dashboard/control/client-sync',
    maturity: 'beta'
  },
  {
    title: '外部集成',
    description: 'Dify / RAGFlow / Neo4j / MCP 集成管理',
    path: '/dashboard/control/external-frameworks',
    maturity: 'beta'
  },
  {
    title: 'MCP 服务',
    description: 'MCP 服务注册、启停与健康检查',
    path: '/dashboard/control/mcp-service',
    maturity: 'available'
  },
  {
    title: '重构灰度控制台',
    description: '统一管理 V2 模块启用状态、阶段与回退开关',
    path: '/dashboard/control/revamp-rollout',
    maturity: 'beta'
  },
  {
    title: '模型默认参数',
    description: '聊天、Embedding、VLM 与评估模型默认值',
    path: '/dashboard/applications/models/config',
    maturity: 'available'
  }
]

const go = (path) => router.push(path)

const refreshPage = () => window.dispatchEvent(new Event('page-refresh'))
</script>

<style scoped>
.entry-card {
  min-height: 150px;
  margin-bottom: 16px;
  cursor: pointer;
}

.entry-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.entry-head h3 {
  margin: 0;
  font-size: 16px;
}

.entry-card p {
  margin: 12px 0;
  color: var(--text-secondary);
}
</style>
