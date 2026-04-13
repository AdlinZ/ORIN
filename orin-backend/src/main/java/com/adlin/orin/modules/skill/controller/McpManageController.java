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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private static final List<Map<String, Object>> MARKET_TEMPLATES = List.of(
            template("filesystem", "Filesystem", "本地文件系统读写与检索", "STDIO",
                    "npx -y @modelcontextprotocol/server-filesystem /path/to/workspace", null),
            template("git", "Git", "Git 仓库状态与提交查询", "STDIO",
                    "npx -y @modelcontextprotocol/server-git /path/to/repo", null),
            template("fetch", "Fetch", "HTTP 内容抓取和网页读取", "STDIO",
                    "uvx mcp-server-fetch", null),
            template("postgres", "PostgreSQL", "数据库查询与结构探索", "STDIO",
                    "npx -y @modelcontextprotocol/server-postgres", null),
            template("sse-proxy", "SSE Proxy", "通过 SSE 方式接入远程 MCP 服务", "SSE",
                    null, "http://localhost:3000/sse"));

    private static Map<String, Object> template(String key, String name, String description, String type,
            String command, String url) {
        Map<String, Object> template = new HashMap<>();
        template.put("key", key);
        template.put("name", name);
        template.put("description", description);
        template.put("type", type);
        template.put("command", command);
        template.put("url", url);
        return template;
    }

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
        List<McpService> services = mcpServiceService.getAllServices();
        Map<String, McpService> serviceByToolKey = new HashMap<>();
        for (McpService service : services) {
            if (service.getToolKey() != null && !service.getToolKey().isBlank()) {
                serviceByToolKey.put(service.getToolKey(), service);
            }
        }

        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, Object> tpl : MARKET_TEMPLATES) {
            String key = String.valueOf(tpl.get("key"));
            McpService installed = serviceByToolKey.get(key);
            Map<String, Object> tool = new HashMap<>(tpl);
            tool.put("id", key);
            tool.put("installed", installed != null);
            tool.put("serviceId", installed != null ? installed.getId() : null);
            tool.put("enabled", installed != null && Boolean.TRUE.equals(installed.getEnabled()));
            tool.put("status", installed != null ? installed.getStatus() : null);
            if (installed != null) {
                tool.put("command", installed.getCommand());
                tool.put("url", installed.getUrl());
            }
            tools.add(tool);
        }

        services.stream()
                .filter(s -> s.getToolKey() == null || s.getToolKey().isBlank())
                .sorted(Comparator.comparing(McpService::getId))
                .forEach(s -> {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("id", "service-" + s.getId());
                    tool.put("key", null);
                    tool.put("name", s.getName());
                    tool.put("description", s.getDescription() != null ? s.getDescription() : "");
                    tool.put("type", s.getType().name());
                    tool.put("installed", true);
                    tool.put("serviceId", s.getId());
                    tool.put("enabled", Boolean.TRUE.equals(s.getEnabled()));
                    tool.put("status", s.getStatus());
                    tool.put("command", s.getCommand());
                    tool.put("url", s.getUrl());
                    tools.add(tool);
                });

        return ResponseEntity.ok(tools);
    }

    @PostMapping("/tools/{toolKey}/install")
    @Operation(summary = "从市场模板安装 MCP 工具")
    public ResponseEntity<McpService> installTool(@PathVariable String toolKey) {
        Optional<Map<String, Object>> templateOpt = MARKET_TEMPLATES.stream()
                .filter(t -> toolKey.equals(t.get("key")))
                .findFirst();
        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("未找到工具模板: " + toolKey);
        }

        Map<String, Object> template = templateOpt.get();
        List<McpService> existing = mcpServiceService.getAllServices();
        Optional<McpService> installedOpt = existing.stream()
                .filter(s -> toolKey.equals(s.getToolKey()))
                .findFirst();
        if (installedOpt.isPresent()) {
            return ResponseEntity.ok(installedOpt.get());
        }

        McpService service = McpService.builder()
                .name(String.valueOf(template.get("name")))
                .toolKey(toolKey)
                .type(McpService.McpType.valueOf(String.valueOf(template.get("type"))))
                .command((String) template.get("command"))
                .url((String) template.get("url"))
                .description((String) template.get("description"))
                .enabled(true)
                .build();

        return ResponseEntity.ok(mcpServiceService.createService(service));
    }

    @PutMapping("/services/{id}/enabled")
    @Operation(summary = "启用/禁用 MCP 服务")
    public ResponseEntity<McpService> setServiceEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(mcpServiceService.setServiceEnabled(id, enabled));
    }
}
