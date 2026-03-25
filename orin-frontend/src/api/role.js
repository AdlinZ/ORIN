import request from '@/utils/request';

// 获取角色列表
export const getRoleList = (params) => {
    return request.get('/roles', { params });
};

// 获取角色详情
export const getRole = (id) => {
    return request.get(`/roles/${id}`);
};

// 创建角色
export const createRole = (data) => {
    return request.post('/roles', data);
};

// 更新角色
export const updateRole = (id, data) => {
    return request.put(`/roles/${id}`, data);
};

// 删除角色
export const deleteRole = (id) => {
    return request.delete(`/roles/${id}`);
};