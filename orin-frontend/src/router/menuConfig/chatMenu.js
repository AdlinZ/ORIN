/**
 * ORIN Chat 菜单 IA。
 *
 * Chat 是给普通用户（ROLE_USER）使用的极简消费端，
 * 仅有一项：「对话 → ORIN Chat」。
 *
 * Chat 页面（UserPortal.vue）有自己的 service / session / file / profile / api-keys 侧栏，
 * 这里暴露的是一个 nav 配置单元，便于一二级菜单统计与未来扩展。
 */
import { ROUTES } from '../routes'
import { USER_MENU_ROLES } from './shared'

export const CHAT_MENU = [
  {
    id: 'chat',
    title: '对话',
    icon: 'ChatRound',
    color: '#0f766e',
    path: ROUTES.CHAT,
    surface: 'chat',
    roles: USER_MENU_ROLES,
    children: [
      { title: 'ORIN Chat', path: ROUTES.CHAT, icon: 'ChatRound', roles: USER_MENU_ROLES },
    ],
  },
]
