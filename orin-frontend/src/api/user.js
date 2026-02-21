import request from '@/utils/request';

/**
 * 获取用户资料
 * @param {string} username 
 */
export function getUserProfile(username) {
    return request({
        url: `/users/profile/${username}`,
        method: 'get'
    });
}

/**
 * 获取用户看板
 * @param {string} username 
 */
export function getUserDashboard(username) {
    return request({
        url: `/users/dashboard/${username}`,
        method: 'get'
    });
}

/**
 * 更新用户资料
 * @param {Object} data 
 */
export function updateUserProfile(data) {
    return request({
        url: '/users/profile',
        method: 'put',
        data
    });
}

/**
 * 更新用户头像
 * @param {number} userId 
 * @param {string} avatarUrl 
 */
export function updateUserAvatar(userId, avatarUrl) {
    return request({
        url: '/users/avatar',
        method: 'post',
        data: { userId, avatarUrl }
    });
}

/**
 * 上传头像文件
 * @param {FormData} formData 
 */
export function uploadAvatar(formData) {
    return request({
        url: '/multimodal/upload',
        method: 'post',
        data: formData,
        headers: {
            'Content-Type': 'multipart/form-data'
        }
    });
}
