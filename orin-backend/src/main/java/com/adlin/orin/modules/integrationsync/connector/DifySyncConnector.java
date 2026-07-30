package com.adlin.orin.modules.integrationsync.connector;

import com.adlin.orin.modules.integrationsync.adapter.DifyWorkflowAdapter;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullApiClient;
import com.adlin.orin.modules.knowledge.service.sync.DifyKnowledgeSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Component
public class DifySyncConnector extends AbstractHttpPlatformConnector {

    private final DifyFullApiClient difyFullApiClient;
    private final DifyWorkflowAdapter workflowAdapter;

    public DifySyncConnector(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            DifyFullApiClient difyFullApiClient,
            DifyWorkflowAdapter workflowAdapter) {
        super(restTemplate, objectMapper);
        this.difyFullApiClient = difyFullApiClient;
        this.workflowAdapter = workflowAdapter;
    }

    @Override
    public PlatformType platform() {
        return PlatformType.DIFY;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return PlatformCapabilities.builder()
                .platformType(platform())
                .secretValueSyncSupported(false)
                .notes("Dify sync supports app/workflow and knowledge metadata best-effort. Unsupported editor details are preserved in rawSnapshot.")
                .resourceCapabilities(Map.of(
                        SyncResourceType.WORKFLOW, Set.of(SyncCapability.PULL, SyncCapability.PUSH, SyncCapability.IMPORT, SyncCapability.EXPORT, SyncCapability.INVOKE),
                        SyncResourceType.KNOWLEDGE_BASE, Set.of(SyncCapability.PULL, SyncCapability.PUSH, SyncCapability.IMPORT),
                        SyncResourceType.DOCUMENT, Set.of(SyncCapability.PULL, SyncCapability.PUSH, SyncCapability.IMPORT),
                        SyncResourceType.EXECUTION, Set.of(SyncCapability.EXECUTION_READ),
                        SyncResourceType.CREDENTIAL_REF, Set.of(SyncCapability.CREDENTIAL_REFERENCE)
                ))
                .build();
    }

    @Override
    public List<ExternalResource> pull(SyncPullRequest request) {
        IntegrationConnection connection = request.getConnection();
        List<ExternalResource> resources = new ArrayList<>();
        Set<SyncResourceType> requested = request.getResourceTypes() == null ? EnumSet.allOf(SyncResourceType.class) : request.getResourceTypes();
        if (requested.contains(SyncResourceType.KNOWLEDGE_BASE) || requested.contains(SyncResourceType.DOCUMENT)) {
            resources.addAll(pullDatasets(connection));
        }
        if (requested.contains(SyncResourceType.WORKFLOW) || requested.contains(SyncResourceType.AGENT)) {
            resources.addAll(pullApps(connection));
        }
        return resources;
    }

    @Override
    public PushResult push(SyncPushRequest request) {
        List<PushResult.PushResultItem> items = request.getResources().stream()
                .map(resource -> pushResource(request.getConnection(), resource))
                .toList();
        boolean hasFailure = items.stream().anyMatch(item -> item.getStatus() == SyncStatus.FAILED);
        boolean hasPartial = items.stream().anyMatch(item -> item.getStatus() == SyncStatus.PARTIAL);
        return PushResult.builder()
                .success(!hasFailure)
                .status(hasFailure ? SyncStatus.PARTIAL : (hasPartial ? SyncStatus.PARTIAL : SyncStatus.COMPLETED))
                .message(hasFailure ? "Dify push completed with failures" : "Dify push completed")
                .items(items)
                .build();
    }

    @Override
    public HealthCheckResult healthCheck(IntegrationConnection connection) {
        String apiKey = apiKey(connection);
        boolean healthy = apiKey != null && difyFullApiClient.testConnection(connection.getBaseUrl(), apiKey);
        return HealthCheckResult.builder()
                .healthy(healthy)
                .message(healthy ? "Dify connection healthy" : "Dify connection failed or apiKey missing")
                .details(Map.of("checkedAt", LocalDateTime.now().toString()))
                .build();
    }

