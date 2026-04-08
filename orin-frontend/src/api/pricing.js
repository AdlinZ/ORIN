/**
 * 定价相关 API
 * 独立模块，专职管理 /api/v1/pricing/* 接口调用
 */
import request from '@/utils/request';

/**
 * 获取所有定价规则列表
 */
export const getPricingConfig = () => {
    return request.get('/pricing/config');
};

/**
 * 注意：由于模型 ID 可能包含斜杠，路径参数查询极易导致 404/400 错误。
 * 请优先使用 getPricingConfig() 获取全量列表并在前端过滤，或由后端支持 QueryParam。
 */
export const getPricingByProvider = async (providerId, tenantGroup = 'default') => {
    // 降级方案：获取全量并在前端过滤
    const res = await getPricingConfig();
    const list = (res && res.data) ? res.data : (Array.isArray(res) ? res : []);
    return list.find(p => p.providerId === providerId && p.tenantGroup === tenantGroup);
};

/**
 * 新增或更新定价规则（若 providerId+tenantGroup 已存在则自动合并）
 */
export const savePricingConfig = (data) => {
    return request.post('/pricing/config', data);
};

/**
 * 删除定价规则（by ID）
 */
export const deletePricingConfig = (id) => {
    return request.delete(`/pricing/config/${id}`);
};
