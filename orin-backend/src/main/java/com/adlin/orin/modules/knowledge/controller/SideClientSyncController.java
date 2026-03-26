package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.knowledge.entity.SyncWebhook;
import com.adlin.orin.modules.knowledge.service.sync.SideClientSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 端侧知识库同步 API
 * 提供标准接口供端侧客户端同步知识库
 */
@RestController
@RequestMapping("/api/v1/knowledge/sync")
@RequiredArgsConstructor
@Tag(name = "Side Client Knowledge Sync", description = "端侧知识库同步接口")
@Slf4j
public class SideClientSyncController {

    private final SideClientSyncService sideClientSyncService;
    private final AuditHelper auditHelper;

    // ==================== 变更查询接口 ====================

    @Operation(summary = "查询变更记录", description = "按时间范围查询知识库变更记录")
    @GetMapping("/client/{agentId}/changes")
    public Page<Map<String, Object>> getChanges(
            @PathVariable String agentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return sideClientSyncService.getChanges(agentId, startTime, endTime, page, size);
    }

    @Operation(summary = "获取待同步变更", description = "获取未同步的变更记录")
    @GetMapping("/client/{agentId}/pending")
    public List<Map<String, Object>> getPendingChanges(@PathVariable String agentId) {
        return sideClientSyncService.getPendingChanges(agentId);
    }

    @Operation(summary = "获取待同步变更数量")
    @GetMapping("/client/{agentId}/pending/count")
    public Map<String, Object> getPendingCount(@PathVariable String agentId) {
        long count = sideClientSyncService.getPendingChangeCount(agentId);
        Map<String, Object> result = new HashMap<>();
        result.put("agentId", agentId);
        result.put("pendingCount", count);
        return result;
    }

    // ==================== 批量导出接口 ====================

    @Operation(summary = "批量导出文档", description = "全量首同步或增量导出文档")
    @GetMapping("/client/{agentId}/export")
    public Map<String, Object> exportDocuments(
            @PathVariable String agentId,
            @RequestParam(required = false) String knowledgeBaseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return sideClientSyncService.exportDocuments(agentId, knowledgeBaseId, page, size);
    }

    // ==================== 文档下载接口 ====================

    @Operation(summary = "按文档ID下载", description = "下载指定文档的内容与元数据")
    @GetMapping("/client/{agentId}/document/{documentId}")
    public Map<String, Object> downloadDocument(
            @PathVariable String agentId,
            @PathVariable String documentId) {
        // 记录下载操作审计
        auditHelper.logFileOperation(agentId, "DOWNLOAD", documentId,
                "Agent: " + agentId + ", DocId: " + documentId, true, null);
        return sideClientSyncService.downloadDocument(documentId);
    }

    // ==================== 检查点接口 ====================

    @Operation(summary = "获取最新检查点", description = "获取最后同步的检查点时间")
    @GetMapping("/client/{agentId}/checkpoint")
    public Map<String, Object> getCheckpoint(@PathVariable String agentId) {
        LocalDateTime checkpoint = sideClientSyncService.getLatestCheckpoint(agentId);
        Map<String, Object> result = new HashMap<>();
        result.put("agentId", agentId);
        result.put("checkpoint", checkpoint != null ? checkpoint.toString() : null);
        return result;
    }

    @Operation(summary = "标记变更已同步")
    @PostMapping("/client/{agentId}/mark-synced")
    public Map<String, Object> markSynced(@PathVariable String agentId) {
        sideClientSyncService.markChangesSynced(agentId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Changes marked as synced");
        return result;
    }

    // ==================== 手动同步接口 ====================

    @Operation(summary = "触发全量同步", description = "手动触发客户端全量同步")
    @PostMapping("/client/{agentId}/sync/full")
    public Map<String, Object> triggerFullSync(@PathVariable String agentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 全量同步：导出全部文档
            var exportResult = sideClientSyncService.exportDocuments(agentId, null, 0, 1000);
            result.put("success", true);
            result.put("message", "全量同步已触发");
            result.put("exportedCount", exportResult.getOrDefault("totalCount", 0));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "全量同步失败: " + e.getMessage());
        }
        return result;
    }

    @Operation(summary = "触发增量同步", description = "手动触发客户端增量同步")
    @PostMapping("/client/{agentId}/sync/incremental")
    public Map<String, Object> triggerIncrementalSync(@PathVariable String agentId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 增量同步：获取待同步变更
            var pendingChanges = sideClientSyncService.getPendingChanges(agentId);
            result.put("success", true);
            result.put("message", "增量同步已触发");
            result.put("pendingCount", pendingChanges.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "增量同步失败: " + e.getMessage());
        }
        return result;
    }

    // ==================== Webhook 配置接口 ====================

    @Operation(summary = "保存Webhook配置")
    @PostMapping("/client/{agentId}/webhook")
    public Map<String, Object> saveWebhook(
            @PathVariable String agentId,
            @RequestBody SyncWebhook webhook) {
        webhook.setAgentId(agentId);
        SyncWebhook saved = sideClientSyncService.saveWebhook(webhook);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("webhook", saved);
        return result;
    }

    @Operation(summary = "删除Webhook配置")
    @DeleteMapping("/client/webhook/{webhookId}")
    public Map<String, Object> deleteWebhook(@PathVariable Long webhookId) {
        sideClientSyncService.deleteWebhook(webhookId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Webhook deleted");
        return result;
    }

    @Operation(summary = "重新启用已失效的Webhook")
    @PostMapping("/client/webhook/{webhookId}/reenable")
    public Map<String, Object> reenableWebhook(@PathVariable Long webhookId) {
        SyncWebhook reenabled = sideClientSyncService.reenableWebhook(webhookId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("webhook", reenabled);
        return result;
    }

    @Operation(summary = "获取Webhook配置列表")
    @GetMapping("/client/{agentId}/webhooks")
    public List<SyncWebhook> getWebhooks(@PathVariable String agentId) {
        return sideClientSyncService.getWebhooks(agentId);
    }
}
