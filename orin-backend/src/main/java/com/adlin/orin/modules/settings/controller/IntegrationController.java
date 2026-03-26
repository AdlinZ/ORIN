package com.adlin.orin.modules.settings.controller;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 外部框架集成控制器
 * 统一管理 Dify、RAGFlow、AutoGen、CrewAI 的配置和测试接口
 */
@Slf4j
@RestController
@RequestMapping("/api/system/integrations")
@RequiredArgsConstructor
@Tag(name = "外部框架集成", description = "外部 AI 框架的集成配置与连接测试")
public class IntegrationController {

    private final DifyIntegrationService difyIntegrationService;
    private final RAGFlowIntegrationService ragFlowIntegrationService;

    // 模拟的配置存储（实际应该存储到数据库或配置中心）
    private static final Map<String, Map<String, Object>> INTEGRATION_CONFIGS = new HashMap<>();

    static {
        // 初始化默认配置
        INTEGRATION_CONFIGS.put("dify", new HashMap<>(Map.of(
            "apiUrl", "http://localhost:3000/v1",
            "enabled", false
        )));
        INTEGRATION_CONFIGS.put("ragflow", new HashMap<>(Map.of(
            "apiUrl", "http://localhost:9380/v1",
            "enabled", false
        )));
        INTEGRATION_CONFIGS.put("autogen", new HashMap<>(Map.of(
            "serviceUrl", "http://localhost:8001",
            "maxConcurrency", 5,
            "enabled", false
        )));
        INTEGRATION_CONFIGS.put("crewai", new HashMap<>(Map.of(
            "serviceUrl", "http://localhost:8002",
            "defaultModel", "gpt-4",
            "enabled", false
        )));
    }

    // ========== Dify ==========

    @GetMapping("/dify")
    @Operation(summary = "获取 Dify 配置")
    public ResponseEntity<Map<String, Object>> getDifyConfig() {
        Map<String, Object> config = INTEGRATION_CONFIGS.get("dify");
        return ResponseEntity.ok(config != null ? config : new HashMap<>());
    }

