import { ROUTES } from '../router/routes.js'

export const REVAMP_ROLLOUT_ITEMS = Object.freeze([
  {
    key: 'revampAgentsHub',
    title: '智能体中枢',
    route: ROUTES.AGENTS.LIST,
    v2View: '@/views/revamp/agents/AgentListV2.vue',
    maturity: 'available',
    stage: 1,
    domain: '智能体中枢',
    description: '统一智能体列表、详情与操作面板主链路。'
  },
  {
    key: 'revampKnowledgeHub',
    title: '知识中枢',
    route: ROUTES.KNOWLEDGE.LIST,
    v2View: '@/views/revamp/knowledge/KnowledgeListV2.vue',
    maturity: 'available',
    stage: 2,
    domain: '知识与工作流',
    description: '统一知识库筛选、绑定与生命周期入口。'
  },
  {
    key: 'revampWorkflowHub',
    title: '工作流中枢',
    route: ROUTES.AGENTS.WORKFLOWS,
    v2View: '@/views/revamp/workflow/WorkflowListV2.vue',
    maturity: 'available',
    stage: 2,
    domain: '知识与工作流',
    description: '统一工作流列表、版本与执行调度入口。'
  },
  {
    key: 'revampRuntimeOverview',
    title: '运行总览',
    route: ROUTES.MONITOR.DASHBOARD,
    v2View: '@/views/revamp/monitor/RuntimeOverviewV2.vue',
    maturity: 'available',
    stage: 3,
    domain: '监控与运维',
    description: '总览、链路、告警与节点监控主路径。'
  },
  {
    key: 'revampCollaboration',
    title: '多智能体协作',
    route: ROUTES.AGENTS.COLLABORATION_DASHBOARD,
    v2View: '@/views/revamp/collaboration/CollaborationDashboardV2.vue',
    maturity: 'beta',
    stage: 4,
    domain: '智能体中枢',
    description: '任务包、子任务、事件流与回退动作可视化。'
  },
  {
    key: 'revampSystemGateway',
    title: '系统与网关',
    route: ROUTES.SYSTEM.GATEWAY,
    v2View: '@/views/revamp/system/SystemGatewayV2.vue',
    maturity: 'available',
    stage: 5,
    domain: '系统与网关',
    description: '统一访问控制、限流策略与审计入口。'
  },
  {
    key: 'revampSystemConfigHub',
    title: '系统配置中心',
    route: ROUTES.SYSTEM.SETTINGS_MONITOR,
    v2View: '@/views/revamp/system/SystemConfigHubV2.vue',
    maturity: 'available',
    stage: 5,
    domain: '系统与网关',
    description: '整合通知、同步、集成与模型默认配置。'
  },
  {
    key: 'revampAuditCenter',
    title: '审计中心',
    route: ROUTES.SYSTEM.AUDIT_LOGS,
    v2View: '@/views/revamp/system/AuditCenterV2.vue',
    maturity: 'available',
    stage: 6,
    domain: '系统与网关',
    description: '统一审计检索、追踪与回放闭环。'
  }
])

export const REVAMP_ROLLOUT_STAGES = Object.freeze([
  { stage: 1, title: '阶段 1', flags: ['revampAgentsHub'] },
  { stage: 2, title: '阶段 2', flags: ['revampKnowledgeHub', 'revampWorkflowHub'] },
  { stage: 3, title: '阶段 3', flags: ['revampRuntimeOverview'] },
  { stage: 4, title: '阶段 4', flags: ['revampCollaboration'] },
  { stage: 5, title: '阶段 5', flags: ['revampSystemGateway', 'revampSystemConfigHub'] },
  { stage: 6, title: '阶段 6', flags: ['revampAuditCenter'] }
])
