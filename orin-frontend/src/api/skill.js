import request from '@/utils/request';

/**
 * 获取技能列表
 * @param {Object} params - 筛选参数 { type, status }
 */
export const getSkillList = (params) => {
    return request.get('/skills', { params });
};

/**
 * 根据 ID 获取技能详情
 */
export const getSkill = (id) => {
    return request.get(`/skills/${id}`);
};

/**
 * 创建新技能
 */
export const createSkill = (data) => {
    return request.post('/skills', data);
};

/**
 * 更新现有技能
 */
export const updateSkill = (id, data) => {
    return request.put(`/skills/${id}`, data);
};

/**
 * 删除技能
 */
export const deleteSkill = (id) => {
    return request.delete(`/skills/${id}`);
};

/**
 * 获取技能的 SKILL.md 内容
 */
export const getSkillMd = (id) => {
    return request.get(`/skills/${id}/skill-md`);
};

/**
 * 执行技能测试
 */
export const executeSkill = (id, inputs) => {
    return request.post(`/skills/${id}/execute`, inputs);
};

/**
 * 导入外部技能
 */
export const importSkill = (params) => {
    return request.post('/skills/import', null, { params });
};
