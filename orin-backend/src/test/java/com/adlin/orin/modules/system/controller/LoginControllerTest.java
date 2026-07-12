package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.audit.service.AuditLogService;
import com.adlin.orin.modules.system.dto.RegisterDTO;
import com.adlin.orin.modules.system.entity.SysUser;
import com.adlin.orin.modules.system.service.AuthService;
import com.adlin.orin.modules.system.service.dto.AuthResult;
import com.adlin.orin.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest {

    private AuthService authService;
    private JwtService jwtService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        LoginController controller = new LoginController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "jwtService", jwtService);
        ReflectionTestUtils.setField(controller, "auditLogService", auditLogService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void loginReturnsSanitizedUser() throws Exception {
        SysUser user = new SysUser();
        user.setUserId(11L);
        user.setUsername("admin");
        user.setPassword("$2a$10$hash");
        user.setNickname("Administrator");
        user.setStatus("ENABLED");
        user.setRole("ROLE_SUPER_ADMIN");

        when(authService.login("admin", "admin123")).thenReturn(
                new AuthResult(user, List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN")));
        when(jwtService.generateToken(eq("11"), eq("admin"), any(), eq(false))).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"admin123\",\"rememberMe\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.user.userId").value(11))
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void registerReturnsJwtWithUserRoleAndSanitizedUser() throws Exception {
        SysUser user = new SysUser();
        user.setUserId(21L);
        user.setUsername("new-user");
        user.setPassword("$2a$10$hash");
        user.setNickname("New User");
        user.setStatus("ENABLED");
        user.setRole("ROLE_USER");

        when(authService.register(any(RegisterDTO.class))).thenReturn(new AuthResult(user, List.of("ROLE_USER")));
        when(jwtService.generateToken(eq("21"), eq("new-user"), any(), eq(true))).thenReturn("register-token");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"new-user\",\"password\":\"StrongPass123\",\"nickname\":\"New User\",\"rememberMe\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("register-token"))
                .andExpect(jsonPath("$.user.userId").value(21))
                .andExpect(jsonPath("$.user.username").value("new-user"))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }
}
