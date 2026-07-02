package com.adlin.orin.gateway.audit;

import com.adlin.orin.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Gateway MVP 审计写入器 —— Gateway-1b 新增。
 *
 * <p>职责：把 {@link GatewayAuditContext} 翻译成 {@link AuditLogService#logApiCall}
 * 第 6 个重载的 25 个参数，写入 audit_logs。封装的目的：
 *
 * <ul>
 *   <li>controller 不直接调 AuditLogService，避免 reactive 链上散落 25 参数调用</li>
 *   <li>未来 stream / embeddings / images 等 gateway 端点复用同一个写入器</li>
 *   <li>audit 写失败不阻塞主链路响应（{@code @Async} + try/catch swallow）</li>
 * </ul>
 *
 * <p>所有公开方法都是 {@code void}（不是 {@code Mono<...>}），让 controller 在 reactive
 * 链上用 {@code doOnSuccess} / {@code Mono.defer} / {@code onErrorResume} 时不需要
 * 操心返回值的 subscribe。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayAuditRecorder {

    private static final String GATEWAY_CHAT_ENDPOINT = "/v1/chat/completions";
    private static final String GATEWAY_CHAT_METHOD = "POST";

    private final AuditLogService auditLogService;

    /**
     * 成功路径记录。latency / tokens 已在 ctx 内算好；statusCode=200，success=true。
     */
    public void recordSuccess(GatewayAuditContext ctx) {
        write(ctx, 200, true, null, null);
    }

    /**
     * 失败路径记录。caller 决定 statusCode / errorMessage / errorCode。
     */
    public void recordError(GatewayAuditContext ctx, int statusCode,
                            String errorMessage, String errorCode) {
        write(ctx, statusCode, false, errorMessage, errorCode);
    }

    private void write(GatewayAuditContext ctx, int statusCode, boolean success,
                       String errorMessage, String errorCode) {
        // 防御：null ctx 直接 swallow（不应发生，但不能让 audit 写失败阻塞主链路）
        if (ctx == null) {
            log.warn("Gateway audit write skipped: ctx is null (statusCode={}, success={})",
                    statusCode, success);
            return;
        }
        // 在 try 之前取 traceId，避免 catch 块里再 NPE
        final String traceId = ctx.getTraceId();
        try {
            auditLogService.logApiCall(
                    ctx.getUserId(),
                    ctx.getApiKeyId(),
                    ctx.getProviderId(),
                    ctx.getProviderType(),
                    GATEWAY_CHAT_ENDPOINT,
                    GATEWAY_CHAT_METHOD,
                    ctx.getModelAlias(),
                    ctx.getProviderModel(),
                    ctx.getIpAddress(),
                    ctx.getUserAgent(),
                    /* requestParams */ null,
                    /* responseContent */ null,
                    statusCode,
                    ctx.getLatencyMs(),
                    ctx.getPromptTokens(),
                    ctx.getCompletionTokens(),
                    /* estimatedCost */ null,  // AuditLogService 内 pricingService.calculate 会重算
                    success,
                    errorMessage,
                    errorCode,
                    /* workflowId */ null,
                    /* conversationId */ null,
                    /* fileId */ null,
                    /* downloadUrl */ null,
                    traceId);
        } catch (Exception e) {
            // audit 写失败不能阻塞主链路响应；只 warn 一行
            log.warn("Gateway audit write failed (traceId={}): {}", traceId, e.getMessage());
        }
    }
}
