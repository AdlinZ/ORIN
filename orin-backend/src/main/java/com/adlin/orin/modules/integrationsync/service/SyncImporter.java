package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.entity.SyncItem;
import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.ExternalResource;
import com.adlin.orin.modules.integrationsync.model.IntegrationConnection;
import com.adlin.orin.modules.integrationsync.model.SyncStatus;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.integrationsync.repository.SyncItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SyncImporter {

    private final ExternalResourceMappingRepository mappingRepository;
    private final SyncItemRepository syncItemRepository;
    private final SyncDiffService diffService;
    private final SyncConflictService conflictService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private DifyResourceImportService difyResourceImportService;

    @Transactional
    public int importResources(SyncJob job, IntegrationConnection connection, List<ExternalResource> resources) {
        int imported = 0;
        for (ExternalResource resource : resources) {
            String externalHash = resource.getContentHash();
            if (externalHash == null) {
                externalHash = diffService.canonicalHash(resource.getCanonicalSnapshot());
                resource.setContentHash(externalHash);
            }
            ExternalResourceMapping mapping = mappingRepository
                    .findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(
                            connection.getIntegrationId(),
                            connection.getPlatformType().name(),
                            resource.getExternalResourceType(),
                            resource.getExternalResourceId())
                    .orElseGet(() -> ExternalResourceMapping.builder()
                            .integrationId(connection.getIntegrationId())
                            .platformType(connection.getPlatformType().name())
                            .orinResourceType(resource.getOrinResourceType().name())
                            .orinResourceId(externalOrinId(resource))
                            .externalResourceType(resource.getExternalResourceType())
                            .externalResourceId(resource.getExternalResourceId())
                            .syncDirection("PULL")
                            .build());

            if (diffService.drifted(mapping.getLastSyncedHash(), externalHash)) {
                resource.setOrinResourceId(mapping.getOrinResourceId());
                conflictService.recordExternalDrift(connection, resource, mapping.getLastSyncedHash(), externalHash, mapping.getRawSnapshot());
                mapping.setSyncStatus(SyncStatus.EXTERNAL_DRIFT.name());
            } else {
                String importedOrinId = importCanonicalResource(connection, resource, mapping);
                if (importedOrinId != null && !importedOrinId.isBlank()) {
                    mapping.setOrinResourceId(importedOrinId);
                    resource.setOrinResourceId(importedOrinId);
                } else {
                    resource.setOrinResourceId(mapping.getOrinResourceId());
                }
                mapping.setSyncStatus(resource.isPartial() ? SyncStatus.PARTIAL.name() : SyncStatus.COMPLETED.name());
                mapping.setLastSyncedHash(externalHash);
                imported++;
            }
            mapping.setExternalVersion(resource.getExternalVersion());
            mapping.setExternalUpdatedAt(resource.getExternalUpdatedAt());
            mapping.setRawSnapshot(toJson(resource.getRawSnapshot()));
            mapping.setCompatibilityReport(resource.getCompatibilityMessage());
            mappingRepository.save(mapping);

            syncItemRepository.save(SyncItem.builder()
                    .syncJobId(job.getId())
                    .orinResourceType(mapping.getOrinResourceType())
                    .orinResourceId(mapping.getOrinResourceId())
                    .externalResourceType(mapping.getExternalResourceType())
                    .externalResourceId(mapping.getExternalResourceId())
                    .status(mapping.getSyncStatus())
                    .message(resource.getCompatibilityMessage())
                    .contentHash(externalHash)
                    .build());
        }
        return imported;
    }

    private String importCanonicalResource(
            IntegrationConnection connection,
            ExternalResource resource,
            ExternalResourceMapping mapping) {
        if (difyResourceImportService != null && difyResourceImportService.supports(connection, resource)) {
            return difyResourceImportService.importResource(connection, resource, mapping);
        }
        return mapping.getOrinResourceId() != null ? mapping.getOrinResourceId() : externalOrinId(resource);
    }

    private String externalOrinId(ExternalResource resource) {
        return "external:" + resource.getExternalResourceType() + ":" + resource.getExternalResourceId();
    }

    private String toJson(Map<String, Object> value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
