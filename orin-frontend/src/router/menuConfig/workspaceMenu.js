/**
 * ORIN 工作台菜单 IA（canonical）。
 *
 * 工作台面向 Agent 创作者 / 开发者 / 业务操作人员。
 * 一级菜单 5 个：首页、智能体、工作流、知识库、扩展能力。
 * 二级菜单按既有的页面映射，不创建空页面。
 */
import { ROUTES } from '../routes'
import { WORKSPACE_MENU_ROLES } from './shared'

const WP = ROUTES.WORKSPACE_PATHS

/**
 * 工作台菜单顶层节点。
 * 每个 menu 节点带 surface = 'workspace' 以便排查时识别。
 */
export const WORKSPACE_MENU = [
  {
    id: 'workspace-home',
    title: '首页',
    icon: 'Monitor',
    color: '#0f766e',
    path: WP.ROOT,
    surface: 'workspace',
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: '我的概览', path: `${WP.ROOT}/developer`, icon: 'DataBoard', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'workspace-agents',
    title: '智能体',
    icon: 'Robot',
    color: '#0ea5e9',
    path: WP.AGENTS,
    surface: 'workspace',
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: '智能体列表', path: WP.AGENTS, icon: 'Grid', roles: WORKSPACE_MENU_ROLES },
      { title: '智能体工作台', path: WP.AGENT_WORKSPACE, icon: 'ChatDotRound', roles: WORKSPACE_MENU_ROLES },
      { title: '会话记录', path: WP.CONVERSATIONS, icon: 'List', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'workspace-workflows',
    title: '工作流',
    icon: 'Connection',
    color: '#7c3aed',
    path: WP.WORKFLOWS,
    surface: 'workspace',
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: '工作流中心', path: WP.WORKFLOWS, icon: 'Connection', roles: WORKSPACE_MENU_ROLES },
      { title: '可视化编排', path: WP.WORKFLOW_VISUAL, icon: 'Edit', roles: WORKSPACE_MENU_ROLES },
      { title: '执行记录', path: WP.WORKFLOW_EXECUTION, icon: 'VideoPlay', roles: WORKSPACE_MENU_ROLES },
      { title: '多智能体协作', path: WP.COLLABORATION, icon: 'Share', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'workspace-knowledge',
    title: '知识库',
    icon: 'Reading',
    color: '#0891b2',
    path: WP.KNOWLEDGE_CENTER,
    surface: 'workspace',
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: '知识中心', path: WP.KNOWLEDGE_CENTER, icon: 'Reading', roles: WORKSPACE_MENU_ROLES },
      { title: '知识资产', path: WP.KNOWLEDGE_ASSETS, icon: 'Collection', roles: WORKSPACE_MENU_ROLES },
      { title: '检索实验', path: WP.KNOWLEDGE_RETRIEVAL, icon: 'Search', roles: WORKSPACE_MENU_ROLES },
    ],
  },
  {
    id: 'workspace-extensions',
    title: '扩展能力',
    icon: 'MagicStick',
    color: '#a855f7',
    path: ROUTES.WORKSPACE_PATHS.EXTENSIONS,
    surface: 'workspace',
    roles: WORKSPACE_MENU_ROLES,
    children: [
      { title: 'Skills', path: `${WP.EXTENSIONS}?tab=skills`, icon: 'Star', roles: WORKSPACE_MENU_ROLES },
      { title: 'MCP 服务', path: `${WP.EXTENSIONS}?tab=mcp`, icon: 'Connection', roles: WORKSPACE_MENU_ROLES },
      { title: '模型工具', path: `${WP.EXTENSIONS}?tab=bindings`, icon: 'Setting', roles: WORKSPACE_MENU_ROLES },
    ],
  },
]
