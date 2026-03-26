package com.adlin.orin.modules.observability.trace;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.dto.EmbeddingResponse;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Langfuse 追踪包装器
 * 包装 ProviderAdapter，在调用时自动记录 LLM 生成信息到 Langfuse
 */
@Slf4j
@RequiredArgsConstructor
public class LangfuseTracingWrapper implements ProviderAdapter {

    private final ProviderAdapter delegate;
    private final LangfuseObservabilityService langfuseService;

    @Override
    public String getProviderType() {
        return delegate.getProviderType();
    }

    @Override
    public String getProviderName() {
        return delegate.getProviderName();
    }

    @Override
    public Mono<Boolean> healthCheck() {
        return delegate.healthCheck();
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request) {
        return delegate.chatCompletion(request);
    }

    @Override
    public Mono<ChatCompletionResponse> chatCompletion(ChatCompletionRequest request, String traceId) {
        long startTime = System.currentTimeMillis();
        String model = request.getModel();

        return delegate.chatCompletion(request, traceId)
                .doOnSuccess(response -> {
                    try {
                        // 提取 token 信息
                        int promptTokens = extractUsage(response, "prompt_tokens");
                        int completionTokens = extractUsage(response, "completion_tokens");
                        int totalTokens = extractUsage(response, "total_tokens");
                        long latencyMs = System.currentTimeMillis() - startTime;

                        // 提取 completion 内容
                        String completion = extractCompletion(response);

                        // 记录到 Langfuse
                        if (langfuseService.isEnabled() && traceId != null) {
                            langfuseService.recordLLMGeneration(
                                    traceId,
                                    model,
                                    "", // prompt 可能会很大，不记录完整内容
                                    completion,
                                    promptTokens,
                                    completionTokens,
                                    totalTokens,
                                    latencyMs
                            );
                        }
                    } catch (Exception e) {
                        // Langfuse 错误降级，不影响主流程
                        log.warn("Failed to record Langfuse generation: {}", e.getMessage());
                    }
                })
                .doOnError(error -> {
                    try {
                        if (langfuseService.isEnabled() && traceId != null) {
                            // 记录失败事件
                            Map<String, Object> metadata = Map.of(
                                    "error", error.getMessage(),
                                    "model", model != null ? model : "unknown"
                            );
                            langfuseService.recordEvent(traceId, "LLM_CALL_FAILED", metadata);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to record Langfuse error event: {}", e.getMessage());
                    }
                });
    }

    @Override
    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        return delegate.chatCompletionStream(request);
    }

    @Override
    public Mono<EmbeddingResponse> embedding(EmbeddingRequest request) {
        return delegate.embedding(request);
    }

    @Override
    public Mono<Map<String, Object>> getModels() {
        return delegate.getModels();
    }

    @Override
    public Map<String, Object> getProviderConfig() {
        return delegate.getProviderConfig();
    }

    @Override
    public double estimateCost(ChatCompletionRequest request) {
        return delegate.estimateCost(request);
    }

    /**
     * 从响应中提取 usage 信息
     */
    private int extractUsage(ChatCompletionResponse response, String key) {
        if (response == null || response.getUsage() == null) {
            return 0;
        }
        ChatCompletionResponse.Usage usage = response.getUsage();
        return switch (key) {
            case "prompt_tokens" -> usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
            case "completion_tokens" -> usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
            case "total_tokens" -> usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
            default -> 0;
        };
    }

    /**
     * 从响应中提取 completion 内容
     */
    private String extractCompletion(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return "";
        }
        var choice = response.getChoices().get(0);
        if (choice != null && choice.getMessage() != null) {
            return choice.getMessage().getContent();
        }
        return "";
    }
}