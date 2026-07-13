/**
 * ORIN 管理台菜单 IA（canonical）。
 *
 * 管理台面向管理员。一级菜单 6 个：
 * 平台总览 / 组织权限 / AI 基础设施 / 开放平台 / 运行运维 / 系统治理。
 * 每个一级菜单 ≤ 6 项二级。
 * 「Provider 配置」合并到「模型管理 / 环境配置」共用 admin/models 与 admin/system-env 的现有入口。
 */
import { ROUTES } from '../routes'
import { ADMIN_MENU_ROLES } from './shared'

const AP = ROUTES.ADMIN_PATHS

export const ADMIN_MENU = [
  {
    id: 'admin-overview',
    title: '平台总览',
    icon: 'DataBoard',
    color: '#0f766e',
    path: AP.ROOT,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '运行摘要', path: AP.HOME, icon: 'DataAnalysis', roles: ADMIN_MENU_ROLES },
    ],
  },
  {
    id: 'admin-organization',
    title: '组织权限',
    icon: 'User',
    color: '#155eef',
    path: AP.USERS,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '用户管理', path: AP.USERS, icon: 'User', roles: ADMIN_MENU_ROLES },
      { title: '部门管理', path: AP.DEPARTMENTS, icon: 'OfficeBuilding', roles: ADMIN_MENU_ROLES },
      { title: '角色管理', path: '/admin/roles', icon: 'UserFilled', roles: ADMIN_MENU_ROLES },
    ],
  },
  {
    id: 'admin-ai-infra',
    title: 'AI 基础设施',
    icon: 'Cpu',
    color: '#7c3aed',
    path: AP.MODELS,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '模型管理', path: AP.MODELS, icon: 'Cpu', roles: ADMIN_MENU_ROLES },
      { title: '定价配置', path: AP.PRICING, icon: 'PriceTag', roles: ADMIN_MENU_ROLES },
      { title: '环境配置', path: AP.ENVIRONMENT, icon: 'Tools', roles: ADMIN_MENU_ROLES },
    ],
  },
  {
    id: 'admin-open-platform',
    title: '开放平台',
    icon: 'Share',
    color: '#0891b2',
    path: AP.GATEWAY,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: 'API Key', path: `${AP.GATEWAY}?workspace=access`, icon: 'Key', roles: ADMIN_MENU_ROLES },
      { title: '统一网关', path: AP.GATEWAY, icon: 'Router', roles: ADMIN_MENU_ROLES },
      { title: 'MCP 服务', path: AP.MCP, icon: 'Connection', roles: ADMIN_MENU_ROLES },
    ],
  },
  {
    id: 'admin-runtime',
    title: '运行运维',
    icon: 'Monitor',
    color: '#f59e0b',
    path: AP.RUNTIME,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '服务器监控', path: `${AP.RUNTIME}/server`, icon: 'DataAnalysis', roles: ADMIN_MENU_ROLES },
      { title: '任务队列', path: `${AP.RUNTIME}/tasks`, icon: 'Tickets', roles: ADMIN_MENU_ROLES },
      { title: '调用链路', path: `${AP.RUNTIME}/traces`, icon: 'Share', roles: ADMIN_MENU_ROLES },
      { title: '用量统计', path: `${AP.RUNTIME}/metrics`, icon: 'Coin', roles: ADMIN_MENU_ROLES },
      { title: '性能分析', path: `${AP.RUNTIME}/latency`, icon: 'Timer', roles: ADMIN_MENU_ROLES },
      { title: '告警与日志', path: `${AP.RUNTIME}/alerts`, icon: 'Bell', roles: ADMIN_MENU_ROLES },
    ],
  },
  {
    id: 'admin-governance',
    title: '系统治理',
    icon: 'Setting',
    color: '#64748b',
    path: AP.NOTIFICATIONS,
    surface: 'admin',
    roles: ADMIN_MENU_ROLES,
    children: [
      { title: '通知设置', path: AP.NOTIFICATIONS, icon: 'Message', roles: ADMIN_MENU_ROLES },
      { title: '数据资产', path: AP.DATA_ASSETS, icon: 'Folder', roles: ADMIN_MENU_ROLES },
      { title: '审计日志', path: '/admin/audit-logs', icon: 'List', roles: ADMIN_MENU_ROLES },
      { title: '系统维护', path: `${AP.RUNTIME}/maintenance`, icon: 'Tools', roles: ADMIN_MENU_ROLES },
    ],
  },
]
