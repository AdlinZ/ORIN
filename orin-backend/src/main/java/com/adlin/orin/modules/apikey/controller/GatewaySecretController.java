package com.adlin.orin.modules.apikey.controller;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.model.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/gateway/secrets")
@RequiredArgsConstructor
@Tag(name = "Gateway Secret Center", description = "统一网关密钥中心")
public class GatewaySecretController {

    private final GatewaySecretService gatewaySecretService;
    private final AuditHelper auditHelper;
    private final ModelConfigService modelConfigService;

    @GetMapping
    @Operation(summary = "查询所有密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GatewaySecretView> listAll() {
        return gatewaySecretService.listAll().stream().map(this::toView).collect(Collectors.toList());
    }

    @GetMapping("/client-access")
    @Operation(summary = "查询客户端访问密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GatewaySecretView> listClientAccess() {
        return gatewaySecretService.listByType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @GetMapping("/provider-credentials")
    @Operation(summary = "查询供应商密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public List<GatewaySecretView> listProviderCredentials() {
        return gatewaySecretService.listByType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL)
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @PostMapping("/client-access")
    @Operation(summary = "创建客户端访问密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createClientAccess(@RequestBody CreateClientAccessSecretRequest request,
                                                                  @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        GatewaySecretService.ClientAccessSecretWithValue result = gatewaySecretService.createClientAccessSecret(
                userId,
                request.getName(),
                request.getDescription(),
                request.getRateLimitPerMinute(),
                request.getRateLimitPerDay(),
                request.getMonthlyTokenQuota(),
                request.getExpiresAt(),
                userId);

        auditHelper.log(userId, "GATEWAY_SECRET_CREATE", "/api/v1/gateway/secrets/client-access",
                "创建客户端访问密钥: " + result.getSecret().getSecretId(), true, null);

        Map<String, Object> response = new HashMap<>();
        response.put("secret", toView(result.getSecret()));
        response.put("secretKey", result.getSecretValue());
        response.put("warning", "请妥善保存此密钥，它只会显示一次！");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/provider-credentials")
    @Operation(summary = "创建或更新供应商密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public GatewaySecretView upsertProviderCredential(@RequestBody UpsertProviderCredentialRequest request,
                                                      @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        GatewaySecret secret = gatewaySecretService.upsertProviderCredential(
                request.getProvider(),
                request.getName(),
                request.getSecret(),
                request.getBaseUrl(),
                request.getDescription(),
                request.getEnabled() == null || request.getEnabled(),
                userId);
        auditHelper.log(userId, "GATEWAY_PROVIDER_SECRET_UPSERT", "/api/v1/gateway/secrets/provider-credentials",
                "更新供应商密钥: " + secret.getProvider(), true, null);
        return toView(secret);
    }

    @PatchMapping("/{secretId}/status")
    @Operation(summary = "更新密钥状态")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> updateStatus(@PathVariable String secretId,
                                            @RequestBody UpdateStatusRequest request,
                                            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        GatewaySecret.SecretStatus status = request.getEnabled() != null && request.getEnabled()
                ? GatewaySecret.SecretStatus.ACTIVE
                : GatewaySecret.SecretStatus.DISABLED;
        boolean success = gatewaySecretService.updateStatus(secretId, status, userId);
        return Map.of("success", success);
    }

    @DeleteMapping("/{secretId}")
    @Operation(summary = "删除密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(@PathVariable String secretId,
                                      @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        boolean success = gatewaySecretService.deleteBySecretId(secretId, userId);
        return Map.of("success", success);
    }

    @PostMapping("/{secretId}/rotate")
    @Operation(summary = "轮换密钥")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rotate(@PathVariable String secretId,
                                                      @RequestBody RotateSecretRequest request,
                                                      @RequestHeader(value = "X-User-Id", required = false, defaultValue = "default-user") String userId) {
        Map<String, Object> response = new HashMap<>();
        if (request.getSecret() != null && !request.getSecret().isBlank()) {
            boolean success = gatewaySecretService.rotateProviderCredential(secretId, request.getSecret(), userId).isPresent();
            response.put("success", success);
            return ResponseEntity.ok(response);
        }

        return gatewaySecretService.rotateClientAccessSecret(secretId, userId)
                .map(rotated -> {
                    response.put("success", true);
                    response.put("secret", toView(rotated.getSecret()));
                    response.put("secretKey", rotated.getSecretValue());
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> fail = new HashMap<>();
                    fail.put("success", false);
                    fail.put("message", "rotate failed");
                    return ResponseEntity.badRequest().body(fail);
                });
    }

    @PostMapping("/{secretId}/reveal")
    @Operation(summary = "管理员受控查看明文")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> reveal(@PathVariable String secretId) {
        return gatewaySecretService.revealSecret(secretId)
                .map(secret -> {
                    Map<String, Object> body = new HashMap<>();
                    body.put("secretId", secretId);
                    body.put("secret", secret);
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404).<Map<String, Object>>build());
    }

    @PostMapping("/provider-credentials/test")
    @Operation(summary = "测试供应商密钥连通性")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> testProviderCredential(@RequestBody TestProviderCredentialRequest request) {
        String provider = request.getProvider() == null ? "" : request.getProvider().trim().toLowerCase();
        String endpoint = request.getEndpoint();
        String secret = request.getSecret();
        if ((secret == null || secret.isBlank()) && request.getSecretId() != null && !request.getSecretId().isBlank()) {
            secret = gatewaySecretService.revealSecret(request.getSecretId()).orElse(null);
        }

        boolean success = false;
        if ("dify".equals(provider)) {
            success = modelConfigService.testDifyConnection(endpoint, secret);
        } else if ("siliconflow".equals(provider)) {
            success = modelConfigService.testSiliconFlowConnection(endpoint, secret, request.getModel());
        } else if ("ollama".equals(provider) || "local-ollama".equals(provider)) {
            success = modelConfigService.testOllamaConnection(endpoint, secret, request.getModel());
        }
        return Map.of("success", success);
    }

    private GatewaySecretView toView(GatewaySecret secret) {
        return GatewaySecretView.builder()
                .id(secret.getId())
                .secretId(secret.getSecretId())
                .name(secret.getName())
                .type(secret.getSecretType().name())
                .provider(secret.getProvider())
                .status(secret.getStatus().name())
                .maskedSecret(mask(secret.getKeyPrefix(), secret.getLast4()))
                .last4(secret.getLast4())
                .baseUrl(secret.getBaseUrl())
                .userId(secret.getUserId())
                .description(secret.getDescription())
                .rateLimitPerMinute(secret.getRateLimitPerMinute())
                .rateLimitPerDay(secret.getRateLimitPerDay())
                .monthlyTokenQuota(secret.getMonthlyTokenQuota())
                .usedTokens(secret.getUsedTokens())
                .rotationAt(secret.getRotationAt())
                .lastError(secret.getLastError())
                .createdAt(secret.getCreatedAt())
                .updatedAt(secret.getUpdatedAt())
                .build();
    }

    private String mask(String prefix, String last4) {
        String safePrefix = prefix == null ? "" : prefix;
        String safeLast4 = last4 == null ? "****" : last4;
        return safePrefix + "***" + safeLast4;
    }

    @Data
    public static class CreateClientAccessSecretRequest {
        private String name;
        private String description;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerDay;
        private Long monthlyTokenQuota;
        private LocalDateTime expiresAt;
    }

    @Data
    public static class UpsertProviderCredentialRequest {
        private String name;
        private String provider;
        private String secret;
        private String baseUrl;
        private String description;
        private Boolean enabled;
    }

    @Data
    public static class UpdateStatusRequest {
        private Boolean enabled;
    }

    @Data
    public static class RotateSecretRequest {
        private String secret;
    }

    @Data
    public static class TestProviderCredentialRequest {
        private String provider;
        private String endpoint;
        private String secret;
        private String secretId;
        private String model;
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class GatewaySecretView {
        private String id;
        private String secretId;
        private String name;
        private String type;
        private String provider;
        private String status;
        private String maskedSecret;
        private String last4;
        private String baseUrl;
        private String userId;
        private String description;
        private Integer rateLimitPerMinute;
        private Integer rateLimitPerDay;
        private Long monthlyTokenQuota;
        private Long usedTokens;
        private String lastError;
        private LocalDateTime rotationAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
