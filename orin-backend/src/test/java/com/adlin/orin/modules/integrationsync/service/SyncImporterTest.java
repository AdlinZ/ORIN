package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.integrationsync.repository.SyncItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncImporterTest {

    private final ExternalResourceMappingRepository mappingRepository = mock(ExternalResourceMappingRepository.class);
    private final SyncItemRepository syncItemRepository = mock(SyncItemRepository.class);
    private final SyncDiffService diffService = new SyncDiffService(new ObjectMapper());
    private final SyncConflictService conflictService = mock(SyncConflictService.class);
    private final SyncImporter importer = new SyncImporter(
            mappingRepository,
            syncItemRepository,
            diffService,
            conflictService,
            new ObjectMapper());

    @Test
    void importResources_ShouldRecordExternalDriftWithoutOverwritingSourceOfTruth() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(3L)
                .platformType(PlatformType.DIFY)
                .build();
        ExternalResource resource = ExternalResource.builder()
                .orinResourceType(SyncResourceType.WORKFLOW)
                .externalResourceType("app")
                .externalResourceId("app-1")
                .name("External App")
                .canonicalSnapshot(Map.of("name", "External App v2"))
                .rawSnapshot(Map.of("id", "app-1", "name", "External App v2"))
                .build();
        ExternalResourceMapping existing = ExternalResourceMapping.builder()
                .id(1L)
                .integrationId(3L)
                .platformType("DIFY")
                .orinResourceType("WORKFLOW")
                .orinResourceId("wf-1")
                .externalResourceType("app")
                .externalResourceId("app-1")
                .lastSyncedHash("old-hash")
                .rawSnapshot("{\"name\":\"External App v1\"}")
                .build();

        when(mappingRepository.findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(3L, "DIFY", "app", "app-1"))
                .thenReturn(Optional.of(existing));

        int imported = importer.importResources(
                SyncJob.builder().id(6L).integrationId(3L).platformType("DIFY").build(),
                connection,
                List.of(resource));

        assertEquals(0, imported);
        verify(conflictService).recordExternalDrift(eq(connection), eq(resource), eq("old-hash"), any(), any());
        verify(mappingRepository).save(argThat(mapping ->
                "EXTERNAL_DRIFT".equals(mapping.getSyncStatus())
                        && "old-hash".equals(mapping.getLastSyncedHash())));
        verify(syncItemRepository).save(any());
    }

    @Test
    void importResources_ShouldScopeExternalMappingLookupToIntegration() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(4L)
                .platformType(PlatformType.DIFY)
                .build();
        ExternalResource resource = ExternalResource.builder()
                .orinResourceType(SyncResourceType.WORKFLOW)
                .externalResourceType("app")
                .externalResourceId("shared-app")
                .name("Shared App")
                .canonicalSnapshot(Map.of("name", "Shared App"))
                .rawSnapshot(Map.of("id", "shared-app", "name", "Shared App"))
                .build();

        when(mappingRepository.findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(
                4L, "DIFY", "app", "shared-app"))
                .thenReturn(Optional.empty());

        int imported = importer.importResources(
                SyncJob.builder().id(7L).integrationId(4L).platformType("DIFY").build(),
                connection,
                List.of(resource));

        assertEquals(1, imported);
        verify(mappingRepository).findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(
                4L, "DIFY", "app", "shared-app");
        verify(mappingRepository).save(argThat(mapping ->
                Long.valueOf(4L).equals(mapping.getIntegrationId())
                        && "external:app:shared-app".equals(mapping.getOrinResourceId())
                        && "COMPLETED".equals(mapping.getSyncStatus())
                        && mapping.getLastSyncedHash() != null));
    }
}
