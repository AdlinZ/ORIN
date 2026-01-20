import request from '@/utils/request';

/**
 * 测试智谱AI连接
 * @param {string} endpoint - API端点
 * @param {string} apiKey - API密钥
 * @param {string} model - 模型名称
 * @returns {Promise}
 */
export const testZhipuConnection = (endpoint, apiKey, model) => {
    return request.post('/model-config/test-zhipu-connection', null, {
        params: { endpoint, apiKey, model }
    });
};

/**
 * 接入智谱AI Agent
 * @param {string} endpoint - API端点
 * @param {string} apiKey - API密钥
 * @param {string} model - 模型名称
 * @param {string} name - Agent名称（可选）
 * @param {number} temperature - 温度参数（可选）
 * @returns {Promise}
 */
export const onboardZhipuAgent = (endpoint, apiKey, model, name, temperature = 1.0) => {
    return request.post('/agents/onboard-zhipu', null, {
        params: {
            endpointUrl: endpoint,
            apiKey: apiKey,
            model: model,
            name: name,
            temperature: temperature
        }
    });
};

/**
 * 发送消息到智谱AI
 * @param {string} endpoint - API端点
 * @param {string} apiKey - API密钥
 * @param {string} model - 模型名称
 * @param {string} message - 消息内容
 * @returns {Promise}
 */
export const sendZhipuMessage = (endpoint, apiKey, model, message) => {
    return request.post('/zhipu-agent/send-message', null, {
        params: { endpoint, apiKey, model, message }
    });
};

/**
 * 发送消息到智谱AI（带参数）
 * @param {string} endpoint - API端点
 * @param {string} apiKey - API密钥
 * @param {string} model - 模型名称
 * @param {string} message - 消息内容
 * @param {number} temperature - 温度参数
 * @param {number} topP - Top P参数
 * @param {number} maxTokens - 最大token数
 * @returns {Promise}
 */
export const sendZhipuMessageWithParams = (
    endpoint,
    apiKey,
    model,
    message,
    temperature = 1.0,
    topP = 0.7,
    maxTokens = 2000
) => {
    return request.post('/zhipu-agent/send-message-with-params', null, {
        params: {
            endpoint,
            apiKey,
            model,
            message,
            temperature,
            topP,
            maxTokens
        }
    });
};
