package com.adlin.orin.gateway.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Gateway MVP 审计上下文 —— Gateway-1b 抽出的字段载体。
 *
 * <p>由 {@link com.adlin.orin.gateway.controller.UnifiedGatewayApiController#chatCompletions}
 * 在各 reactive 路径里组装，传给 {@link GatewayAuditRecorder} 落 audit_logs。
 *
 * <p>字段命名与 {@link com.adlin.orin.modules.audit.entity.AuditLog} 一一对应；
 * nullable 字段允许 controller 端在 503 / 500 路径不取 provider 信息。
 *
 * <p>providerModel 在 1b 简化为 = modelAlias（90% provider 透传场景成立）。
 * Dify 等不透传场景留待 Gateway-2a 通过 {@code ProviderAdapter.getResolvedModel()} 解决。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayAuditContext {

    /** 用户 ID —— 从 ApiKeyAuthInterceptor/UnifiedGatewayProxyFilter 注入的 apiKey attribute 取。 */
    private String userId;

    /** API Key ID —— GatewaySecret.secretId。 */
    private String apiKeyId;

    /** Provider 实例名 —— provider.getProviderName()。失败路径可能为 null。 */
    private String providerId;

    /** Provider 类型 —— provider.getProviderType()。失败路径可能为 null。 */
    private String providerType;

    /** 用户请求里的 model 字符串（路由前别名）。来自 ChatCompletionRequest.getModel()。 */
    private String modelAlias;

    /** 实际转发到上游的 model 字符串（路由后）。1b 简化策略 = modelAlias。 */
    private String providerModel;

    /** 链路追踪 ID —— X-Trace-Id header 或生成的 UUID。 */
    private String traceId;

    /** 端到端耗时（毫秒）—— System.currentTimeMillis() 在 controller 入口 + 各 lambda 内取。 */
    private Long latencyMs;

    /** 提示词 token 数 —— ChatCompletionResponse.usage.promptTokens。 */
    private Integer promptTokens;

    /** 完成 token 数 —— ChatCompletionResponse.usage.completionTokens。 */
    private Integer completionTokens;

    /** 总 token 数 —— ChatCompletionResponse.usage.totalTokens。 */
    private Integer totalTokens;

    /** 客户端 IP —— HttpServletRequest.getRemoteAddr()。 */
    private String ipAddress;

    /** User-Agent —— HttpServletRequest.getHeader("User-Agent")。 */
    private String userAgent;
}
