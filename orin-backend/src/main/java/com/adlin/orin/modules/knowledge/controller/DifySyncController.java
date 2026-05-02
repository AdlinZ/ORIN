package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.service.sync.DifyKnowledgeSyncService;
import com.adlin.orin.modules.knowledge.service.sync.DifyKnowledgeSyncService.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dify 知识库同步 API（系统级）
 *
 * @deprecated Use /api/v1/integration-sync for new Dify synchronization flows.
 */
@Deprecated
@RestController
@RequestMapping("/api/v1/knowledge/sync")
@RequiredArgsConstructor
@Tag(name = "Dify Knowledge Sync", description = "Dify 知识库同步管理")
public class DifySyncController {

    private final DifyKnowledgeSyncService difySyncService;

    @Operation(summary = "触发知识库同步（增量）")
    @PostMapping("/dify")
    public Map<String, Object> syncDifyKnowledge(
            @RequestParam(value = "full", defaultValue = "false") boolean full) {
        SyncResult result = difySyncService.syncKnowledgeBases(full);
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("added", result.getAdded());
        response.put("updated", result.getUpdated());
        response.put("deleted", result.getDeleted());
        return response;
    }

    @Operation(summary = "强制全量同步")
    @PostMapping("/dify/full")
    public Map<String, Object> fullSync() {
        SyncResult result = difySyncService.syncKnowledgeBases(true);
        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("added", result.getAdded());
        response.put("updated", result.getUpdated());
        response.put("deleted", result.getDeleted());
        return response;
    }

    @Operation(summary = "获取同步历史")
    @GetMapping("/dify/history")
    public List<SyncRecord> getSyncHistory(@RequestParam(defaultValue = "10") int limit) {
        return difySyncService.getSyncHistory(limit);
    }

    @Operation(summary = "测试 Dify 连接")
    @PostMapping("/dify/test")
    public Map<String, Object> testConnection(@RequestBody Map<String, String> config) {
        String endpoint = config.get("endpoint");
        String apiKey = config.get("apiKey");
        boolean success = difySyncService.testConnection(endpoint, apiKey);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", success ? "Connection successful" : "Connection failed");
        return response;
    }
}
