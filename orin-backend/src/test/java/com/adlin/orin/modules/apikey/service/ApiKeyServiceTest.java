package com.adlin.orin.modules.apikey.service;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import com.adlin.orin.modules.gateway.repository.UnifiedGatewayAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    private GatewaySecretService gatewaySecretService;
    private UnifiedGatewayAuditLogRepository gatewayAuditLogRepository;
    private AuditLogRepository auditLogRepository;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        gatewaySecretService = mock(GatewaySecretService.class);
        gatewayAuditLogRepository = mock(UnifiedGatewayAuditLogRepository.class);
        auditLogRepository = mock(AuditLogRepository.class);
        apiKeyService = new ApiKeyService(gatewaySecretService, gatewayAuditLogRepository, auditLogRepository);
    }

    @Test
    void lifecycleOperationsOnlyApplyToClientAccessSecretsOwnedByUser() {
        GatewaySecret provider = GatewaySecret.builder()
                .secretId("gsec_provider")
                .secretType(GatewaySecret.SecretType.PROVIDER_CREDENTIAL)
                .userId("1")
                .build();
        when(gatewaySecretService.findBySecretId("gsec_provider")).thenReturn(Optional.of(provider));

        assertThat(apiKeyService.disableApiKey("gsec_provider", "1")).isFalse();
        assertThat(apiKeyService.enableApiKey("gsec_provider", "1")).isFalse();
        assertThat(apiKeyService.deleteApiKey("gsec_provider", "1")).isFalse();
        assertThat(apiKeyService.rotateApiKey("gsec_provider", "1")).isEmpty();
        assertThat(apiKeyService.getApiKeyById("gsec_provider")).isEmpty();
        assertThat(apiKeyService.getSecretKeyForAdmin("gsec_provider")).isEmpty();

        verify(gatewaySecretService, never()).updateStatus(anyString(), any(), anyString());
        verify(gatewaySecretService, never()).deleteBySecretId(anyString(), anyString());
        verify(gatewaySecretService, never()).rotateClientAccessSecret(anyString(), anyString());
        verify(gatewaySecretService, never()).revealSecret(anyString());
    }

    @Test
    void disableRequiresMatchingOwner() {
        GatewaySecret client = GatewaySecret.builder()
                .secretId("gsec_client")
                .secretType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .userId("owner")
                .build();
        when(gatewaySecretService.findBySecretId("gsec_client")).thenReturn(Optional.of(client));

        assertThat(apiKeyService.disableApiKey("gsec_client", "other")).isFalse();

        verify(gatewaySecretService, never()).updateStatus(anyString(), any(), anyString());
    }

    @Test
    void usageSummaryRequiresClientAccessSecretOwnedByUserAndRedactsEvents() {
        GatewaySecret client = GatewaySecret.builder()
                .secretId("gsec_client")
                .secretType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .status(GatewaySecret.SecretStatus.ACTIVE)
                .userId("owner")
                .usedTokens(42L)
                .monthlyTokenQuota(1000L)
                .lastUsedAt(LocalDateTime.now())
                .build();
        when(gatewaySecretService.findBySecretId("gsec_client")).thenReturn(Optional.of(client));
        when(gatewayAuditLogRepository.countByApiKeyIdAndCreatedAtAfter(eq("gsec_client"), any()))
                .thenReturn(2L);
        when(gatewayAuditLogRepository.countByApiKeyIdAndResultAndCreatedAtAfter(eq("gsec_client"), eq("SUCCESS"), any()))
                .thenReturn(1L);
        when(auditLogRepository.countByApiKeyIdAndCreatedAtAfter(eq("gsec_client"), any()))
                .thenReturn(1L);
        when(auditLogRepository.countByApiKeyIdAndSuccessAndCreatedAtAfter(eq("gsec_client"), eq(true), any()))
                .thenReturn(1L);
        when(auditLogRepository.sumTokensByApiKeyIdAndCreatedAtAfter(eq("gsec_client"), any()))
                .thenReturn(99L);
        when(gatewayAuditLogRepository.findAverageLatencyByApiKeySince(eq("gsec_client"), any()))
                .thenReturn(120.0);
        when(auditLogRepository.avgResponseTimeByApiKeyIdAndCreatedAtAfter(eq("gsec_client"), any()))
                .thenReturn(80.0);
        when(gatewayAuditLogRepository.findByApiKeyIdOrderByCreatedAtDesc(eq("gsec_client"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(UnifiedGatewayAuditLog.builder()
                        .traceId("trace-1")
                        .method("POST")
                        .path("/v1/mcp")
                        .statusCode(200)
                        .result("SUCCESS")
                        .latencyMs(120L)
                        .createdAt(LocalDateTime.now())
                        .build())));
        when(auditLogRepository.findByApiKeyIdOrderByCreatedAtDesc(eq("gsec_client"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(AuditLog.builder()
                        .traceId("trace-2")
                        .endpoint("/v1/chat/completions")
                        .method("POST")
                        .statusCode(500)
                        .success(false)
                        .responseTime(80L)
                        .totalTokens(12)
                        .requestParams("{\"message\":\"secret\"}")
                        .responseContent("{\"token\":\"secret\"}")
                        .errorMessage("provider failed")
                        .createdAt(LocalDateTime.now())
                        .build())));

        ApiKeyService.ApiKeyUsageResponse usage = apiKeyService
                .getApiKeyUsage("gsec_client", "owner", 10)
                .orElseThrow();

        assertThat(usage.getTotalCalls()).isEqualTo(3);
        assertThat(usage.getSuccessCalls()).isEqualTo(2);
        assertThat(usage.getFailedCalls()).isEqualTo(1);
        assertThat(usage.getTokensInWindow()).isEqualTo(99);
        assertThat(usage.getAverageLatencyMs()).isEqualTo(100.0);
        assertThat(usage.getRecentEvents()).hasSize(2);
        assertThat(usage.getRecentEvents().toString())
                .doesNotContain("requestParams")
                .doesNotContain("responseContent")
                .doesNotContain("message");
    }

    @Test
    void usageSummaryRejectsWrongOwner() {
        GatewaySecret client = GatewaySecret.builder()
                .secretId("gsec_client")
                .secretType(GatewaySecret.SecretType.CLIENT_ACCESS)
                .userId("owner")
                .build();
        when(gatewaySecretService.findBySecretId("gsec_client")).thenReturn(Optional.of(client));

        assertThat(apiKeyService.getApiKeyUsage("gsec_client", "other", 10)).isEmpty();

        verifyNoInteractions(gatewayAuditLogRepository, auditLogRepository);
    }
}
