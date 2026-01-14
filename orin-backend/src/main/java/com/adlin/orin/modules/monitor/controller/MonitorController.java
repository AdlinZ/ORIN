package com.adlin.orin.modules.monitor.controller;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
@Tag(name = "Phase 3: Resource & Health Monitoring", description = "智能体监控相关接口")
@CrossOrigin(origins = "*") // Allow cross-origin for Frontend development
public class MonitorController {

    private final MonitorService monitorService;
    private final DifyIntegrationService difyIntegrationService;

    @Operation(summary = "获取全局监控看板数据")
    @GetMapping("/dashboard/summary")
    public Map<String, Object> getGlobalSummary() {
        return monitorService.getGlobalSummary();
    }

    @Operation(summary = "获取所有受监控的智能体列表")
    @GetMapping("/agents/list")
    public List<AgentHealthStatus> getAgentList() {
        return monitorService.getAgentList();
    }

    @Operation(summary = "获取指定智能体的实时状态")
    @GetMapping("/agents/{agentId}/status")
    public AgentHealthStatus getAgentStatus(@PathVariable String agentId) {
        return monitorService.getAgentStatus(agentId);
    }

    @Operation(summary = "获取指定智能体的历史趋势")
    @GetMapping("/agents/{agentId}/metrics")
    public List<AgentMetric> getAgentMetrics(
            @PathVariable String agentId,
            @RequestParam(name = "start") Long startTime,
            @RequestParam(name = "end") Long endTime,
            @RequestParam(defaultValue = "1m") String interval) {
        return monitorService.getAgentMetrics(agentId, startTime, endTime, interval);
    }

    @Operation(summary = "触发模拟数据生成 (调试用)")
    @PostMapping("/mock/trigger")
    public void triggerMockData() {
        monitorService.triggerMockDataGeneration();
    }

    @Operation(summary = "测试Dify连接")
    @PostMapping("/dify/test-connection")
    public boolean testDifyConnection(@RequestParam String endpointUrl, @RequestParam String apiKey) {
        return monitorService.testDifyConnection(endpointUrl, apiKey);
    }

    @Operation(summary = "获取Dify应用列表")
    @GetMapping("/dify/apps")
    public Object getDifyApps(@RequestParam String endpointUrl, @RequestParam String apiKey) {
        return monitorService.getDifyApps(endpointUrl, apiKey);
    }
}
