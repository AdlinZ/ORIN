/**
 * Shared menu utilities for ORIN frontend.
 *
 * 工作台与管理台各自维护一份专属菜单（workspaceMenu.js / adminMenu.js）。
 * 共用 helpers（角色判断、菜单过滤、激活项）放到本文件，避免两份菜单各自重复。
 */
import { ROUTES } from '../routes'

export const ADMIN_MENU_ROLES = ['ROLE_ADMIN', 'ADMIN']
export const USER_MENU_ROLES = ['ROLE_USER', 'USER']
export const WORKSPACE_MENU_ROLES = [...ADMIN_MENU_ROLES, ...USER_MENU_ROLES]

export function hasAnyRole(userRoles = [], targetRoles = []) {
  if (!targetRoles || targetRoles.length === 0) return true
  return targetRoles.some((role) => userRoles.includes(role))
}

export function isAdminLike(userRoles = []) {
  return hasAnyRole(userRoles, ADMIN_MENU_ROLES)
}

/**
 * 判断该节点是否是 section / divider（用于分组渲染）。
 */
export function isMenuSection(item = {}) {
  return item.type === 'section' || item.divider
}

/**
 * 根据角色过滤一组子菜单，section 仅在还有可见项时保留。
 */
export function filterMenuChildren(children = [], userRoles = [], fallbackRoles = []) {
  const visibleChildren = children.filter((child) => (
    isMenuSection(child) || hasAnyRole(userRoles, child.roles || fallbackRoles)
  ))

  return visibleChildren.filter((child, index) => {
    if (!isMenuSection(child)) return true

    const nextSectionIndex = visibleChildren
      .slice(index + 1)
      .findIndex((nextChild) => isMenuSection(nextChild))
    const sectionChildren = nextSectionIndex >= 0
      ? visibleChildren.slice(index + 1, index + 1 + nextSectionIndex)
      : visibleChildren.slice(index + 1)

    return sectionChildren.some((nextChild) => !isMenuSection(nextChild))
  })
}

/**
 * 根据当前路由返回该菜单组内命中的顶级菜单 id。
 * 顶级菜单无 path 命中时，按子菜单最长前缀匹配。
 */
export function getActiveMenuIdForMenu(menuList = [], currentPath = '') {
  if (!currentPath) return null

  let matchedByChild = null
  let longestChildPath = -1
  let matchedByMenu = null
  let longestMenuPath = -1

  for (const menu of menuList) {
    if (menu.path && currentPath.startsWith(menu.path) && menu.path.length > longestMenuPath) {
      matchedByMenu = menu.id
      longestMenuPath = menu.path.length
    }

    if (menu.children) {
      for (const child of menu.children) {
        if (child.path && currentPath.startsWith(child.path) && child.path.length > longestChildPath) {
          matchedByChild = menu.id
          longestChildPath = child.path.length
        }
      }
    }
  }

  return matchedByChild || matchedByMenu
}

/**
 * 把菜单配置按角色过滤，返回最终可显示的菜单。
 */
export function buildVisibleMenus(menuList = [], userRoles = []) {
  return menuList
    .filter((menu) => hasAnyRole(userRoles, menu.roles))
    .map((menu) => ({
      ...menu,
      children: filterMenuChildren(menu.children || [], userRoles, menu.roles),
    }))
    .filter((menu) => !menu.children || menu.children.length > 0)
}

export { ROUTES }
