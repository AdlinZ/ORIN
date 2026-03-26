package com.adlin.orin.modules.observability.aspect;

import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Langfuse 追踪切面
 * 在 ProviderAdapter.chatCompletion 方法调用时自动记录 LLM 生成信息
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class LangfuseTracingAspect {

    private final LangfuseObservabilityService langfuseService;

    /**
     * 拦截 chatCompletion(ChatCompletionRequest, String) 方法
     * 记录 LLM 调用到 Langfuse
     */
    @Around("execution(* com.adlin.orin.gateway.adapter.ProviderAdapter.chatCompletion(com.adlin.orin.gateway.dto.ChatCompletionRequest, java.lang.String))")
    public Object traceChatCompletion(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();
        ChatCompletionRequest request = (ChatCompletionRequest) args[0];
        String traceId = (String) args[1];

        // 如果 traceId 为空或 Langfuse 未启用，直接执行
        if (traceId == null || !langfuseService.isEnabled()) {
            return joinPoint.proceed();
        }

        long startTime = System.currentTimeMillis();
        String model = request.getModel();

        try {
            Object result = joinPoint.proceed();
            ChatCompletionResponse response = (ChatCompletionResponse) result;

            // 记录成功调用
            recordLLMGeneration(traceId, model, response, startTime);

            return result;

        } catch (Throwable error) {
            // 记录失败事件
            recordFailedEvent(traceId, model, error);
            throw error;
        }
    }

    /**
     * 记录 LLM 生成信息
     */
    private void recordLLMGeneration(String traceId, String model,
                                     ChatCompletionResponse response, long startTime) {
        try {
            int promptTokens = response != null && response.getUsage() != null
                    ? (response.getUsage().getPromptTokens() != null ? response.getUsage().getPromptTokens() : 0)
                    : 0;
            int completionTokens = response != null && response.getUsage() != null
                    ? (response.getUsage().getCompletionTokens() != null ? response.getUsage().getCompletionTokens() : 0)
                    : 0;
            int totalTokens = response != null && response.getUsage() != null
                    ? (response.getUsage().getTotalTokens() != null ? response.getUsage().getTotalTokens() : 0)
                    : 0;
            long latencyMs = System.currentTimeMillis() - startTime;

            String completion = extractCompletion(response);

            langfuseService.recordLLMGeneration(
                    traceId,
                    model,
                    "", // prompt 内容太大，不记录完整内容
                    completion,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    latencyMs
            );

            log.debug("Recorded LLM generation to Langfuse: traceId={}, model={}, tokens={}",
                    traceId, model, totalTokens);

        } catch (Exception e) {
            // Langfuse 错误降级，不影响主流程
            log.warn("Failed to record Langfuse generation: {}", e.getMessage());
        }
    }

    /**
     * 记录失败事件
     */
    private void recordFailedEvent(String traceId, String model, Throwable error) {
        try {
            Map<String, Object> metadata = Map.of(
                    "error", error.getMessage() != null ? error.getMessage() : "unknown",
                    "model", model != null ? model : "unknown"
            );
            langfuseService.recordEvent(traceId, "LLM_CALL_FAILED", metadata);
        } catch (Exception e) {
            log.warn("Failed to record Langfuse error event: {}", e.getMessage());
        }
    }

    /**
     * 提取 completion 内容
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