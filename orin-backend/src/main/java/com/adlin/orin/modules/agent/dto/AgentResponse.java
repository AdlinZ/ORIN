package com.adlin.orin.modules.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent响应DTO
 * 用于返回Agent信息给客户端
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentResponse {

    /**
     * Agent ID
     */
    private String agentId;

    /**
     * Agent名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 模型名称
     */
    private String modelName;

    /**
     * 提供商类型
     */
    private String providerType;

    /**
     * 连接状态
     */
    private String connectionStatus;

    /**
     * 端点URL（敏感信息，仅显示域名）
     */
    private String endpointDomain;

    /**
     * 同步时间
     */
    private LocalDateTime syncTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 健康状态
     */
    private HealthStatus health;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthStatus {
        private String status;
        private Integer responseTimeMs;
        private LocalDateTime lastCheckTime;
    }
}