    @PostMapping("/dify")
    @Operation(summary = "保存 Dify 配置")
    public ResponseEntity<Map<String, Object>> saveDifyConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> difyConfig = INTEGRATION_CONFIGS.computeIfAbsent("dify", k -> new HashMap<>());
        difyConfig.put("apiUrl", config.getOrDefault("apiUrl", ""));
        difyConfig.put("apiKey", config.getOrDefault("apiKey", ""));
        difyConfig.put("enabled", config.getOrDefault("enabled", false));
        return ResponseEntity.ok(difyConfig);
    }

    @GetMapping("/dify/test")
    @Operation(summary = "测试 Dify 连接")
    public ResponseEntity<Map<String, Object>> testDifyConnection() {
        Map<String, Object> difyConfig = INTEGRATION_CONFIGS.get("dify");
        Map<String, Object> result = new HashMap<>();

        if (difyConfig == null || !Boolean.TRUE.equals(difyConfig.get("enabled"))) {
            result.put("success", false);
            result.put("message", "Dify 未启用");
            return ResponseEntity.ok(result);
        }

        String apiUrl = (String) difyConfig.get("apiUrl");
        String apiKey = (String) difyConfig.get("apiKey");

        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            result.put("success", false);
            result.put("message", "Dify 配置不完整");
            return ResponseEntity.ok(result);
        }

        boolean connected = difyIntegrationService.testConnection(apiUrl, apiKey);
        result.put("success", connected);
        result.put("message", connected ? "连接成功" : "连接失败");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dify/apps")
    @Operation(summary = "获取 Dify 应用列表")
    public ResponseEntity<Map<String, Object>> getDifyApps() {
        Map<String, Object> difyConfig = INTEGRATION_CONFIGS.get("dify");
        if (difyConfig == null || !Boolean.TRUE.equals(difyConfig.get("enabled"))) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Dify 未启用"));
        }

        String apiUrl = (String) difyConfig.get("apiUrl");
        String apiKey = (String) difyConfig.get("apiKey");

        var apps = difyIntegrationService.getApplications(apiUrl, apiKey);
        if (apps.isPresent()) {
            return ResponseEntity.ok(Map.of("success", true, "data", apps.get()));
        }
        return ResponseEntity.ok(Map.of("success", false, "message", "获取应用列表失败"));
    }

    // ========== RAGFlow ==========

    @GetMapping("/ragflow")
    @Operation(summary = "获取 RAGFlow 配置")
    public ResponseEntity<Map<String, Object>> getRagflowConfig() {
        Map<String, Object> config = INTEGRATION_CONFIGS.get("ragflow");
        return ResponseEntity.ok(config != null ? config : new HashMap<>());
    }

    @PostMapping("/ragflow")
    @Operation(summary = "保存 RAGFlow 配置")
    public ResponseEntity<Map<String, Object>> saveRagflowConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> ragflowConfig = INTEGRATION_CONFIGS.computeIfAbsent("ragflow", k -> new HashMap<>());
        ragflowConfig.put("apiUrl", config.getOrDefault("apiUrl", ""));
        ragflowConfig.put("apiKey", config.getOrDefault("apiKey", ""));
        ragflowConfig.put("enabled", config.getOrDefault("enabled", false));
        return ResponseEntity.ok(ragflowConfig);
    }

    @GetMapping("/ragflow/test")
    @Operation(summary = "测试 RAGFlow 连接")
    public ResponseEntity<Map<String, Object>> testRagflowConnection() {
        Map<String, Object> ragflowConfig = INTEGRATION_CONFIGS.get("ragflow");
        Map<String, Object> result = new HashMap<>();

        if (ragflowConfig == null || !Boolean.TRUE.equals(ragflowConfig.get("enabled"))) {
            result.put("success", false);
            result.put("message", "RAGFlow 未启用");
            return ResponseEntity.ok(result);
        }

        String apiUrl = (String) ragflowConfig.get("apiUrl");
        String apiKey = (String) ragflowConfig.get("apiKey");

        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            result.put("success", false);
            result.put("message", "RAGFlow 配置不完整");
            return ResponseEntity.ok(result);
        }

        boolean connected = ragFlowIntegrationService.testConnection(apiUrl, apiKey);
        result.put("success", connected);
        result.put("message", connected ? "连接成功" : "连接失败");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ragflow/knowledge-bases")
    @Operation(summary = "获取 RAGFlow 知识库列表")
    public ResponseEntity<Map<String, Object>> getRagflowKnowledgeBases() {
        Map<String, Object> ragflowConfig = INTEGRATION_CONFIGS.get("ragflow");
        if (ragflowConfig == null || !Boolean.TRUE.equals(ragflowConfig.get("enabled"))) {
            return ResponseEntity.ok(Map.of("success", false, "message", "RAGFlow 未启用"));
        }

        String apiUrl = (String) ragflowConfig.get("apiUrl");
        String apiKey = (String) ragflowConfig.get("apiKey");

        var kbList = ragFlowIntegrationService.listKnowledgeBases(apiUrl, apiKey);
        return ResponseEntity.ok(Map.of("success", true, "data", kbList));
    }

    // ========== AutoGen ==========

    @GetMapping("/autogen")
    @Operation(summary = "获取 AutoGen 配置")
    public ResponseEntity<Map<String, Object>> getAutogenConfig() {
        Map<String, Object> config = INTEGRATION_CONFIGS.get("autogen");
        return ResponseEntity.ok(config != null ? config : new HashMap<>());
    }

    @PostMapping("/autogen")
    @Operation(summary = "保存 AutoGen 配置")
    public ResponseEntity<Map<String, Object>> saveAutogenConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> autogenConfig = INTEGRATION_CONFIGS.computeIfAbsent("autogen", k -> new HashMap<>());
        autogenConfig.put("serviceUrl", config.getOrDefault("serviceUrl", ""));
        autogenConfig.put("apiKey", config.getOrDefault("apiKey", ""));
        autogenConfig.put("maxConcurrency", config.getOrDefault("maxConcurrency", 5));
        autogenConfig.put("enabled", config.getOrDefault("enabled", false));
        return ResponseEntity.ok(autogenConfig);
    }

    @GetMapping("/autogen/test")
    @Operation(summary = "测试 AutoGen 连接")
    public ResponseEntity<Map<String, Object>> testAutogenConnection() {
        Map<String, Object> autogenConfig = INTEGRATION_CONFIGS.get("autogen");
        Map<String, Object> result = new HashMap<>();

        if (autogenConfig == null || !Boolean.TRUE.equals(autogenConfig.get("enabled"))) {
            result.put("success", false);
            result.put("message", "AutoGen 未启用");
            return ResponseEntity.ok(result);
        }

        String serviceUrl = (String) autogenConfig.get("serviceUrl");
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            result.put("success", false);
            result.put("message", "AutoGen 服务地址未配置");
            return ResponseEntity.ok(result);
        }

        // TODO: 实现真实的 AutoGen 连接测试
        // 目前为预留位实现
        result.put("success", false);
        result.put("message", "AutoGen 为预留集成，暂未实现");
        return ResponseEntity.ok(result);
    }

    // ========== CrewAI ==========

    @GetMapping("/crewai")
    @Operation(summary = "获取 CrewAI 配置")
    public ResponseEntity<Map<String, Object>> getCrewaiConfig() {
        Map<String, Object> config = INTEGRATION_CONFIGS.get("crewai");
        return ResponseEntity.ok(config != null ? config : new HashMap<>());
    }

    @PostMapping("/crewai")
    @Operation(summary = "保存 CrewAI 配置")
    public ResponseEntity<Map<String, Object>> saveCrewaiConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> crewaiConfig = INTEGRATION_CONFIGS.computeIfAbsent("crewai", k -> new HashMap<>());
        crewaiConfig.put("serviceUrl", config.getOrDefault("serviceUrl", ""));
        crewaiConfig.put("apiKey", config.getOrDefault("apiKey", ""));
        crewaiConfig.put("defaultModel", config.getOrDefault("defaultModel", "gpt-4"));
        crewaiConfig.put("enabled", config.getOrDefault("enabled", false));
        return ResponseEntity.ok(crewaiConfig);
    }

    @GetMapping("/crewai/test")
    @Operation(summary = "测试 CrewAI 连接")
    public ResponseEntity<Map<String, Object>> testCrewaiConnection() {
        Map<String, Object> crewaiConfig = INTEGRATION_CONFIGS.get("crewai");
        Map<String, Object> result = new HashMap<>();

        if (crewaiConfig == null || !Boolean.TRUE.equals(crewaiConfig.get("enabled"))) {
            result.put("success", false);
            result.put("message", "CrewAI 未启用");
            return ResponseEntity.ok(result);
        }

        String serviceUrl = (String) crewaiConfig.get("serviceUrl");
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            result.put("success", false);
            result.put("message", "CrewAI 服务地址未配置");
            return ResponseEntity.ok(result);
        }

        // TODO: 实现真实的 CrewAI 连接测试
        // 目前为预留位实现
        result.put("success", false);
        result.put("message", "CrewAI 为预留集成，暂未实现");
        return ResponseEntity.ok(result);
    }

    // ========== 统一状态查询 ==========

    @GetMapping("/status")
    @Operation(summary = "获取所有集成状态")
    public ResponseEntity<Map<String, Object>> getIntegrationStatus() {
        Map<String, Object> status = new HashMap<>();

        // Dify
        Map<String, Object> difyConfig = INTEGRATION_CONFIGS.get("dify");
        if (difyConfig != null && Boolean.TRUE.equals(difyConfig.get("enabled"))) {
            String apiUrl = (String) difyConfig.get("apiUrl");
            String apiKey = (String) difyConfig.get("apiKey");
            boolean connected = apiUrl != null && apiKey != null &&
                difyIntegrationService.testConnection(apiUrl, apiKey);
            status.put("dify", Map.of(
                "enabled", true,
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"
            ));
        } else {
            status.put("dify", Map.of("enabled", false, "status", "DISABLED"));
        }

        // RAGFlow
        Map<String, Object> ragflowConfig = INTEGRATION_CONFIGS.get("ragflow");
        if (ragflowConfig != null && Boolean.TRUE.equals(ragflowConfig.get("enabled"))) {
            String apiUrl = (String) ragflowConfig.get("apiUrl");
            String apiKey = (String) ragflowConfig.get("apiKey");
            boolean connected = apiUrl != null && apiKey != null &&
                ragFlowIntegrationService.testConnection(apiUrl, apiKey);
            status.put("ragflow", Map.of(
                "enabled", true,
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"
            ));
        } else {
            status.put("ragflow", Map.of("enabled", false, "status", "DISABLED"));
        }

        // AutoGen (预留位)
        Map<String, Object> autogenConfig = INTEGRATION_CONFIGS.get("autogen");
        status.put("autogen", Map.of(
            "enabled", autogenConfig != null && Boolean.TRUE.equals(autogenConfig.get("enabled")),
            "status", "NOT_IMPLEMENTED"
        ));

        // CrewAI (预留位)
        Map<String, Object> crewaiConfig = INTEGRATION_CONFIGS.get("crewai");
        status.put("crewai", Map.of(
            "enabled", crewaiConfig != null && Boolean.TRUE.equals(crewaiConfig.get("enabled")),
            "status", "NOT_IMPLEMENTED"
        ));

        return ResponseEntity.ok(status);
    }
}