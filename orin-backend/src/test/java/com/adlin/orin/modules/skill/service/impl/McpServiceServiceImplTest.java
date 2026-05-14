package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.common.exception.ValidationException;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServiceServiceImplTest {

    @Mock
    private McpServiceRepository mcpServiceRepository;
    @Mock
    private GatewaySecretService gatewaySecretService;

    private McpServiceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new McpServiceServiceImpl(mcpServiceRepository, gatewaySecretService);
        lenient().when(mcpServiceRepository.existsByName(anyString())).thenReturn(false);
        lenient().when(mcpServiceRepository.save(any(McpService.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private McpService withEnv(String envVars) {
        return McpService.builder()
                .name("svc-" + System.nanoTime())
                .type(McpService.McpType.STDIO)
                .command("github")
                .envVars(envVars)
                .build();
    }

    private GatewaySecret secret(GatewaySecret.SecretType type, GatewaySecret.SecretStatus status) {
        return GatewaySecret.builder()
                .secretId("gsec_mcp_x")
                .name("gh token")
                .secretType(type)
                .status(status)
                .encryptedSecret("enc")
                .build();
    }

    // ---- validateEnvVars (via createService) ----

    @Test
    void createService_nonSensitivePlaintext_ok() {
        assertDoesNotThrow(() -> service.createService(withEnv("WORKDIR=/srv\nLOG_LEVEL=debug")));
    }

    @Test
    void createService_sensitivePlaintext_rejected() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.createService(withEnv("GITHUB_TOKEN=ghp_plain")));
        assertTrue(ex.getMessage().contains("仅支持"));
    }

    @Test
    void createService_sensitiveRefToActiveMcpSecret_ok() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x"))
                .thenReturn(Optional.of(secret(GatewaySecret.SecretType.MCP_ENV, GatewaySecret.SecretStatus.ACTIVE)));
        assertDoesNotThrow(() ->
                service.createService(withEnv("GITHUB_TOKEN=${secret:gsec_mcp_x}")));
    }

    @Test
    void createService_sensitiveRefToMissingSecret_rejected() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x")).thenReturn(Optional.empty());
        assertThrows(ValidationException.class,
                () -> service.createService(withEnv("GITHUB_TOKEN=${secret:gsec_mcp_x}")));
    }

    @Test
    void createService_sensitiveRefToDisabledSecret_rejected() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x"))
                .thenReturn(Optional.of(secret(GatewaySecret.SecretType.MCP_ENV, GatewaySecret.SecretStatus.DISABLED)));
        assertThrows(ValidationException.class,
                () -> service.createService(withEnv("GITHUB_TOKEN=${secret:gsec_mcp_x}")));
    }

    @Test
    void createService_sensitiveRefToNonMcpSecret_rejected() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x"))
                .thenReturn(Optional.of(secret(GatewaySecret.SecretType.PROVIDER_CREDENTIAL, GatewaySecret.SecretStatus.ACTIVE)));
        assertThrows(ValidationException.class,
                () -> service.createService(withEnv("GITHUB_TOKEN=${secret:gsec_mcp_x}")));
    }

    // ---- resolveEnvVars ----

    @Test
    void resolveEnvVars_substitutesRefWithDecryptedValue() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x"))
                .thenReturn(Optional.of(secret(GatewaySecret.SecretType.MCP_ENV, GatewaySecret.SecretStatus.ACTIVE)));
        when(gatewaySecretService.revealSecret("gsec_mcp_x")).thenReturn(Optional.of("ghp_realtoken"));

        String resolved = service.resolveEnvVars("GITHUB_TOKEN=${secret:gsec_mcp_x}\nWORKDIR=/srv");

        assertEquals("GITHUB_TOKEN=ghp_realtoken\nWORKDIR=/srv", resolved);
    }

    @Test
    void resolveEnvVars_nonRefLinesUnchanged() {
        assertEquals("WORKDIR=/srv\nLOG_LEVEL=debug",
                service.resolveEnvVars("WORKDIR=/srv\nLOG_LEVEL=debug"));
    }

    @Test
    void resolveEnvVars_unresolvedRef_throws() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x")).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class,
                () -> service.resolveEnvVars("GITHUB_TOKEN=${secret:gsec_mcp_x}"));
    }

    @Test
    void resolveEnvVars_disabledRef_throws() {
        when(gatewaySecretService.findBySecretId("gsec_mcp_x"))
                .thenReturn(Optional.of(secret(GatewaySecret.SecretType.MCP_ENV, GatewaySecret.SecretStatus.DISABLED)));
        assertThrows(IllegalStateException.class,
                () -> service.resolveEnvVars("GITHUB_TOKEN=${secret:gsec_mcp_x}"));
    }
}
