package com.adlin.orin.modules.observability.controller;

import com.adlin.orin.modules.observability.service.LangfuseObservabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 可观测性配置控制器
 * 提供 Langfuse 配置状态查询与开关控制
 */
@RestController
@RequestMapping("/api/v1/observability")
@RequiredArgsConstructor
@Tag(name = "Observability Config", description = "可观测性配置 API")
public class ObservabilityController {

    private final LangfuseObservabilityService langfuseService;

    @GetMapping("/langfuse/status")
    @Operation(summary = "获取 Langfuse 配置状态")
    public ResponseEntity<Map<String, Object>> getLangfuseStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", langfuseService.isEnabled());
        status.put("configured", langfuseService.isConfigured());

        if (langfuseService.isEnabled()) {
            status.put("message", "Langfuse 已启用");
            status.put("link", langfuseService.getDashboardUrl());
        } else if (langfuseService.isConfigured()) {
            status.put("message", "配置已完善但未启用");
        } else {
            status.put("message", "请配置 langfuse.public-key 和 langfuse.secret-key");
        }

        return ResponseEntity.ok(status);
    }

    @PostMapping("/langfuse/enable")
    @Operation(summary = "启用 Langfuse")
    public ResponseEntity<Map<String, Object>> enableLangfuse(
            @RequestParam boolean enabled,
            @RequestParam(required = false) String publicKey,
            @RequestParam(required = false) String secretKey,
            @RequestParam(required = false) String host) {
        Map<String, Object> result = new HashMap<>();

        if (!enabled) {
            result.put("success", false);
            result.put("message", "请通过配置文件设置 langfuse.enabled=false 禁用");
            return ResponseEntity.badRequest().body(result);
        }

        if (publicKey != null && secretKey != null) {
            result.put("success", false);
            result.put("message", "配置修改请通过配置文件或环境变量");
            result.put("hint", "langfuse.public-key, langfuse.secret-key, langfuse.host");
            return ResponseEntity.badRequest().body(result);
        }

        if (!langfuseService.isConfigured()) {
            result.put("success", false);
            result.put("message", "请先配置 Langfuse credentials");
            return ResponseEntity.badRequest().body(result);
        }

        result.put("success", true);
        result.put("message", "Langfuse 已在配置中启用");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    @Operation(summary = "获取可观测性配置信息")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> langfuse = new HashMap<>();
        langfuse.put("enabled", langfuseService.isEnabled());
        langfuse.put("configured", langfuseService.isConfigured());
        langfuse.put("hasPublicKey", langfuseService.hasPublicKey());
        config.put("langfuse", langfuse);

        return ResponseEntity.ok(config);
    }

    @GetMapping("/dashboard/unified")
    @Operation(summary = "获取统一监控看板数据")
    public ResponseEntity<Map<String, Object>> getUnifiedDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        // Langfuse 数据
        Map<String, Object> langfuse = new HashMap<>();
        langfuse.put("enabled", langfuseService.isEnabled());
        langfuse.put("link", langfuseService.getDashboardUrl());
        dashboard.put("langfuse", langfuse);

        // Prometheus/系统监控数据 - 指向现有监控接口
        dashboard.put("prometheus", Map.of(
                "endpoint", "/api/v1/monitor/dashboard/summary",
                "description", "系统监控大盘"
        ));

        // 本地 Trace 数据
        dashboard.put("trace", Map.of(
                "endpoint", "/api/traces",
                "description", "业务追踪记录"
        ));

        return ResponseEntity.ok(dashboard);
    }
}
