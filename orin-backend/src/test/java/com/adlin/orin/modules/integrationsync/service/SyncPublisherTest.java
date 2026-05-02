package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.repository.ExternalResourceMappingRepository;
import com.adlin.orin.modules.integrationsync.repository.SyncItemRepository;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyncPublisherTest {

    private final ExternalResourceMappingRepository mappingRepository = mock(ExternalResourceMappingRepository.class);
    private final SyncChangeLogRepository changeLogRepository = mock(SyncChangeLogRepository.class);
    private final SyncItemRepository syncItemRepository = mock(SyncItemRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SyncPublisher publisher = new SyncPublisher(
            mappingRepository,
            changeLogRepository,
            syncItemRepository,
            objectMapper);

    @Test
    void push_ShouldMarkChangeSyncedAndReuseExistingMapping() {
        IntegrationConnection connection = IntegrationConnection.builder()
                .integrationId(7L)
                .platformType(PlatformType.N8N)
                .baseUrl("http://n8n.test")
                .syncDirection(SyncDirectionMode.PUSH)
                .build();
        SyncChangeLog change = SyncChangeLog.builder()
                .id(11L)
                .integrationId(7L)
                .platformType("N8N")
                .orinResourceType("WORKFLOW")
                .orinResourceId("wf-1")
                .resourceName("Demo")
                .changeType("UPDATED")
                .version(1)
                .payloadHash("hash-1")
                .payloadSnapshot("{\"name\":\"Demo\"}")
                .syncStatus("PENDING")
                .synced(false)
                .build();
        ExternalResourceMapping existing = ExternalResourceMapping.builder()
                .id(99L)
                .integrationId(7L)
                .platformType("N8N")
                .orinResourceType("WORKFLOW")
                .orinResourceId("wf-1")
                .externalResourceType("workflow")
                .externalResourceId("ext-1")
                .lastSyncedHash("old")
                .build();

        when(mappingRepository.findByIntegrationIdAndOrinResourceTypeAndOrinResourceId(7L, "WORKFLOW", "wf-1"))
                .thenReturn(Optional.of(existing));

        PushResult result = publisher.push(
                SyncJob.builder().id(5L).integrationId(7L).platformType("N8N").build(),
                connection,
                new SuccessfulConnector(),
                List.of(change));

        assertTrue(result.isSuccess());
        assertTrue(change.getSynced());
        assertEquals("SYNCED", change.getSyncStatus());
        verify(changeLogRepository).save(change);
        verify(mappingRepository).save(argThat(mapping ->
                mapping.getId().equals(99L)
                        && mapping.getExternalResourceId().equals("ext-1")
                        && mapping.getLastSyncedHash().equals("hash-1")
                        && mapping.getSyncStatus().equals("COMPLETED")));
        verify(syncItemRepository).save(any());
    }

    private static class SuccessfulConnector implements PlatformConnector {
        @Override
        public PlatformType platform() {
            return PlatformType.N8N;
        }

        @Override
        public PlatformCapabilities capabilities() {
            return PlatformCapabilities.builder().platformType(PlatformType.N8N).build();
        }

        @Override
        public List<ExternalResource> pull(SyncPullRequest request) {
            return List.of();
        }

        @Override
        public PushResult push(SyncPushRequest request) {
            ExternalResource resource = request.getResources().get(0);
            return PushResult.builder()
                    .success(true)
                    .status(SyncStatus.COMPLETED)
                    .message("ok")
                    .items(List.of(PushResult.PushResultItem.builder()
                            .orinResourceType(resource.getOrinResourceType())
                            .orinResourceId(resource.getOrinResourceId())
                            .externalResourceType("workflow")
                            .externalResourceId(resource.getExternalResourceId())
                            .status(SyncStatus.COMPLETED)
                            .message("ok")
                            .contentHash(resource.getContentHash())
                            .build()))
                    .build();
        }

        @Override
        public HealthCheckResult healthCheck(IntegrationConnection connection) {
            return HealthCheckResult.builder().healthy(true).build();
        }
    }
}
