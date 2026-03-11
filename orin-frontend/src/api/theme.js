import request from '@/utils/request';

// ==================== 主题管理 ====================

export const getTheme = () => {
    return request.get('/theme');
};

export const updateTheme = (theme) => {
    return request.put('/theme', { theme });
};

export const getThemeOptions = () => {
    return request.get('/theme/options');
};
