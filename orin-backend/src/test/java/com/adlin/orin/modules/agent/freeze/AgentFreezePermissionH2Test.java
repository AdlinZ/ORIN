package com.adlin.orin.modules.agent.freeze;

import com.adlin.orin.H2SecurityIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F02 permission failure path tests — H2 + real Spring Security chain.
 *
 * <p>Verifies that authorization is enforced at both layers:
 * <ul>
 *   <li>Controller: {@code @PreAuthorize} role gates</li>
 *   <li>Service: ownership ACL via {@code assertCanManage()}</li>
 * </ul>
 *
 * <p>No Docker required. All 9 test scenarios cover:
 * <ul>
 *   <li>ROLE_USER blocked from OPERATOR-only endpoints (403)</li>
 *   <li>Cross-owner access denied (403 FORBIDDEN)</li>
 *   <li>Active version deprecation rejected (409)</li>
 *   <li>Unauthenticated access denied (401)</li>
 *   <li>Positive controls: Operator CAN perform lifecycle ops</li>
 * </ul>
 */
@DisplayName("F02 Permission Failure Paths (H2 + Real Spring Security)")
class AgentFreezePermissionH2Test extends H2SecurityIntegrationTest {

    private static final String AGENT_ID = "ag_perm_h2";
    private static final String AGENT_C2_ID = "ag_c2_perm_h2";
    private String versionV1Id;
    private String versionV2Id;

    @BeforeEach
    void seedAgents() {
        // Clean up from previous test methods
        db.update("DELETE FROM agent_versions WHERE agent_id IN (?, ?)", AGENT_ID, AGENT_C2_ID);
        db.update("DELETE FROM agent_metadata WHERE agent_id IN (?, ?)", AGENT_ID, AGENT_C2_ID);

        // Agent owned by test-creator (id=100)
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                AGENT_ID, Long.parseLong(CREATOR_ID), "Perm Test Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        // Agent owned by test-creator-2 (id=102)
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                AGENT_C2_ID, Long.parseLong(CREATOR2_ID), "Creator2 Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        // Version v1 (will be set as active)
        versionV1Id = "ver_h2_v1_" + UUID.randomUUID().toString().substring(0, 6);
        LocalDateTime now = LocalDateTime.now();
        db.update("INSERT INTO agent_versions (id, agent_id, version_number, status, "
                        + "config_snapshot, content_digest, snapshot_schema_version, "
                        + "frozen_at, frozen_by, created_by, created_at, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                versionV1Id, AGENT_ID, 1, "FROZEN",
                "{}", "a".repeat(64), (short) 1,
                now, CREATOR_USERNAME, CREATOR_USERNAME, now, false);

        // Version v2 (non-active, can be deprecated)
        versionV2Id = "ver_h2_v2_" + UUID.randomUUID().toString().substring(0, 6);
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

    // ============================================================
    // @PreAuthorize(OPERATOR_ROLES) — ROLE_USER rejected (403)
    // ============================================================

    @Test
    @DisplayName("1. ROLE_USER cannot switch active — 403")
    void userCannotSwitchActive() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("version_id", versionV2Id));

        mockMvc.perform(put("/api/v1/agents/{agentId}/active-version", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("2. ROLE_USER cannot deprecate — 403")
    void userCannotDeprecate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "no longer needed"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV2Id)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Ownership ACL — cross-owner access denied (403 FORBIDDEN)
    // ============================================================

    @Test
    @DisplayName("3. Creator cannot freeze another's agent — 403 FORBIDDEN(10004)")
    void creatorCannotFreezeOthersAgent() throws Exception {
        mockMvc.perform(post("/api/v1/agents/{agentId}/versions", AGENT_C2_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .header("Idempotency-Key", "h2-ownership-test")
                        .contentType("application/json"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    @Test
    @DisplayName("4. Creator cannot upsert draft on another's agent — 403 FORBIDDEN(10004)")
    void creatorCannotUpsertDraftOnOthersAgent() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "hijacked", "systemPrompt", "evil"));

        mockMvc.perform(put("/api/v1/agents/{agentId}/draft", AGENT_C2_ID)
                        .header("Authorization", "Bearer " + jwtCreator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    // ============================================================
    // Positive controls — Operator CAN perform lifecycle operations
    // ============================================================

    @Test
    @DisplayName("5. ROLE_OPERATOR can switch active — 200")
    void operatorCanSwitchActive() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("version_id", versionV2Id));

        mockMvc.perform(put("/api/v1/agents/{agentId}/active-version", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent_version_id").value(versionV2Id));
    }

    @Test
    @DisplayName("6. ROLE_OPERATOR can deprecate non-active version — 200")
    void operatorCanDeprecateNonActive() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "outdated"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV2Id)
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEPRECATED"));
    }

    // ============================================================
    // Business rule: cannot deprecate active version (409 CONFLICT)
    // ============================================================

    @Test
    @DisplayName("7. Cannot deprecate active version — 409 RUN_VERSION_RETIRED(30009)")
    void cannotDeprecateActiveVersion() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("reason", "want to retire active"));

        mockMvc.perform(post("/api/v1/agents/{agentId}/versions/{vid}/deprecate",
                        AGENT_ID, versionV1Id) // v1 IS the active version!
                        .header("Authorization", "Bearer " + jwtOperator())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("30009"));
    }

    // ============================================================
    // Unauthenticated — 401 UNAUTHORIZED
    // ============================================================

    @Test
    @DisplayName("8. Unauthenticated GET draft — 401")
    void unauthenticatedGetDraftReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. Unauthenticated POST freeze — 401")
    void unauthenticatedPostFreezeReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/agents/{agentId}/versions", AGENT_ID)
                        .header("Idempotency-Key", "no-auth-key")
                        .contentType("application/json"))
                .andExpect(status().isUnauthorized());
    }
}
