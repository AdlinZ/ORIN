<template>
  <div class="page-container">
    <OrinPageShell
      title="系统与网关"
      description="统一访问控制、网关策略、安全审计与外部集成总入口"
      icon="Setting"
      domain="系统与网关"
      maturity="beta"
    >
      <template #actions>
        <el-button :icon="Refresh" @click="refreshNow">
          刷新状态
        </el-button>
      </template>
    </OrinPageShell>

    <el-row :gutter="16">
      <el-col v-for="item in cards" :key="item.title" :xs="24" :sm="12" :lg="8">
        <el-card shadow="hover" class="gateway-card" @click="go(item.path)">
          <div class="card-head">
            <h3>{{ item.title }}</h3>
            <OrinMaturityBadge :level="item.maturity" />
          </div>
          <p>{{ item.description }}</p>
          <div class="card-foot">
            <el-link type="primary" :underline="false">
              进入页面
            </el-link>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="audit-card">
      <template #header>
        <div class="card-head">
          <span>近期审计活动（示例视图）</span>
        </div>
      </template>
      <OrinAuditTable :rows="auditRows" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { Refresh } from '@element-plus/icons-vue'
import OrinPageShell from '@/components/orin/OrinPageShell.vue'
import OrinMaturityBadge from '@/components/orin/OrinMaturityBadge.vue'
import OrinAuditTable from '@/components/orin/OrinAuditTable.vue'
import { getAuditLogs } from '@/api/audit'

const router = useRouter()

const cards = [
  {
    title: '统一网关策略',
    description: '路由、服务注册、限流与 ACL 策略管理',
    path: '/dashboard/control/gateway',
    maturity: 'available'
  },
  {
    title: '统一 API 文档',
    description: '统一接口门户、版本追踪与调用入口',
    path: '/dashboard/control/unified-api-docs',
    maturity: 'available'
  },
  {
    title: '限流规则',
    description: '用户/接口级限流策略与熔断配置',
    path: '/dashboard/control/rate-limit',
    maturity: 'available'
  },
  {
    title: '外部集成',
    description: 'Dify、RAGFlow、MCP 服务统一接入',
    path: '/dashboard/control/external-frameworks',
    maturity: 'beta'
  },
  {
    title: 'MCP 服务',
    description: '注册、启停、健康检查与权限绑定',
    path: '/dashboard/control/mcp-service',
    maturity: 'available'
  },
  {
    title: '安全审计',
    description: '审计日志查询与追踪回放',
    path: '/dashboard/control/audit-logs',
    maturity: 'available'
  }
]

const fallbackAuditRows = [
  {
    time: '2026-04-08 21:40:10',
    actor: 'admin',
    action: 'UPDATE_RATE_LIMIT',
    resource: 'gateway/rate-limit',
    result: 'SUCCESS',
    traceId: 'gw-66da8e78cc1f'
  },
  {
    time: '2026-04-08 21:35:32',
    actor: 'security-bot',
    action: 'BLOCK_IP',
    resource: 'gateway/acl',
    result: 'SUCCESS',
    traceId: 'gw-66da8c1304d2'
  },
  {
    time: '2026-04-08 21:19:52',
    actor: 'admin',
    action: 'UPDATE_MCP_SERVICE',
    resource: 'mcp/filesystem',
    result: 'FAILED',
    traceId: 'gw-66da89f050f4'
  }
]
const auditRows = ref([])

const go = (path) => {
  router.push(path)
}

const refreshNow = () => {
  window.dispatchEvent(new Event('page-refresh'))
  loadAuditLogs()
}

const toAuditRows = (payload) => {
  const source = Array.isArray(payload) ? payload : (payload?.records || payload?.content || [])
  return source.slice(0, 10).map((item) => ({
    time: item.createdAt ? dayjs(item.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    actor: item.userName || item.userId || item.operator || 'system',
    action: item.providerId || item.operationType || item.action || '-',
    resource: item.endpoint || item.resource || '-',
    result: item.success ? 'SUCCESS' : 'FAILED',
    traceId: item.traceId || item.conversationId || '-'
  }))
}

const loadAuditLogs = async () => {
  try {
    const response = await getAuditLogs({ page: 1, size: 10 })
    const rows = toAuditRows(response)
    auditRows.value = rows.length > 0 ? rows : fallbackAuditRows
  } catch (error) {
    auditRows.value = fallbackAuditRows
  }
}

onMounted(() => {
  loadAuditLogs()
})
</script>

<style scoped>
.gateway-card {
  cursor: pointer;
  min-height: 156px;
  margin-bottom: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.card-head h3 {
  margin: 0;
  font-size: 16px;
}

.gateway-card p {
  margin: 12px 0;
  color: var(--text-secondary);
}

.card-foot {
  margin-top: auto;
}

.audit-card {
  margin-top: 8px;
}
</style>
