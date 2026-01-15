/**
 * JWT Token 工具函数
 */

/**
 * 解析 JWT Token
 * @param {string} token - JWT Token
 * @returns {object|null} - 解析后的 payload 或 null
 */
export function parseJwt(token) {
    try {
        if (!token) return null;

        const base64Url = token.split('.')[1];
        if (!base64Url) return null;

        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
            atob(base64)
                .split('')
                .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );

        return JSON.parse(jsonPayload);
    } catch (error) {
        console.error('Failed to parse JWT:', error);
        return null;
    }
}

/**
 * 检查 Token 是否过期
 * @param {string} token - JWT Token
 * @returns {boolean} - true 表示已过期
 */
export function isTokenExpired(token) {
    const payload = parseJwt(token);
    if (!payload || !payload.exp) {
        return true; // 无法解析或没有过期时间，视为过期
    }

    // exp 是秒级时间戳，需要转换为毫秒
    const expirationTime = payload.exp * 1000;
    const currentTime = Date.now();

    return currentTime >= expirationTime;
}

/**
 * 获取 Token 剩余有效时间（毫秒）
 * @param {string} token - JWT Token
 * @returns {number} - 剩余时间（毫秒），如果已过期返回 0
 */
export function getTokenRemainingTime(token) {
    const payload = parseJwt(token);
    if (!payload || !payload.exp) {
        return 0;
    }

    const expirationTime = payload.exp * 1000;
    const currentTime = Date.now();
    const remaining = expirationTime - currentTime;

    return remaining > 0 ? remaining : 0;
}

/**
 * 格式化剩余时间为可读字符串
 * @param {number} milliseconds - 毫秒数
 * @returns {string} - 格式化的时间字符串
 */
export function formatRemainingTime(milliseconds) {
    if (milliseconds <= 0) {
        return '已过期';
    }

    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    const days = Math.floor(hours / 24);

    if (days > 0) {
        return `${days} 天`;
    } else if (hours > 0) {
        return `${hours} 小时`;
    } else if (minutes > 0) {
        return `${minutes} 分钟`;
    } else {
        return `${seconds} 秒`;
    }
}

/**
 * 检查 Token 是否即将过期（默认 5 分钟内）
 * @param {string} token - JWT Token
 * @param {number} thresholdMinutes - 阈值（分钟）
 * @returns {boolean} - true 表示即将过期
 */
export function isTokenExpiringSoon(token, thresholdMinutes = 5) {
    const remaining = getTokenRemainingTime(token);
    const threshold = thresholdMinutes * 60 * 1000; // 转换为毫秒

    return remaining > 0 && remaining <= threshold;
}
