package com.adlin.orin.modules.settings.controller;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 外部框架集成控制器
 * 统一管理 Dify、RAGFlow、AutoGen、CrewAI、Neo4j 的配置和测试接口
 */
@Slf4j
@RestController
@RequestMapping({"/api/system/integrations", "/api/v1/system/integrations"})
@RequiredArgsConstructor
@Tag(name = "外部框架集成", description = "外部 AI 框架的集成配置与连接测试")
public class IntegrationController {

    private final DifyIntegrationService difyIntegrationService;
    private final RAGFlowIntegrationService ragFlowIntegrationService;
    private final SystemConfigRepository systemConfigRepository;

    // Dify 配置的 sys_system_config key 前缀
    private static final String DIFY_API_URL_KEY = "dify.apiUrl";
    private static final String DIFY_API_KEY_KEY = "dify.apiKey";
    private static final String DIFY_ENABLED_KEY = "dify.enabled";

    // 其他框架仍用内存存储（预留位，暂未持久化）
    private static final Map<String, Map<String, Object>> INTEGRATION_CONFIGS = new HashMap<>();

    static {
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
        INTEGRATION_CONFIGS.put("neo4j", new HashMap<>(Map.of(
            "uri", "",
            "host", "localhost",
            "port", 7687,
            "username", "neo4j",
            "password", "",
            "database", "neo4j",
            "maxConnectionPoolSize", 50,
            "connectionAcquisitionTimeoutMs", 60000,
            "enabled", false
        )));
    }

    // ========== Dify 持久化读写辅助 ==========

