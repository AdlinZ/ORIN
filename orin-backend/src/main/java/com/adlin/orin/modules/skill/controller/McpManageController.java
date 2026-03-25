package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.service.McpServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP 服务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/system/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP 服务管理", description = "MCP 服务的增删改查和连接测试")
public class McpManageController {

    private final McpServiceService mcpServiceService;

    @GetMapping("/services")
    @Operation(summary = "获取 MCP 服务列表")
    public ResponseEntity<List<McpService>> getServices(@RequestParam(required = false) String keyword) {
        List<McpService> services;
        if (keyword != null && !keyword.isBlank()) {
            services = mcpServiceService.searchServices(keyword);
        } else {
            services = mcpServiceService.getAllServices();
        }
        return ResponseEntity.ok(services);
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "获取单个 MCP 服务")
    public ResponseEntity<McpService> getService(@PathVariable Long id) {
        McpService service = mcpServiceService.getServiceById(id);
        return ResponseEntity.ok(service);
    }

    @PostMapping("/services")
    @Operation(summary = "创建 MCP 服务")
    public ResponseEntity<McpService> createService(@RequestBody McpService service) {
        McpService created = mcpServiceService.createService(service);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "更新 MCP 服务")
    public ResponseEntity<McpService> updateService(@PathVariable Long id, @RequestBody McpService service) {
        McpService updated = mcpServiceService.updateService(id, service);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/services/{id}")
    @Operation(summary = "删除 MCP 服务")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        mcpServiceService.deleteService(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/services/{id}/test")
    @Operation(summary = "测试 MCP 服务连接")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long id) {
        Map<String, Object> result = mcpServiceService.testConnection(id);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/tools")
    @Operation(summary = "获取 MCP 工具列表")
    public ResponseEntity<List<Map<String, Object>>> getTools() {
        // 这里复用现有技能作为工具来源
        // 后续可扩展为真正的 MCP 工具市场
        List<McpService> services = mcpServiceService.getAllServices();
        List<Map<String, Object>> tools = new ArrayList<>();
        for (McpService s : services) {
            if (s.getStatus() == McpService.McpStatus.CONNECTED) {
                Map<String, Object> tool = new HashMap<>();
                tool.put("id", s.getId());
                tool.put("name", s.getName());
                tool.put("description", s.getDescription() != null ? s.getDescription() : "");
                tool.put("type", s.getType().name());
                tool.put("installed", true);
                tools.add(tool);
            }
        }
        return ResponseEntity.ok(tools);
    }
}