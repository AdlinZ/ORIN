package com.adlin.orin.gateway.controller;

import com.adlin.orin.gateway.adapter.ProviderAdapter;
import com.adlin.orin.gateway.dto.ChatCompletionRequest;
import com.adlin.orin.gateway.dto.ChatCompletionResponse;
import com.adlin.orin.gateway.dto.EmbeddingRequest;
import com.adlin.orin.gateway.service.ProviderRegistry;
import com.adlin.orin.gateway.service.RouterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一API网关控制器
 * 提供OpenAI兼容的API接口
 */
@Slf4j
@RestController
@RequestMapping("/v1")
@Tag(name = "Unified API Gateway", description = "OpenAI兼容的统一API接口")
@RequiredArgsConstructor
public class ApiGatewayController {

    private final ProviderRegistry providerRegistry;
    private final RouterService routerService;

    /**
     * 聊天完成接口 (OpenAI兼容)
     * POST /v1/chat/completions
     */
    @Operation(summary = "聊天完成", description = "OpenAI兼容的聊天完成接口，支持流式和非流式响应")
    @PostMapping("/chat/completions")
    public Mono<ResponseEntity<Object>> chatCompletions(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "X-Provider-Id", required = false) String providerId,
            @RequestHeader(value = "X-Routing-Strategy", required = false) String routingStrategy) {
        log.info("Chat completion request: model={}, stream={}, providerId={}",
                request.getModel(), request.getStream(), providerId);

        // 选择Provider
        Mono<ProviderAdapter> providerMono;
        if (providerId != null && !providerId.isEmpty()) {
            // 使用指定的Provider
            providerMono = Mono.justOrEmpty(routerService.selectProviderById(providerId));
        } else if (request.getModel() != null) {
            // 根据模型名称智能选择
            providerMono = Mono.justOrEmpty(routerService.selectProviderByModel(request.getModel(), request));
        } else {
            // 使用路由策略选择
            RouterService.RoutingStrategy strategy = parseRoutingStrategy(routingStrategy);
            providerMono = Mono.justOrEmpty(routerService.selectProvider(request, strategy));
        }

        // 执行请求
        return providerMono
                .flatMap(provider -> {
                    log.info("Selected provider: {} (type: {})", provider.getProviderName(),
                            provider.getProviderType());

                    if (Boolean.TRUE.equals(request.getStream())) {
                        // 流式响应
                        return Mono.error(
                                new UnsupportedOperationException("Streaming not yet implemented in this endpoint"));
                    } else {
                        // 非流式响应
                        return provider.chatCompletion(request)
                                .map(response -> ResponseEntity.ok((Object) response));
                    }
                })
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body((Object) createError("No available provider", "service_unavailable"))))
                .onErrorResume(e -> {
                    log.error("Chat completion error: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) createError(e.getMessage(), "internal_error")));
                });
    }

    /**
     * 聊天完成流式接口
     * POST /v1/chat/completions (with stream=true)
     */
    @Operation(summary = "聊天完成（流式）", description = "流式聊天完成接口")
    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatCompletionResponse>> chatCompletionsStream(
            @RequestBody ChatCompletionRequest request,
            @RequestHeader(value = "X-Provider-Id", required = false) String providerId) {
        log.info("Stream chat completion request: model={}, providerId={}", request.getModel(), providerId);

        // 选择Provider
        ProviderAdapter provider;
        if (providerId != null && !providerId.isEmpty()) {
            provider = routerService.selectProviderById(providerId).orElse(null);
        } else {
            provider = routerService.selectProviderByModel(request.getModel(), request).orElse(null);
        }

        if (provider == null) {
            return Flux.error(new RuntimeException("No available provider"));
        }

        return provider.chatCompletionStream(request)
                .map(response -> ServerSentEvent.builder(response).build())
                .doOnError(e -> log.error("Stream error: {}", e.getMessage()));
    }

    /**
     * 文本嵌入接口 (OpenAI兼容)
     * POST /v1/embeddings
     */
    @Operation(summary = "文本嵌入", description = "OpenAI兼容的文本嵌入接口")
    @PostMapping("/embeddings")
    public Mono<ResponseEntity<Object>> embeddings(
            @RequestBody EmbeddingRequest request,
            @RequestHeader(value = "X-Provider-Id", required = false) String providerId) {
        log.info("Embedding request: model={}, providerId={}", request.getModel(), providerId);

        // 选择Provider（优先OpenAI类型）
        Mono<ProviderAdapter> providerMono;
        if (providerId != null && !providerId.isEmpty()) {
            providerMono = Mono.justOrEmpty(routerService.selectProviderById(providerId));
        } else {
            // 默认选择OpenAI Provider
            providerMono = Mono.justOrEmpty(
                    providerRegistry.getProvidersByType("openai").stream().findFirst());
        }

        return providerMono
                .flatMap(provider -> provider.embedding(request).map(response -> ResponseEntity.ok((Object) response)))
                .switchIfEmpty(Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body((Object) createError("No available provider for embeddings", "service_unavailable"))))
                .onErrorResume(e -> {
                    log.error("Embedding error: {}", e.getMessage(), e);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body((Object) createError(e.getMessage(), "internal_error")));
                });
    }

    /**
     * 获取可用模型列表 (OpenAI兼容)
     * GET /v1/models
     */
    @Operation(summary = "获取模型列表", description = "获取所有可用Provider的模型列表")
    @GetMapping("/models")
    public Mono<ResponseEntity<Map<String, Object>>> getModels() {
        log.info("Get models request");

        return Flux.fromIterable(providerRegistry.getHealthyProviders())
                .flatMap(ProviderAdapter::getModels)
                .collectList()
                .map(modelLists -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("object", "list");
                    result.put("data", modelLists);
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    log.error("Get models error: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(createError(e.getMessage(), "internal_error")));
                });
    }

    /**
     * Provider健康检查
     * GET /v1/health
     */
    @Operation(summary = "健康检查", description = "检查所有Provider的健康状态")
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return providerRegistry.checkAllHealth()
                .map(healthStatus -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("status", "ok");
                    result.put("providers", healthStatus);
                    result.put("statistics", providerRegistry.getStatistics());
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(e -> {
                    Map<String, Object> error = new HashMap<>();
                    error.put("status", "error");
                    error.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });
    }

    /**
     * Provider管理接口 - 获取Provider详情
     * GET /v1/providers
     */
    @Operation(summary = "获取Provider列表", description = "获取所有已注册Provider的详细信息")
    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> getProviders() {
        Map<String, Object> result = new HashMap<>();
        result.put("providers", providerRegistry.getProviderDetails());
        result.put("statistics", providerRegistry.getStatistics());
        return ResponseEntity.ok(result);
    }

    /**
     * 路由统计信息
     * GET /v1/routing/stats
     */
    @Operation(summary = "路由统计", description = "获取路由服务的统计信息")
    @GetMapping("/routing/stats")
    public ResponseEntity<Map<String, Object>> getRoutingStats() {
        return ResponseEntity.ok(routerService.getRoutingStatistics());
    }

    /**
     * 解析路由策略
     */
    private RouterService.RoutingStrategy parseRoutingStrategy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return RouterService.RoutingStrategy.LOWEST_COST; // 默认策略
        }

        try {
            return RouterService.RoutingStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid routing strategy: {}, using default LOWEST_COST", strategy);
            return RouterService.RoutingStrategy.LOWEST_COST;
        }
    }

    private final com.adlin.orin.modules.workflow.service.WorkflowService workflowService;

    /**
     * 执行工作流
     * POST /v1/workflows/{workflowId}/execute
     */
    @Operation(summary = "执行工作流", description = "执行指定的工作流编排")
    @PostMapping("/workflows/{workflowId}/execute")
    public Mono<ResponseEntity<Map<String, Object>>> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody Map<String, Object> input) {

        return Mono.fromCallable(() -> {
            Long id = Long.parseLong(workflowId);
            Long instanceId = workflowService.triggerWorkflow(id, input, "API_GATEWAY");
            Map<String, Object> result = new HashMap<>();
            result.put("instanceId", instanceId);
            result.put("status", "RUNNING");
            result.put("message", "Workflow execution started");
            return result;
        })
                .map(result -> ResponseEntity.ok(result))
                .onErrorResume(e -> {
                    log.error("Workflow execution error: {}", e.getMessage(), e);
                    Map<String, Object> error = new HashMap<>();
                    error.put("error", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error));
                });
    }

    /**
     * 创建错误响应
     */
    private Map<String, Object> createError(String message, String type) {
        Map<String, Object> error = new HashMap<>();
        Map<String, Object> errorDetail = new HashMap<>();
        errorDetail.put("message", message);
        errorDetail.put("type", type);
        errorDetail.put("code", null);
        error.put("error", errorDetail);
        return error;
    }
}
