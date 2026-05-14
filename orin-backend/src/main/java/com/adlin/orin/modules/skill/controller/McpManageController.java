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
                    "filesystem /app", null),
            template("github", "GitHub", "GitHub 仓库与 Issue 查询", "STDIO",
                    "github", null),
            template("fetch", "Fetch", "HTTP 内容抓取和网页读取", "STDIO",
                    "fetch", null),
            template("sqlite", "SQLite", "SQLite 数据库查询与结构探索", "STDIO",
                    "sqlite /app/orin-mcp.sqlite", null),
            template("time", "Time", "时间与时区工具", "STDIO",
                    "time", null));

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
        return ResponseEntity.ok(services.stream().map(this::maskService).toList());
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "获取单个 MCP 服务")
    public ResponseEntity<McpService> getService(@PathVariable Long id) {
        McpService service = mcpServiceService.getServiceById(id);
        return ResponseEntity.ok(maskService(service));
    }

    @PostMapping("/services")
    @Operation(summary = "创建 MCP 服务")
    public ResponseEntity<McpService> createService(@RequestBody McpService service) {
        McpService created = mcpServiceService.createService(service);
        return ResponseEntity.ok(maskService(created));
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "更新 MCP 服务")
    public ResponseEntity<McpService> updateService(@PathVariable Long id, @RequestBody McpService service) {
        McpService updated = mcpServiceService.updateService(id, service);
        return ResponseEntity.ok(maskService(updated));
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

    /**
     * 仅供 AI Engine 服务间调用：返回启用 MCP 服务的真实配置（含明文 envVars），
     * 以便 MCP Server 能拿到实际运行所需的环境变量。
     *
     * <p><b>禁止</b>暴露给前端或普通用户接口——前端一律走 {@link #maskService} 后的 masked 视图。
     * 该路径在 {@code SecurityConfig} 中作为服务间内部白名单放行，不要给它加面向用户的入口。
     */
    @GetMapping("/internal/enabled/{id}")
    @Operation(summary = "AI Engine 内部读取启用 MCP 配置（含明文 env，禁止前端使用）")
    public ResponseEntity<Map<String, Object>> getEnabledServiceForAiEngine(@PathVariable Long id) {
        McpService service = mcpServiceService.getServiceById(id);
        if (!Boolean.TRUE.equals(service.getEnabled())) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", service.getId());
        payload.put("name", service.getName());
        payload.put("toolKey", service.getToolKey());
        payload.put("type", service.getType() != null ? service.getType().name() : "STDIO");
        payload.put("command", service.getCommand());
        payload.put("url", service.getUrl());
        // 真实 env：AI Engine 需要明文才能启动需要凭据/路径的 MCP Server
        payload.put("envVars", service.getEnvVars());
        return ResponseEntity.ok(payload);
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
            return ResponseEntity.ok(maskService(installedOpt.get()));
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

        return ResponseEntity.ok(maskService(mcpServiceService.createService(service)));
    }

    @PutMapping("/services/{id}/enabled")
    @Operation(summary = "启用/禁用 MCP 服务")
    public ResponseEntity<McpService> setServiceEnabled(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return ResponseEntity.ok(maskService(mcpServiceService.setServiceEnabled(id, enabled)));
    }

    private McpService maskService(McpService service) {
        if (service == null) {
            return null;
        }
        return McpService.builder()
                .id(service.getId())
                .name(service.getName())
                .toolKey(service.getToolKey())
                .type(service.getType())
                .command(service.getCommand())
                .url(service.getUrl())
                .envVars(maskEnvVars(service.getEnvVars()))
                .description(service.getDescription())
                .enabled(service.getEnabled())
                .status(service.getStatus())
                .lastConnected(service.getLastConnected())
                .lastError(service.getLastError())
                .healthScore(service.getHealthScore())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }

    private String maskEnvVars(String envVars) {
        if (envVars == null || envVars.isBlank()) {
            return envVars;
        }
        return java.util.Arrays.stream(envVars.split("\\R"))
                .map(line -> line.contains("=") ? line.substring(0, line.indexOf('=') + 1) + "******" : line)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
