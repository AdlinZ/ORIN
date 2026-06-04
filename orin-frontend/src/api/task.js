import request from '@/utils/request';

// ==================== 任务队列管理 ====================

export const getTaskById = (taskId, config = {}) => {
    return request.get(`/workflow-tasks/${taskId}`, config);
};

export const getTasksByWorkflowId = (workflowId, config = {}) => {
    return request.get(`/workflow-tasks/workflow/${workflowId}`, config);
};

export const getTaskStatus = (taskId, config = {}) => {
    return request.get(`/workflow-tasks/${taskId}/status`, config);
};

export const getQueuedTasks = (params = {}, config = {}) => {
    return request.get('/workflow-tasks/queued', {
        params,
        ...config
    });
};

export const getRunningTasks = (params = {}, config = {}) => {
    return request.get('/workflow-tasks/running', {
        params,
        ...config
    });
};

export const getFailedTasks = (params = {}, config = {}) => {
    return request.get('/workflow-tasks/failed', {
        params,
        ...config
    });
};

export const getDeadTasks = (params = {}, config = {}) => {
    return request.get('/workflow-tasks/dead', {
        params,
        ...config
    });
};

export const getCancelledTasks = (params = {}, config = {}) => {
    return request.get('/workflow-tasks/cancelled', {
        params,
        ...config
    });
};

export const replayTask = (taskId, config = {}) => {
    return request.post(`/workflow-tasks/${taskId}/replay`, null, config);
};

export const cancelTask = (taskId, config = {}) => {
    return request.post(`/workflow-tasks/${taskId}/cancel`, null, config);
};

export const getTaskStatistics = (config = {}) => {
    return request.get('/workflow-tasks/statistics', config);
};

export const getPendingPriorityStatistics = (config = {}) => {
    return request.get('/workflow-tasks/priority-statistics', config);
};
