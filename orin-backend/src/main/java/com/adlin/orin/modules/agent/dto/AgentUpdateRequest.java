package com.adlin.orin.modules.agent.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent更新请求DTO
 * 用于接收更新Agent的请求数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUpdateRequest {

    /**
     * Agent名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 端点URL
     */
    @Pattern(regexp = "^https?://.*", message = "端点URL格式不正确")
    private String endpointUrl;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 数据集API密钥
     */
    private String datasetApiKey;

    /**
     * 连接状态
     */
    private String connectionStatus;
}
