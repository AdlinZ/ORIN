package com.adlin.orin.modules.integrationsync.connector;

import com.adlin.orin.modules.integrationsync.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class CozeSyncConnector extends AbstractHttpPlatformConnector {

    public CozeSyncConnector(RestTemplate restTemplate, ObjectMapper objectMapper) {
        super(restTemplate, objectMapper);
    }

    @Override
    public PlatformType platform() {
        return PlatformType.COZE;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return PlatformCapabilities.builder()
                .platformType(platform())
                .secretValueSyncSupported(false)
                .notes("Coze v1 sync treats published bots/chatflows/workflows as external agent/tool capabilities; full editor sync is partial.")
                .resourceCapabilities(Map.of(
                        SyncResourceType.AGENT, Set.of(SyncCapability.PULL, SyncCapability.IMPORT, SyncCapability.INVOKE),
                        SyncResourceType.WORKFLOW, Set.of(SyncCapability.PULL, SyncCapability.IMPORT, SyncCapability.INVOKE),
                        SyncResourceType.TOOL, Set.of(SyncCapability.PULL, SyncCapability.IMPORT),
                        SyncResourceType.PUBLISH_STATUS, Set.of(SyncCapability.PULL),
                        SyncResourceType.CREDENTIAL_REF, Set.of(SyncCapability.CREDENTIAL_REFERENCE)
                ))
                .build();
    }

    @Override
    public List<ExternalResource> pull(SyncPullRequest request) {
        IntegrationConnection connection = request.getConnection();
        Set<SyncResourceType> requested = request.getResourceTypes() == null ? EnumSet.allOf(SyncResourceType.class) : request.getResourceTypes();
        List<ExternalResource> resources = new ArrayList<>();
        if (requested.contains(SyncResourceType.AGENT)) {
            resources.addAll(pullList(connection, "/v1/bots", "bots", SyncResourceType.AGENT, "bot"));
        }
        if (requested.contains(SyncResourceType.WORKFLOW)) {
            resources.addAll(pullList(connection, "/v1/workflows", "workflows", SyncResourceType.WORKFLOW, "workflow"));
        }
        return resources;
    }

    @Override
    public PushResult push(SyncPushRequest request) {
        return unsupportedPush(request, "Coze push is partial in this version; register ORIN resources as external API/tool capabilities instead of overwriting Coze editor state.");
    }

    @Override
    public HealthCheckResult healthCheck(IntegrationConnection connection) {
        return defaultHealthCheck(connection, "/v1/bots");
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
            List<Map<String, Object>> rows = readListPayload(asMap(response.getBody()), "data", listKey, "items");
            return rows.stream()
                    .map(row -> ExternalResource.builder()
                            .orinResourceType(resourceType)
                            .externalResourceType(externalType)
                            .externalResourceId(String.valueOf(row.getOrDefault("id", row.getOrDefault(externalType + "_id", ""))))
                            .name(String.valueOf(row.getOrDefault("name", row.getOrDefault("title", ""))))
                            .rawSnapshot(row)
                            .canonicalSnapshot(Map.of(
                                    "name", row.getOrDefault("name", row.getOrDefault("title", "")),
                                    "source", "COZE",
                                    "externalId", row.getOrDefault("id", row.getOrDefault(externalType + "_id", ""))
                            ))
                            .partial(true)
                            .compatibilityMessage("Coze resource imported as external runnable capability; editor-state conversion is partial.")
                            .build())
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
