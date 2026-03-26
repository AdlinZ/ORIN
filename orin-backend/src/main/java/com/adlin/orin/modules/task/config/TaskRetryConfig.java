package com.adlin.orin.modules.task.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 任务重试策略配置
 * 支持为不同任务类型配置不同的重试参数
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "orin.task.retry")
public class TaskRetryConfig {

    /**
     * 是否启用重试
     */
    private boolean enabled = true;

    /**
     * 默认最大重试次数
     */
    private int defaultMaxRetries = 3;

    /**
     * 默认初始延迟（毫秒）
     */
    private long defaultInitialDelay = 30000;

    /**
     * 默认指数退避倍数
     */
    private double defaultBackoffMultiplier = 2.0;

    /**
     * 按任务类型的重试配置
     * key: 任务类型 (WORKFLOW, COLLABORATION, SYNC 等)
     */
    private Map<String, TaskTypeRetryConfig> typeConfigs = new HashMap<>();

    /**
     * 获取任务类型的重试配置
     */
    public TaskTypeRetryConfig getConfigForType(String taskType) {
        TaskTypeRetryConfig config = typeConfigs.get(taskType);
        if (config == null) {
            // 返回默认配置
            return new TaskTypeRetryConfig(defaultMaxRetries, defaultInitialDelay, defaultBackoffMultiplier);
        }
        return config;
    }

    /**
     * 单个任务类型的重试配置
     */
    @Data
    public static class TaskTypeRetryConfig {
        private int maxRetries;
        private long initialDelay;
        private double backoffMultiplier;

        public TaskTypeRetryConfig() {}

        public TaskTypeRetryConfig(int maxRetries, long initialDelay, double backoffMultiplier) {
            this.maxRetries = maxRetries;
            this.initialDelay = initialDelay;
            this.backoffMultiplier = backoffMultiplier;
        }
    }
}
