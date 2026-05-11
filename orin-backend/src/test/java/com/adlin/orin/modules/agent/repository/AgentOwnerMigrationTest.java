package com.adlin.orin.modules.agent.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentOwnerMigrationTest {

    @Test
    void migrationContainsOwnerBackfillAndMcpExposureSemantics() throws Exception {
        // DDL executability is covered by Flyway against production MySQL; this unit test
        // validates the migration semantics without rewriting MySQL SQL for H2.
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V84__Agent_Owner_And_Mcp_Exposure.sql"));

        assertTrue(migration.contains("owner_user_id BIGINT"));
        assertTrue(migration.contains("mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(migration.contains("UPDATE agent_metadata"));
        assertTrue(migration.contains("ROLE_SUPER_ADMIN"));
        assertTrue(migration.contains("ROLE_ADMIN"));
        assertTrue(migration.contains("MODIFY COLUMN owner_user_id BIGINT NOT NULL"));
        assertTrue(migration.contains("idx_agent_metadata_owner_user_id"));
        assertTrue(migration.contains("idx_agent_metadata_owner_mcp_exposed"));
    }
}
