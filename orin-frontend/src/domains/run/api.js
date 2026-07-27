import request from '@/utils/request';

/**
 * F03 + F04 Run 领域 API client。
 *
 * 路由映射（与 backend RunController 一一对应）：
 *   POST   /runs              → createRun
 *   GET    /runs              → listRuns
 *   GET    /runs/{id}         → getRun
 *   POST   /runs/{id}/cancel  → cancelRun
 *   POST   /runs/{id}/retry   → retryRun
 *   GET    /runs/{id}/logs    → getRunLogs (F04)
 *   GET    /runs/{id}/events  → getRunEvents (F04)
 *   GET    /runs/{id}/assignments → getRunAssignments (F04)
 */

const RUN_ROOT = '/runs';

/** F03：选择已冻结 AgentVersion + 可用 Runner → 创建 Run。 */
export const createRun = (payload) =>
    request.post(RUN_ROOT, payload);

/** F03/F04：分页列出 Run（支持 status/agentId/runnerId 筛选）。 */
export const listRuns = (params = {}) =>
    request.get(RUN_ROOT, { params });

/** F03：Run 详情。 */
export const getRun = (runId) =>
    request.get(`${RUN_ROOT}/${runId}`);

/** F03：取消 Run。 */
export const cancelRun = (runId) =>
    request.post(`${RUN_ROOT}/${runId}/cancel`);

/** F03：重试 Run（创建新 Run 并关联 originalRunId）。 */
export const retryRun = (runId) =>
    request.post(`${RUN_ROOT}/${runId}/retry`);

/** F04：拉取 Run 日志（增量：afterSeq 之后的新行）。 */
export const getRunLogs = (runId, afterSeq) =>
    request.get(`${RUN_ROOT}/${runId}/logs`, { params: { afterSeq } });

/** F04：拉取 Run 事件时间线（增量：afterSeq 之后的新事件）。 */
export const getRunEvents = (runId, afterSeq) =>
    request.get(`${RUN_ROOT}/${runId}/events`, { params: { afterSeq } });

/** F04：拉取 Run 分配历史（run_assignment 行）。 */
export const getRunAssignments = (runId) =>
    request.get(`${RUN_ROOT}/${runId}/assignments`);
