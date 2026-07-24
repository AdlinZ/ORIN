import request from '@/utils/request';

/**
 * F05 Endpoint 领域 API client。
 *
 * 路由映射（与 backend EndpointController 一一对应）：
 *   POST   /endpoints                → publishEndpoint
 *   GET    /endpoints                → listEndpoints
 *   GET    /endpoints/{id}           → getEndpoint
 *   POST   /endpoints/{id}/deactivate → deactivateEndpoint
 *   POST   /endpoints/{id}/activate   → activateEndpoint
 */

const EP_ROOT = '/endpoints';

/** F05：发布已冻结 AgentVersion 为 API / MCP 端点。 */
export const publishEndpoint = (payload) =>
    request.post(EP_ROOT, payload);

/** F05：列表。 */
export const listEndpoints = (params = {}) =>
    request.get(EP_ROOT, { params });

/** F05：详情。 */
export const getEndpoint = (id) =>
    request.get(`${EP_ROOT}/${id}`);

/** F05：下线。 */
export const deactivateEndpoint = (id) =>
    request.post(`${EP_ROOT}/${id}/deactivate`);

/** F05：重新激活。 */
export const activateEndpoint = (id) =>
    request.post(`${EP_ROOT}/${id}/activate`);
