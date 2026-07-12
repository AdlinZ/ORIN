package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class McpSecretControllerTest {

    private GatewaySecretService gatewaySecretService;
    private AuditHelper auditHelper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        gatewaySecretService = mock(GatewaySecretService.class);
        auditHelper = mock(AuditHelper.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpSecretController(gatewaySecretService, auditHelper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private GatewaySecret secret(String secretId, GatewaySecret.SecretStatus status) {
        return GatewaySecret.builder()
                .secretId(secretId)
                .name("gh token")
                .secretType(GatewaySecret.SecretType.MCP_ENV)
                .status(status)
                .encryptedSecret("enc")
                .last4("oken")
                .build();
    }

    @Test
    void create_returnsViewWithoutPlaintext() throws Exception {
        when(gatewaySecretService.createMcpEnvSecret(eq("gh token"), eq("ghp_realsecret"), any(), any()))
                .thenReturn(secret("gsec_mcp_1", GatewaySecret.SecretStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/mcp/secrets")
                        .contentType("application/json")
                        .content("{\"name\":\"gh token\",\"secret\":\"ghp_realsecret\",\"description\":\"d\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretId").value("gsec_mcp_1"))
                .andExpect(jsonPath("$.maskedSecret").value("***oken"))
                .andExpect(jsonPath("$.secret").doesNotExist())
                .andExpect(jsonPath("$.encryptedSecret").doesNotExist());
    }

    @Test
    void list_returnsViewsAndExcludesDeleted() throws Exception {
        when(gatewaySecretService.listMcpEnvSecrets()).thenReturn(List.of(
                secret("gsec_mcp_1", GatewaySecret.SecretStatus.ACTIVE),
                secret("gsec_mcp_2", GatewaySecret.SecretStatus.DELETED)));

        mockMvc.perform(get("/api/v1/mcp/secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].secretId").value("gsec_mcp_1"))
                .andExpect(jsonPath("$[0].encryptedSecret").doesNotExist());
    }

    @Test
    void delete_softDeletesViaService() throws Exception {
        when(gatewaySecretService.findBySecretId("gsec_mcp_1"))
                .thenReturn(java.util.Optional.of(secret("gsec_mcp_1", GatewaySecret.SecretStatus.ACTIVE)));
        when(gatewaySecretService.deleteBySecretId(eq("gsec_mcp_1"), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/mcp/secrets/gsec_mcp_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(gatewaySecretService).deleteBySecretId(eq("gsec_mcp_1"), any());
    }

    // --- 非管理员 ACL 路径 ---

    @Test
    void list_asNonAdmin_returnsOnlyOwnSecrets() throws Exception {
        authenticateAs("42", "ROLE_USER");
        GatewaySecret mine = secret("gsec_mine", GatewaySecret.SecretStatus.ACTIVE);
        mine.setUserId("42");
        GatewaySecret others = secret("gsec_others", GatewaySecret.SecretStatus.ACTIVE);
        others.setUserId("99");
        when(gatewaySecretService.listMcpEnvSecrets()).thenReturn(java.util.List.of(mine, others));

        mockMvc.perform(get("/api/v1/mcp/secrets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].secretId").value("gsec_mine"));
    }

    @Test
    void delete_asNonAdmin_deletesOwnSecret() throws Exception {
        authenticateAs("42", "ROLE_USER");
        GatewaySecret mine = secret("gsec_mine", GatewaySecret.SecretStatus.ACTIVE);
        mine.setUserId("42");
        when(gatewaySecretService.findBySecretId("gsec_mine"))
                .thenReturn(java.util.Optional.of(mine));
        when(gatewaySecretService.deleteBySecretId(eq("gsec_mine"), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/mcp/secrets/gsec_mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_asNonAdmin_otherUsersSecret_returns403() throws Exception {
        authenticateAs("42", "ROLE_USER");
        GatewaySecret others = secret("gsec_others", GatewaySecret.SecretStatus.ACTIVE);
        others.setUserId("99");
        when(gatewaySecretService.findBySecretId("gsec_others"))
                .thenReturn(java.util.Optional.of(others));

        mockMvc.perform(delete("/api/v1/mcp/secrets/gsec_others"))
                .andExpect(status().is4xxClientError());

        verify(gatewaySecretService, never()).deleteBySecretId(any(), any());
    }

    @Test
    void delete_asNonAdmin_missingSecret_returns404() throws Exception {
        authenticateAs("42", "ROLE_USER");
        when(gatewaySecretService.findBySecretId("gsec_missing"))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(delete("/api/v1/mcp/secrets/gsec_missing"))
                .andExpect(status().is4xxClientError());

        verify(gatewaySecretService, never()).deleteBySecretId(any(), any());
    }

    private void authenticateAs(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, java.util.List.of(new SimpleGrantedAuthority(role))));
        // 每个非管理员用例单独构造 mockMvc，避免 setUp 里的 ROLE_ADMIN 干扰
        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpSecretController(gatewaySecretService, auditHelper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
