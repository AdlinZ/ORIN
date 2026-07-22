package com.adlin.orin.modules.agent.freeze;

import com.adlin.orin.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F02 permission failure path integration tests against real MySQL (Testcontainers).
 *
 * <p>Exercises the full Spring Security → @PreAuthorize → Controller → Service → MySQL chain
 * with real JWT tokens carrying different roles. Each test proves that unauthorized
 * requests are correctly rejected at the right layer with the right HTTP status.
 */
@DisplayName("F02 Agent Freeze Permission Integration")
class AgentFreezePermissionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate db;

    // Agent owned by test-creator (id=100), with two frozen versions
    private static final String AGENT_ID = "ag_perm_test";
    private String versionV1Id; // active version
    private String versionV2Id; // non-active version

    // Agent owned by test-creator-2 (id=102)
    private static final String AGENT_CREATOR2_ID = "ag_creator2_test";

    @BeforeEach
    void seedAgentsAndVersions() {
        // Seed creator's agent with minimal required fields
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)",
                AGENT_ID, Long.parseLong(CREATOR_ID), "Perm Test Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        // Seed creator-2's agent
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)",
                AGENT_CREATOR2_ID, Long.parseLong(CREATOR2_ID), "Creator2 Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        // Create version v1 (active)
        versionV1Id = "ver_perm_v1_" + UUID.randomUUID().toString().substring(0, 8);
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO agent_versions (id, agent_id, version_number, status, "
                        + "config_snapshot, content_digest, snapshot_schema_version, "
                        + "frozen_at, frozen_by, created_by, created_at, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                versionV1Id, AGENT_ID, 1, "FROZEN",
                "{}", "a".repeat(64), (short) 1,
                now, CREATOR_USERNAME, CREATOR_USERNAME, now, false);

        // Create version v2 (non-active, for deprecation tests)
        versionV2Id = "ver_perm_v2_" + UUID.randomUUID().toString().substring(0, 8);
        db.update("INSERT INTO agent_versions (id, agent_id, version_number, status, "
                        + "config_snapshot, content_digest, snapshot_schema_version, "
                        + "frozen_at, frozen_by, created_by, created_at, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                versionV2Id, AGENT_ID, 2, "FROZEN",
                "{}", "b".repeat(64), (short) 1,
                now, CREATOR_USERNAME, CREATOR_USERNAME, now, false);

        // Set v1 as active
        db.update("UPDATE agent_metadata SET active_version_id = ? WHERE agent_id = ?",
                versionV1Id, AGENT_ID);
    }

    // ================================================================
    // @PreAuthorize(OPERATOR_ROLES) — ROLE_USER should be DENIED (403)
    // ================================================================

    @Test
    @DisplayName("ROLE_USER cannot switch active version (OPERATOR_ROLES required)")
    void userCannotSwitchActive() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("version_id", versionV2Id));

        mockMvc.perform(put("/api/v1/agents/{agentId}/active-version", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ROLE_USER cannot deprecate a version (OPERATOR_ROLES required)")
    void userCannotDeprecate() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("reason", "no longer needed"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV2Id)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ================================================================
    // Ownership ACL — cross-owner access DENIED (403 FORBIDDEN 10004)
    // ================================================================

    @Test
    @DisplayName("Creator cannot freeze another user's agent (ownership ACL)")
    void creatorCannotFreezeOthersAgent() throws Exception {
        mockMvc.perform(post("/api/v1/agents/{agentId}/versions", AGENT_CREATOR2_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .header("Idempotency-Key", "test-key-ownership-1")
                        .contentType("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    @Test
    @DisplayName("Creator cannot upsert draft on another user's agent (ownership ACL)")
    void creatorCannotUpsertDraftOnOthersAgent() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "hijacked", "systemPrompt", "evil"));

        mockMvc.perform(put("/api/v1/agents/{agentId}/draft", AGENT_CREATOR2_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    // ================================================================
    // Positive controls — Operator CAN perform lifecycle operations
    // ================================================================

    @Test
    @DisplayName("ROLE_OPERATOR can switch active version")
    void operatorCanSwitchActive() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("version_id", versionV2Id));

        mockMvc.perform(put("/api/v1/agents/{agentId}/active-version", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentVersionId").value(versionV2Id));
    }

    @Test
    @DisplayName("ROLE_OPERATOR can deprecate a non-active version")
    void operatorCanDeprecateNonActive() throws Exception {
        // First ensure v2 is NOT active (switch back to v1 if needed)
        // v2 was created as non-active, so it should be fine
        String body = objectMapper.writeValueAsString(
                Map.of("reason", "outdated"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV2Id)
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));
    }

    // ================================================================
    // Business rule: cannot deprecate active version (409 CONFLICT)
    // ================================================================

    @Test
    @DisplayName("Cannot deprecate the current active version — 409 RUN_VERSION_RETIRED")
    void cannotDeprecateActiveVersion() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("reason", "want to deprecate active"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV1Id) // v1 IS active
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict()) // 409
                .andExpect(jsonPath("$.code").value("30009")); // RUN_VERSION_RETIRED
    }

    // ================================================================
    // Unauthenticated — 401 UNAUTHORIZED
    // ================================================================

    @Test
    @DisplayName("Unauthenticated GET draft returns 401")
    void unauthenticatedGetDraftReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Unauthenticated POST freeze returns 401")
    void unauthenticatedPostFreezeReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/agents/{agentId}/versions", AGENT_ID)
                        .header("Idempotency-Key", "no-auth-key")
                        .contentType("application/json"))
                .andExpect(status().isUnauthorized());
    }
}
