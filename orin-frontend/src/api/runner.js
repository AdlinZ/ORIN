/**
 * Runner API — F01 接入并监控服务器。
 *
 * 业务端点：/api/v1/runners、/api/v1/runner-enrollment-tokens（JWT 鉴权）。
 * 机器通道端点（/api/system/runners/**）仅供 Runner 二进制调用，前端不直连。
 */
import request from '@/utils/request';

// ============================================================
// Runner 管理（业务通道 /api/v1/runners）
// ============================================================

/** 获取 Runner 分页列表 */
export function listRunners(params = {}) {
    return request.get('/runners', { params });
}

/** 获取 Runner 详情 + 最近心跳 */
export function getRunnerDetail(id) {
    return request.get(`/runners/${id}`);
}

/** Drain Runner（停止接新 Run） */
export function drainRunner(id) {
    return request.post(`/runners/${id}/drain`);
}

/** 恢复 Runner 到 ONLINE */
export function restoreRunner(id) {
    return request.post(`/runners/${id}/restore`);
}

/** Revoke Runner（终态，凭据永久失效） */
export function revokeRunner(id) {
    return request.post(`/runners/${id}/revoke`);
}

// ============================================================
// Enrollment Token 管理（业务通道 /api/v1/runner-enrollment-tokens）
// ============================================================

/** 创建一次性 Enrollment Token */
export function createEnrollmentToken(data) {
    return request.post('/runner-enrollment-tokens', data);
}

/** 列出当前用户创建的 Token */
export function listEnrollmentTokens() {
    return request.get('/runner-enrollment-tokens');
}

/** 撤销未使用的 Token */
export function revokeEnrollmentToken(id) {
    return request.delete(`/runner-enrollment-tokens/${id}`);
}
