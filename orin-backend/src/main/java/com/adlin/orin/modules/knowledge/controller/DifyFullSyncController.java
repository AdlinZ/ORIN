package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullSyncService;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullApiClient;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 完整同步 API
 * 支持知识库、文档、应用、工作流、对话历史等同步
 */
@RestController
@RequestMapping("/api/v1/sync/dify")
@RequiredArgsConstructor
@Tag(name = "Dify Full Sync", description = "Dify 完整数据同步")
public class DifyFullSyncController {

    private final DifyFullSyncService difyFullSyncService;
    private final DifyFullApiClient difyFullApiClient;
    private final AgentAccessProfileRepository profileRepository;

    @Operation(summary = "完整同步 (知识库+应用+工作流+对话)")
    @PostMapping("/full/{agentId}")
    public Map<String, Object> fullSync(@PathVariable String agentId) {
        var result = difyFullSyncService.fullSync(agentId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("added", result.getAdded());
        response.put("updated", result.getUpdated());
        response.put("deleted", result.getDeleted());
        
        return response;
    }

    @Operation(summary = "同步工作流")
    @PostMapping("/workflows/{agentId}")
    public Map<String, Object> syncWorkflows(@PathVariable String agentId) {
        var result = difyFullSyncService.syncWorkflows(agentId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("count", result.getAdded());
        
        return response;
    }

    @Operation(summary = "同步对话历史")
    @PostMapping("/conversations/{agentId}")
    public Map<String, Object> syncConversations(
            @PathVariable String agentId,
            @RequestParam String appId) {
        var result = difyFullSyncService.syncConversations(agentId, appId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("count", result.getAdded());
        
        return response;
    }

    @Operation(summary = "获取同步概览")
    @GetMapping("/overview/{agentId}")
    public Map<String, Object> getSyncOverview(@PathVariable String agentId) {
        var profileOpt = profileRepository.findById(agentId);
        
        if (profileOpt.isEmpty()) {
            return Map.of("success", false, "message", "Agent not found");
        }
        
        var profile = profileOpt.get();
        String endpoint = profile.getEndpointUrl();
        String apiKey = profile.getDatasetApiKey();
        
        Map<String, Object> overview = difyFullApiClient.fullSync(endpoint, apiKey);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("appsCount", ((List<?>) overview.getOrDefault("apps", List.of())).size());
        result.put("workflowsCount", ((List<?>) overview.getOrDefault("workflows", List.of())).size());
        result.put("datasetsCount", ((List<?>) overview.getOrDefault("datasets", List.of())).size());
        result.put("apiKeysCount", ((List<?>) overview.getOrDefault("apiKeys", List.of())).size());
        result.put("user", overview.get("user"));
        result.put("workspace", overview.get("workspace"));
        
        return result;
    }

    @Operation(summary = "测试 Dify 连接")
    @PostMapping("/test")
    public Map<String, Object> testConnection(@RequestBody Map<String, String> config) {
        String endpoint = config.get("endpoint");
        String apiKey = config.get("apiKey");
        
        boolean success = difyFullSyncService.testConnection(endpoint, apiKey);
        
        return Map.of("success", success, "message", success ? "连接成功" : "连接失败");
    }

    @Operation(summary = "获取同步历史")
    @GetMapping("/history/{agentId}")
    public List<SyncRecord> getSyncHistory(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "10") int limit) {
        return List.of();
    }
}
