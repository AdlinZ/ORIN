package com.adlin.orin.modules.integrationsync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SyncDiffServiceTest {

    private final SyncDiffService service = new SyncDiffService(new ObjectMapper());

    @Test
    void canonicalHash_ShouldBeStableForSameSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", "wf-1");
        snapshot.put("name", "Workflow");

        String first = service.canonicalHash(snapshot);
        String second = service.canonicalHash(snapshot);

        assertNotNull(first);
        assertEquals(first, second);
        assertFalse(service.drifted(first, second));
    }

    @Test
    void drifted_ShouldDetectDifferentHashesOnlyWhenBothExist() {
        assertTrue(service.drifted("a", "b"));
        assertFalse(service.drifted(null, "b"));
        assertFalse(service.drifted("a", null));
    }
}
