package com.adlin.orin.modules.integrationsync.controller;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import com.adlin.orin.modules.integrationsync.model.HealthCheckResult;
import com.adlin.orin.modules.integrationsync.model.PlatformCapabilities;
import com.adlin.orin.modules.integrationsync.model.SyncResourceType;
import com.adlin.orin.modules.integrationsync.service.SyncOrchestrator;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/integration-sync")
@RequiredArgsConstructor
public class IntegrationSyncController {

    private final SyncOrchestrator syncOrchestrator;

    @GetMapping("/capabilities")
    public Result<List<PlatformCapabilities>> capabilities() {
        return Result.success(syncOrchestrator.capabilities());
    }

    @PostMapping("/integrations/{integrationId}/changes")
    public Result<SyncChangeLog> recordLocalChange(
            @PathVariable Long integrationId,
            @RequestBody RecordChangePayload payload) {
        SyncChangeLog changeLog = syncOrchestrator.recordLocalChange(
                integrationId,
                payload.getResourceType(),
                payload.getResourceId(),
                payload.getResourceName(),
                payload.getChangeType() == null ? "UPDATED" : payload.getChangeType(),
                payload.getCanonicalSnapshot());
        return Result.success(changeLog);
    }

    @PostMapping("/integrations/{integrationId}/push-pending")
    public Result<SyncJob> pushPending(@PathVariable Long integrationId) {
        return Result.success(syncOrchestrator.pushPendingChanges(integrationId));
    }

    @PostMapping("/integrations/{integrationId}/pull")
    public Result<SyncJob> pull(
            @PathVariable Long integrationId,
            @RequestBody(required = false) PullPayload payload) {
        Set<SyncResourceType> resourceTypes = payload == null ? null : payload.getResourceTypes();
        boolean fullSync = payload != null && payload.isFullSync();
        return Result.success(syncOrchestrator.pullFromExternal(integrationId, resourceTypes, fullSync));
    }

    @GetMapping("/integrations/{integrationId}/health")
    public Result<HealthCheckResult> health(@PathVariable Long integrationId) {
        return Result.success(syncOrchestrator.healthCheck(integrationId));
    }

    @Data
    public static class RecordChangePayload {
        private SyncResourceType resourceType;
        private String resourceId;
        private String resourceName;
        private String changeType;
        private Map<String, Object> canonicalSnapshot;
    }

    @Data
    public static class PullPayload {
        private Set<SyncResourceType> resourceTypes;
        private boolean fullSync;
    }
}
