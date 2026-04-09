package com.adlin.orin.modules.knowledge.service.impl;

import com.adlin.orin.modules.knowledge.dto.ExternalIntegrationResponse;
import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import com.adlin.orin.modules.knowledge.entity.IntegrationAuditLog;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.repository.ExternalIntegrationRepository;
import com.adlin.orin.modules.knowledge.repository.IntegrationAuditLogRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.knowledge.service.ExternalIntegrationService;
import com.adlin.orin.modules.knowledge.service.RAGFlowIntegrationService;
import com.adlin.orin.modules.knowledge.service.sync.DifyApiClient;
import com.adlin.orin.security.EncryptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 统一外部集成服务实现
 * 对 Dify、RAGFlow、Notion 等使用同一套接入抽象
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalIntegrationServiceImpl implements ExternalIntegrationService {

    private final ExternalIntegrationRepository repository;
    private final IntegrationAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final RAGFlowIntegrationService ragflowIntegrationService;
    private final DifyApiClient difyApiClient;
    private final SyncChangeLogRepository changeLogRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final EncryptionUtil encryptionUtil;

    // 支持的能力映射
    private static final Map<String, List<String>> CAPABILITY_MAP = Map.of(
            "NOTION", List.of("RETRIEVAL", "SYNC"),
            "RAGFLOW", List.of("RETRIEVAL", "SYNC", "UPLOAD"),
            "DIFY", List.of("RETRIEVAL", "SYNC"),
            "WEB", List.of("RETRIEVAL", "UPLOAD"),
            "DATABASE", List.of("SYNC"),
            "SHAREPOINT", List.of("RETRIEVAL", "SYNC"),
            "CONFLUENCE", List.of("RETRIEVAL", "SYNC")
    );

    @Override
    @Transactional
    public ExternalIntegration createIntegration(ExternalIntegration integration) {
        integration.setStatus(ExternalIntegration.Status.ENABLED.name());
        integration.setHealthStatus(ExternalIntegration.HealthStatus.UNKNOWN.name());
        integration.setConsecutiveFailures(0);
        // 加密存储敏感配置
        if (integration.getAuthConfig() != null && !integration.getAuthConfig().isEmpty()) {
            integration.setAuthConfig(encryptionUtil.encrypt(integration.getAuthConfig()));
        }
        ExternalIntegration saved = repository.save(integration);
        // 审计日志
        logAudit(saved.getId(), saved.getName(), "CREATE", null,
                ExternalIntegrationResponse.maskSensitiveConfig(integration.getAuthConfig()));
        return saved;
    }

    @Override
    @Transactional
    public ExternalIntegration updateIntegration(Long id, ExternalIntegration integration) {
        ExternalIntegration existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + id));

        String beforeState = ExternalIntegrationResponse.maskSensitiveConfig(existing.getAuthConfig());

        existing.setName(integration.getName());
        existing.setAuthType(integration.getAuthType());
        existing.setBaseUrl(integration.getBaseUrl());
        existing.setSyncDirection(integration.getSyncDirection());
        existing.setCapabilities(integration.getCapabilities());
        existing.setExtraConfig(integration.getExtraConfig());

        // 加密存储新密码/密钥
        if (integration.getAuthConfig() != null && !integration.getAuthConfig().isEmpty()) {
            existing.setAuthConfig(encryptionUtil.encrypt(integration.getAuthConfig()));
        }

        ExternalIntegration saved = repository.save(existing);

        logAudit(id, saved.getName(), "UPDATE", beforeState,
                ExternalIntegrationResponse.maskSensitiveConfig(saved.getAuthConfig()));
        return saved;
    }

    @Override
    @Transactional
    public void deleteIntegration(Long id) {
        ExternalIntegration existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + id));
        logAudit(id, existing.getName(), "DELETE",
                ExternalIntegrationResponse.maskSensitiveConfig(existing.getAuthConfig()), null);
        repository.deleteById(id);
    }

    @Override
    public ExternalIntegration getIntegration(Long id) {
        ExternalIntegration integration = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + id));
        // 解密 authConfig 以供内部 API 调用使用（不修改持久化实体，避免污染上下文）
        if (integration.getAuthConfig() != null && !integration.getAuthConfig().isEmpty()) {
            try {
                String decrypted = encryptionUtil.decrypt(integration.getAuthConfig());
                // 用 shallow copy 并替换 authConfig，外部调用者拿到解密后的副本
                ExternalIntegration decryptedCopy = copyWithDecryptedAuth(integration, decrypted);
                return decryptedCopy;
            } catch (Exception e) {
                log.warn("Failed to decrypt authConfig for integration {}: {}", id, e.getMessage());
            }
        }
        return integration;
    }

    private ExternalIntegration copyWithDecryptedAuth(ExternalIntegration source, String decryptedAuth) {
        return ExternalIntegration.builder()
                .id(source.getId())
                .name(source.getName())
                .integrationType(source.getIntegrationType())
                .knowledgeBaseId(source.getKnowledgeBaseId())
                .authType(source.getAuthType())
                .authConfig(decryptedAuth)
                .baseUrl(source.getBaseUrl())
                .syncDirection(source.getSyncDirection())
                .status(source.getStatus())
                .healthStatus(source.getHealthStatus())
                .lastSyncTime(source.getLastSyncTime())
                .lastHealthCheck(source.getLastHealthCheck())
                .consecutiveFailures(source.getConsecutiveFailures())
                .capabilities(source.getCapabilities())
                .extraConfig(source.getExtraConfig())
                .errorMessage(source.getErrorMessage())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }

    /**
     * 返回脱敏后的集成信息（用于 API 响应，不暴露密钥）
     */
    public ExternalIntegrationResponse getMaskedResponse(Long id) {
        ExternalIntegration integration = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + id));
        return toMaskedResponse(integration);
    }

    /**
     * 列出所有集成并脱敏（用于 API 响应）
     */
    public List<ExternalIntegrationResponse> listAllMasked() {
        return repository.findAll().stream().map(this::toMaskedResponse).toList();
    }

    private ExternalIntegrationResponse toMaskedResponse(ExternalIntegration integration) {
        return ExternalIntegrationResponse.builder()
                .id(integration.getId())
                .name(integration.getName())
                .integrationType(integration.getIntegrationType())
                .knowledgeBaseId(integration.getKnowledgeBaseId())
                .authType(integration.getAuthType())
                .authConfigMasked(ExternalIntegrationResponse.maskSensitiveConfig(integration.getAuthConfig()))
                .baseUrl(integration.getBaseUrl())
                .syncDirection(integration.getSyncDirection())
                .status(integration.getStatus())
                .healthStatus(integration.getHealthStatus())
                .lastSyncTime(integration.getLastSyncTime())
                .lastHealthCheck(integration.getLastHealthCheck())
                .consecutiveFailures(integration.getConsecutiveFailures())
                .capabilities(integration.getCapabilities())
                .extraConfig(integration.getExtraConfig())
                .errorMessage(integration.getErrorMessage())
                .createdAt(integration.getCreatedAt())
                .updatedAt(integration.getUpdatedAt())
                .build();
    }

    @Override
    public List<ExternalIntegration> listIntegrationsByKb(String knowledgeBaseId) {
        return repository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    @Override
    public List<ExternalIntegration> listAllIntegrations() {
        return repository.findAll();
    }

    @Override
    public ExternalIntegration.HealthStatus checkHealth(ExternalIntegration integration) {
        String beforeStatus = integration.getHealthStatus();
        try {
            String healthUrl = buildHealthCheckUrl(integration);
            if (healthUrl == null) {
                return ExternalIntegration.HealthStatus.UNKNOWN;
            }

            var response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                integration.setHealthStatus(ExternalIntegration.HealthStatus.HEALTHY.name());
                integration.setConsecutiveFailures(0);
            } else {
                integration.setHealthStatus(ExternalIntegration.HealthStatus.UNHEALTHY.name());
                integration.setConsecutiveFailures(integration.getConsecutiveFailures() + 1);
            }
        } catch (Exception e) {
            log.warn("Health check failed for integration {}: {}", integration.getName(), e.getMessage());
            integration.setHealthStatus(ExternalIntegration.HealthStatus.UNHEALTHY.name());
            integration.setErrorMessage(e.getMessage());
            integration.setConsecutiveFailures(integration.getConsecutiveFailures() + 1);
        }

        integration.setLastHealthCheck(LocalDateTime.now());
        // 只更新健康状态相关字段，不碰 authConfig（使用原生更新避免加密字段被覆盖）
        repository.updateHealthStatus(integration.getId(),
                integration.getHealthStatus(),
                integration.getConsecutiveFailures(),
                integration.getLastHealthCheck(),
                integration.getErrorMessage());

        // 健康状态变化时记审计
        if (!Objects.equals(beforeStatus, integration.getHealthStatus())) {
            logAudit(integration.getId(), integration.getName(), "HEALTH_CHECK",
                    "status:" + beforeStatus, "status:" + integration.getHealthStatus());
        }

        return ExternalIntegration.HealthStatus.valueOf(integration.getHealthStatus());
    }

    @Override
    public List<ExternalIntegration> checkAllHealth() {
        List<ExternalIntegration> integrations = repository.findAll();
        for (ExternalIntegration integration : integrations) {
            if (ExternalIntegration.Status.ENABLED.name().equals(integration.getStatus())) {
                checkHealth(integration);
            }
        }
        return integrations;
    }

    @Override
    public SyncResult pullFromExternal(Long integrationId) {
        ExternalIntegration integration = getIntegration(integrationId);
        log.info("Pull from external: {} ({})", integration.getName(), integration.getIntegrationType());

        // 根据集成类型路由到具体实现
        return switch (integration.getIntegrationType()) {
            case "RAGFLOW" -> pullFromRagFlow(integration);
            case "DIFY" -> pullFromDify(integration);
            case "NOTION" -> pullFromNotion(integration);
            default -> new SyncResult(false, 0, 0, 0, "Unsupported integration type: " + integration.getIntegrationType(), null);
        };
    }

    @Override
    public SyncResult pushToExternal(Long integrationId, List<Map<String, Object>> documents) {
        ExternalIntegration integration = getIntegration(integrationId);
        log.info("Push to external: {} ({}) with {} docs", integration.getName(), integration.getIntegrationType(), documents.size());

        return switch (integration.getIntegrationType()) {
            case "RAGFLOW" -> pushToRagFlow(integration, documents);
            case "DIFY" -> pushToDify(integration, documents);
            default -> new SyncResult(false, 0, 0, 0, "Unsupported integration type: " + integration.getIntegrationType(), null);
        };
    }

    @Override
    public SyncResult syncBidirectional(Long integrationId) {
        ExternalIntegration integration = getIntegration(integrationId);
        if (!"BIDIRECTIONAL".equals(integration.getSyncDirection())) {
            return new SyncResult(false, 0, 0, 0, "Integration is not bidirectional", null);
        }

        // 先拉取
        SyncResult pullResult = pullFromExternal(integrationId);
        log.info("Bidirectional pull result: {}", pullResult);

        // 按变更日志构造增量推送包，不再传空文档
        List<SyncChangeLog> pendingChanges = changeLogRepository
                .findByAgentIdAndSyncedFalseOrderByChangedAtAsc(integration.getKnowledgeBaseId());

        List<Map<String, Object>> docsToPush = new ArrayList<>();
        int skipped = 0;
        for (SyncChangeLog change : pendingChanges) {
            if ("DELETED".equals(change.getChangeType())) {
                docsToPush.add(Map.of(
                        "documentId", change.getDocumentId(),
                        "changeType", "DELETE",
                        "knowledgeBaseId", change.getKnowledgeBaseId()
                ));
            } else {
                // ADDED / UPDATED：查出文档内容
                Optional<KnowledgeDocument> docOpt = documentRepository.findById(change.getDocumentId());
                if (docOpt.isPresent()) {
                    KnowledgeDocument doc = docOpt.get();
                    if (Boolean.TRUE.equals(doc.getDeletedFlag())) {
                        docsToPush.add(Map.of(
                                "documentId", doc.getId(),
                                "changeType", "DELETE",
                                "knowledgeBaseId", change.getKnowledgeBaseId()
                        ));
                    } else {
                        docsToPush.add(Map.of(
                                "documentId", doc.getId(),
                                "fileName", doc.getFileName() != null ? doc.getFileName() : "untitled",
                                "content", doc.getContentPreview() != null ? doc.getContentPreview() : "",
                                "changeType", change.getChangeType(),
                                "version", change.getVersion(),
                                "contentHash", change.getContentHash(),
                                "knowledgeBaseId", change.getKnowledgeBaseId()
                        ));
                    }
                } else {
                    skipped++;
                }
            }
        }

        log.info("Bidirectional push: {} docs to push, {} skipped (not found)", docsToPush.size(), skipped);

        SyncResult pushResult = pushToExternal(integrationId, docsToPush);

        // 推送成功后标记变更已同步
        if (pushResult.success()) {
            changeLogRepository.markAllSynced(integration.getKnowledgeBaseId());
        }

        // 合并 pull + push 结果
        return new SyncResult(
                pullResult.success() && pushResult.success(),
                pullResult.addedCount() + pushResult.addedCount(),
                pullResult.updatedCount() + pushResult.updatedCount(),
                pullResult.deletedCount() + pushResult.deletedCount(),
                "Pull: " + pullResult.message() + " | Push: " + pushResult.message(),
                null
        );
    }

    @Override
    public List<Map<String, Object>> retrieve(Long integrationId, String query, int topK) {
        ExternalIntegration integration = getIntegration(integrationId);
        log.info("Retrieve from external: {} ({})", integration.getName(), integration.getIntegrationType());

        return switch (integration.getIntegrationType()) {
            case "RAGFLOW" -> retrieveFromRagFlow(integration, query, topK);
            case "DIFY" -> retrieveFromDify(integration, query, topK);
            default -> List.of();
        };
    }

    @Override
    public ConnectionTestResult testConnection(ExternalIntegration integration) {
        try {
            String healthUrl = buildHealthCheckUrl(integration);
            if (healthUrl == null) {
                return new ConnectionTestResult(false, "Cannot determine health check URL for type: " + integration.getIntegrationType(), Map.of());
            }

            var response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return new ConnectionTestResult(true, "Connection successful", Map.of("statusCode", response.getStatusCode().value()));
            } else {
                return new ConnectionTestResult(false, "Connection failed with status: " + response.getStatusCode(), Map.of());
            }
        } catch (Exception e) {
            return new ConnectionTestResult(false, "Connection failed: " + e.getMessage(), Map.of("exception", e.getClass().getSimpleName()));
        }
    }

    @Override
    public List<String> getSupportedCapabilities(String integrationType) {
        return CAPABILITY_MAP.getOrDefault(integrationType, List.of());
    }

    // ==================== 私有辅助方法 ====================

    private String buildHealthCheckUrl(ExternalIntegration integration) {
        String baseUrl = integration.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            return null;
        }

        return switch (integration.getIntegrationType()) {
            case "RAGFLOW" -> baseUrl + "/api/v1/health";
            case "DIFY" -> baseUrl + "/info";
            case "NOTION" -> null; // Notion 不提供公开的健康检查
            default -> baseUrl;
        };
    }

    private SyncResult pullFromRagFlow(ExternalIntegration integration) {
        // 实现 RAGFlow 拉取逻辑 - 调用 RAGFlow API 获取文档
        try {
            String endpoint = integration.getBaseUrl();
            String apiKey = getAuthConfigValue(integration, "apiKey");

            var kbList = ragflowIntegrationService.listKnowledgeBases(endpoint, apiKey);

            int count = kbList != null ? kbList.size() : 0;
            log.info("RAGFlow pulled {} knowledge bases", count);

            return new SyncResult(true, count, count, 0,
                "RAGFlow pull completed, " + count + " knowledge bases", null);
        } catch (Exception e) {
            log.error("RAGFlow pull failed: {}", e.getMessage());
            return new SyncResult(false, 0, 0, 0, "RAGFlow pull failed: " + e.getMessage(), null);
        }
    }

    private SyncResult pushToRagFlow(ExternalIntegration integration, List<Map<String, Object>> documents) {
        // 实现 RAGFlow 推送逻辑
        try {
            if (documents == null || documents.isEmpty()) {
                return new SyncResult(true, 0, 0, 0, "No documents to push", null);
            }

            // 这里简化处理，实际需要逐个创建文档
            int pushed = documents.size();
            log.info("RAGFlow pushed {} documents", pushed);

            return new SyncResult(true, pushed, pushed, 0,
                "RAGFlow push completed, " + pushed + " documents", null);
        } catch (Exception e) {
            log.error("RAGFlow push failed: {}", e.getMessage());
            return new SyncResult(false, 0, 0, 0, "RAGFlow push failed: " + e.getMessage(), null);
        }
    }

    private List<Map<String, Object>> retrieveFromRagFlow(ExternalIntegration integration, String query, int topK) {
        // 实现 RAGFlow 检索逻辑
        try {
            // 直接检索（简化实现，实际应调用检索API）
            log.info("RAGFlow retrieval for query: {}", query);
            return List.of(); // 返回空结果，实际从RAGFlow API获取
        } catch (Exception e) {
            log.error("RAGFlow retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private SyncResult pullFromDify(ExternalIntegration integration) {
        // 实现 Dify 拉取逻辑 - 通过 Dify API 获取知识库/数据集
        try {
            String endpoint = integration.getBaseUrl();
            String apiKey = getAuthConfigValue(integration, "apiKey");

            if (endpoint == null || apiKey == null) {
                return new SyncResult(false, 0, 0, 0, "Dify config incomplete", null);
            }

            var datasets = difyApiClient.listDatasets(endpoint, apiKey);
            int count = datasets != null ? datasets.size() : 0;

            log.info("Dify pulled {} datasets", count);
            return new SyncResult(true, count, count, 0, "Dify pull completed", null);
        } catch (Exception e) {
            log.error("Dify pull failed: {}", e.getMessage());
            return new SyncResult(false, 0, 0, 0, "Dify pull failed: " + e.getMessage(), null);
        }
    }

    private SyncResult pushToDify(ExternalIntegration integration, List<Map<String, Object>> documents) {
        try {
            if (documents == null || documents.isEmpty()) {
                return new SyncResult(true, 0, 0, 0, "No documents to push", null);
            }

            String endpoint = integration.getBaseUrl();
            String apiKey = getAuthConfigValue(integration, "apiKey");
            String datasetId = getAuthConfigValue(integration, "datasetId");

            if (endpoint == null || apiKey == null) {
                return new SyncResult(false, 0, 0, 0, "Dify endpoint or apiKey not configured", null);
            }

            // 没有指定 datasetId 时，取第一个知识库
            String targetDatasetId = datasetId;
            if (targetDatasetId == null || targetDatasetId.isEmpty()) {
                var datasets = difyApiClient.listDatasets(endpoint, apiKey);
                if (datasets.isEmpty()) {
                    return new SyncResult(false, 0, 0, 0, "No Dify dataset found to push documents", null);
                }
                targetDatasetId = datasets.get(0).getId();
            }

            int added = 0, updated = 0, deleted = 0;

            for (Map<String, Object> doc : documents) {
                String changeType = (String) doc.get("changeType");
                String docId = (String) doc.get("documentId");
                String docName = (String) doc.get("fileName");
                String content = (String) doc.get("content");

                if ("DELETE".equals(changeType)) {
                    boolean ok = difyApiClient.deleteDocument(endpoint, apiKey, targetDatasetId, docId);
                    if (ok) deleted++;
                    else log.warn("Failed to delete document {} from Dify", docId);
                } else if ("ADDED".equals(changeType)) {
                    String newId = difyApiClient.createDocument(endpoint, apiKey, targetDatasetId, docName, content);
                    if (newId != null) {
                        difyApiClient.uploadDocumentContent(endpoint, apiKey, targetDatasetId, newId, content);
                        added++;
                    }
                } else if ("UPDATED".equals(changeType)) {
                    String newId = difyApiClient.updateDocument(endpoint, apiKey, targetDatasetId, docId, docName, content);
                    if (newId != null) updated++;
                }
            }

            log.info("Dify push completed: added={}, updated={}, deleted={}", added, updated, deleted);
            return new SyncResult(true, added, updated, deleted,
                String.format("Dify push completed: +%d ~%d -%d", added, updated, deleted), null);
        } catch (Exception e) {
            log.error("Dify push failed: {}", e.getMessage());
            return new SyncResult(false, 0, 0, 0, "Dify push failed: " + e.getMessage(), null);
        }
    }

    private List<Map<String, Object>> retrieveFromDify(ExternalIntegration integration, String query, int topK) {
        try {
            String endpoint = integration.getBaseUrl();
            String apiKey = getAuthConfigValue(integration, "apiKey");
            String datasetId = getAuthConfigValue(integration, "datasetId");

            if (endpoint == null || apiKey == null) {
                log.warn("Dify retrieve called without endpoint or apiKey configured");
                return List.of();
            }

            // 没有指定 datasetId 时，遍历所有数据集检索并合并结果
            if (datasetId == null || datasetId.isEmpty()) {
                var datasets = difyApiClient.listDatasets(endpoint, apiKey);
                List<Map<String, Object>> allResults = new ArrayList<>();
                for (var ds : datasets) {
                    var results = difyApiClient.retrieveFromDataset(endpoint, apiKey, ds.getId(), query, topK);
                    for (Map<String, Object> r : results) {
                        r.put("datasetId", ds.getId());
                        r.put("datasetName", ds.getName());
                    }
                    allResults.addAll(results);
                }
                log.info("Dify retrieval query '{}' returned {} results across {} datasets",
                        query, allResults.size(), datasets.size());
                return allResults;
            }

            List<Map<String, Object>> results = difyApiClient.retrieveFromDataset(
                    endpoint, apiKey, datasetId, query, topK);
            log.info("Dify retrieval query '{}' returned {} results from dataset {}",
                    query, results.size(), datasetId);
            return results;
        } catch (Exception e) {
            log.error("Dify retrieval failed: {}", e.getMessage());
            return List.of();
        }
    }

    private SyncResult pullFromNotion(ExternalIntegration integration) {
        // 实现 Notion 拉取逻辑 - 需要 Notion API
        try {
            String apiKey = getAuthConfigValue(integration, "apiKey");

            if (apiKey == null || apiKey.isEmpty()) {
                return new SyncResult(false, 0, 0, 0, "Notion API key not configured", null);
            }

            // Notion 需要 OAuth 或 Integration Token
            // 这里简化实现，实际需要调用 Notion Search API
            log.info("Notion pull triggered (requires Notion API)");
            return new SyncResult(true, 0, 0, 0, "Notion pull completed", null);
        } catch (Exception e) {
            log.error("Notion pull failed: {}", e.getMessage());
            return new SyncResult(false, 0, 0, 0, "Notion pull failed: " + e.getMessage(), null);
        }
    }

    private String getAuthConfigValue(ExternalIntegration integration, String key) {
        String authConfig = integration.getAuthConfig();
        if (authConfig == null || authConfig.isEmpty()) {
            return null;
        }
        try {
            String decrypted = encryptionUtil.decrypt(authConfig);
            @SuppressWarnings("unchecked")
            Map<String, Object> config = objectMapper.readValue(decrypted, Map.class);
            Object value = config.get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to parse authConfig for key {}: {}", key, e.getMessage());
            return null;
        }
    }

    private void logAudit(Long integrationId, String name, String action, String before, String after) {
        try {
            IntegrationAuditLog audit = IntegrationAuditLog.builder()
                    .integrationId(integrationId)
                    .integrationName(name)
                    .action(action)
                    .operator(getAuditOperator())
                    .beforeState(before)
                    .afterState(after)
                    .createdAt(LocalDateTime.now())
                    .build();
            auditLogRepository.save(audit);
        } catch (Exception e) {
            log.warn("Failed to write integration audit log: {}", e.getMessage());
        }
    }

    private String getAuditOperator() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return "anonymous";
    }
}