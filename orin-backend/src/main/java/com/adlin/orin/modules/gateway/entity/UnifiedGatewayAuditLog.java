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

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
