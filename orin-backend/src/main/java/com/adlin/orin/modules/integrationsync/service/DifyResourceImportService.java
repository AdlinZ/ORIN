package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.agent.service.AgentOwnershipResolver;
import com.adlin.orin.modules.integrationsync.adapter.DifyWorkflowAdapter;
import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.model.ExternalResource;
import com.adlin.orin.modules.integrationsync.model.IntegrationConnection;
import com.adlin.orin.modules.integrationsync.model.PlatformType;
import com.adlin.orin.modules.integrationsync.model.SyncResourceType;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeType;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DifyResourceImportService {

    private final WorkflowRepository workflowRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ExternalResourceMappingRepository mappingRepository;
    private final DifyWorkflowAdapter workflowAdapter;
    private final ObjectMapper objectMapper;
    private final AgentOwnershipResolver ownershipResolver;

    public boolean supports(IntegrationConnection connection, ExternalResource resource) {
        return connection != null
                && connection.getPlatformType() == PlatformType.DIFY
                && resource != null
                && resource.getOrinResourceType() != null;
    }

    public String importResource(IntegrationConnection connection, ExternalResource resource, ExternalResourceMapping existingMapping) {
        if (!supports(connection, resource)) {
            return resource.getOrinResourceId();
        }
        return switch (resource.getOrinResourceType()) {
            case WORKFLOW -> importWorkflow(resource, existingMapping);
            case KNOWLEDGE_BASE -> importKnowledgeBase(connection, resource, existingMapping);
            case DOCUMENT -> importDocument(connection, resource, existingMapping);
            default -> resource.getOrinResourceId();
        };
    }

    @SuppressWarnings("unchecked")
    private String importWorkflow(ExternalResource resource, ExternalResourceMapping existingMapping) {
        Map<String, Object> canonical = mutable(resource.getCanonicalSnapshot());
        Map<String, Object> workflowDefinition = asMap(canonical.get("workflowDefinition"));
        if (workflowDefinition.isEmpty()) {
            workflowDefinition = workflowAdapter.toWorkflowDefinition(resource.getRawSnapshot(), resource.getRawSnapshot());
        }
        String name = stringValue(canonical.get("name"), resource.getName(), "Imported Dify Workflow");
        String description = "Imported from Dify app " + resource.getExternalResourceId();

        Optional<WorkflowEntity> existing = workflowEntity(existingMapping);
        WorkflowEntity entity = existing.orElseGet(WorkflowEntity::new);
        entity.setWorkflowName(name);
        entity.setDescription(description);
        entity.setWorkflowType(WorkflowEntity.WorkflowType.DAG);
        entity.setWorkflowDefinition(workflowDefinition);
        if (entity.getOwnerUserId() == null) {
            entity.setOwnerUserId(ownershipResolver.resolveForSystemSeed());
        }
        entity.setStatus(WorkflowEntity.WorkflowStatus.DRAFT);
        if (entity.getVersion() == null) {
            entity.setVersion("1.0.0");
        }

        WorkflowEntity saved = workflowRepository.save(entity);
        resource.setOrinResourceId(String.valueOf(saved.getId()));
        resource.setCompatibilityMessage(workflowAdapter.compatibilityMessage(workflowDefinition));
        Object report = workflowDefinition.get("compatibilityReport");
        if (report instanceof Map<?, ?> reportMap && Boolean.TRUE.equals(reportMap.get("partial"))) {
            resource.setPartial(true);
        }
        return String.valueOf(saved.getId());
    }

    private String importKnowledgeBase(IntegrationConnection connection, ExternalResource resource, ExternalResourceMapping existingMapping) {
        Map<String, Object> canonical = mutable(resource.getCanonicalSnapshot());
        String kbId = existingMapping != null && existingMapping.getOrinResourceId() != null
                ? existingMapping.getOrinResourceId()
                : localId("dify", connection.getIntegrationId(), resource.getExternalResourceId());
        KnowledgeBase kb = knowledgeBaseRepository.findById(kbId).orElseGet(KnowledgeBase::new);
        kb.setId(kbId);
        kb.setName(stringValue(canonical.get("name"), resource.getName(), resource.getExternalResourceId()));
        kb.setDescription(stringValue(canonical.get("description"), "", ""));
        kb.setType(KnowledgeType.UNSTRUCTURED);
        kb.setStatus("ENABLED");
        kb.setDocCount(intValue(canonical.get("documentCount")));
        kb.setSyncTime(LocalDateTime.now());
        if (kb.getCreatedAt() == null) {
            kb.setCreatedAt(LocalDateTime.now());
        }
        knowledgeBaseRepository.save(kb);
        resource.setOrinResourceId(kbId);
        return kbId;
    }

    private String importDocument(IntegrationConnection connection, ExternalResource resource, ExternalResourceMapping existingMapping) {
        Map<String, Object> canonical = mutable(resource.getCanonicalSnapshot());
        String documentId = existingMapping != null && existingMapping.getOrinResourceId() != null
                ? existingMapping.getOrinResourceId()
                : localId("dify-doc", connection.getIntegrationId(), resource.getExternalResourceId());
        String externalDatasetId = stringValue(canonical.get("externalDatasetId"), canonical.get("knowledgeBaseId"), "");
        String knowledgeBaseId = resolveKnowledgeBaseId(connection, externalDatasetId);

        KnowledgeDocument document = documentRepository.findById(documentId).orElseGet(KnowledgeDocument::new);
        document.setId(documentId);
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setFileName(stringValue(canonical.get("name"), resource.getName(), resource.getExternalResourceId()));
        document.setOriginalFilename(document.getFileName());
        document.setFileType(stringValue(canonical.get("type"), "txt", "txt"));
        document.setMediaType("text");
        document.setFileCategory("DOCUMENT");
        document.setContentPreview(preview(stringValue(canonical.get("content"), "", "")));
        document.setContentHash(resource.getContentHash());
        document.setVectorIndexId(resource.getExternalResourceId());
        document.setVectorStatus(statusToVectorStatus(stringValue(canonical.get("status"), "", "")));
        document.setParseStatus(document.getContentPreview() == null || document.getContentPreview().isBlank() ? "PENDING" : "PARSED");
        document.setCharCount(intValue(canonical.get("wordCount")));
        document.setChunkCount(intValue(canonical.get("chunkCount")));
        document.setDeletedFlag(false);
        document.setUploadedBy("DIFY_SYNC");
        document.setMetadata(toJson(sanitizedMetadata(resource)));
        documentRepository.save(document);

        if (document.getContentPreview() == null || document.getContentPreview().isBlank()) {
            resource.setPartial(true);
            resource.setCompatibilityMessage("Dify document metadata imported; document content was unavailable");
        }
        resource.setOrinResourceId(documentId);
        return documentId;
    }

    private Optional<WorkflowEntity> workflowEntity(ExternalResourceMapping mapping) {
        if (mapping == null || mapping.getOrinResourceId() == null) {
            return Optional.empty();
        }
        try {
            return workflowRepository.findById(Long.parseLong(mapping.getOrinResourceId()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String resolveKnowledgeBaseId(IntegrationConnection connection, String externalDatasetId) {
        if (externalDatasetId == null || externalDatasetId.isBlank()) {
            return localId("dify", connection.getIntegrationId(), "default");
        }
        return mappingRepository.findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(
                        connection.getIntegrationId(), PlatformType.DIFY.name(), "dataset", externalDatasetId)
                .map(ExternalResourceMapping::getOrinResourceId)
                .orElse(localId("dify", connection.getIntegrationId(), externalDatasetId));
    }

    private Map<String, Object> sanitizedMetadata(ExternalResource resource) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "DIFY");
        metadata.put("externalResourceType", resource.getExternalResourceType());
        metadata.put("externalResourceId", resource.getExternalResourceId());
        metadata.put("externalVersion", resource.getExternalVersion());
        metadata.put("externalUpdatedAt", resource.getExternalUpdatedAt());
        metadata.put("rawSnapshot", resource.getRawSnapshot());
        return metadata;
    }

    private String statusToVectorStatus(String status) {
        if (status == null) {
            return "PENDING";
        }
        String normalized = status.trim().toLowerCase();
        if (normalized.contains("completed") || normalized.contains("available") || normalized.contains("indexed")) {
            return "INDEXED";
        }
        if (normalized.contains("error") || normalized.contains("fail")) {
            return "FAILED";
        }
        return "PENDING";
    }

    private String localId(String prefix, Long integrationId, String externalId) {
        String raw = prefix + "-" + integrationId + "-" + (externalId == null ? "unknown" : externalId);
        return raw.length() <= 120 ? raw : raw.substring(0, 120);
    }

    private String preview(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.length() <= 500 ? content : content.substring(0, 500);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> mutable(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }

    private String stringValue(Object primary, Object fallback, String defaultValue) {
        Object value = primary != null ? primary : fallback;
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value);
        return text.isBlank() || "null".equals(text) ? defaultValue : text;
    }

    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }
}
