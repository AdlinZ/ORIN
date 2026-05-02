package com.adlin.orin.modules.integrationsync.connector;

import com.adlin.orin.modules.integrationsync.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class N8nSyncConnector extends AbstractHttpPlatformConnector {

    public N8nSyncConnector(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate, objectMapper);
    }

    @Override
    public PlatformType platform() {
        return PlatformType.N8N;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return PlatformCapabilities.builder()
                .platformType(platform())
                .secretValueSyncSupported(false)
                .notes("n8n connector syncs workflows, executions, variable metadata, tags, and credential references without secret values.")
                .resourceCapabilities(Map.of(
                        SyncResourceType.WORKFLOW, Set.of(SyncCapability.PULL, SyncCapability.PUSH, SyncCapability.IMPORT, SyncCapability.EXPORT),
                        SyncResourceType.EXECUTION, Set.of(SyncCapability.PULL, SyncCapability.EXECUTION_READ),
                        SyncResourceType.VARIABLE, Set.of(SyncCapability.PULL, SyncCapability.PUSH),
                        SyncResourceType.CREDENTIAL_REF, Set.of(SyncCapability.PULL, SyncCapability.CREDENTIAL_REFERENCE),
                        SyncResourceType.PUBLISH_STATUS, Set.of(SyncCapability.PULL, SyncCapability.PUSH)
                ))
                .build();
    }

    @Override
    public List<ExternalResource> pull(SyncPullRequest request) {
        IntegrationConnection connection = request.getConnection();
        Set<SyncResourceType> requested = request.getResourceTypes() == null ? EnumSet.allOf(SyncResourceType.class) : request.getResourceTypes();
        List<ExternalResource> resources = new ArrayList<>();
        if (requested.contains(SyncResourceType.WORKFLOW)) {
            resources.addAll(pullList(connection, "/api/v1/workflows", "workflows", SyncResourceType.WORKFLOW, "workflow"));
        }
        if (requested.contains(SyncResourceType.EXECUTION)) {
            resources.addAll(pullList(connection, "/api/v1/executions", "results", SyncResourceType.EXECUTION, "execution"));
        }
        if (requested.contains(SyncResourceType.CREDENTIAL_REF)) {
            resources.addAll(pullList(connection, "/api/v1/credentials", "data", SyncResourceType.CREDENTIAL_REF, "credential"));
        }
        if (requested.contains(SyncResourceType.VARIABLE)) {
            resources.addAll(pullList(connection, "/api/v1/variables", "data", SyncResourceType.VARIABLE, "variable"));
        }
        return resources;
    }

    @Override
    public PushResult push(SyncPushRequest request) {
        List<PushResult.PushResultItem> items = request.getResources().stream()
                .map(resource -> {
                    if (resource.getOrinResourceType() != SyncResourceType.WORKFLOW && resource.getOrinResourceType() != SyncResourceType.VARIABLE) {
                        return PushResult.PushResultItem.builder()
                                .orinResourceType(resource.getOrinResourceType())
                                .orinResourceId(resource.getOrinResourceId())
                                .externalResourceType(resource.getExternalResourceType())
                                .externalResourceId(resource.getExternalResourceId())
                                .status(SyncStatus.SKIPPED)
                                .message("n8n push supports workflow and variable resources in this version")
                                .contentHash(resource.getContentHash())
                                .build();
                    }
                    return PushResult.PushResultItem.builder()
                            .orinResourceType(resource.getOrinResourceType())
                            .orinResourceId(resource.getOrinResourceId())
                            .externalResourceType(resource.getExternalResourceType() != null ? resource.getExternalResourceType() : resource.getOrinResourceType().name().toLowerCase())
                            .externalResourceId(resource.getExternalResourceId())
                            .status(SyncStatus.PARTIAL)
                            .message("n8n export snapshot prepared; credential references are preserved without secret values")
                            .contentHash(resource.getContentHash())
                            .build();
                })
                .toList();
        boolean success = items.stream().anyMatch(item -> item.getStatus() != SyncStatus.SKIPPED);
        return PushResult.builder()
                .success(success)
                .status(success ? SyncStatus.PARTIAL : SyncStatus.SKIPPED)
                .message("n8n push completed as export snapshot")
                .items(items)
                .build();
    }

    @Override
    public HealthCheckResult healthCheck(IntegrationConnection connection) {
        return defaultHealthCheck(connection, "/api/v1/workflows?limit=1");
    }

    private List<ExternalResource> pullList(
            IntegrationConnection connection,
            String path,
            String listKey,
            SyncResourceType resourceType,
            String externalType) {
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    connection.getBaseUrl() + path,
                    HttpMethod.GET,
                    new HttpEntity<>(headers(connection)),
                    Map.class);
            List<Map<String, Object>> rows = readListPayload(asMap(response.getBody()), "data", listKey, "results");
            return rows.stream()
                    .map(row -> ExternalResource.builder()
                            .orinResourceType(resourceType)
                            .externalResourceType(externalType)
                            .externalResourceId(String.valueOf(row.get("id")))
                            .name(String.valueOf(row.getOrDefault("name", row.get("id"))))
                            .rawSnapshot(row)
                            .canonicalSnapshot(Map.of(
                                    "name", row.getOrDefault("name", ""),
                                    "source", "N8N",
                                    "externalId", row.getOrDefault("id", "")
                            ))
                            .partial(resourceType == SyncResourceType.CREDENTIAL_REF)
                            .compatibilityMessage(resourceType == SyncResourceType.CREDENTIAL_REF ? "Credential secret values are intentionally not imported." : null)
                            .build())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
