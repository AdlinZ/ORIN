/**
 * 把 axios / sendChatMessageStream / chatAgent 的失败转换成"普通用户友好 + 含
 * traceId"的助手气泡文本。前端拦截器（utils/request.js → buildErrorMessage）已经
 * 把 traceId 拼到 message 末尾；如果上游漏掉了 traceId，这里补一份可读的中文。
 */
import { buildErrorMessage } from '@/utils/request'

const FALLBACK = '请求失败，请稍后重试或联系管理员检查服务状态。'

/**
 * @param {unknown} error axios 错误、SSE 抛出的 Error，或任意可读对象
 * @returns {string} 给普通用户看的中文短句；traceId 会附在末尾
 */
export const formatChatError = (error) => {
  if (!error) {
    return FALLBACK
  }

  // 已经是字符串（兜底）：原样透传，避免双层包装
  if (typeof error === 'string') {
    return error.trim() || FALLBACK
  }

  // 收集候选信息：直接 message、axios 格式化结果、错误码映射
  const directMessage = typeof error?.message === 'string' ? error.message.trim() : ''
  let formatted = ''
  try {
    formatted = buildErrorMessage(error) || ''
  } catch (e) {
    formatted = ''
  }

  // 提取 traceId：top-level > response.data.traceId
  const traceId = error?.traceId || error?.response?.data?.traceId || ''

  // 1. 优先使用 directMessage（SSE / 自定义 Error）
  if (directMessage) {
    if (traceId && !directMessage.includes(traceId)) {
      return `${directMessage}（Trace ID: ${traceId}）`
    }
    return directMessage
  }

  // 2. 否则用 axios 的格式化（含 traceId 已拼接）
  if (formatted && formatted !== '请求失败，请稍后重试') {
    return formatted
  }

  // 3. 最后兜底
  if (traceId) {
    return `请求失败（Trace ID: ${traceId}）`
  }
  return FALLBACK
}

export const __testables = {
  FALLBACK
}
