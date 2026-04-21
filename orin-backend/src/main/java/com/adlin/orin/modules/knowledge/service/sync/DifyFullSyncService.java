package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.*;
import com.adlin.orin.modules.knowledge.repository.*;
import com.adlin.orin.modules.settings.service.SystemDifyConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dify 完整同步服务（系统级）
 * 使用系统统一的 Dify 连接配置，不依赖 AgentAccessProfile
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyFullSyncService {

    private final DifyFullApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final DifyAppRepository difyAppRepository;
    private final DifyWorkflowRepository difyWorkflowRepository;
    private final DifyConversationRepository difyConversationRepository;
    private final SystemDifyConfigProvider difyConfigProvider;

    /**
     * 完整同步所有内容
     */
    @Transactional
    public SyncResult fullSync() {
        log.info("Starting system-level full sync");

        if (!difyConfigProvider.isActive()) {
            return SyncResult.failure("Dify 未启用或配置不完整，请先在「数据同步 > Dify 同步」中启用并保存 API 地址和 Key");
        }

        String endpoint = difyConfigProvider.getApiUrl();
        String apiKey   = difyConfigProvider.getApiKey();

        SyncRecord syncRecord = new SyncRecord();
        syncRecord.setSyncType("FULL");
        syncRecord.setDirection("PULL");
        syncRecord.setSyncDirection("INBOUND");
        syncRecord.setStartTime(LocalDateTime.now());
        syncRecord.setStatus("RUNNING");

        try {
            Map<String, Object> syncData = difyApiClient.fullSync(endpoint, apiKey);

            int appsCount = 0, workflowsCount = 0, datasetsCount = 0, docsCount = 0;

            List<Map<String, Object>> apps = castList(syncData.get("apps"));
            if (apps != null) {
                appsCount = syncApps(apps);
                log.info("Synced {} apps", appsCount);
            }

            List<Map<String, Object>> workflows = castList(syncData.get("workflows"));
            if (workflows != null) {
                workflowsCount = syncWorkflows(workflows, endpoint, apiKey);
                log.info("Synced {} workflows", workflowsCount);
            }

            @SuppressWarnings("unchecked")
            List<DifyKnowledgeSyncService.DifyDataset> datasets =
                    (List<DifyKnowledgeSyncService.DifyDataset>) syncData.get("datasets");
            if (datasets != null) {
                datasetsCount = datasets.size();
                for (DifyKnowledgeSyncService.DifyDataset dataset : datasets) {
                    var result = syncDataset(dataset, endpoint, apiKey);
                    docsCount += result.getAdded() + result.getUpdated();
                }
            }

            syncRecord.setStatus("COMPLETED");
            syncRecord.setAddedCount(appsCount + workflowsCount + datasetsCount);
            syncRecord.setUpdatedCount(docsCount);
            syncRecord.setTotalDocs(datasetsCount + docsCount);
            syncRecord.setEndTime(LocalDateTime.now());
            syncRecordRepository.save(syncRecord);

            log.info("Full sync completed: apps={}, workflows={}, datasets={}, docs={}",
                    appsCount, workflowsCount, datasetsCount, docsCount);

            return SyncResult.success("Full sync completed", appsCount, workflowsCount, datasetsCount + docsCount);

        } catch (Exception e) {
            log.error("Full sync failed", e);
            syncRecord.setStatus("FAILED");
            syncRecord.setErrorMessage(e.getMessage());
            syncRecord.setEndTime(LocalDateTime.now());
            syncRecordRepository.save(syncRecord);
            return SyncResult.failure("Full sync failed: " + e.getMessage());
        }
    }

    private int syncApps(List<Map<String, Object>> apps) {
        int count = 0;
        for (Map<String, Object> app : apps) {
            String appId = String.valueOf(app.get("id"));
            DifyApp entity = difyAppRepository.findById(appId).orElseGet(
                    () -> DifyApp.builder().id(appId).lastSyncedAt(LocalDateTime.now()).build());
            entity.setName(String.valueOf(app.get("name")));
            entity.setType(String.valueOf(app.get("type")));
            entity.setDescription(String.valueOf(app.get("description")));
            entity.setIconUrl(String.valueOf(app.get("icon")));
            entity.setMode(String.valueOf(app.get("mode")));
            entity.setCreatedFrom(String.valueOf(app.get("created_from")));
            entity.setUpdatedAt(LocalDateTime.now());
            difyAppRepository.save(entity);
            count++;
        }
        return count;
    }

    private int syncWorkflows(List<Map<String, Object>> workflows, String endpoint, String apiKey) {
        int count = 0;
        for (Map<String, Object> wf : workflows) {
            String appId = String.valueOf(wf.get("id"));
            Map<String, Object> dsl = difyApiClient.getWorkflowDSL(endpoint, apiKey, appId);
            DifyWorkflow entity = difyWorkflowRepository.findById(appId).orElseGet(
                    () -> DifyWorkflow.builder().id(appId).build());
            entity.setAppId(appId);
            entity.setName(String.valueOf(wf.get("name")));
            entity.setDescription(String.valueOf(wf.get("description")));
            entity.setDslDefinition(dsl != null ? dsl.toString() : null);
            entity.setStatus(String.valueOf(wf.get("status")));
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setLastSyncedAt(LocalDateTime.now());
            difyWorkflowRepository.save(entity);
            count++;
        }
        return count;
    }

    private DatasetSyncResult syncDataset(DifyKnowledgeSyncService.DifyDataset dataset,
                                          String endpoint, String apiKey) {
        int added = 0, updated = 0;

        KnowledgeBase kb = knowledgeBaseRepository.findById(dataset.getId()).orElse(null);
        if (kb != null) {
            if (!Objects.equals(kb.getName(), dataset.getName())) { kb.setName(dataset.getName()); }
            if (!Objects.equals(kb.getDescription(), dataset.getDescription())) { kb.setDescription(dataset.getDescription()); }
            kb.setDocCount(dataset.getDocumentCount() == null ? 0 : dataset.getDocumentCount());
            kb.setSyncTime(LocalDateTime.now());
            kb.setStatus("ENABLED");
            knowledgeBaseRepository.save(kb);
            updated++;
        } else {
            kb = new KnowledgeBase();
            kb.setId(dataset.getId());
            kb.setName(dataset.getName());
            kb.setDescription(dataset.getDescription());
            kb.setDocCount(dataset.getDocumentCount());
            kb.setStatus("ENABLED");
            kb.setCreatedAt(LocalDateTime.now());
            kb.setSyncTime(LocalDateTime.now());
            knowledgeBaseRepository.save(kb);
            added++;
            log.info("Created new knowledge base: {}", dataset.getName());
        }

        List<DifyKnowledgeSyncService.DifyDocument> documents =
                difyApiClient.listDocuments(endpoint, apiKey, dataset.getId());
        for (DifyKnowledgeSyncService.DifyDocument doc : documents) {
            if (documentRepository.findById(doc.getId()).isEmpty()) {
                KnowledgeDocument newDoc = new KnowledgeDocument();
                newDoc.setId(doc.getId());
                newDoc.setFileName(doc.getName());
                newDoc.setKnowledgeBaseId(dataset.getId());
                newDoc.setFileCategory("DIFY");
                newDoc.setParseStatus("SUCCESS");
                documentRepository.save(newDoc);
                added++;
            }
        }

        return new DatasetSyncResult(added, updated, 0);
    }

    /**
     * 同步对话历史
     */
    public SyncResult syncConversations(String appId) {
        if (!difyConfigProvider.isActive()) {
            return SyncResult.failure("Dify 未启用或配置不完整");
        }
        String endpoint = difyConfigProvider.getApiUrl();
        String apiKey   = difyConfigProvider.getApiKey();

        try {
            List<Map<String, Object>> conversations = difyApiClient.listConversations(endpoint, apiKey, appId);
            int count = 0;
            for (Map<String, Object> conv : conversations) {
                String convId = String.valueOf(conv.get("id"));
                DifyConversation entity = difyConversationRepository.findById(convId).orElseGet(
                        () -> DifyConversation.builder().id(convId).build());
                entity.setAppId(appId);
                entity.setName(String.valueOf(conv.get("name")));
                entity.setMode(String.valueOf(conv.get("mode")));
                entity.setFromSource(String.valueOf(conv.get("from_source")));
                entity.setIsDeleted((Boolean) conv.get("is_deleted"));
                entity.setUpdatedAt(LocalDateTime.now());
                entity.setLastSyncedAt(LocalDateTime.now());
                difyConversationRepository.save(entity);
                count++;
            }
            log.info("Synced {} conversations for app {}", count, appId);
            return SyncResult.success("Conversations synced", count, 0, 0);
        } catch (Exception e) {
            log.error("Failed to sync conversations: {}", e.getMessage());
            return SyncResult.failure("Sync failed: " + e.getMessage());
        }
    }

    /**
     * 同步工作流 DSL
     */
    public SyncResult syncWorkflows() {
        if (!difyConfigProvider.isActive()) {
            return SyncResult.failure("Dify 未启用或配置不完整");
        }
        String endpoint = difyConfigProvider.getApiUrl();
        String apiKey   = difyConfigProvider.getApiKey();

        try {
            List<Map<String, Object>> workflows = difyApiClient.listWorkflows(endpoint, apiKey);
            int count = syncWorkflows(workflows, endpoint, apiKey);
            return SyncResult.success("Workflows synced", count, 0, 0);
        } catch (Exception e) {
            log.error("Failed to sync workflows: {}", e.getMessage());
            return SyncResult.failure("Sync failed: " + e.getMessage());
        }
    }

    public boolean testConnection(String endpoint, String apiKey) {
        return difyApiClient.testConnection(endpoint, apiKey);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(Object obj) {
        return obj instanceof List ? (List<T>) obj : null;
    }

    // ==================== 内部类 ====================

    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final int added, updated, deleted;

        public static SyncResult success(String message, int added, int updated, int deleted) {
            return new SyncResult(true, message, added, updated, deleted);
        }

        public static SyncResult failure(String message) {
            return new SyncResult(false, message, 0, 0, 0);
        }

        private SyncResult(boolean success, String message, int added, int updated, int deleted) {
            this.success = success; this.message = message;
            this.added = added; this.updated = updated; this.deleted = deleted;
        }

        public boolean isSuccess() { return success; }
        public String getMessage()  { return message; }
        public int getAdded()       { return added; }
        public int getUpdated()     { return updated; }
        public int getDeleted()     { return deleted; }
    }

    private static class DatasetSyncResult {
        private final int added, updated, deleted;
        DatasetSyncResult(int added, int updated, int deleted) {
            this.added = added; this.updated = updated; this.deleted = deleted;
        }
        public int getAdded()   { return added; }
        public int getUpdated() { return updated; }
        public int getDeleted() { return deleted; }
    }
}
