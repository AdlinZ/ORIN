package com.adlin.orin.modules.integrationsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

@Service
public class SyncDiffService {

    private final ObjectMapper objectMapper;

    public SyncDiffService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String canonicalHash(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        try {
            byte[] payload = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(snapshot)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate sync hash", e);
        }
    }

    public boolean drifted(String localHash, String externalHash) {
        return localHash != null && externalHash != null && !localHash.equals(externalHash);
    }
}
