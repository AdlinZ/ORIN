package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP env 密钥管理。复用统一密钥中心存储（{@code SecretType.MCP_ENV}），
 * 供 MCP 服务的 env 用 {@code ${secret:<secretId>}} 引用。
 *
 * <p>所有接口仅返回不含明文的视图；明文只由 {@code /api/system/mcp/internal} 的
 * 解析器在下发给 AI Engine 时解密，人类接口不回读，故不提供 reveal。
 */
@RestController
@RequestMapping("/api/v1/mcp/secrets")
@RequiredArgsConstructor
@Tag(name = "MCP 密钥管理", description = "MCP 服务 env 的密钥引用存储")
public class McpSecretController {

    private final GatewaySecretService gatewaySecretService;
    private final AuditHelper auditHelper;

    @GetMapping
    @Operation(summary = "列出 MCP env 密钥")
    public List<McpSecretView> list() {
        return gatewaySecretService.listMcpEnvSecrets().stream()
                .filter(s -> s.getStatus() != GatewaySecret.SecretStatus.DELETED)
                .filter(this::canRead)
                .map(this::toView)
                .collect(Collectors.toList());
    }

    @PostMapping
    @Operation(summary = "创建 MCP env 密钥")
    public McpSecretView create(@RequestBody CreateMcpSecretRequest request) {
        String userId = currentUserId();
        GatewaySecret secret = gatewaySecretService.createMcpEnvSecret(
                request.getName(), request.getSecret(), request.getDescription(), userId);
        auditHelper.log(userId, "MCP_SECRET_CREATE", "/api/v1/mcp/secrets",
                "创建 MCP 密钥: " + secret.getSecretId() + " (" + secret.getName() + ")", true, null);
        return toView(secret);
    }

    @PatchMapping("/{secretId}/status")
    @Operation(summary = "启用/禁用 MCP env 密钥")
    public Map<String, Object> updateStatus(@PathVariable String secretId,
            @RequestBody UpdateStatusRequest request) {
        String userId = currentUserId();
        assertCanManage(secretId);
        GatewaySecret.SecretStatus status = Boolean.TRUE.equals(request.getEnabled())
                ? GatewaySecret.SecretStatus.ACTIVE
                : GatewaySecret.SecretStatus.DISABLED;
        boolean success = gatewaySecretService.updateStatus(secretId, status, userId);
        auditHelper.log(userId, "MCP_SECRET_STATUS", "/api/v1/mcp/secrets/" + secretId + "/status",
                "更新 MCP 密钥状态: " + secretId + " -> " + status, success, null);
        return Map.of("success", success);
    }

    @DeleteMapping("/{secretId}")
    @Operation(summary = "删除 MCP env 密钥")
    public Map<String, Object> delete(@PathVariable String secretId) {
        String userId = currentUserId();
        assertCanManage(secretId);
        boolean success = gatewaySecretService.deleteBySecretId(secretId, userId);
        auditHelper.log(userId, "MCP_SECRET_DELETE", "/api/v1/mcp/secrets/" + secretId,
                "删除 MCP 密钥: " + secretId, success, null);
        return Map.of("success", success);
    }

    @PostMapping("/{secretId}/rotate")
    @Operation(summary = "轮换 MCP env 密钥")
    public Map<String, Object> rotate(@PathVariable String secretId,
            @RequestBody RotateSecretRequest request) {
        String userId = currentUserId();
        assertCanManage(secretId);
        boolean success = gatewaySecretService.rotateProviderCredential(secretId, request.getSecret(), userId)
                .isPresent();
        auditHelper.log(userId, "MCP_SECRET_ROTATE", "/api/v1/mcp/secrets/" + secretId + "/rotate",
                "轮换 MCP 密钥: " + secretId, success, null);
        return Map.of("success", success);
    }

    private McpSecretView toView(GatewaySecret secret) {
        return McpSecretView.builder()
                .secretId(secret.getSecretId())
                .name(secret.getName())
                .status(secret.getStatus() != null ? secret.getStatus().name() : null)
                .maskedSecret("***" + (secret.getLast4() != null ? secret.getLast4() : "****"))
                .description(secret.getDescription())
                .createdAt(secret.getCreatedAt())
                .updatedAt(secret.getUpdatedAt())
                .build();
    }

    private boolean canRead(GatewaySecret secret) {
        return isAdmin() || currentUserId().equals(secret.getUserId());
    }

    private void assertCanManage(String secretId) {
        GatewaySecret secret = gatewaySecretService.findBySecretId(secretId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MCP 密钥不存在"));
        if (!canRead(secret)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该 MCP 密钥");
        }
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前请求缺少用户上下文");
        }
        return authentication.getPrincipal().toString();
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_SUPER_ADMIN".equals(authority.getAuthority())
                        || "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority())
                        || "ADMIN".equals(authority.getAuthority()));
    }

    @Data
    public static class CreateMcpSecretRequest {
        private String name;
        private String secret;
        private String description;
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
    @Builder
    @AllArgsConstructor
    public static class McpSecretView {
        private String secretId;
        private String name;
        private String status;
        private String maskedSecret;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
