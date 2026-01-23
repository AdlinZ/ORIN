package com.adlin.orin.modules.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent创建请求DTO
 * 用于接收创建Agent的请求数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentCreateRequest {

    /**
     * Agent名称
     */
    @NotBlank(message = "Agent名称不能为空")
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 端点URL
     */
    @NotBlank(message = "端点URL不能为空")
    @Pattern(regexp = "^https?://.*", message = "端点URL格式不正确")
    private String endpointUrl;

    /**
     * API密钥
     */
    @NotBlank(message = "API密钥不能为空")
    private String apiKey;

    /**
     * 数据集API密钥（可选）
     */
    private String datasetApiKey;

    /**
     * 提供商类型
     */
    private String providerType;

    /**
     * 模型名称
     */
    private String modelName;
}
