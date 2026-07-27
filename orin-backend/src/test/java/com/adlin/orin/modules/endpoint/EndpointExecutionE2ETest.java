package com.adlin.orin.modules.endpoint;

import com.adlin.orin.BaseIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F05 Endpoint execution E2E test.
 *
 * <p>Verifies the full publish closed loop:
 * frozen AgentVersion → published Endpoint → API Key assigned →
 * external REST call → Run created with traceId → audit record.
 *
 * <p>Uses real database (Testcontainers MySQL), MockMvc with full filter chain.
 */
@DisplayName("F05 Endpoint Execution E2E")
class EndpointExecutionE2ETest extends BaseIntegrationTest {

    private static final BCryptPasswordEncoder BCrypt = new BCryptPasswordEncoder();

    @Autowired
    private JdbcTemplate jdbc;

    // Test fixture IDs
    private String agentId;
    private String versionId;
    private String runnerId;
    private String endpointId;
    private String apiKeyId;
    /** The raw API key string that the test client sends. */
    private String rawApiKey;

    @BeforeEach
    void seedFixture() {
        agentId = "agent_" + shortUuid();
        versionId = "ver_" + shortUuid();
        runnerId = "runner_" + shortUuid();
        endpointId = "ep_" + shortUuid();
        apiKeyId = "gsec_" + shortUuid();

        // 1) Agent
        jdbc.update(
                "INSERT INTO agent_metadata (agent_id, name, mode, owner_user_id, mcp_exposed) "
                        + "VALUES (?, 'E2E Test Agent', 'chat', ?, false)",
                agentId, Long.parseLong(CREATOR_ID));

        // 2) Frozen AgentVersion
        jdbc.update(
                "INSERT INTO agent_versions (id, agent_id, version_number, config_snapshot, status, "
                        + "content_digest, snapshot_schema_version, frozen_at, frozen_by, is_active, created_at) "
                        + "VALUES (?, ?, 1, '{}', 'FROZEN', 'sha256test', 1, NOW(), ?, false, NOW())",
                versionId, agentId, CREATOR_ID);

        // 3) Online Runner
        jdbc.update(
                "INSERT INTO runners (id, name, status, max_concurrency, active_runs, queued_runs, "
                        + "drain_requested, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'ONLINE', 5, 0, 0, false, ?, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000)",
                runnerId, "E2E Runner " + runnerId, CREATOR_ID);

        // 4) GatewaySecret (CLIENT_ACCESS) with a known raw key
        //    The API Key format is sk-orin-<32 random chars>.
        //    The keyHash stores bcrypt(rawKey after prefix).
        String rawSecret = randomRawSecret();
        rawApiKey = "sk-orin-" + rawSecret;
        String bcryptHash = BCrypt.encode(rawSecret);

        jdbc.update(
                "INSERT INTO gateway_secrets (id, secret_id, name, secret_type, provider, status, "
                        + "key_hash, key_prefix, encrypted_secret, last4, user_id, description, "
                        + "rate_limit_per_minute, rate_limit_per_day, monthly_token_quota, used_tokens, "
                        + "created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'E2E API Key', 'CLIENT_ACCESS', NULL, 'ACTIVE', "
                        + "?, ?, ?, ?, ?, 'E2E test key', 100, 10000, 1000000, 0, ?, NOW(), NOW())",
                UUID.randomUUID().toString(), apiKeyId, bcryptHash, "sk-orin-", "encrypted-placeholder",
                rawSecret.substring(Math.max(0, rawSecret.length() - 4)), CREATOR_ID, CREATOR_ID);

        // 5) Endpoint with allowedApiKeyIds config
        String config = "{\"allowedApiKeyIds\":[\"" + apiKeyId + "\"]}";
        jdbc.update(
                "INSERT INTO agent_endpoints (id, agent_id, agent_version_id, name, endpoint_type, "
                        + "status, endpoint_path, config, description, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'E2E Endpoint', 'REST_API', 'ACTIVE', '/v1/endpoints/"
                        + endpointId + "/run', ?, 'E2E test endpoint', ?, UNIX_TIMESTAMP()*1000, UNIX_TIMESTAMP()*1000)",
                endpointId, agentId, versionId, config, CREATOR_ID);
    }

