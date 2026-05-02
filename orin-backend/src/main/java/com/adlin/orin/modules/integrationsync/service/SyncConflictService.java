package com.adlin.orin.modules.integrationsync.service;

import com.adlin.orin.modules.integrationsync.entity.SyncConflict;
import com.adlin.orin.modules.integrationsync.model.ExternalResource;
import com.adlin.orin.modules.integrationsync.model.IntegrationConnection;
import com.adlin.orin.modules.integrationsync.repository.SyncConflictRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SyncConflictService {

    private final SyncConflictRepository conflictRepository;
    private final ObjectMapper objectMapper;

    public SyncConflict recordExternalDrift(
            IntegrationConnection connection,
            ExternalResource resource,
            String localHash,
            String externalHash,
            String localSnapshot) {
        return conflictRepository.save(SyncConflict.builder()
                .integrationId(connection.getIntegrationId())
                .platformType(connection.getPlatformType().name())
                .orinResourceType(resource.getOrinResourceType().name())
                .orinResourceId(resource.getOrinResourceId())
                .conflictType("EXTERNAL_DRIFT")
                .status("OPEN")
                .localHash(localHash)
                .externalHash(externalHash)
                .message("External resource changed after last ORIN sync; ORIN remains the source of truth.")
                .localSnapshot(localSnapshot)
                .externalSnapshot(toJson(resource.getRawSnapshot()))
                .build());
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
