package com.adlin.orin.modules.agent.freeze;

import com.adlin.orin.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F02 draft read ownership ACL integration tests against real MySQL.
 *
 * <p>Verifies that {@code AgentDraftService.getDraft()} enforces the
 * resource-level ownership check: non-privileged users can only read
 * drafts of agents they own.
 */
@DisplayName("F02 Agent Draft Ownership ACL Integration")
class AgentDraftOwnershipIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JdbcTemplate db;

    // Agent owned by test-creator (id=100)
    private static final String AGENT_ID = "ag_draft_own_test";

    // Agent owned by test-creator-2 (id=102)
    private static final String AGENT_CREATOR2_ID = "ag_draft_own_c2";

    @BeforeEach
    void seedAgents() {
        // Agent owned by test-creator
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)",
                AGENT_ID, Long.parseLong(CREATOR_ID), "Draft Ownership Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        // Agent owned by test-creator-2
        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name)",
                AGENT_CREATOR2_ID, Long.parseLong(CREATOR2_ID), "Creator2 Draft Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);
    }

    @Test
    @DisplayName("Owner (ROLE_USER) can read own draft")
    void ownerCanReadOwnDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(AGENT_ID));
    }

    @Test
    @DisplayName("Non-owner (ROLE_USER) cannot read another user's draft — 403 FORBIDDEN")
    void nonOwnerCannotReadDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_CREATOR2_ID)
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    @Test
    @DisplayName("Unauthenticated cannot read draft — 401")
    void unauthenticatedCannotReadDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID))
                .andExpect(status().isUnauthorized());
    }
}
