package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.*;
import com.adlin.orin.modules.knowledge.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 端侧知识库同步服务
 * 提供标准接口供端侧客户端同步知识库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SideClientSyncService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final SyncChangeLogRepository changeLogRepository;
    private final SyncWebhookRepository webhookRepository;
    private final ObjectMapper objectMapper;

    @Value("${knowledge.sync.webhook.timeout:5000}")
    private int webhookTimeout;

    private final RestTemplate restTemplate = new RestTemplate();

    // ==================== 变更查询接口 ====================

    /**
     * 查询变更记录 (按时间戳或版本号)
     *
     * @param agentId     Agent ID
     * @param startTime   开始时间
     * @param endTime     结束时间
     * @param page        页码
     * @param size        每页大小
     * @return 变更记录分页
     */
    public Page<Map<String, Object>> getChanges(String agentId, LocalDateTime startTime,
                                                  LocalDateTime endTime, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SyncChangeLog> changeLogs;

        if (startTime != null && endTime != null) {
            changeLogs = changeLogRepository.findByAgentIdAndChangedAtBetweenOrderByChangedAtDesc(
                    agentId, startTime, endTime, pageable);
        } else {
            changeLogs = changeLogRepository.findByAgentIdOrderByChangedAtDesc(agentId, pageable);
        }

        return changeLogs.map(this::toChangeMap);
    }

    /**
     * 获取待同步变更
     *
     * @param agentId Agent ID
     * @return 待同步变更列表
     */
    public List<Map<String, Object>> getPendingChanges(String agentId) {
        List<SyncChangeLog> pendingChanges = changeLogRepository
                .findByAgentIdAndSyncedFalseOrderByChangedAtAsc(agentId);

        return pendingChanges.stream()
                .map(this::toChangeMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取未同步变更数量
     *
     * @param agentId Agent ID
     * @return 未同步变更数量
     */
    public long getPendingChangeCount(String agentId) {
        return changeLogRepository.countByAgentIdAndSyncedFalse(agentId);
    }

    // ==================== 批量导出接口 ====================

    /**
     * 批量导出 (全量首同步)
     *
     * @param agentId     Agent ID
     * @param knowledgeBaseId 知识库ID (可选，为空则导出所有)
     * @param page        页码
     * @param size        每页大小
     * @return 文档列表
     */
    public Map<String, Object> exportDocuments(String agentId, String knowledgeBaseId, int page, int size) {
        // 获取该Agent关联的知识库
        List<KnowledgeBase> knowledgeBases;
        if (knowledgeBaseId != null && !knowledgeBaseId.isEmpty()) {
            knowledgeBases = knowledgeBaseRepository.findBySourceAgentId(agentId).stream()
                    .filter(kb -> kb.getId().equals(knowledgeBaseId))
                    .collect(Collectors.toList());
        } else {
            knowledgeBases = knowledgeBaseRepository.findBySourceAgentId(agentId);
        }

        if (knowledgeBases.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("documents", Collections.emptyList());
            result.put("total", 0);
            result.put("page", page);
            result.put("size", size);
            return result;
        }

        List<String> kbIds = knowledgeBases.stream()
                .map(KnowledgeBase::getId)
                .collect(Collectors.toList());

        // 分页查询文档
        Pageable pageable = PageRequest.of(page, size);
        Page<KnowledgeDocument> documents = documentRepository.findByKnowledgeBaseIdIn(kbIds, pageable);

        // 构建导出数据
        List<Map<String, Object>> docList = documents.getContent().stream()
                .map(this::toExportDocument)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("documents", docList);
        result.put("total", documents.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", documents.getTotalPages());
        result.put("checkpoint", LocalDateTime.now().toString());

        // 更新同步记录
        saveSyncRecord(agentId, "FULL", docList.size(), "INBOUND");

        return result;
    }

    // ==================== 文档下载接口 ====================

    /**
     * 按文档ID下载内容与元数据
     *
     * @param documentId 文档ID
     * @return 文档内容和元数据
     */
    public Map<String, Object> downloadDocument(String documentId) {
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(documentId);

        if (docOpt.isEmpty()) {
            throw new RuntimeException("Document not found: " + documentId);
        }

        KnowledgeDocument doc = docOpt.get();
        return toFullDocument(doc);
    }

    // ==================== Webhook 通知 ====================

    /**
     * 发送Webhook通知
     */
    @Transactional
    public void sendWebhookNotification(String agentId, String eventType, Map<String, Object> data) {
        List<SyncWebhook> webhooks = webhookRepository.findByAgentIdAndEnabledTrue(agentId);

        for (SyncWebhook webhook : webhooks) {
            // 检查事件类型是否匹配
            if (!isEventEnabled(webhook.getEventTypes(), eventType)) {
                continue;
            }

            // 检查 webhook 是否已失效
            if (Boolean.TRUE.equals(webhook.getDisabled())) {
                log.warn("Webhook {} is disabled, skipping", webhook.getWebhookUrl());
                continue;
            }

            try {
                sendWebhook(webhook, eventType, data);
                // 成功后重置失败计数
                webhook.setFailureCount(0);
                webhook.setLastFailureTime(null);
                webhook.setLastFailureReason(null);
                webhookRepository.save(webhook);
            } catch (Exception e) {
                log.error("Failed to send webhook to {}: {}", webhook.getWebhookUrl(), e.getMessage());
                handleWebhookFailure(webhook, e.getMessage());
            }
        }
    }

    /**
     * 处理 webhook 失败，增加失败计数并在连续失败3次后标记为失效
     */
    @Transactional
    private void handleWebhookFailure(SyncWebhook webhook, String errorMsg) {
        int failureCount = webhook.getFailureCount() != null ? webhook.getFailureCount() : 0;
        webhook.setFailureCount(failureCount + 1);
        webhook.setLastFailureTime(LocalDateTime.now());
        webhook.setLastFailureReason(errorMsg != null && errorMsg.length() > 500
                ? errorMsg.substring(0, 500) : errorMsg);

        // 连续失败3次以上标记为失效
        if (failureCount >= 2) { // 已经失败2次，这是第3次失败
            webhook.setDisabled(true);
            log.warn("Webhook {} has failed {} times, disabling it",
                    webhook.getWebhookUrl(), failureCount + 1);
        }

        webhookRepository.save(webhook);
    }

    /**
     * 重新启用已失效的 webhook
     */
    @Transactional
    public SyncWebhook reenableWebhook(Long webhookId) {
        SyncWebhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new RuntimeException("Webhook not found: " + webhookId));
        webhook.setDisabled(false);
        webhook.setFailureCount(0);
        webhook.setLastFailureTime(null);
        webhook.setLastFailureReason(null);
        return webhookRepository.save(webhook);
    }

    /**
     * 保存Webhook配置
     */
    @Transactional
    public SyncWebhook saveWebhook(SyncWebhook webhook) {
        return webhookRepository.save(webhook);
    }

    /**
     * 删除Webhook配置
     */
    @Transactional
    public void deleteWebhook(Long webhookId) {
        webhookRepository.deleteById(webhookId);
    }

    /**
     * 获取Webhook配置列表
     */
    public List<SyncWebhook> getWebhooks(String agentId) {
        return webhookRepository.findByAgentIdAndEnabledTrue(agentId);
    }

    // ==================== 内部方法 ====================

    /**
     * 记录变更日志
     */
    @Transactional
    public void recordChange(String agentId, String documentId, String knowledgeBaseId,
                              String changeType, Integer version, String contentHash) {
        // 生成幂等键，避免同一变更重复记录
        String idempotencyKey = String.format("%s_%s_%s", agentId, documentId, changeType);

        // 如果幂等键已存在且未同步，跳过
        if (changeLogRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.debug("Change already recorded for idempotency key: {}", idempotencyKey);
            return;
        }

        SyncChangeLog changeLog = SyncChangeLog.builder()
                .agentId(agentId)
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .changeType(changeType)
                .version(version)
                .contentHash(contentHash)
                .changedAt(LocalDateTime.now())
                .synced(false)
                .idempotencyKey(idempotencyKey)
                .build();

        changeLogRepository.save(changeLog);

        // 触发Webhook
        String eventType = mapChangeTypeToEvent(changeType);
        Map<String, Object> data = new HashMap<>();
        data.put("documentId", documentId);
        data.put("knowledgeBaseId", knowledgeBaseId);
        data.put("changeType", changeType);
        data.put("version", version);
        data.put("timestamp", LocalDateTime.now().toString());

        sendWebhookNotification(agentId, eventType, data);
    }

    /**
     * 标记变更已同步
     */
    @Transactional
    public void markChangesSynced(String agentId) {
        changeLogRepository.markAllSynced(agentId);
    }

    /**
     * 获取最新同步检查点
     */
    public LocalDateTime getLatestCheckpoint(String agentId) {
        return changeLogRepository.findLatestChangeTime(agentId);
    }

    // ==================== 冲突处理 ====================

    /**
     * 检测冲突
     * 比较客户端内容哈希与服务端内容哈希
     *
     * @param agentId     Agent ID
     * @param documentId  文档ID
     * @param clientHash 客户端内容哈希
     * @return 冲突结果
     */
    public ConflictResult detectConflict(String agentId, String documentId, String clientHash) {
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(documentId);

        if (docOpt.isEmpty()) {
            // 文档不存在，可能是被删除
            return new ConflictResult(true, ConflictType.DELETED_ON_SERVER,
                    "Document does not exist on server", null);
        }

        KnowledgeDocument serverDoc = docOpt.get();

        // 验证文档属于该 Agent
        if (!belongsToAgent(serverDoc, agentId)) {
            return new ConflictResult(true, ConflictType.UNAUTHORIZED,
                    "Document does not belong to this agent", null);
        }

        String serverHash = serverDoc.getContentHash();
        if (serverHash == null) {
            return new ConflictResult(false, null, null, serverDoc);
        }

        if (!serverHash.equals(clientHash)) {
            // 哈希不一致，发生冲突
            return new ConflictResult(true, ConflictType.CONTENT_MISMATCH,
                    "Content hash mismatch - server: " + serverHash + ", client: " + clientHash,
                    serverDoc);
        }

        return new ConflictResult(false, null, null, serverDoc);
    }

    /**
     * 解决冲突 - 服务端胜出
     */
    public ConflictResult resolveConflictServerWins(String agentId, String documentId) {
        KnowledgeDocument serverDoc = documentRepository.findById(documentId)
                .filter(doc -> belongsToAgent(doc, agentId))
                .orElseThrow(() -> new RuntimeException("Document not found or unauthorized: " + documentId));

        return new ConflictResult(false, null, "Server version retained", serverDoc);
    }

    /**
     * 解决冲突 - 客户端胜出（强制覆盖服务端）
     */
    @Transactional
    public ConflictResult resolveConflictClientWins(String agentId, String documentId,
                                                     Map<String, Object> clientDoc) {
        KnowledgeDocument serverDoc = documentRepository.findById(documentId)
                .filter(doc -> belongsToAgent(doc, agentId))
                .orElseThrow(() -> new RuntimeException("Document not found or unauthorized: " + documentId));

        // 更新服务端文档
        if (clientDoc.containsKey("content")) {
            serverDoc.setContentPreview((String) clientDoc.get("content"));
        }
        if (clientDoc.containsKey("version")) {
            serverDoc.setVersion((Integer) clientDoc.get("version"));
        }

        documentRepository.save(serverDoc);

        // 记录变更
        recordChange(agentId, documentId, serverDoc.getKnowledgeBaseId(),
                "UPDATED", serverDoc.getVersion(), serverDoc.getContentHash());

        return new ConflictResult(false, null, "Client version applied", serverDoc);
    }

    /**
     * 解决冲突 - 合并（保留两端变更）
     */
    @Transactional
    public ConflictResult resolveConflictMerge(String agentId, String documentId,
                                               String clientContent) {
        KnowledgeDocument serverDoc = documentRepository.findById(documentId)
                .filter(doc -> belongsToAgent(doc, agentId))
                .orElseThrow(() -> new RuntimeException("Document not found or unauthorized: " + documentId));

        // 简单合并策略：客户端内容 + 服务端元数据
        String mergedContent = clientContent + "\n\n[Server metadata: " +
                "version=" + serverDoc.getVersion() + ", lastModified=" + serverDoc.getLastModified() + "]";

        serverDoc.setContentPreview(mergedContent);
        serverDoc.setVersion(serverDoc.getVersion() + 1);

        documentRepository.save(serverDoc);

        recordChange(agentId, documentId, serverDoc.getKnowledgeBaseId(),
                "UPDATED", serverDoc.getVersion(), serverDoc.getContentHash());

        return new ConflictResult(false, null, "Merged successfully", serverDoc);
    }

    /**
     * 冲突结果
     */
    public record ConflictResult(
            boolean hasConflict,
            ConflictType conflictType,
            String message,
            KnowledgeDocument serverDocument
    ) {}

    /**
     * 冲突类型
     */
    public enum ConflictType {
        CONTENT_MISMATCH,  // 内容不一致
        DELETED_ON_SERVER, // 服务端已删除
        DELETED_ON_CLIENT, // 客户端已删除
        UNAUTHORIZED       // 无权限
    }

    // ==================== 权限校验 ====================

    /**
     * 验证 Agent 是否有权访问知识库
     */
    public boolean validateAgentAccess(String agentId, String knowledgeBaseId) {
        List<KnowledgeBase> agentKBs = knowledgeBaseRepository.findBySourceAgentId(agentId);
        return agentKBs.stream().anyMatch(kb -> kb.getId().equals(knowledgeBaseId));
    }

    /**
     * 验证 Agent 是否有权访问文档
     */
    public boolean validateDocumentAccess(String agentId, String documentId) {
        Optional<KnowledgeDocument> docOpt = documentRepository.findById(documentId);
        if (docOpt.isEmpty()) {
            return false;
        }
        return belongsToAgent(docOpt.get(), agentId);
    }

    /**
     * 检查文档是否属于该 Agent 的知识库
     */
    private boolean belongsToAgent(KnowledgeDocument doc, String agentId) {
        if (doc.getKnowledgeBaseId() == null) {
            return false;
        }
        return validateAgentAccess(agentId, doc.getKnowledgeBaseId());
    }

    // ==================== 同步记录查询 ====================

    /**
     * 获取同步历史记录
     */
    public List<SyncRecord> getSyncHistory(String agentId, int limit) {
        return syncRecordRepository.findByAgentIdOrderByStartTimeDesc(agentId)
                .stream().limit(limit).toList();
    }

    /**
     * 获取同步状态摘要
     */
    public SyncStatusSummary getSyncStatusSummary(String agentId) {
        List<SyncRecord> recentRecords = getSyncHistory(agentId, 10);

        long totalSyncs = recentRecords.size();
        long successfulSyncs = recentRecords.stream()
                .filter(r -> "COMPLETED".equals(r.getStatus()) || "SUCCESS".equals(r.getStatus()))
                .count();
        long failedSyncs = recentRecords.stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .count();
        long pendingChanges = changeLogRepository.countByAgentIdAndSyncedFalse(agentId);

        return new SyncStatusSummary(
                agentId,
                totalSyncs,
                successfulSyncs,
                failedSyncs,
                pendingChanges,
                recentRecords.isEmpty() ? null : recentRecords.get(0).getStartTime()
        );
    }

    /**
     * 同步状态摘要
     */
    public record SyncStatusSummary(
            String agentId,
            long totalSyncs,
            long successfulSyncs,
            long failedSyncs,
            long pendingChanges,
            LocalDateTime lastSyncTime
    ) {}

    private Map<String, Object> toChangeMap(SyncChangeLog changeLog) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", changeLog.getId());
        map.put("documentId", changeLog.getDocumentId());
        map.put("knowledgeBaseId", changeLog.getKnowledgeBaseId());
        map.put("changeType", changeLog.getChangeType());
        map.put("version", changeLog.getVersion());
        map.put("contentHash", changeLog.getContentHash());
        map.put("changedAt", changeLog.getChangedAt().toString());
        map.put("synced", changeLog.getSynced());
        return map;
    }

    private Map<String, Object> toExportDocument(KnowledgeDocument doc) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", doc.getId());
        map.put("knowledgeBaseId", doc.getKnowledgeBaseId());
        map.put("fileName", doc.getFileName());
        map.put("fileType", doc.getFileType());
        map.put("fileSize", doc.getFileSize());
        map.put("version", doc.getVersion());
        map.put("contentHash", doc.getContentHash());
        map.put("deletedFlag", doc.getDeletedFlag());
        map.put("uploadTime", doc.getUploadTime() != null ? doc.getUploadTime().toString() : null);
        map.put("lastModified", doc.getLastModified() != null ? doc.getLastModified().toString() : null);
        map.put("metadata", doc.getMetadata());
        return map;
    }

    private Map<String, Object> toFullDocument(KnowledgeDocument doc) {
        Map<String, Object> map = toExportDocument(doc);
        map.put("contentPreview", doc.getContentPreview());
        map.put("storagePath", doc.getStoragePath());
        map.put("parsedTextPath", doc.getParsedTextPath());
        map.put("vectorStatus", doc.getVectorStatus());
        map.put("chunkCount", doc.getChunkCount());
        map.put("charCount", doc.getCharCount());
        map.put("chunkMethod", doc.getChunkMethod());
        map.put("chunkSize", doc.getChunkSize());
        map.put("chunkOverlap", doc.getChunkOverlap());
        return map;
    }

    private void saveSyncRecord(String agentId, String syncType, int totalDocs, String direction) {
        SyncRecord record = SyncRecord.builder()
                .agentId(agentId)
                .syncType(syncType)
                .direction(direction)
                .syncDirection(direction)
                .status("COMPLETED")
                .startTime(LocalDateTime.now())
                .endTime(LocalDateTime.now())
                .totalDocs(totalDocs)
                .addedCount(totalDocs)
                .build();

        syncRecordRepository.save(record);
    }

    private boolean isEventEnabled(String eventTypes, String eventType) {
        if (eventTypes == null || eventTypes.isEmpty()) {
            return false;
        }
        List<String> enabled = Arrays.asList(eventTypes.split(","));
        return enabled.contains(eventType) || enabled.contains("*");
    }

    private String mapChangeTypeToEvent(String changeType) {
        return switch (changeType) {
            case "ADDED" -> "document_added";
            case "UPDATED" -> "document_updated";
            case "DELETED" -> "document_deleted";
            default -> "document_changed";
        };
    }

    private void sendWebhook(SyncWebhook webhook, String eventType, Map<String, Object> data) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 添加签名
        if (webhook.getWebhookSecret() != null && !webhook.getWebhookSecret().isEmpty()) {
            String payload = toJson(data);
            String signature = generateSignature(payload, webhook.getWebhookSecret());
            headers.set("X-Webhook-Signature", signature);
        }

        headers.set("X-Webhook-Event", eventType);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    webhook.getWebhookUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook sent successfully to {}", webhook.getWebhookUrl());
            } else {
                log.warn("Webhook returned non-2xx: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send webhook: {}", e.getMessage());
            throw e;
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String generateSignature(String payload, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = payload + secret;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
