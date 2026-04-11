package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.*;
import com.adlin.orin.modules.knowledge.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Dify 完整同步服务
 * 支持知识库、文档、应用、工作流、对话历史、用户等同步
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DifyFullSyncService {

    private final DifyFullApiClient difyApiClient;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final AgentAccessProfileRepository profileRepository;
    
    // 新增 Repository
    private final DifyAppRepository difyAppRepository;
    private final DifyWorkflowRepository difyWorkflowRepository;
    private final DifyConversationRepository difyConversationRepository;

    /**
     * 完整同步所有内容
     */
    @Transactional
    public SyncResult fullSync(String agentId) {
        log.info("Starting full sync for agent: {}", agentId);

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
        syncRecord.setSyncType("FULL");
        syncRecord.setDirection("PULL");
        syncRecord.setSyncDirection("INBOUND");
        syncRecord.setStartTime(LocalDateTime.now());
        syncRecord.setStatus("RUNNING");

        try {
            // 获取数据
            Map<String, Object> syncData = difyApiClient.fullSync(endpoint, apiKey);

            int appsCount = 0, workflowsCount = 0, datasetsCount = 0, docsCount = 0, convCount = 0;

            // 1. 同步应用
            List<Map<String, Object>> apps = (List<Map<String, Object>>) syncData.get("apps");
            if (apps != null) {
                appsCount = syncApps(agentId, apps);
                log.info("Synced {} apps", appsCount);
            }

            // 2. 同步工作流
            List<Map<String, Object>> workflows = (List<Map<String, Object>>) syncData.get("workflows");
            if (workflows != null) {
                workflowsCount = syncWorkflows(agentId, workflows, endpoint, apiKey);
                log.info("Synced {} workflows", workflowsCount);
            }

            // 3. 同步知识库和文档
            List<DifyKnowledgeSyncService.DifyDataset> datasets = 
                    (List<DifyKnowledgeSyncService.DifyDataset>) syncData.get("datasets");
            if (datasets != null) {
                datasetsCount = datasets.size();
                for (DifyKnowledgeSyncService.DifyDataset dataset : datasets) {
                    var result = syncDataset(dataset, endpoint, apiKey);
                    docsCount += result.getAdded() + result.getUpdated();
                }
            }

            // 4. 记录同步结果
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
            log.error("Full sync failed for agent: {}", agentId, e);
            syncRecord.setStatus("FAILED");
            syncRecord.setErrorMessage(e.getMessage());
            syncRecord.setEndTime(LocalDateTime.now());
            syncRecordRepository.save(syncRecord);
            return SyncResult.failure("Full sync failed: " + e.getMessage());
        }
    }

    /**
     * 同步应用
     */
    private int syncApps(String agentId, List<Map<String, Object>> apps) {
        int count = 0;
        for (Map<String, Object> app : apps) {
            String appId = String.valueOf(app.get("id"));
            
            Optional<DifyApp> existing = difyAppRepository.findById(appId);
            DifyApp entity;
            
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = DifyApp.builder().id(appId).build();
                entity.setLastSyncedAt(LocalDateTime.now());
            }
            
            entity.setAgentId(agentId);
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

    /**
     * 同步工作流
     */
    private int syncWorkflows(String agentId, List<Map<String, Object>> workflows, 
            String endpoint, String apiKey) {
        int count = 0;
        
        for (Map<String, Object> wf : workflows) {
            String appId = String.valueOf(wf.get("id"));
            
            // 获取 DSL
            Map<String, Object> dsl = difyApiClient.getWorkflowDSL(endpoint, apiKey, appId);
            String dslJson = dsl != null ? dsl.toString() : null;
            
            Optional<DifyWorkflow> existing = difyWorkflowRepository.findById(appId);
            DifyWorkflow entity;
            
            if (existing.isPresent()) {
                entity = existing.get();
            } else {
                entity = DifyWorkflow.builder().id(appId).build();
            }
            
            entity.setAgentId(agentId);
            entity.setAppId(appId);
            entity.setName(String.valueOf(wf.get("name")));
            entity.setDescription(String.valueOf(wf.get("description")));
            entity.setDslDefinition(dslJson);
            entity.setStatus(String.valueOf(wf.get("status")));
            entity.setUpdatedAt(LocalDateTime.now());
            entity.setLastSyncedAt(LocalDateTime.now());
            
            difyWorkflowRepository.save(entity);
            count++;
        }
        
        return count;
    }

    /**
     * 同步单个知识库
     */
    private DatasetSyncResult syncDataset(DifyKnowledgeSyncService.DifyDataset dataset, 
            String endpoint, String apiKey) {
        int added = 0, updated = 0;

        Optional<KnowledgeBase> existingKb = knowledgeBaseRepository.findById(dataset.getId());

        KnowledgeBase kb;
        if (existingKb.isPresent()) {
            kb = existingKb.get();
            if (kb.getName() != null && !kb.getName().equals(dataset.getName())) {
                kb.setName(dataset.getName());
                knowledgeBaseRepository.save(kb);
                updated++;
            }
        } else {
            kb = new KnowledgeBase();
            kb.setId(dataset.getId());
            kb.setName(dataset.getName());
            kb.setDescription(dataset.getDescription());
            kb.setSource("DIFY");
            kb.setSourceAgentId("");
            kb.setStatus("ACTIVE");
            kb.setDocumentCount(dataset.getDocumentCount());
            knowledgeBaseRepository.save(kb);
            added++;
            log.info("Created new knowledge base: {}", dataset.getName());
        }

        List<DifyKnowledgeSyncService.DifyDocument> documents = 
                difyApiClient.listDocuments(endpoint, apiKey, dataset.getId());

        for (DifyKnowledgeSyncService.DifyDocument doc : documents) {
            Optional<KnowledgeDocument> existingDoc = documentRepository.findById(doc.getId());

            if (existingDoc.isEmpty()) {
                KnowledgeDocument newDoc = new KnowledgeDocument();
                newDoc.setId(doc.getId());
                newDoc.setTitle(doc.getName());
                newDoc.setKnowledgeBaseId(dataset.getId());
                newDoc.setSource("DIFY");
                newDoc.setStatus("ACTIVE");
                documentRepository.save(newDoc);
                added++;
            }
        }

        return new DatasetSyncResult(added, updated, 0);
    }

    /**
     * 同步对话历史
     */
    public SyncResult syncConversations(String agentId, String appId) {
        var profileOpt = profileRepository.findById(agentId);
        if (profileOpt.isEmpty()) {
            return SyncResult.failure("Agent not found");
        }

        var profile = profileOpt.get();
        String endpoint = profile.getEndpointUrl();
        String apiKey = profile.getDatasetApiKey();

        try {
            List<Map<String, Object>> conversations = difyApiClient.listConversations(endpoint, apiKey, appId);
            
            int count = 0;
            for (Map<String, Object> conv : conversations) {
                String convId = String.valueOf(conv.get("id"));
                
                Optional<DifyConversation> existing = difyConversationRepository.findById(convId);
                DifyConversation entity;
                
                if (existing.isPresent()) {
                    entity = existing.get();
                } else {
                    entity = DifyConversation.builder().id(convId).build();
                }
                
                entity.setAgentId(agentId);
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
    public SyncResult syncWorkflows(String agentId) {
        var profileOpt = profileRepository.findById(agentId);
        if (profileOpt.isEmpty()) {
            return SyncResult.failure("Agent not found");
        }

        var profile = profileOpt.get();
        String endpoint = profile.getEndpointUrl();
        String apiKey = profile.getDatasetApiKey();

        try {
            List<Map<String, Object>> workflows = difyApiClient.listWorkflows(endpoint, apiKey);
            int count = syncWorkflows(agentId, workflows, endpoint, apiKey);
            
            return SyncResult.success("Workflows synced", count, 0, 0);
        } catch (Exception e) {
            log.error("Failed to sync workflows: {}", e.getMessage());
            return SyncResult.failure("Sync failed: " + e.getMessage());
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection(String endpoint, String apiKey) {
        return difyApiClient.testConnection(endpoint, apiKey);
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
            this.success = success;
            this.message = message;
            this.added = added;
            this.updated = updated;
            this.deleted = deleted;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getDeleted() { return deleted; }
    }

    private static class DatasetSyncResult {
        private final int added, updated, deleted;

        DatasetSyncResult(int added, int updated, int deleted) {
            this.added = added;
            this.updated = updated;
            this.deleted = deleted;
        }

        public int getAdded() { return added; }
        public int getUpdated() { return updated; }
        public int getDeleted() { return deleted; }
    }
}
