package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.SyncRecordRepository;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
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
            // 调用完整同步 API
            Map<String, Object> syncData = difyApiClient.fullSync(endpoint, apiKey);

            int appsCount = 0, workflowsCount = 0, datasetsCount = 0, docsCount = 0;

            // 1. 同步应用
            List<Map<String, Object>> apps = (List<Map<String, Object>>) syncData.get("apps");
            if (apps != null) {
                appsCount = apps.size();
                log.info("Found {} apps", appsCount);
                // TODO: 保存应用信息到数据库
            }

            // 2. 同步工作流
            List<Map<String, Object>> workflows = (List<Map<String, Object>>) syncData.get("workflows");
            if (workflows != null) {
                workflowsCount = workflows.size();
                log.info("Found {} workflows", workflowsCount);
                // TODO: 保存工作流信息到数据库
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
     * 同步单个知识库
     */
    private DatasetSyncResult syncDataset(DifyKnowledgeSyncService.DifyDataset dataset, 
            String endpoint, String apiKey) {
        int added = 0, updated = 0;

        // 检查知识库是否已存在
        Optional<KnowledgeBase> existingKb = knowledgeBaseRepository.findById(dataset.getId());

        KnowledgeBase kb;
        if (existingKb.isPresent()) {
            kb = existingKb.get();
            // 检查是否需要更新
            if (kb.getName() != null && !kb.getName().equals(dataset.getName())) {
                kb.setName(dataset.getName());
                knowledgeBaseRepository.save(kb);
                updated++;
            }
        } else {
            // 创建新知识库
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

        // 同步文档
        List<DifyKnowledgeSyncService.DifyDocument> documents = 
                difyApiClient.listDocuments(endpoint, apiKey, dataset.getId());

        for (DifyKnowledgeSyncService.DifyDocument doc : documents) {
            Optional<KnowledgeDocument> existingDoc = documentRepository.findById(doc.getId());

            if (existingDoc.isEmpty()) {
                // 创建新文档
                KnowledgeDocument newDoc = new KnowledgeDocument();
                newDoc.setId(doc.getId());
                newDoc.setTitle(doc.getName());
                newDoc.setKnowledgeBaseId(dataset.getId());
                newDoc.setSource("DIFY");
                newDoc.setStatus("ACTIVE");
                documentRepository.save(newDoc);
                added++;
            }
            // TODO: 检查增量更新
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
            log.info("Found {} conversations for app {}", conversations.size(), appId);
            
            // TODO: 保存对话历史
            
            return SyncResult.success("Conversations synced", conversations.size(), 0, 0);
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
            log.info("Found {} workflows", workflows.size());
            
            int count = 0;
            for (Map<String, Object> wf : workflows) {
                String appId = String.valueOf(wf.get("id"));
                Map<String, Object> dsl = difyApiClient.getWorkflowDSL(endpoint, apiKey, appId);
                if (dsl != null) {
                    // TODO: 保存 DSL 到数据库
                    count++;
                }
            }
            
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
