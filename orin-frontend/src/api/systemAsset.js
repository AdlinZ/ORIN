import request from '@/utils/request';

// ==================== 系统资产管理 ====================

export const uploadAvatar = (formData) => {
    return request.post('/system-asset/upload/avatar', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};

export const uploadSystemAsset = (formData) => {
    return request.post('/system-asset/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};

export const getSystemAssets = (params) => {
    return request.get('/system-asset', { params });
};

export const deleteSystemAsset = (id) => {
    return request.delete(`/system-asset/${id}`);
};
