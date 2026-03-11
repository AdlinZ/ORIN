import request from '@/utils/request';

// 获取用户列表
export const getUserList = (params) => {
    return request.get('/users', { params });
};

// 获取用户详情
export const getUser = (id) => {
    return request.get(`/users/${id}`);
};

// 创建用户
export const createUser = (data) => {
    return request.post('/users', data);
};

// 更新用户
export const updateUser = (id, data) => {
    return request.put(`/users/${id}`, data);
};

// 删除用户
export const deleteUser = (id) => {
    return request.delete(`/users/${id}`);
};

// 修改密码
export const changePassword = (data) => {
    return request.post('/users/change-password', data);
};

// 重置密码
export const resetPassword = (id) => {
    return request.post(`/users/${id}/reset-password`);
};

// 启用/禁用用户
export const toggleUserStatus = (id, enabled) => {
    return request.put(`/users/${id}/status`, { enabled });
};

// 获取用户角色列表
export const getRoles = () => {
    return request.get('/users/roles');
};

// 分配角色
export const assignRole = (userId, roleId) => {
    return request.post(`/users/${userId}/role`, { roleId });
};