    private String getDifyConfigValue(String key, String defaultValue) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfigEntity::getConfigValue)
                .orElse(defaultValue);
    }

    private void saveDifyConfigValue(String key, String value, String description) {
        SystemConfigEntity entity = systemConfigRepository.findByConfigKey(key)
                .orElseGet(() -> {
                    SystemConfigEntity e = new SystemConfigEntity();
                    e.setConfigKey(key);
                    e.setDescription(description);
                    return e;
                });
        entity.setConfigValue(value);
        systemConfigRepository.save(entity);
    }

    // ========== Dify ==========

    @GetMapping("/dify")
    @Operation(summary = "获取 Dify 配置")
    public ResponseEntity<Map<String, Object>> getDifyConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("apiUrl", getDifyConfigValue(DIFY_API_URL_KEY, ""));
        result.put("apiKey", getDifyConfigValue(DIFY_API_KEY_KEY, ""));
        result.put("enabled", Boolean.parseBoolean(getDifyConfigValue(DIFY_ENABLED_KEY, "false")));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/dify")
    @Operation(summary = "保存 Dify 配置")
    public ResponseEntity<Map<String, Object>> saveDifyConfig(@RequestBody Map<String, Object> config) {
        saveDifyConfigValue(DIFY_API_URL_KEY, asString(config.getOrDefault("apiUrl", "")), "Dify API 地址");
        saveDifyConfigValue(DIFY_API_KEY_KEY, asString(config.getOrDefault("apiKey", "")), "Dify API Key");
        saveDifyConfigValue(DIFY_ENABLED_KEY, String.valueOf(config.getOrDefault("enabled", false)), "Dify 启用状态");
        return getDifyConfig();
    }

    @GetMapping("/dify/test")
    @Operation(summary = "测试 Dify 连接")
    public ResponseEntity<Map<String, Object>> testDifyConnection() {
        String apiUrl = getDifyConfigValue(DIFY_API_URL_KEY, "");
        String apiKey = getDifyConfigValue(DIFY_API_KEY_KEY, "");
        boolean enabled = Boolean.parseBoolean(getDifyConfigValue(DIFY_ENABLED_KEY, "false"));
        Map<String, Object> result = new HashMap<>();

        if (!enabled) {
            result.put("success", false);
            result.put("message", "Dify 未启用");
            return ResponseEntity.ok(result);
        }
        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey)) {
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
        String apiUrl = getDifyConfigValue(DIFY_API_URL_KEY, "");
        String apiKey = getDifyConfigValue(DIFY_API_KEY_KEY, "");
        boolean enabled = Boolean.parseBoolean(getDifyConfigValue(DIFY_ENABLED_KEY, "false"));

        if (!enabled) {
            return ResponseEntity.ok(Map.of("success", false, "message", "Dify 未启用"));
        }
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

        String apiUrl = asString(ragflowConfig.get("apiUrl"));
        String apiKey = asString(ragflowConfig.get("apiKey"));

        if (!StringUtils.hasText(apiUrl) || !StringUtils.hasText(apiKey)) {
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

        String apiUrl = asString(ragflowConfig.get("apiUrl"));
        String apiKey = asString(ragflowConfig.get("apiKey"));

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

        String serviceUrl = asString(autogenConfig.get("serviceUrl"));
        if (!StringUtils.hasText(serviceUrl)) {
            result.put("success", false);
            result.put("message", "AutoGen 服务地址未配置");
            return ResponseEntity.ok(result);
        }

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

        String serviceUrl = asString(crewaiConfig.get("serviceUrl"));
        if (!StringUtils.hasText(serviceUrl)) {
            result.put("success", false);
            result.put("message", "CrewAI 服务地址未配置");
            return ResponseEntity.ok(result);
        }

        result.put("success", false);
        result.put("message", "CrewAI 为预留集成，暂未实现");
        return ResponseEntity.ok(result);
    }

    // ========== Neo4j ==========

    @GetMapping("/neo4j")
    @Operation(summary = "获取 Neo4j 配置")
    public ResponseEntity<Map<String, Object>> getNeo4jConfig() {
        Map<String, Object> config = INTEGRATION_CONFIGS.get("neo4j");
        return ResponseEntity.ok(config != null ? config : new HashMap<>());
    }

    @PostMapping("/neo4j")
    @Operation(summary = "保存 Neo4j 配置")
    public ResponseEntity<Map<String, Object>> saveNeo4jConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> neo4jConfig = INTEGRATION_CONFIGS.computeIfAbsent("neo4j", k -> new HashMap<>());
        neo4jConfig.put("uri", config.getOrDefault("uri", ""));
        neo4jConfig.put("host", config.getOrDefault("host", "localhost"));
        neo4jConfig.put("port", config.getOrDefault("port", 7687));
        neo4jConfig.put("username", config.getOrDefault("username", "neo4j"));
        neo4jConfig.put("password", config.getOrDefault("password", ""));
        neo4jConfig.put("database", config.getOrDefault("database", "neo4j"));
        neo4jConfig.put("maxConnectionPoolSize", config.getOrDefault("maxConnectionPoolSize", 50));
        neo4jConfig.put("connectionAcquisitionTimeoutMs", config.getOrDefault("connectionAcquisitionTimeoutMs", 60000));
        neo4jConfig.put("enabled", config.getOrDefault("enabled", false));
        return ResponseEntity.ok(neo4jConfig);
    }

    @GetMapping("/neo4j/test")
    @Operation(summary = "测试 Neo4j 连接")
    public ResponseEntity<Map<String, Object>> testNeo4jConnection() {
        Map<String, Object> neo4jConfig = INTEGRATION_CONFIGS.get("neo4j");
        Map<String, Object> result = new HashMap<>();

        if (neo4jConfig == null || !Boolean.TRUE.equals(neo4jConfig.get("enabled"))) {
            result.put("success", false);
            result.put("message", "Neo4j 未启用");
            return ResponseEntity.ok(result);
        }

        String uri = asString(neo4jConfig.get("uri"));
        String host = asString(neo4jConfig.get("host"));
        int port = asInt(neo4jConfig.get("port"), 7687);
        String username = asString(neo4jConfig.get("username"));
        String password = asString(neo4jConfig.get("password"));
        String database = asString(neo4jConfig.get("database"));
        int maxPool = asInt(neo4jConfig.get("maxConnectionPoolSize"), 50);
        int acquisitionTimeoutMs = asInt(neo4jConfig.get("connectionAcquisitionTimeoutMs"), 60000);

        String boltUri = StringUtils.hasText(uri) ? uri : String.format("bolt://%s:%d", host, port);
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password) || !StringUtils.hasText(database)) {
            result.put("success", false);
            result.put("message", "Neo4j 配置不完整（用户名/密码/数据库必填）");
            return ResponseEntity.ok(result);
        }

        try (var driver = GraphDatabase.driver(
                boltUri,
                AuthTokens.basic(username, password),
                Config.builder()
                        .withMaxConnectionPoolSize(maxPool)
                        .withConnectionAcquisitionTimeout(acquisitionTimeoutMs, TimeUnit.MILLISECONDS)
                        .build());
             var session = driver.session(SessionConfig.forDatabase(database))) {
            session.run("RETURN 1 AS ok").single();
            result.put("success", true);
            result.put("message", "Neo4j 连接成功");
            result.put("uri", boltUri);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("Neo4j test connection failed: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "Neo4j 连接失败: " + e.getMessage());
            return ResponseEntity.ok(result);
        }
    }

    // ========== 统一状态查询 ==========

    @GetMapping("/status")
    @Operation(summary = "获取所有集成状态")
    public ResponseEntity<Map<String, Object>> getIntegrationStatus() {
        Map<String, Object> status = new HashMap<>();

        String difyApiUrl = getDifyConfigValue(DIFY_API_URL_KEY, "");
        String difyApiKey = getDifyConfigValue(DIFY_API_KEY_KEY, "");
        boolean difyEnabled = Boolean.parseBoolean(getDifyConfigValue(DIFY_ENABLED_KEY, "false"));
        if (difyEnabled) {
            boolean connected = StringUtils.hasText(difyApiUrl) && StringUtils.hasText(difyApiKey)
                    && difyIntegrationService.testConnection(difyApiUrl, difyApiKey);
            status.put("dify", Map.of(
                "enabled", true,
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"
            ));
        } else {
            status.put("dify", Map.of("enabled", false, "status", "DISABLED"));
        }

        Map<String, Object> ragflowConfig = INTEGRATION_CONFIGS.get("ragflow");
        if (ragflowConfig != null && Boolean.TRUE.equals(ragflowConfig.get("enabled"))) {
            String apiUrl = asString(ragflowConfig.get("apiUrl"));
            String apiKey = asString(ragflowConfig.get("apiKey"));
            boolean connected = StringUtils.hasText(apiUrl) && StringUtils.hasText(apiKey)
                    && ragFlowIntegrationService.testConnection(apiUrl, apiKey);
            status.put("ragflow", Map.of(
                "enabled", true,
                "connected", connected,
                "status", connected ? "CONNECTED" : "DISCONNECTED"
            ));
        } else {
            status.put("ragflow", Map.of("enabled", false, "status", "DISABLED"));
        }

        Map<String, Object> autogenConfig = INTEGRATION_CONFIGS.get("autogen");
        status.put("autogen", Map.of(
            "enabled", autogenConfig != null && Boolean.TRUE.equals(autogenConfig.get("enabled")),
            "status", "NOT_IMPLEMENTED"
        ));

        Map<String, Object> crewaiConfig = INTEGRATION_CONFIGS.get("crewai");
        status.put("crewai", Map.of(
            "enabled", crewaiConfig != null && Boolean.TRUE.equals(crewaiConfig.get("enabled")),
            "status", "NOT_IMPLEMENTED"
        ));

        Map<String, Object> neo4jConfig = INTEGRATION_CONFIGS.get("neo4j");
        status.put("neo4j", Map.of(
            "enabled", neo4jConfig != null && Boolean.TRUE.equals(neo4jConfig.get("enabled")),
            "status", "CONFIGURED"
        ));

        return ResponseEntity.ok(status);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
