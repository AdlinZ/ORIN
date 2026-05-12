package com.adlin.orin.modules.workflow.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowMcpExposureMigrationTest {

    @Test
    void migrationContainsOwnerBackfillAndMcpExposureSemantics() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V86__Workflow_Owner_And_Mcp_Exposure.sql"));

        assertTrue(migration.contains("owner_user_id BIGINT"));
        assertTrue(migration.contains("mcp_exposed BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(migration.contains("w.created_by = CAST(u.user_id AS CHAR)"));
        assertTrue(migration.contains("w.created_by = u.username"));
        assertTrue(migration.contains("ROLE_SUPER_ADMIN"));
        assertTrue(migration.contains("ROLE_ADMIN"));
        assertTrue(migration.contains("MODIFY COLUMN owner_user_id BIGINT NOT NULL"));
        assertTrue(migration.contains("idx_workflows_owner_mcp_exposed"));
    }
}
