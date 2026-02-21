import request from '@/utils/request';

export const getAlertHistory = (params = {}) => {
    return request.get('/alerts/history', { params });
};

export const getAlertStats = () => {
    return request.get('/alerts/stats');
};

export const resolveAlert = (id) => {
    return request.post(`/alerts/history/${id}/resolve`);
};

export const triggerAlert = (data) => {
    return request.post('/alerts/trigger', data);
};
