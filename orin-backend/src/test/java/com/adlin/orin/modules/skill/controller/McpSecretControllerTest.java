package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.service.GatewaySecretService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpSecretController(gatewaySecretService, auditHelper))
                .build();
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
        when(gatewaySecretService.deleteBySecretId(eq("gsec_mcp_1"), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/mcp/secrets/gsec_mcp_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(gatewaySecretService).deleteBySecretId(eq("gsec_mcp_1"), any());
    }
}
