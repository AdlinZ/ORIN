package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.*;
import com.adlin.orin.modules.integrationsync.repository.SyncJobRepository;
import com.adlin.orin.modules.integrationsync.spi.PlatformConnector;
import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.knowledge.repository.SyncRecordRepository;
import com.adlin.orin.modules.knowledge.service.ExternalIntegrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SyncOrchestrator {

    private final ExternalIntegrationService externalIntegrationService;
    private final PlatformConnectorRegistry connectorRegistry;
    private final SyncPlanner syncPlanner;
    private final SyncPublisher syncPublisher;
    private final SyncImporter syncImporter;
    private final SyncDiffService diffService;
    private final SyncChangeLogRepository changeLogRepository;
    private final SyncJobRepository syncJobRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SyncChangeLog recordLocalChange(
            Long integrationId,
            SyncResourceType resourceType,
            String resourceId,
            String resourceName,
            String changeType,
            Map<String, Object> canonicalSnapshot) {
        ExternalIntegration integration = externalIntegrationService.getIntegration(integrationId);
        String payload = toJson(canonicalSnapshot);
        String hash = diffService.canonicalHash(canonicalSnapshot);
        String idempotencyKey = integrationId + ":" + resourceType + ":" + Math.abs(resourceId.hashCode()) + ":" + changeType + ":" + hash;
        return changeLogRepository.findByIdempotencyKey(idempotencyKey)
                .orElseGet(() -> changeLogRepository.save(SyncChangeLog.builder()
                        .integrationId(integrationId)
                        .platformType(normalizePlatform(integration.getIntegrationType()).name())
                        .agentId(null)
                        .documentId(resourceId)
                        .knowledgeBaseId(integration.getKnowledgeBaseId())
                        .orinResourceType(resourceType.name())
                        .orinResourceId(resourceId)
                        .resourceName(resourceName)
                        .changeType(changeType)
                        .version(1)
                        .contentHash(hash)
                        .payloadHash(hash)
                        .payloadSnapshot(payload)
                        .changeSource("ORIN")
                        .syncStatus("PENDING")
                        .synced(false)
                        .idempotencyKey(idempotencyKey)
                        .build()));
    }

    @Transactional
    public SyncJob pushPendingChanges(Long integrationId) {
        ExternalIntegration integration = externalIntegrationService.getIntegration(integrationId);
        IntegrationConnection connection = connection(integration);
        PlatformConnector connector = connectorRegistry.require(connection.getPlatformType());
        List<SyncChangeLog> changes = changeLogRepository.findByIntegrationIdAndSyncStatusOrderByChangedAtAsc(integrationId, "PENDING");
        SyncJob job = createJob(connection, "PUSH", "CHANGELOG", "ALL");
        if (changes.isEmpty()) {
            return finishJob(job, SyncStatus.COMPLETED, 0, 0, 0, 0, "No pending changes");
        }
        try {
            PushResult result = syncPublisher.push(job, connection, connector, changes);
            long successCount = changes.stream().filter(change -> Boolean.TRUE.equals(change.getSynced())).count();
            long failureCount = changes.size() - successCount;
            SyncStatus status = failureCount == 0 ? SyncStatus.COMPLETED : SyncStatus.PARTIAL;
            writeSyncRecord(job, connection, status, "PUSH", "CHANGELOG", changes.size(), (int) successCount, (int) failureCount, 0, result.getMessage());
            return finishJob(job, status, changes.size(), (int) successCount, (int) failureCount, 0, result.getMessage());
        } catch (Exception ex) {
            writeSyncRecord(job, connection, SyncStatus.FAILED, "PUSH", "CHANGELOG", changes.size(), 0, changes.size(), 0, ex.getMessage());
            return finishJob(job, SyncStatus.FAILED, changes.size(), 0, changes.size(), 0, ex.getMessage());
        }
    }

    @Transactional
    public SyncJob pullFromExternal(Long integrationId, Set<SyncResourceType> requestedTypes, boolean fullSync) {
        ExternalIntegration integration = externalIntegrationService.getIntegration(integrationId);
        IntegrationConnection connection = connection(integration);
        PlatformConnector connector = connectorRegistry.require(connection.getPlatformType());
        Set<SyncResourceType> resourceTypes = syncPlanner.supportedPullResources(connector, requestedTypes);
        SyncJob job = createJob(connection, "PULL", "MANUAL", resourceTypes.isEmpty() ? "NONE" : "ALL");
        try {
            List<ExternalResource> resources = connector.pull(SyncPullRequest.builder()
                    .connection(connection)
                    .resourceTypes(resourceTypes)
                    .fullSync(fullSync)
                    .build());
            int imported = syncImporter.importResources(job, connection, resources);
            int conflictCount = Math.max(0, resources.size() - imported);
            SyncStatus status = conflictCount == 0 ? SyncStatus.COMPLETED : SyncStatus.PARTIAL;
            writeSyncRecord(job, connection, status, "PULL", "MANUAL", resources.size(), imported, 0, conflictCount, "Pulled external resources");
            return finishJob(job, status, resources.size(), imported, 0, conflictCount, "Pulled external resources");
        } catch (Exception ex) {
            writeSyncRecord(job, connection, SyncStatus.FAILED, "PULL", "MANUAL", 0, 0, 1, 0, ex.getMessage());
            return finishJob(job, SyncStatus.FAILED, 0, 0, 1, 0, ex.getMessage());
        }
    }

    public HealthCheckResult healthCheck(Long integrationId) {
        ExternalIntegration integration = externalIntegrationService.getIntegration(integrationId);
        IntegrationConnection connection = connection(integration);
        return connectorRegistry.require(connection.getPlatformType()).healthCheck(connection);
    }

    public List<PlatformCapabilities> capabilities() {
        return connectorRegistry.list().stream().map(PlatformConnector::capabilities).toList();
    }

    private IntegrationConnection connection(ExternalIntegration integration) {
        return IntegrationConnection.from(integration, parseMap(integration.getExtraConfig()));
    }

    private PlatformType normalizePlatform(String value) {
        try {
            return PlatformType.valueOf(value == null ? "CUSTOM" : value.trim().toUpperCase());
        } catch (Exception ignored) {
            return PlatformType.CUSTOM;
        }
    }

    private SyncJob createJob(IntegrationConnection connection, String direction, String triggerType, String scope) {
        return syncJobRepository.save(SyncJob.builder()
                .integrationId(connection.getIntegrationId())
                .platformType(connection.getPlatformType().name())
                .direction(direction)
                .triggerType(triggerType)
                .resourceScope(scope)
                .status(SyncStatus.RUNNING.name())
                .startedAt(LocalDateTime.now())
                .build());
    }

    private SyncJob finishJob(
            SyncJob job,
            SyncStatus status,
            int total,
            int success,
            int failure,
            int conflicts,
            String message) {
        job.setStatus(status.name());
        job.setCompletedAt(LocalDateTime.now());
        job.setTotalCount(total);
        job.setSuccessCount(success);
        job.setFailureCount(failure);
        job.setConflictCount(conflicts);
        job.setErrorMessage(status == SyncStatus.FAILED ? message : null);
        job.setDetails(message);
        return syncJobRepository.save(job);
    }

    private void writeSyncRecord(
            SyncJob job,
            IntegrationConnection connection,
            SyncStatus status,
            String direction,
            String triggerType,
            int total,
            int success,
            int failure,
            int conflicts,
            String message) {
        syncRecordRepository.save(SyncRecord.builder()
                .integrationId(connection.getIntegrationId())
                .platformType(connection.getPlatformType().name())
                .syncJobId(job.getId())
                .syncType("INCREMENTAL")
                .status(status == SyncStatus.FAILED ? "FAILED" : "COMPLETED")
                .startTime(job.getStartedAt())
                .endTime(LocalDateTime.now())
                .addedCount(success)
                .updatedCount(0)
                .deletedCount(0)
                .errorMessage(status == SyncStatus.FAILED ? message : null)
                .details(message)
                .direction(direction)
                .syncDirection("PUSH".equals(direction) ? "OUTBOUND" : "INBOUND")
                .triggerType(triggerType)
                .resourceScope(job.getResourceScope())
                .totalDocs(total)
                .conflictCount(conflicts)
                .build());
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private Map<String, Object> parseMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    @Data
    @Builder
    public static class RecordChangeRequest {
        private Long integrationId;
        private SyncResourceType resourceType;
        private String resourceId;
        private String resourceName;
        private String changeType;
        private Map<String, Object> canonicalSnapshot;
    }
}