    @Test
    @DisplayName("REST execution: curl + API key → Run created → traceId returned")
    void restExecutionCreatesRunWithTraceId() throws Exception {
        // Use short timeout since no real Runner picks up the Run
        MvcResult result = mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"Hello from E2E test\",\"timeoutMs\":500}"))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());

        // Must return runId and traceId even on timeout
        assertTrue(body.has("runId"), "response should contain runId");
        assertTrue(body.has("traceId"), "response should contain traceId");
        assertTrue(body.has("status"), "response should contain status");
        String runId = body.get("runId").asText();
        String traceId = body.get("traceId").asText();
        assertFalse(runId.isBlank());
        assertFalse(traceId.isBlank());

        // Verify Run exists in DB
        Integer runCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM runs WHERE id = ?", Integer.class, runId);
        assertEquals(1, runCount, "Run should exist in database");

        // Verify traceId is persisted
        String dbTraceId = jdbc.queryForObject(
                "SELECT trace_id FROM runs WHERE id = ?", String.class, runId);
        assertEquals(traceId, dbTraceId);

        // Verify created_by marks API key origin
        String createdBy = jdbc.queryForObject(
                "SELECT created_by FROM runs WHERE id = ?", String.class, runId);
        assertTrue(createdBy.startsWith("api-key:"), "Run should be created by api-key:*");

        // F05 P0: Verify endpointId is persisted on the Run
        String dbEndpointId = jdbc.queryForObject(
                "SELECT endpoint_id FROM runs WHERE id = ?", String.class, runId);
        assertEquals(endpointId, dbEndpointId, "Run should be bound to the endpoint");

        // Verify statusUrl uses API-key-accessible path
        if (body.has("statusUrl")) {
            String statusUrl = body.get("statusUrl").asText();
            assertTrue(statusUrl.contains("/v1/endpoints/" + endpointId),
                    "statusUrl should use /v1/endpoints/ path, got: " + statusUrl);
        }
    }

    @Test
    @DisplayName("Missing API key returns 401")
    void missingApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Invalid API key returns 401")
    void invalidApiKeyReturns401() throws Exception {
        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer sk-orin-invalidkey123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("API key not in allowed list returns 403")
    void unauthorizedApiKeyReturns403() throws Exception {
        // Create a second API key NOT in allowedApiKeyIds
        String otherKeyId = "gsec_other_" + shortUuid();
        String otherRaw = randomRawSecret();
        String otherApiKey = "sk-orin-" + otherRaw;
        jdbc.update(
                "INSERT INTO gateway_secrets (id, secret_id, name, secret_type, provider, status, "
                        + "key_hash, key_prefix, encrypted_secret, last4, user_id, description, "
                        + "rate_limit_per_minute, rate_limit_per_day, monthly_token_quota, used_tokens, "
                        + "created_by, created_at, updated_at) "
                        + "VALUES (?, ?, 'Unauthorized Key', 'CLIENT_ACCESS', NULL, 'ACTIVE', "
                        + "?, ?, ?, ?, ?, 'unauthorized', 100, 10000, 1000000, 0, ?, NOW(), NOW())",
                UUID.randomUUID().toString(), otherKeyId, BCrypt.encode(otherRaw), "sk-orin-", "encrypted-placeholder",
                otherRaw.substring(Math.max(0, otherRaw.length() - 4)), CREATOR_ID, CREATOR_ID);

        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + otherApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("150004")); // ENDPOINT_ACCESS_DENIED
    }

    @Test
    @DisplayName("Inactive endpoint returns error")
    void inactiveEndpointReturnsError() throws Exception {
        // Deactivate endpoint
        jdbc.update("UPDATE agent_endpoints SET status = 'INACTIVE' WHERE id = ?", endpointId);

        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("150006")); // ENDPOINT_INACTIVE
    }

    @Test
    @DisplayName("Endpoint not found returns 404")
    void nonexistentEndpointReturns404() throws Exception {
        mockMvc.perform(post("/v1/endpoints/nonexistent_ep/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("150001")); // ENDPOINT_NOT_FOUND
    }

    @Test
    @DisplayName("No online runner returns 503 RUNNER_UNAVAILABLE")
    void noOnlineRunnerReturns503() throws Exception {
        // Set runner offline
        jdbc.update("UPDATE runners SET status = 'OFFLINE'");

        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("150005")); // RUNNER_UNAVAILABLE
    }

    @Test
    @DisplayName("Missing input returns 400 validation error")
    void missingInputReturns400() throws Exception {
        mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Async mode (timeoutMs=0) returns 202 with statusUrl")
    void asyncModeReturns202() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"async test\",\"timeoutMs\":0}"))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals("QUEUED", body.get("status").asText());
        assertTrue(body.has("statusUrl"));
        assertTrue(body.has("runId"));
        assertTrue(body.has("traceId"));
    }

    @Test
    @DisplayName("GET run status for endpoint-created run")
    void getRunStatus() throws Exception {
        // Create a run first
        MvcResult createResult = mockMvc.perform(post("/v1/endpoints/" + endpointId + "/run")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"status check test\",\"timeoutMs\":0}"))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String runId = created.get("runId").asText();

        // Now query it
        mockMvc.perform(get("/v1/endpoints/" + endpointId + "/runs/" + runId)
                        .header("Authorization", "Bearer " + rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(runId));
    }

    // ---- helpers ----

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static String randomRawSecret() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(32);
        java.security.SecureRandom rng = new java.security.SecureRandom();
        for (int i = 0; i < 32; i++) {
            sb.append(chars.charAt(rng.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
