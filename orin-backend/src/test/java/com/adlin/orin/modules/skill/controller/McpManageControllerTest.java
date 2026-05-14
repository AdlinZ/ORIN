package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.service.McpServiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpManageControllerTest {

    private McpServiceService mcpServiceService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mcpServiceService = mock(McpServiceService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new McpManageController(mcpServiceService))
                .build();
    }

    private McpService service(boolean enabled) {
        return McpService.builder()
                .id(9L)
                .name("github")
                .toolKey("github")
                .type(McpService.McpType.STDIO)
                .command("github")
                .envVars("GITHUB_TOKEN=ghp_realsecret\nWORKDIR=/srv")
                .enabled(enabled)
                .status(McpService.McpStatus.CONNECTED)
                .build();
    }

    @Test
    void getService_returnsMaskedEnvVars() throws Exception {
        when(mcpServiceService.getServiceById(9L)).thenReturn(service(true));

        mockMvc.perform(get("/api/system/mcp/services/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envVars").value("GITHUB_TOKEN=******\nWORKDIR=******"));
    }

    @Test
    void getServices_returnsMaskedEnvVars() throws Exception {
        when(mcpServiceService.getAllServices()).thenReturn(List.of(service(true)));

        mockMvc.perform(get("/api/system/mcp/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].envVars").value("GITHUB_TOKEN=******\nWORKDIR=******"));
    }

    @Test
    void getEnabledServiceForAiEngine_returnsRealEnvVars() throws Exception {
        when(mcpServiceService.getServiceById(9L)).thenReturn(service(true));

        mockMvc.perform(get("/api/system/mcp/internal/enabled/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envVars").value("GITHUB_TOKEN=ghp_realsecret\nWORKDIR=/srv"));
    }

    @Test
    void getEnabledServiceForAiEngine_disabledService_returns404() throws Exception {
        when(mcpServiceService.getServiceById(9L)).thenReturn(service(false));

        mockMvc.perform(get("/api/system/mcp/internal/enabled/9"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTools_exposesDockerAndLocalCommandsForPathBearingTemplate() throws Exception {
        when(mcpServiceService.getAllServices()).thenReturn(List.of());

        mockMvc.perform(get("/api/system/mcp/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key == 'filesystem')].command").value("filesystem /app"))
                .andExpect(jsonPath("$[?(@.key == 'filesystem')].localCommand").value("filesystem"));
    }

    @Test
    void installTool_dockerMode_usesContainerPathCommand() throws Exception {
        when(mcpServiceService.getAllServices()).thenReturn(List.of());
        when(mcpServiceService.createService(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/system/mcp/tools/filesystem/install"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("filesystem /app"));
    }

    @Test
    void installTool_localMode_usesPathlessCommand() throws Exception {
        when(mcpServiceService.getAllServices()).thenReturn(List.of());
        when(mcpServiceService.createService(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/system/mcp/tools/filesystem/install").param("mode", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.command").value("filesystem"));
    }
}
