package com.adlin.orin.modules.run;

import com.adlin.orin.BaseIntegrationTest;
import com.adlin.orin.modules.run.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * F03 Run minimal contract tests.
 *
 * <p>What this covers: endpoint paths, auth gating, basic error codes,
 * and DTO serialization shapes. These tests verify that the API surface
 * matches the documented paths and error contracts.
 *
 * <p>What this does NOT cover: successful lease-claim → result → events
 * flow with a real Runner credential, frozen Agent, and seeded Run.
 * Those require fixture infrastructure (Runner enrollment, Agent freeze,
 * Run creation) that is being built in R2 alongside the run_assignment
 * table, lease persistence, and idempotency layer.
 */
class RunContractTest extends BaseIntegrationTest {

    // ============================================================
    // Business API (JWT-auth /api/v1/runs)
    // ============================================================

    @Test
    void listRuns_ReturnsPaginatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/runs")
                        .header("Authorization", "Bearer " + jwtCreator())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(root.has("content"));
        assertTrue(root.has("totalElements") || root.has("totalPages"));
    }

    @Test
    void getRun_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/runs/nonexistent-run-id")
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("140001")); // RUN_NOT_FOUND
    }

    // ============================================================
    // Machine channel — auth gating (all Runner endpoints require
    // a valid Runner credential; without it, every path → 401).
    // ============================================================

    @Test
    void claimLease_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/lease/claim")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("100002")); // RUNNER_CREDENTIAL_INVALID
    }

    @Test
    void submitResult_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaseToken\":\"t\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitEvents_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaseToken\":\"t\",\"events\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secretBind_UnauthenticatedReturns401() throws Exception {
        // /secret-bind requires a valid Runner credential to get past auth.
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/secret-bind")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignmentId\":\"assign-1\"}"))
                .andExpect(status().isUnauthorized()) // blocked by auth filter
                .andExpect(jsonPath("$.code").value("100002"));
    }

    @Test
    void renewLease_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/lease/renew")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leaseId\":\"lease-1\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // Legacy paths remain protected by the Runner authentication boundary.
    // These tests do not assert handler mapping; that requires a valid Runner
    // credential and belongs with the R2 protocol integration tests.
    // ============================================================

    @Test
    void oldLeasePath_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/lease")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldStartPath_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldCompletePath_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/complete")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oldLogPath_UnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/system/runners/test-runner/runs/some-run/log")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // DTO serialization contract
    // ============================================================

    @Test
    void leaseRunResponse_HasRequiredFields() {
        LeaseRunResponse empty = LeaseRunResponse.empty();
        assertFalse(empty.isAcquired());
        assertNull(empty.getRunId());

        LeaseRunResponse full = LeaseRunResponse.builder()
                .acquired(true)
                .runId("run-1")
                .assignmentId("asgn-1")
                .leaseId("lease-1")
                .leaseToken("token-1")
                .configSnapshot("{}")
                .input("hello")
                .leaseExpiresAt(1000L)
                .traceId("trace-1")
                .build();
        assertTrue(full.isAcquired());
        assertEquals("run-1", full.getRunId());
        assertEquals("asgn-1", full.getAssignmentId());
        assertEquals("lease-1", full.getLeaseId());
        assertEquals("token-1", full.getLeaseToken());
        assertEquals("trace-1", full.getTraceId());
    }

    @Test
    void renewLeaseResponse_HasControlActionFields() {
        RenewLeaseResponse noOp = RenewLeaseResponse.noOp(2000L, "trace-2");
        assertEquals("no_op", noOp.getAction());
        assertNull(noOp.getReason());
        assertEquals(2000L, noOp.getLeaseExpiresAt());
        assertEquals("trace-2", noOp.getTraceId());

        RenewLeaseResponse cancel = RenewLeaseResponse.builder()
                .action("cancel")
                .reason("USER_CANCELLED")
                .leaseExpiresAt(0L)
                .traceId("trace-3")
                .build();
        assertEquals("cancel", cancel.getAction());
        assertEquals("USER_CANCELLED", cancel.getReason());
    }

    @Test
    void secretBindResponse_HasMaterializedFields() {
        SecretBindResponse resp = SecretBindResponse.builder()
                .leaseId("lease-1")
                .runId("run-1")
                .materializedSecrets(Map.of("OPENAI_API_KEY", "sk-test"))
                .secretRevisionBindings(Map.of("OPENAI_API_KEY", "gsec_1@v1"))
                .expiresAtEpochMs(2000L)
                .build();
        assertEquals("lease-1", resp.getLeaseId());
        assertEquals("run-1", resp.getRunId());
        assertEquals("sk-test", resp.getMaterializedSecrets().get("OPENAI_API_KEY"));
        assertEquals(2000L, resp.getExpiresAtEpochMs());
    }

    @Test
    void batchEventsRequest_AcceptsEventList() {
        BatchEventsRequest.EventEntry e = new BatchEventsRequest.EventEntry();
        e.setSeq(1);
        e.setLevel("INFO");
        e.setMessage("test");
        assertEquals(1, e.getSeq());
        assertEquals("INFO", e.getLevel());
        assertEquals("test", e.getMessage());
    }
}
