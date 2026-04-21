package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.SyncRecordRepository;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.settings.service.SystemDifyConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dify 知识库同步服务（系统级）
 * 使用系统统一的 Dify 连接配置，不依赖 AgentAccessProfile
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyKnowledgeSyncService {

    private final DifyApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final SyncChangeLogRepository changeLogRepository;
    private final SystemDifyConfigProvider difyConfigProvider;

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

    private void recordChange(String documentId, String knowledgeBaseId,
                              String changeType, Integer version, String contentHash) {
        SyncChangeLog changeLog = SyncChangeLog.builder()
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
     * 触发知识库同步（系统级）
     */
    @Transactional
    public SyncResult syncKnowledgeBases(boolean fullSync) {
        log.info("Starting knowledge base sync, fullSync={}", fullSync);

        if (!difyConfigProvider.isActive()) {
            return SyncResult.failure("Dify 未启用或配置不完整，请先在「数据同步 > Dify 同步」中启用并保存 API 地址和 Key");
        }

        String endpoint = difyConfigProvider.getApiUrl();
        String apiKey   = difyConfigProvider.getApiKey();

        SyncRecord syncRecord = new SyncRecord();
        syncRecord.setSyncType(fullSync ? "FULL" : "INCREMENTAL");
        syncRecord.setDirection("PULL");
        syncRecord.setSyncDirection("INBOUND");
        syncRecord.setStartTime(LocalDateTime.now());
        syncRecord.setStatus("RUNNING");

        try {
            List<DifyDataset> difyDatasets = difyApiClient.listDatasets(endpoint, apiKey);
            if (difyDatasets.isEmpty()) {
                syncRecord.setStatus("COMPLETED");
                syncRecord.setEndTime(LocalDateTime.now());
                syncRecordRepository.save(syncRecord);
                return SyncResult.success("No datasets found in Dify", 0, 0, 0);
            }

            LocalDateTime lastSyncTime = fullSync ? null : syncRecordRepository
                    .findTopByAgentIdOrderByEndTimeDesc(null)
                    .map(SyncRecord::getEndTime)
                    .orElse(null);

            int added = 0, updated = 0, deleted = 0;

            for (DifyDataset difyKb : difyDatasets) {
                var result = syncSingleDataset(difyKb, endpoint, apiKey, lastSyncTime);
                added += result.getAdded();
                updated += result.getUpdated();
                deleted += result.getDeleted();
            }

            if (!fullSync && lastSyncTime != null) {
                List<String> difyKbIds = difyDatasets.stream()
                        .map(DifyDataset::getId)
                        .collect(Collectors.toList());
                for (KnowledgeBase localKb : knowledgeBaseRepository.findAll()) {
                    if (!difyKbIds.contains(localKb.getId())) {
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
            if (syncRecord.getStartTime() != null) {
                long durationMs = java.time.Duration.between(syncRecord.getStartTime(), syncRecord.getEndTime()).toMillis();
                syncRecord.setDurationMs(durationMs);
            }
            syncRecordRepository.save(syncRecord);

            log.info("Sync completed: added={}, updated={}, deleted={}", added, updated, deleted);
            return SyncResult.success("Sync completed", added, updated, deleted);

        } catch (Exception e) {
            log.error("Sync failed", e);
            syncRecord.setStatus("FAILED");
            syncRecord.setErrorMessage(e.getMessage());
            syncRecord.setEndTime(LocalDateTime.now());
            syncRecordRepository.save(syncRecord);
            return SyncResult.failure("Sync failed: " + e.getMessage());
        }
    }

    private DatasetSyncResult syncSingleDataset(DifyDataset difyKb, String endpoint, String apiKey,
                                                 LocalDateTime lastSyncTime) {
        int added = 0, updated = 0;

        Optional<KnowledgeBase> existingKb = knowledgeBaseRepository.findById(difyKb.getId());
        KnowledgeBase kb;
        if (existingKb.isPresent()) {
            kb = existingKb.get();
            if (lastSyncTime != null && kb.getSyncTime() != null
                    && kb.getSyncTime().isAfter(lastSyncTime)) {
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
                    .createdAt(LocalDateTime.now())
                    .syncTime(LocalDateTime.now())
                    .build();
            added++;
        }
        knowledgeBaseRepository.save(kb);

        List<DifyDocument> difyDocs = difyApiClient.listDocuments(endpoint, apiKey, difyKb.getId());
        Set<String> currentDocIds = new HashSet<>();

        for (DifyDocument difyDoc : difyDocs) {
            currentDocIds.add(difyDoc.getId());
            Optional<KnowledgeDocument> existingDoc = documentRepository.findById(difyDoc.getId());

            if (existingDoc.isPresent()) {
                KnowledgeDocument doc = existingDoc.get();
                String content = difyApiClient.getDocumentContent(endpoint, apiKey, difyKb.getId(), difyDoc.getId());
                String newHash = generateContentHash(content);

                if (!newHash.equals(doc.getContentHash())) {
                    doc.setFileName(difyDoc.getName());
                    doc.setFileSize((long) (content.length() * 2));
                    doc.setContentHash(newHash);
                    doc.setVersion(doc.getVersion() + 1);
                    doc.setLastModified(LocalDateTime.now());
                    documentRepository.save(doc);
                    recordChange(doc.getId(), kb.getId(), "UPDATED", doc.getVersion(), newHash);
                    updated++;
                }
            } else {
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
                recordChange(doc.getId(), kb.getId(), "ADDED", 1, contentHash);
                added++;
            }
        }

        List<KnowledgeDocument> allDocs = documentRepository.findByKnowledgeBaseIdOrderByUploadTimeDesc(kb.getId());
        for (KnowledgeDocument doc : allDocs) {
            if (!currentDocIds.contains(doc.getId()) && !Boolean.TRUE.equals(doc.getDeletedFlag())) {
                doc.setDeletedFlag(true);
                doc.setVersion(doc.getVersion() + 1);
                doc.setLastModified(LocalDateTime.now());
                documentRepository.save(doc);
                recordChange(doc.getId(), kb.getId(), "DELETED", doc.getVersion(), doc.getContentHash());
            }
        }

        return new DatasetSyncResult(added, updated, 0);
    }

    public List<SyncRecord> getSyncHistory(int limit) {
        return syncRecordRepository.findTopByAgentIdOrderByEndTimeDesc(null,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public boolean testConnection(String endpoint, String apiKey) {
        return difyApiClient.testConnection(endpoint, apiKey);
    }

    // ==================== DTO ====================

    @lombok.Data
    @lombok.Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class DifyDataset {
        private String id;
        private String name;
        private String description;
        private Integer documentCount;
        private String agentId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @lombok.Data
    @lombok.Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class DifyDocument {
        private String documentId;
        private String knowledgeBaseId;
        private String status;
        private Integer wordCount;
        private String id;
        private String name;
        private String type;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @lombok.Data
    @lombok.Builder
    @NoArgsConstructor @AllArgsConstructor
    public static class SyncResult {
        private boolean success;
        private String message;
        private int added;
        private int updated;
        private int deleted;

        public static SyncResult success(String message, int added, int updated, int deleted) {
            return SyncResult.builder().success(true).message(message)
                    .added(added).updated(updated).deleted(deleted).build();
        }

        public static SyncResult failure(String message) {
            return SyncResult.builder().success(false).message(message).build();
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class DatasetSyncResult {
        private int added;
        private int updated;
        private int deleted;
    }
}
