import request from '@/utils/request';

export const getMultimodalModels = () => {
    return request.get('/multimodal/models');
};

export const uploadMultimodalFile = (file, fields = {}) => {
    const formData = new FormData();
    if (fields.fileName) {
        formData.append('file', file, fields.fileName);
    } else {
        formData.append('file', file);
    }
    Object.entries(fields).forEach(([key, value]) => {
        if (key !== 'fileName' && value !== undefined && value !== null) {
            formData.append(key, value);
        }
    });
    return request.post('/multimodal/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    });
};

export const getMultimodalFiles = (params) => {
    return request.get('/multimodal/files', { params });
};

export const getMultimodalFilesByType = (fileType, params) => {
    return request.get(`/multimodal/files/type/${fileType}`, { params });
};

export const getMultimodalFile = (fileId) => {
    return request.get(`/multimodal/files/${fileId}`);
};

export const downloadMultimodalFile = (fileId) => {
    return request.get(`/multimodal/files/${fileId}/download`, {
        responseType: 'blob'
    });
};

export const getMultimodalThumbnail = (fileId) => {
    return request.get(`/multimodal/files/${fileId}/thumbnail`);
};

export const deleteMultimodalFile = (fileId) => {
    return request.delete(`/multimodal/files/${fileId}`);
};

export const getMultimodalStats = () => {
    return request.get('/multimodal/stats');
};
