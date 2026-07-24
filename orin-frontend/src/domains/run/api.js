import request from '@/utils/request';

/**
 * F03 Run 领域 API client。
 *
 * 路由映射（与 backend RunController 一一对应）：
 *   POST   /runs              → createRun
 *   GET    /runs              → listRuns
 *   GET    /runs/{id}         → getRun
 *   POST   /runs/{id}/cancel  → cancelRun
 *   POST   /runs/{id}/retry   → retryRun
 */

const RUN_ROOT = '/runs';

/** F03：选择已冻结 AgentVersion + 可用 Runner → 创建 Run。 */
export const createRun = (payload) =>
    request.post(RUN_ROOT, payload);

/** F03：分页列出 Run。 */
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
