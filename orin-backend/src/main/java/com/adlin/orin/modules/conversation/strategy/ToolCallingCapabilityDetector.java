package com.adlin.orin.modules.conversation.strategy;

import com.adlin.orin.modules.agent.entity.AgentAccessProfile;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.model.service.OllamaIntegrationService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 检测指定 agent 的模型是否支持 tool calling。
 * 判断顺序：用户覆盖 → 模型名白名单 → 内存缓存 → 实时探测
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallingCapabilityDetector {

    private static final Set<String> KNOWN_PREFIXES = Set.of(
            "qwen", "deepseek", "glm-4", "glm-z1", "internlm", "yi-",
            "llama-3", "mistral", "mixtral", "gpt-", "claude-", "gemini-",
            "baichuan", "minimax", "phi-3", "phi-4", "command-r",
            "moonshot", "moonshotai", "kimi"
    );

    private final OllamaIntegrationService ollamaIntegrationService;

    private final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    /**
     * 判断该 agent 的模型是否支持 tool calling。
     */
    public boolean supports(String agentId, AgentMetadata metadata, AgentAccessProfile profile) {
        return detect(agentId, metadata, profile).isSupported();
    }

    /**
     * 检测结果及来源，便于前端展示“检索策略如何被决策”。
     */
    public ToolCallingDecision detect(String agentId, AgentMetadata metadata, AgentAccessProfile profile) {
        // 1. 用户手动覆盖
        if (metadata != null && metadata.getToolCallingOverride() != null) {
            boolean enabled = metadata.getToolCallingOverride();
            return ToolCallingDecision.builder()
                    .supported(enabled)
                    .source("manual_override")
                    .reason(enabled ? "用户强制开启 tool calling" : "用户强制关闭 tool calling")
                    .build();
        }

        // 2. 模型名白名单
        if (metadata != null && metadata.getModelName() != null) {
            String lower = metadata.getModelName().toLowerCase();
            for (String prefix : KNOWN_PREFIXES) {
                if (lower.startsWith(prefix)
                        || lower.contains(":" + prefix)
                        || lower.contains("/" + prefix)
                        || lower.contains(prefix)) {
                    log.debug("Tool calling whitelist hit: model={}", metadata.getModelName());
                    return ToolCallingDecision.builder()
                            .supported(true)
                            .source("model_whitelist")
                            .reason("模型命中 tool calling 白名单前缀: " + prefix)
                            .build();
                }
            }
        }

        // 3. 内存缓存
        if (cache.containsKey(agentId)) {
            boolean enabled = cache.get(agentId);
            return ToolCallingDecision.builder()
                    .supported(enabled)
                    .source("capability_cache")
                    .reason("命中本地探测缓存")
                    .build();
        }

        // 4. 实时探测
        if (profile != null && profile.getEndpointUrl() != null) {
            String model = metadata != null ? metadata.getModelName() : null;
            boolean result = ollamaIntegrationService.probeToolCalling(
                    profile.getEndpointUrl(), profile.getApiKey(), model);
            cache.put(agentId, result);
            log.info("Tool calling probe result: agentId={}, model={}, supports={}", agentId, model, result);
            return ToolCallingDecision.builder()
                    .supported(result)
                    .source("live_probe")
                    .reason("实时探测模型 tool calling 能力")
                    .build();
        }

        return ToolCallingDecision.builder()
                .supported(false)
                .source("default_off")
                .reason("缺少可探测 endpoint，默认关闭")
                .build();
    }

    /**
     * 清除指定 agent 的探测缓存（如 agent 配置变更后调用）。
     */
    public void invalidate(String agentId) {
        cache.remove(agentId);
    }

    @Data
    @Builder
    public static class ToolCallingDecision {
        private boolean supported;
        private String source;
        private String reason;
    }
}
