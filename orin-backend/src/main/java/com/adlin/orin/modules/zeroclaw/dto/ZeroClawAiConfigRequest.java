package com.adlin.orin.modules.zeroclaw.dto;

import lombok.Data;

/**
 * ZeroClaw AI 配置请求
 */
@Data
public class ZeroClawAiConfigRequest {

    /**
     * AI 提供商: deepseek, siliconflow, ollama, kimi, zhipu, custom
     */
    private String provider;

    /**
     * API Key (可选，用于覆盖 ModelConfig 中的配置)
     */
    private String apiKey;

    /**
     * Base URL (可选)
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;
}
