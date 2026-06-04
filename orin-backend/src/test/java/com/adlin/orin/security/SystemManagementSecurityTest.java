package com.adlin.orin.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.List;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig
@ContextConfiguration(classes = {
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtService.class,
        SystemManagementSecurityTest.SystemManagementTestController.class,
        SystemManagementSecurityTest.SetupPublicTestController.class,
        SystemManagementSecurityTest.TestWebConfig.class
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-system-management-security-tests-1234567890",
        "jwt.expiration=3600000"
})
class SystemManagementSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void systemManagementEndpointsRejectAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void systemManagementEndpointsRejectRegularUsers() throws Exception {
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token(List.of("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizationManagementEndpointsRejectPlatformAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token(List.of("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemManagementEndpointsAllowAdminRoles() throws Exception {
        mockMvc.perform(get("/api/v1/departments")
                        .header("Authorization", "Bearer " + token(List.of("ROLE_SUPER_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void setupStatusAllowsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/v1/setup/status"))
                .andExpect(status().isOk());
    }

    private String token(List<String> roles) {
        return jwtService.generateToken("1", "tester", Map.of("roles", roles));
    }

    @RestController
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    static class SystemManagementTestController {
        @GetMapping({"/api/v1/users", "/api/v1/roles", "/api/v1/departments"})
        void ok(HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    @RestController
    static class SetupPublicTestController {
        @GetMapping("/api/v1/setup/status")
        void ok(HttpServletResponse response) {
            response.setStatus(HttpServletResponse.SC_OK);
        }
    }

    @Configuration
    static class TestWebConfig {
        @Bean(name = "mvcHandlerMappingIntrospector")
        HandlerMappingIntrospector mvcHandlerMappingIntrospector() {
            return new HandlerMappingIntrospector();
        }
    }
}
