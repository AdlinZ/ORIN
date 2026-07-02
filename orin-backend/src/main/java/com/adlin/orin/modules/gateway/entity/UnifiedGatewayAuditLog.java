package com.adlin.orin.modules.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gateway_audit_logs", indexes = {
    @Index(name = "idx_gal_route_id", columnList = "routeId"),
    @Index(name = "idx_gal_trace_id", columnList = "traceId"),
    @Index(name = "idx_gal_created_at", columnList = "createdAt")
})
public class UnifiedGatewayAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id")
    private Long routeId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "path", length = 500)
    private String path;

    /**
     * 用户请求里写的 model 字符串（路由前）。
     * 网关场景下多数 provider 透传，因此 model_alias == provider_model。
     * Dify 等不透传场景保留 alias 用以定位"用户写了什么"。
     */
    @Column(name = "model_alias", length = 100)
    private String modelAlias;

    /**
     * 实际转发到上游 provider 的 model 字符串（路由后）。
     */
    @Column(name = "provider_model", length = 100)
    private String providerModel;

    @Column(name = "target_service", length = 100)
    private String targetService;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "api_key_id", length = 100)
    private String apiKeyId;

    @Column(name = "result", length = 20)
    private String result;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    /**
     * 结构化错误码（来自 GatewayErrorMapper 映射 ErrorCode 枚举或 HTTP status）。
     */
    @Column(name = "error_code", length = 32)
    private String errorCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
