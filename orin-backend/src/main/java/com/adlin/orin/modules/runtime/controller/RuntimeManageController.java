package com.adlin.orin.modules.runtime.controller;

import com.adlin.orin.modules.runtime.entity.AgentLog;
import com.adlin.orin.modules.runtime.service.RuntimeManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/runtime")
@RequiredArgsConstructor
@Tag(name = "Phase 2: Runtime Awareness", description = "智能体运行期管理")
public class RuntimeManageController {

    private final RuntimeManageService runtimeManageService;

    @Operation(summary = "控制智能体 (Start/Stop)")
    @PostMapping("/agents/{agentId}/control")
    public void controlAgent(@PathVariable String agentId, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        runtimeManageService.controlAgent(agentId, action);
    }

    @Operation(summary = "获取运行日志")
    @GetMapping("/agents/{agentId}/logs")
    public List<AgentLog> getAgentLogs(@PathVariable String agentId) {
        return runtimeManageService.getAgentLogs(agentId);
    }
}
