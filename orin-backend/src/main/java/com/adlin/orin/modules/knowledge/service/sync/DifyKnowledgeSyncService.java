package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.SyncRecordRepository;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.knowledge.component.EmbeddingService;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
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
 * Dify 知识库同步服务
 * 支持完整同步、增量同步、双向同步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyKnowledgeSyncService {

    private final DifyApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final AgentAccessProfileRepository profileRepository;
    private final EmbeddingService embeddingService;
    private final SyncChangeLogRepository changeLogRepository;

    private static final int MAX_RETRIES = 3;

    /**
     * 生成内容hash
     */
    private String generateContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * 记录变更日志
     */
    private void recordChange(String agentId, String documentId, String knowledgeBaseId,
                              String changeType, Integer version, String contentHash) {
        SyncChangeLog changeLog = SyncChangeLog.builder()
                .agentId(agentId)
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .changeType(changeType)
                .version(version)
                .contentHash(contentHash)
                .changedAt(LocalDateTime.now())
                .synced(false)
                .build();
        changeLogRepository.save(changeLog);
    }

    /**
     * 触发知识库同步
     */
    @Transactional
    public SyncResult syncKnowledgeBases(String agentId, boolean fullSync) {
        log.info("Starting knowledge base sync for agent: {}, fullSync: {}", agentId, fullSync);

        var profileOpt = profileRepository.findById(agentId);
        if (profileOpt.isEmpty()) {
            return SyncResult.failure("Agent not found: " + agentId);
        }

        var profile = profileOpt.get();
        if (profile.getDatasetApiKey() == null || profile.getDatasetApiKey().isEmpty()) {
            return SyncResult.failure("No dataset API key configured for agent: " + agentId);
        }

        String endpoint = profile.getEndpointUrl();
        String apiKey = profile.getDatasetApiKey();

        SyncRecord syncRecord = new SyncRecord();
        syncRecord.setAgentId(agentId);
        syncRecord.setSyncType(fullSync ? "FULL" : "INCREMENTAL");
        syncRecord.setDirection("PULL");
        syncRecord.setSyncDirection("INBOUND");
        syncRecord.setStartTime(LocalDateTime.now());
        syncRecord.setStatus("RUNNING");

        try {
            // 1. 获取 Dify 知识库列表
            List<DifyDataset> difyDatasets = difyApiClient.listDatasets(endpoint, apiKey);
            if (difyDatasets.isEmpty()) {
                syncRecord.setStatus("COMPLETED");
                syncRecord.setEndTime(LocalDateTime.now());
                syncRecordRepository.save(syncRecord);
                return SyncResult.success("No datasets found in Dify", 0, 0, 0);
            }

            // 2. 获取上次同步时间
            LocalDateTime lastSyncTime = fullSync ? null : syncRecordRepository
                    .findTopByAgentIdOrderByEndTimeDesc(agentId)
                    .map(SyncRecord::getEndTime)
                    .orElse(null);

            // 3. 同步每个知识库
            int added = 0, updated = 0, deleted = 0;

            for (DifyDataset difyKb : difyDatasets) {
                var result = syncSingleDataset(agentId, difyKb, endpoint, apiKey, lastSyncTime);
                added += result.getAdded();
                updated += result.getUpdated();
                deleted += result.getDeleted();
            }

            // 4. 处理已删除的知识库（增量同步时）
            if (!fullSync && lastSyncTime != null) {
                List<String> difyKbIds = difyDatasets.stream()
                        .map(DifyDataset::getId)
                        .collect(Collectors.toList());

                List<KnowledgeBase> localKbs = knowledgeBaseRepository.findBySourceAgentId(agentId);
                for (KnowledgeBase localKb : localKbs) {
                    if (!difyKbIds.contains(localKb.getId())) {
                        // Dify 上已删除
                        localKb.setStatus("DISABLED");
                        knowledgeBaseRepository.save(localKb);
                        deleted++;
                    }
                }
            }

            syncRecord.setStatus("COMPLETED");
            syncRecord.setAddedCount(added);
            syncRecord.setUpdatedCount(updated);
            syncRecord.setDeletedCount(deleted);
            syncRecord.setTotalDocs(added + updated);
            syncRecord.setEndTime(LocalDateTime.now());
            // 计算同步耗时
            if (syncRecord.getStartTime() != null) {
                long durationMs = java.time.Duration.between(syncRecord.getStartTime(), syncRecord.getEndTime()).toMillis();
                syncRecord.setDurationMs(durationMs);
            }
            syncRecordRepository.save(syncRecord);

            log.info("Sync completed: added={}, updated={}, deleted={}", added, updated, deleted);
            return SyncResult.success("Sync completed", added, updated, deleted);

        } catch (Exception e) {
            log.error("Sync failed for agent: {}", agentId, e);
            syncRecord.setStatus("FAILED");
            syncRecord.setErrorMessage(e.getMessage());
            syncRecord.setEndTime(LocalDateTime.now());
            syncRecordRepository.save(syncRecord);
            return SyncResult.failure("Sync failed: " + e.getMessage());
        }
    }

    /**
     * 同步单个知识库
     */
    private DatasetSyncResult syncSingleDataset(String agentId, DifyDataset difyKb, String endpoint, String apiKey, 
                                                 LocalDateTime lastSyncTime) {
        int added = 0, updated = 0;

        // 检查知识库是否已存在
        Optional<KnowledgeBase> existingKb = knowledgeBaseRepository.findById(difyKb.getId());

        KnowledgeBase kb;
        if (existingKb.isPresent()) {
            kb = existingKb.get();
            // 增量同步时检查是否需要更新
            if (lastSyncTime != null && kb.getSyncTime() != null 
                    && kb.getSyncTime().isAfter(lastSyncTime)) {
                log.debug("Skipping KB {} - no changes since last sync", difyKb.getId());
                return new DatasetSyncResult(0, 0, 0);
            }
            kb.setName(difyKb.getName());
            kb.setDescription(difyKb.getDescription());
            kb.setDocCount(difyKb.getDocumentCount());
            kb.setSyncTime(LocalDateTime.now());
        } else {
            kb = KnowledgeBase.builder()
                    .id(difyKb.getId())
                    .name(difyKb.getName())
                    .description(difyKb.getDescription())
                    .docCount(difyKb.getDocumentCount())
                    .totalSizeMb(0.0)
                    .status("ENABLED")
                    .sourceAgentId(difyKb.getAgentId() != null ? difyKb.getAgentId() : null)
                    .createdAt(LocalDateTime.now())
                    .syncTime(LocalDateTime.now())
                    .build();
            added++;
        }

        knowledgeBaseRepository.save(kb);

        // 同步文档
        List<DifyDocument> difyDocs = difyApiClient.listDocuments(endpoint, apiKey, difyKb.getId());
        Set<String> currentDocIds = new HashSet<>();

        for (DifyDocument difyDoc : difyDocs) {
            currentDocIds.add(difyDoc.getId());
            Optional<KnowledgeDocument> existingDoc = documentRepository.findById(difyDoc.getId());

            if (existingDoc.isPresent()) {
                // 检查是否需要更新（版本控制）
                KnowledgeDocument doc = existingDoc.get();
                String content = difyApiClient.getDocumentContent(endpoint, apiKey, difyKb.getId(), difyDoc.getId());
                String newHash = generateContentHash(content);

                if (!newHash.equals(doc.getContentHash())) {
                    // 内容有变化，更新文档
                    doc.setFileName(difyDoc.getName());
                    doc.setFileSize((long) (content.length() * 2));
                    doc.setContentHash(newHash);
                    doc.setVersion(doc.getVersion() + 1);
                    doc.setLastModified(LocalDateTime.now());
                    documentRepository.save(doc);

                    // 记录变更日志
                    recordChange(agentId, doc.getId(), kb.getId(), "UPDATED", doc.getVersion(), newHash);
                    updated++;
                }
            } else {
                // 下载文档内容
                String content = difyApiClient.getDocumentContent(endpoint, apiKey, difyKb.getId(), difyDoc.getId());
                String contentHash = generateContentHash(content);

                KnowledgeDocument doc = KnowledgeDocument.builder()
                        .id(difyDoc.getId())
                        .knowledgeBaseId(kb.getId())
                        .fileName(difyDoc.getName())
                        .fileSize((long) (content.length() * 2))
                        .fileType(difyDoc.getType())
                        .contentHash(contentHash)
                        .version(1)
                        .deletedFlag(false)
                        .vectorStatus("PENDING")
                        .chunkCount(0)
                        .uploadTime(LocalDateTime.now())
                        .lastModified(LocalDateTime.now())
                        .build();
                documentRepository.save(doc);

                // 记录变更日志
                recordChange(agentId, doc.getId(), kb.getId(), "ADDED", 1, contentHash);
                added++;
            }
        }

        // 处理已删除的文档（软删除）
        List<KnowledgeDocument> allDocs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kb.getId());
        for (KnowledgeDocument doc : allDocs) {
            if (!currentDocIds.contains(doc.getId()) && !Boolean.TRUE.equals(doc.getDeletedFlag())) {
                doc.setDeletedFlag(true);
                doc.setVersion(doc.getVersion() + 1);
                doc.setLastModified(LocalDateTime.now());
                documentRepository.save(doc);

                // 记录变更日志
                recordChange(agentId, doc.getId(), kb.getId(), "DELETED", doc.getVersion(), doc.getContentHash());
            }
        }

        return new DatasetSyncResult(added, updated, 0);
    }

    /**
     * 获取同步状态
     */
    public List<SyncRecord> getSyncHistory(String agentId, int limit) {
        return syncRecordRepository.findTopByAgentIdOrderByEndTimeDesc(agentId, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * 测试 Dify 连接
     */
    public boolean testConnection(String endpoint, String apiKey) {
        return difyApiClient.testConnection(endpoint, apiKey);
    }

    // ==================== 内部类 ====================

    /**
     * Dify 知识库 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class DifyDataset {
        private String id;
        private String name;
        private String description;
        private Integer documentCount;
        private String agentId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * Dify 文档 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class DifyDocument {
        private String id;
        private String name;
        private String type;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    /**
     * 同步结果
     */
    @lombok.Data
    @lombok.Builder
    public static class SyncResult {
        private boolean success;
        private String message;
        private int added;
        private int updated;
        private int deleted;

        public static SyncResult success(String message, int added, int updated, int deleted) {
            return SyncResult.builder()
                    .success(true)
                    .message(message)
                    .added(added)
                    .updated(updated)
                    .deleted(deleted)
                    .build();
        }

        public static SyncResult failure(String message) {
            return SyncResult.builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }

    /**
     * 单个知识库同步结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DatasetSyncResult {
        private int added;
        private int updated;
        private int deleted;
    }
}
