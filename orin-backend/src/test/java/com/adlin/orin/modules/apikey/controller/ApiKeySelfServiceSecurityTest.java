package com.adlin.orin.modules.apikey.controller;

import com.adlin.orin.modules.apikey.entity.ApiKey;
import com.adlin.orin.modules.apikey.service.ApiKeyService;
import com.adlin.orin.modules.apikey.service.ProviderKeyService;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.security.JwtAuthenticationFilter;
import com.adlin.orin.security.JwtService;
import com.adlin.orin.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = {
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        ApiKeyController.class,
        ApiKeySelfServiceSecurityTest.TestWebConfig.class
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-api-key-self-service-tests-1234567890",
        "jwt.expiration=3600000"
})
class ApiKeySelfServiceSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ProviderKeyService providerKeyService;

    @Autowired
    private AuditHelper auditHelper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(apiKeyService, providerKeyService, auditHelper);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void apiKeyEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/api-keys"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void regularUserCreateUsesJwtPrincipalAndIgnoresSpoofedOwnerAndQuota() throws Exception {
        ApiKey created = apiKey("gsec_self", "42");
        when(apiKeyService.createApiKey(eq("42"), eq("self key"), eq("for mcp"),
                isNull(), isNull(), isNull(), any()))
                .thenReturn(new ApiKeyService.ApiKeyWithSecret(created, "sk-orin-secret"));

        mockMvc.perform(post("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + token("42", List.of("ROLE_USER")))
                        .header("X-User-Id", "evil-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"self key",
                                  "description":"for mcp",
                                  "targetUserId":"admin-target",
                                  "rateLimitPerMinute":9999,
                                  "rateLimitPerDay":999999,
                                  "monthlyTokenQuota":999999999
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey.userId").value("42"))
                .andExpect(jsonPath("$.secretKey").value("sk-orin-secret"));

        verify(apiKeyService).createApiKey(eq("42"), eq("self key"), eq("for mcp"),
                isNull(), isNull(), isNull(), any());
    }

    @Test
    void regularUserListsOnlyOwnKeys() throws Exception {
        when(apiKeyService.getApiKeysForActor("42", false)).thenReturn(List.of(apiKey("gsec_self", "42")));

        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + token("42", List.of("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("gsec_self"))
                .andExpect(jsonPath("$[0].userId").value("42"));

        verify(apiKeyService).getApiKeysForActor("42", false);
    }

    @Test
    void regularUserCannotOperateInvisibleKeys() throws Exception {
        when(apiKeyService.deleteApiKey("gsec_other", "42", false)).thenReturn(false);

        mockMvc.perform(delete("/api/v1/api-keys/gsec_other")
                        .header("Authorization", "Bearer " + token("42", List.of("ROLE_USER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("API Key不存在或无权限"));
    }

    @Test
    void regularUserCannotUseAdminOnlySecretEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/api-keys/gsec_self/secret")
                        .header("Authorization", "Bearer " + token("42", List.of("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"secret\",\"confirmReveal\":\"REVEAL_API_KEY\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/api-keys/external")
                        .header("Authorization", "Bearer " + token("42", List.of("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"openai\",\"provider\":\"openai\",\"apiKey\":\"sk-test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminKeepsGlobalApiKeyGovernance() throws Exception {
        when(apiKeyService.getApiKeysForActor("7", true)).thenReturn(List.of(apiKey("gsec_global", "42")));

        mockMvc.perform(get("/api/v1/api-keys")
                        .header("Authorization", "Bearer " + token("7", List.of("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("gsec_global"))
                .andExpect(jsonPath("$[0].userId").value("42"));

        verify(apiKeyService).getApiKeysForActor("7", true);
    }

    private String token(String userId, List<String> roles) {
        return jwtService.generateToken(userId, "tester", Map.of("roles", roles));
    }

    private ApiKey apiKey(String keyId, String userId) {
        return ApiKey.builder()
                .id(keyId)
                .keyPrefix("sk-orin-test")
                .encryptedSecret("encrypted")
                .name("test key")
                .description("desc")
                .enabled(true)
                .rateLimitPerMinute(100)
                .rateLimitPerDay(10000)
                .monthlyTokenQuota(1_000_000L)
                .usedTokens(0L)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {
        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }

        @Bean
        ApiKeyService apiKeyService() {
            return mock(ApiKeyService.class);
        }

        @Bean
        ProviderKeyService providerKeyService() {
            return mock(ProviderKeyService.class);
        }

        @Bean
        AuditHelper auditHelper() {
            return mock(AuditHelper.class);
        }

        @Bean
        SysUserRepository sysUserRepository() {
            return mock(SysUserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
