package com.adlin.orin.modules.dashboard.controller;

import com.adlin.orin.modules.dashboard.service.DashboardSummaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardSummaryControllerTest {

    private DashboardSummaryService dashboardSummaryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardSummaryService = mock(DashboardSummaryService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardSummaryController(dashboardSummaryService))
                .build();
    }

    @Test
    void getSummaryReturnsRoleAwarePayload() throws Exception {
        when(dashboardSummaryService.getSummary(any())).thenReturn(Map.of(
                "roles", List.of("ROLE_ADMIN"),
                "defaultHome", "/dashboard/runtime/overview",
                "systemHealth", Map.of("backend", Map.of("status", "UP")),
                "metrics", Map.of("agents", 2),
                "recentActivity", List.of(),
                "quickLinks", List.of(Map.of("title", "运行监控", "path", "/dashboard/runtime/overview"))
        ));

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "11",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.defaultHome").value("/dashboard/runtime/overview"))
                .andExpect(jsonPath("$.metrics.agents").value(2))
                .andExpect(jsonPath("$.quickLinks[0].title").value("运行监控"));
    }
}
