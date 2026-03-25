import request from '@/utils/request';

// 获取部门列表（树形结构）
export const getDepartmentList = () => {
    return request.get('/departments');
};

// 获取所有部门（扁平列表）
export const getAllDepartments = () => {
    return request.get('/departments/all');
};

// 获取部门详情
export const getDepartment = (id) => {
    return request.get(`/departments/${id}`);
};

// 获取子部门列表
export const getChildDepartments = (id) => {
    return request.get(`/departments/${id}/children`);
};

// 创建部门
export const createDepartment = (data) => {
    return request.post('/departments', data);
};

// 更新部门
export const updateDepartment = (id, data) => {
    return request.put(`/departments/${id}`, data);
};

// 删除部门
export const deleteDepartment = (id) => {
    return request.delete(`/departments/${id}`);
};