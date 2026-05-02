package com.adlin.orin.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关模型前缀配置属性
 * 将模型前缀映射外部化到配置文件，避免硬编码
 */
@Data
@Component
@ConfigurationProperties(prefix = "orin.gateway.model-mapping")
public class GatewayModelProperties {

    /**
     * 模型前缀映射列表
     * 例如: [{prefix: "gpt-", providerType: "openai"}, {prefix: "pro/", providerType: "siliconflow"}]
     */
    private List<ModelMapping> mappings = new ArrayList<>();

    /**
     * 默认路由策略
     */
    private String defaultStrategy = "LOWEST_COST";

    @Data
    public static class ModelMapping {
        /**
         * 模型前缀，如 "gpt-", "pro/", "deepseek-ai/"
         */
        private String prefix;

        /**
         * Provider 类型: openai, dify, ollama, local, siliconflow
         */
        private String providerType;

        /**
         * 可选，指定特定 Provider ID
         */
        private String providerId;
    }

    /**
     * 缓存前缀到配置的映射，加速查找
     */
    private final Map<String, ModelMapping> prefixCache = new ConcurrentHashMap<>();

    /**
     * 根据模型名称查找映射配置
     */
    public ModelMapping findMapping(String modelName) {
        if (modelName == null || mappings.isEmpty()) {
            return null;
        }
        String normalized = modelName.toLowerCase();
        for (ModelMapping mapping : mappings) {
            if (normalized.startsWith(mapping.getPrefix().toLowerCase())) {
                return mapping;
            }
        }
        return null;
    }
}