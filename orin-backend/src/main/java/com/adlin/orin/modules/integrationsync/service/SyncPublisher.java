package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.entity.SyncItem;
import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.integrationsync.repository.SyncItemRepository;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SyncPublisher {

    private final ExternalResourceMappingRepository mappingRepository;
    private final SyncChangeLogRepository changeLogRepository;
    private final SyncItemRepository syncItemRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public PushResult push(
            SyncJob job,
            IntegrationConnection connection,
            PlatformConnector connector,
            List<SyncChangeLog> changes) {
        List<ExternalResource> resources = changes.stream()
                .map(change -> toExternalResource(connection, change))
                .toList();

        PushResult result = connector.push(SyncPushRequest.builder()
                .connection(connection)
                .resources(resources)
                .dryRun(false)
                .build());

        Map<String, PushResult.PushResultItem> byResourceKey = new HashMap<>();
        if (result.getItems() != null) {
            for (PushResult.PushResultItem item : result.getItems()) {
                byResourceKey.put(key(item.getOrinResourceType().name(), item.getOrinResourceId()), item);
            }
        }

        for (SyncChangeLog change : changes) {
            PushResult.PushResultItem item = byResourceKey.get(key(change.getOrinResourceType(), change.getOrinResourceId()));
            if (item != null && (item.getStatus() == SyncStatus.COMPLETED || item.getStatus() == SyncStatus.PARTIAL)) {
                change.setSynced(true);
                change.setSyncStatus("SYNCED");
                upsertMapping(connection, change, item);
            } else {
                change.setSyncStatus("FAILED");
                change.setRetryCount(change.getRetryCount() == null ? 1 : change.getRetryCount() + 1);
                change.setErrorMessage(item != null ? item.getMessage() : result.getMessage());
            }
            changeLogRepository.save(change);
            syncItemRepository.save(toSyncItem(job, change, item, result));
        }

        return result;
    }

    private ExternalResource toExternalResource(IntegrationConnection connection, SyncChangeLog change) {
        Map<String, Object> snapshot = new LinkedHashMap<>(parseSnapshot(change.getPayloadSnapshot()));
        snapshot.putIfAbsent("changeType", change.getChangeType());
        ExternalResourceMapping mapping = mappingRepository
                .findByIntegrationIdAndOrinResourceTypeAndOrinResourceId(
                        connection.getIntegrationId(), change.getOrinResourceType(), change.getOrinResourceId())
                .orElse(null);
        return ExternalResource.builder()
                .orinResourceType(SyncResourceType.valueOf(change.getOrinResourceType()))
                .orinResourceId(change.getOrinResourceId())
                .externalResourceType(mapping != null ? mapping.getExternalResourceType() : change.getOrinResourceType().toLowerCase())
                .externalResourceId(mapping != null ? mapping.getExternalResourceId() : null)
                .name(change.getResourceName())
                .canonicalSnapshot(snapshot)
                .rawSnapshot(snapshot)
                .contentHash(change.getPayloadHash() != null ? change.getPayloadHash() : change.getContentHash())
                .build();
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private void upsertMapping(IntegrationConnection connection, SyncChangeLog change, PushResult.PushResultItem item) {
        ExternalResourceMapping mapping = mappingRepository
                .findByIntegrationIdAndOrinResourceTypeAndOrinResourceId(
                        connection.getIntegrationId(), change.getOrinResourceType(), change.getOrinResourceId())
                .orElseGet(() -> ExternalResourceMapping.builder()
                        .integrationId(connection.getIntegrationId())
                        .platformType(connection.getPlatformType().name())
                        .orinResourceType(change.getOrinResourceType())
                        .orinResourceId(change.getOrinResourceId())
                        .syncDirection("PUSH")
                        .build());
        mapping.setExternalResourceType(item.getExternalResourceType());
        if (item.getExternalResourceId() != null && !item.getExternalResourceId().isBlank()) {
            mapping.setExternalResourceId(item.getExternalResourceId());
        }
        mapping.setExternalVersion(item.getExternalVersion());
        mapping.setLastSyncedHash(item.getContentHash() != null ? item.getContentHash() : change.getPayloadHash());
        mapping.setSyncStatus(item.getStatus().name());
        mapping.setRawSnapshot(change.getPayloadSnapshot());
        mappingRepository.save(mapping);
    }

    private SyncItem toSyncItem(SyncJob job, SyncChangeLog change, PushResult.PushResultItem item, PushResult result) {
        return SyncItem.builder()
                .syncJobId(job.getId())
                .orinResourceType(change.getOrinResourceType())
                .orinResourceId(change.getOrinResourceId())
                .externalResourceType(item != null ? item.getExternalResourceType() : null)
                .externalResourceId(item != null ? item.getExternalResourceId() : null)
                .changeLogId(change.getId())
                .status(item != null ? item.getStatus().name() : result.getStatus().name())
                .message(item != null ? item.getMessage() : result.getMessage())
                .contentHash(item != null ? item.getContentHash() : change.getPayloadHash())
                .build();
    }

    private String key(String resourceType, String resourceId) {
        return resourceType + ":" + resourceId;
    }
}
