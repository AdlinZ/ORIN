package com.adlin.orin.modules.system.dto;

import lombok.Data;

/**
 * 系统健康检查响应
 */
@Data
public class HealthCheckResponse {
    
    private String status;
    private long timestamp;
    private String version;
    
    // 各组件状态
    private ComponentStatus database;
    private ComponentStatus redis;
    private ComponentStatus milvus;
    
    @Data
    public static class ComponentStatus {
        private String name;
        private String status; // UP, DOWN, UNKNOWN
        private String message;
        private long responseTimeMs;
    }
}
