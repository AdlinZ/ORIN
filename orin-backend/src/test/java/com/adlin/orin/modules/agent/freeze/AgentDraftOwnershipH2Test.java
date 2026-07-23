package com.adlin.orin.modules.agent.freeze;

import com.adlin.orin.H2SecurityIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F02 draft read ownership ACL — H2 + real Spring Security chain.
 *
 * <p>Verifies that {@code AgentDraftService.getDraft()} enforces the
 * resource-level ownership check: non-privileged users can only read
 * drafts of agents they own.
 */
@DisplayName("F02 Draft Ownership ACL (H2 + Real Spring Security)")
class AgentDraftOwnershipH2Test extends H2SecurityIntegrationTest {

    private static final String AGENT_ID = "ag_draft_h2";
    private static final String AGENT_C2_ID = "ag_draft_c2_h2";

    @BeforeEach
    void seedAgents() {
        // Clean up from previous test methods
        db.update("DELETE FROM agent_metadata WHERE agent_id IN (?, ?)", AGENT_ID, AGENT_C2_ID);

        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                AGENT_ID, Long.parseLong(CREATOR_ID), "Draft Owner Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);

        db.update("INSERT INTO agent_metadata (agent_id, owner_user_id, name, description, mode, "
                        + "model_name, provider_type, system_prompt, temperature, mcp_exposed) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                AGENT_C2_ID, Long.parseLong(CREATOR2_ID), "Creator2 Draft Agent", "desc",
                "agent", "gpt-4o", "OPENAI", "You are helpful", 0.7, false);
    }

    @Test
    @DisplayName("10. Owner (ROLE_USER) can read own draft — 200")
    void ownerCanReadOwnDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID)
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentId").value(AGENT_ID));
    }

    @Test
    @DisplayName("11. Non-owner cannot read another's draft — 403 FORBIDDEN(10004)")
    void nonOwnerCannotReadOthersDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_C2_ID)
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
    }

    @Test
    @DisplayName("12. Unauthenticated cannot read draft — 401")
    void unauthenticatedCannotReadDraft() throws Exception {
        mockMvc.perform(get("/api/v1/agents/{agentId}/draft", AGENT_ID))
                .andExpect(status().isUnauthorized());
    }
}