    private List<ExternalResource> pullDatasets(IntegrationConnection connection) {
        String apiKey = apiKey(connection);
        if (apiKey == null) {
            return List.of();
        }
        List<ExternalResource> resources = new ArrayList<>();
        for (DifyKnowledgeSyncService.DifyDataset dataset : difyFullApiClient.listDatasets(connection.getBaseUrl(), apiKey)) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", dataset.getId());
            raw.put("name", dataset.getName());
            raw.put("description", dataset.getDescription());
            raw.put("document_count", dataset.getDocumentCount());
            raw.put("created_at", dataset.getCreatedAt());
            raw.put("updated_at", dataset.getUpdatedAt());
            resources.add(ExternalResource.builder()
                    .orinResourceType(SyncResourceType.KNOWLEDGE_BASE)
                    .externalResourceType("dataset")
                    .externalResourceId(dataset.getId())
                    .name(dataset.getName())
                    .externalUpdatedAt(dataset.getUpdatedAt())
                    .rawSnapshot(raw)
                    .canonicalSnapshot(Map.of(
                            "name", nullToEmpty(dataset.getName()),
                            "description", nullToEmpty(dataset.getDescription()),
                            "documentCount", dataset.getDocumentCount() == null ? 0 : dataset.getDocumentCount(),
                            "source", "DIFY",
                            "externalId", nullToEmpty(dataset.getId())
                    ))
                    .partial(false)
                    .build());
            resources.addAll(pullDocuments(connection, apiKey, dataset));
        }
        return resources;
    }

    private List<ExternalResource> pullApps(IntegrationConnection connection) {
        String apiKey = apiKey(connection);
        if (apiKey == null) {
            return List.of();
        }
        List<ExternalResource> resources = new ArrayList<>();
        for (Map<String, Object> app : difyFullApiClient.listApps(connection.getBaseUrl(), apiKey)) {
            String appId = String.valueOf(app.get("id"));
            String mode = String.valueOf(app.getOrDefault("mode", app.getOrDefault("type", "")));
            boolean workflow = mode.toLowerCase().contains("workflow");
            Map<String, Object> raw = new LinkedHashMap<>(app);
            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("name", app.getOrDefault("name", ""));
            canonical.put("source", "DIFY");
            canonical.put("mode", mode);
            canonical.put("externalId", appId);
            boolean partial = !workflow;
            String message = "Dify app metadata imported";
            if (workflow) {
                Map<String, Object> dsl = difyFullApiClient.getWorkflowDSL(connection.getBaseUrl(), apiKey, appId);
                if (dsl != null && !dsl.isEmpty()) {
                    raw.put("workflowDsl", dsl);
                    Map<String, Object> definition = workflowAdapter.toWorkflowDefinition(dsl, app);
                    canonical.put("workflowDefinition", definition);
                    partial = isPartialWorkflow(definition);
                    message = workflowAdapter.compatibilityMessage(definition);
                } else {
                    partial = true;
                    message = "Dify workflow metadata imported; workflow DSL export was unavailable";
                }
            }
            resources.add(ExternalResource.builder()
                    .orinResourceType(workflow ? SyncResourceType.WORKFLOW : SyncResourceType.AGENT)
                    .externalResourceType("app")
                    .externalResourceId(appId)
                    .name(String.valueOf(app.getOrDefault("name", appId)))
                    .externalUpdatedAt(epochOrNull(app.get("updated_at")))
                    .rawSnapshot(raw)
                    .canonicalSnapshot(canonical)
                    .partial(partial)
                    .compatibilityMessage(message)
                    .build());
        }
        return resources;
    }

    private List<ExternalResource> pullDocuments(
            IntegrationConnection connection,
            String apiKey,
            DifyKnowledgeSyncService.DifyDataset dataset) {
        List<ExternalResource> resources = new ArrayList<>();
        if (dataset.getId() == null) {
            return resources;
        }
        for (DifyKnowledgeSyncService.DifyDocument document : difyFullApiClient.listDocuments(connection.getBaseUrl(), apiKey, dataset.getId())) {
            String content = difyFullApiClient.getDocumentContent(connection.getBaseUrl(), apiKey, dataset.getId(), document.getId());
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("id", document.getId());
            raw.put("document_id", document.getDocumentId());
            raw.put("dataset_id", dataset.getId());
            raw.put("name", document.getName());
            raw.put("type", document.getType());
            raw.put("status", document.getStatus());
            raw.put("word_count", document.getWordCount());
            raw.put("created_at", document.getCreatedAt());
            raw.put("updated_at", document.getUpdatedAt());
            raw.put("contentAvailable", content != null);

            Map<String, Object> canonical = new LinkedHashMap<>();
            canonical.put("name", nullToEmpty(document.getName()));
            canonical.put("type", document.getType() == null ? "txt" : document.getType());
            canonical.put("status", nullToEmpty(document.getStatus()));
            canonical.put("wordCount", document.getWordCount() == null ? 0 : document.getWordCount());
            canonical.put("chunkCount", 0);
            canonical.put("knowledgeBaseId", dataset.getId());
            canonical.put("externalDatasetId", dataset.getId());
            canonical.put("externalId", nullToEmpty(document.getId()));
            canonical.put("source", "DIFY");
            if (content != null) {
                canonical.put("content", content);
            }

            resources.add(ExternalResource.builder()
                    .orinResourceType(SyncResourceType.DOCUMENT)
                    .externalResourceType("document")
                    .externalResourceId(document.getId())
                    .name(document.getName())
                    .externalUpdatedAt(document.getUpdatedAt())
                    .rawSnapshot(raw)
                    .canonicalSnapshot(canonical)
                    .partial(content == null)
                    .compatibilityMessage(content == null ? "Dify document metadata imported; content unavailable" : "Dify document imported with content preview")
                    .build());
        }
        return resources;
    }

    private PushResult.PushResultItem pushResource(IntegrationConnection connection, ExternalResource resource) {
        try {
            if (resource.getOrinResourceType() == SyncResourceType.DOCUMENT) {
                return pushDocument(connection, resource);
            }
            if (resource.getOrinResourceType() == SyncResourceType.WORKFLOW) {
                return pushWorkflowSnapshot(resource);
            }
            return PushResult.PushResultItem.builder()
                    .orinResourceType(resource.getOrinResourceType())
                    .orinResourceId(resource.getOrinResourceId())
                    .externalResourceType(defaultExternalType(resource))
                    .externalResourceId(resource.getExternalResourceId())
                    .status(SyncStatus.PARTIAL)
                    .message("Dify mutation for " + resource.getOrinResourceType() + " is not supported by this adapter; compatibility snapshot recorded")
                    .contentHash(resource.getContentHash())
                    .build();
        } catch (Exception e) {
            return PushResult.PushResultItem.builder()
                    .orinResourceType(resource.getOrinResourceType())
                    .orinResourceId(resource.getOrinResourceId())
                    .externalResourceType(defaultExternalType(resource))
                    .externalResourceId(resource.getExternalResourceId())
                    .status(SyncStatus.FAILED)
                    .message(e.getMessage())
                    .contentHash(resource.getContentHash())
                    .build();
        }
    }

    private PushResult.PushResultItem pushDocument(IntegrationConnection connection, ExternalResource resource) {
        String apiKey = apiKey(connection);
        if (apiKey == null) {
            throw new IllegalStateException("Dify apiKey missing");
        }
        Map<String, Object> snapshot = resource.getCanonicalSnapshot() == null ? Map.of() : resource.getCanonicalSnapshot();
        String datasetId = firstNonBlank(
                string(snapshot.get("externalDatasetId")),
                string(snapshot.get("datasetId")),
                configValue(connection, "datasetId"));
        if (datasetId == null) {
            throw new IllegalStateException("Dify datasetId missing for document push");
        }
        String changeType = string(snapshot.get("changeType"));
        String name = firstNonBlank(resource.getName(), string(snapshot.get("fileName")), string(snapshot.get("name")), "ORIN Document");
        String content = firstNonBlank(string(snapshot.get("content")), string(snapshot.get("contentPreview")), "");
        String externalId = resource.getExternalResourceId();
        if ("DELETE".equalsIgnoreCase(changeType) && externalId != null) {
            boolean deleted = difyFullApiClient.deleteDocument(connection.getBaseUrl(), apiKey, datasetId, externalId);
            return pushItem(resource, "document", externalId, deleted ? SyncStatus.COMPLETED : SyncStatus.FAILED,
                    deleted ? "Dify document deleted" : "Failed to delete Dify document");
        }
        String resultingId;
        if (externalId != null && !externalId.isBlank()) {
            resultingId = difyFullApiClient.updateDocument(connection.getBaseUrl(), apiKey, datasetId, externalId, name, content);
        } else {
            resultingId = difyFullApiClient.createDocument(connection.getBaseUrl(), apiKey, datasetId, name, content);
            if (resultingId != null && content != null && !content.isBlank()) {
                difyFullApiClient.uploadDocumentContent(connection.getBaseUrl(), apiKey, datasetId, resultingId, content);
            }
        }
        if (resultingId == null) {
            return pushItem(resource, "document", externalId, SyncStatus.FAILED, "Failed to publish Dify document");
        }
        return pushItem(resource, "document", resultingId, SyncStatus.COMPLETED, "Dify document published");
    }

    private PushResult.PushResultItem pushWorkflowSnapshot(ExternalResource resource) {
        Map<String, Object> snapshot = resource.getCanonicalSnapshot() == null ? Map.of() : resource.getCanonicalSnapshot();
        Map<String, Object> exportSnapshot = workflowAdapter.toDifyExportSnapshot(asMap(snapshot.get("workflowDefinition")), resource.getName());
        boolean partial = isPartialWorkflow(exportSnapshot);
        return PushResult.PushResultItem.builder()
                .orinResourceType(resource.getOrinResourceType())
                .orinResourceId(resource.getOrinResourceId())
                .externalResourceType("workflow_export")
                .externalResourceId(firstNonBlank(resource.getExternalResourceId(), "dify-export-" + resource.getOrinResourceId()))
                .status(partial ? SyncStatus.PARTIAL : SyncStatus.COMPLETED)
                .message(partial
                        ? "Dify workflow export snapshot recorded with unsupported nodes"
                        : "Dify workflow export snapshot recorded")
                .contentHash(resource.getContentHash())
                .build();
    }

    private PushResult.PushResultItem pushItem(
            ExternalResource resource,
            String externalType,
            String externalId,
            SyncStatus status,
            String message) {
        return PushResult.PushResultItem.builder()
                .orinResourceType(resource.getOrinResourceType())
                .orinResourceId(resource.getOrinResourceId())
                .externalResourceType(externalType)
                .externalResourceId(externalId)
                .status(status)
                .message(message)
                .contentHash(resource.getContentHash())
                .build();
    }

    private String apiKey(IntegrationConnection connection) {
        return firstNonBlank(configValue(connection, "apiKey"), configValue(connection, "token"), configValue(connection, "bearerToken"));
    }

    private String configValue(IntegrationConnection connection, String key) {
        if (connection == null || connection.getAuthConfig() == null || connection.getAuthConfig().isBlank()) {
            return null;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(connection.getAuthConfig(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
            Object value = config.get(key);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private boolean isPartialWorkflow(Map<String, Object> definition) {
        Object report = definition.get("compatibilityReport");
        if (report instanceof Map<?, ?> reportMap) {
            Object partial = reportMap.get("partial");
            return Boolean.TRUE.equals(partial);
        }
        return false;
    }

    private LocalDateTime epochOrNull(Object value) {
        if (value instanceof Number number) {
            return LocalDateTime.ofEpochSecond(number.longValue(), 0, ZoneOffset.UTC);
        }
        return null;
    }

    private String defaultExternalType(ExternalResource resource) {
        return resource.getExternalResourceType() != null ? resource.getExternalResourceType() : resource.getOrinResourceType().name().toLowerCase();
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"null".equals(value)) {
                return value;
            }
        }
        return null;
    }
}
