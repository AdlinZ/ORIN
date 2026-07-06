/**
 * ROLE_USER /chat 上下文持久化
 *
 * 把"当前选中的智能体 / 当前会话"持久化到 localStorage，使刷新或重新打开
 * 浏览器后能自动恢复最近的对话上下文。所有方法对 SSR / 隐私模式 / quota
 * 异常做了兜底（任何抛错都退化为 no-op，不会让页面崩溃）。
 */

const AGENT_KEY = 'orin_chat_agent_id'
const SESSION_KEY = 'orin_chat_session_id'

const safeGet = (key) => {
  if (typeof window === 'undefined') return ''
  try {
    return window.localStorage.getItem(key) || ''
  } catch (e) {
    return ''
  }
}

const safeSet = (key, value) => {
  if (typeof window === 'undefined') return
  try {
    if (value == null || value === '') {
      window.localStorage.removeItem(key)
    } else {
      window.localStorage.setItem(key, String(value))
    }
  } catch (e) {
    // quota exceeded / private mode — 忽略
  }
}

const safeRemove = (key) => {
  if (typeof window === 'undefined') return
  try {
    window.localStorage.removeItem(key)
  } catch (e) {
    // ignore
  }
}

export const useChatSelectionPersistence = () => {
  return {
    /** 一次性读出当前保存的 agent / session id */
    restore() {
      return {
        agentId: safeGet(AGENT_KEY),
        sessionId: safeGet(SESSION_KEY)
      }
    },
    /** 写入"当前选中的智能体"，空值/空串会清空 */
    persistAgent(agentId) {
      if (agentId == null || agentId === '') {
        safeRemove(AGENT_KEY)
        return
      }
      safeSet(AGENT_KEY, agentId)
    },
    /** 写入"当前会话 id"，空值/空串会清空 */
    persistSession(sessionId) {
      if (sessionId == null || sessionId === '') {
        safeRemove(SESSION_KEY)
        return
      }
      safeSet(SESSION_KEY, sessionId)
    },
    /** 清空当前会话 id（开始新对话时调用） */
    clearSession() {
      safeRemove(SESSION_KEY)
    },
    /** 清空 agent + session（用户级"清空上下文"，预留） */
    clear() {
      safeRemove(AGENT_KEY)
      safeRemove(SESSION_KEY)
    }
  }
}

export const __testables = {
  AGENT_KEY,
  SESSION_KEY,
  safeGet,
  safeSet,
  safeRemove
}
