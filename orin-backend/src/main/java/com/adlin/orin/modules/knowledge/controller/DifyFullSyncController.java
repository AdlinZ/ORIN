package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullSyncService;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullApiClient;
import com.adlin.orin.modules.settings.service.SystemDifyConfigProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 完整同步 API（系统级）
 */
@RestController
@RequestMapping("/api/v1/sync/dify")
@RequiredArgsConstructor
@Tag(name = "Dify Full Sync", description = "Dify 完整数据同步（系统级）")
public class DifyFullSyncController {

    private final DifyFullSyncService difyFullSyncService;
    private final DifyFullApiClient difyFullApiClient;
    private final SystemDifyConfigProvider difyConfigProvider;

    @Operation(summary = "完整同步（知识库 + 应用 + 工作流）")
    @PostMapping("/full")
    public Map<String, Object> fullSync() {
        var result = difyFullSyncService.fullSync();
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("added", result.getAdded());
        response.put("updated", result.getUpdated());
        response.put("deleted", result.getDeleted());
        return response;
    }

    @Operation(summary = "同步工作流")
    @PostMapping("/workflows")
    public Map<String, Object> syncWorkflows() {
        var result = difyFullSyncService.syncWorkflows();
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("count", result.getAdded());
        return response;
    }

    @Operation(summary = "同步对话历史")
    @PostMapping("/conversations")
    public Map<String, Object> syncConversations(@RequestParam String appId) {
        var result = difyFullSyncService.syncConversations(appId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("count", result.getAdded());
        return response;
    }

    @Operation(summary = "获取同步概览")
    @GetMapping("/overview")
    public Map<String, Object> getSyncOverview() {
        if (!difyConfigProvider.isConfigured()) {
            return Map.of("success", false, "message", "Dify 未配置");
        }

        String endpoint = difyConfigProvider.getApiUrl();
        String apiKey   = difyConfigProvider.getApiKey();

        Map<String, Object> overview = difyFullApiClient.fullSync(endpoint, apiKey);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("appsCount",      ((List<?>) overview.getOrDefault("apps",      List.of())).size());
        result.put("workflowsCount", ((List<?>) overview.getOrDefault("workflows", List.of())).size());
        result.put("datasetsCount",  ((List<?>) overview.getOrDefault("datasets",  List.of())).size());
        result.put("apiKeysCount",   ((List<?>) overview.getOrDefault("apiKeys",   List.of())).size());
        result.put("user",           overview.get("user"));
        result.put("workspace",      overview.get("workspace"));
        return result;
    }

    @Operation(summary = "测试 Dify 连接")
    @PostMapping("/test")
    public Map<String, Object> testConnection(@RequestBody Map<String, String> config) {
        String endpoint = config.get("endpoint");
        String apiKey   = config.get("apiKey");
        boolean success = difyFullSyncService.testConnection(endpoint, apiKey);
        return Map.of("success", success, "message", success ? "连接成功" : "连接失败");
    }

    @Operation(summary = "获取同步历史")
    @GetMapping("/history")
    public List<SyncRecord> getSyncHistory(@RequestParam(defaultValue = "10") int limit) {
        return List.of();
    }
}
