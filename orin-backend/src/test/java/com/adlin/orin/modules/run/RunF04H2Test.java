package com.adlin.orin.modules.run;

import com.adlin.orin.H2SecurityIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F04 positive-path contract tests — H2 + real Spring MVC chain.
 *
 * <p>Verifies that the F04 business API endpoints return data from
 * the real {@code run_events} and {@code run_assignment} tables.
 * No Docker required.
 *
 * <p>Test matrix:
 * <ul>
 *   <li>events endpoint: full list + incremental (afterSeq)</li>
 *   <li>assignments endpoint: returns assignment rows</li>
 *   <li>listRuns with filter: status / agentId / runnerId</li>
 *   <li>auth gating: unauthenticated → 401</li>
 * </ul>
 */
@DisplayName("F04 Run Events / Assignments / Filters (H2)")
class RunF04H2Test extends H2SecurityIntegrationTest {

    private static final String RUN_A = "run_f04_a";
    private static final String RUN_B = "run_f04_b";
    private static final String LEASE_A = "lease-f04-a";

    @BeforeEach
    void seedRuns() {
        // Clean up
        db.update("DELETE FROM run_events WHERE run_id IN (?, ?)", RUN_A, RUN_B);
        db.update("DELETE FROM run_assignment WHERE run_id IN (?, ?)", RUN_A, RUN_B);
        db.update("DELETE FROM run_logs WHERE run_id IN (?, ?)", RUN_A, RUN_B);
        db.update("DELETE FROM runs WHERE id IN (?, ?)", RUN_A, RUN_B);

        long now = System.currentTimeMillis();

        // Run A — COMPLETED with events and assignment
        db.update("INSERT INTO runs (id, agent_id, agent_version_id, runner_id, status, "
                        + "config_snapshot, input, output, trace_id, run_attempt, "
                        + "retry_count, max_retries, created_by, created_at, updated_at, "
                        + "started_at, completed_at, terminal_reason) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                RUN_A, "agent-f04", "ver-f04", "runner-f04", "COMPLETED",
                "{}", "hello f04", "world", "trace-f04-aaa", 1,
                0, 3, CREATOR_ID, now, now, now, now, null);

        // Run B — QUEUED (for filter test)
        db.update("INSERT INTO runs (id, agent_id, agent_version_id, runner_id, status, "
                        + "config_snapshot, input, trace_id, run_attempt, "
                        + "retry_count, max_retries, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                RUN_B, "agent-f04-other", "ver-f04-other", "runner-f04-other", "QUEUED",
                "{}", "pending run", "trace-f04-bbb", 0,
                0, 3, CREATOR2_ID, now, now);

        // Events for Run A
        for (int i = 1; i <= 3; i++) {
            db.update("INSERT INTO run_events (run_id, lease_id, run_attempt, event_seq, "
                            + "level, message, timestamp, payload_hash, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    RUN_A, LEASE_A, 1, i, "INFO",
                    "Event #" + i, now + i * 1000,
                    "sha256-event-" + i, now);
        }

        // Assignment for Run A
        db.update("INSERT INTO run_assignment (id, run_id, runner_id, lease_id, status, "
                        + "lease_expires_at, run_attempt, trace_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "asgn-f04-a", RUN_A, "runner-f04", LEASE_A, "COMPLETED",
                now + 30000, 1, "trace-f04-aaa", now, now);
    }

    // ============================================================
    // events endpoint
    // ============================================================

    @Test
    void getEvents_ReturnsAllEvents() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A + "/events")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventSeq").value(1))
                .andExpect(jsonPath("$[0].level").value("INFO"))
                .andExpect(jsonPath("$[0].message").value("Event #1"))
                .andExpect(jsonPath("$[0].runAttempt").value(1))
                .andExpect(jsonPath("$[0].leaseId").value(LEASE_A))
                .andExpect(jsonPath("$[2].eventSeq").value(3));
    }

    @Test
    void getEvents_Incremental_ReturnsOnlyNew() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A + "/events")
                        .param("afterSeq", "1")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].eventSeq").value(2))
                .andExpect(jsonPath("$[1].eventSeq").value(3));
    }

    @Test
    void getEvents_NotFoundRun_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/runs/nonexistent/events")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("140001"));
    }

    @Test
    void getEvents_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A + "/events"))
                .andExpect(status().isUnauthorized());
    }

    // ============================================================
    // assignments endpoint
    // ============================================================

    @Test
    void getAssignments_ReturnsAssignments() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A + "/assignments")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("asgn-f04-a"))
                .andExpect(jsonPath("$[0].runnerId").value("runner-f04"))
                .andExpect(jsonPath("$[0].leaseId").value(LEASE_A))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].runAttempt").value(1));
    }

    @Test
    void getAssignments_EmptyRun_ReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_B + "/assignments")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAssignments_NotFoundRun_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/runs/nonexistent/assignments")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("140001"));
    }

    // ============================================================
    // listRuns with filters (F04)
    // ============================================================

    @Test
    void listRuns_FilterByStatus_ReturnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/v1/runs")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.status == 'COMPLETED')]").exists());
    }

    @Test
    void listRuns_FilterByAgentId_ReturnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/v1/runs")
                        .param("agentId", "agent-f04")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.agentId == 'agent-f04')]").exists());
    }

    @Test
    void listRuns_FilterByRunnerId_ReturnsOnlyMatching() throws Exception {
        mockMvc.perform(get("/api/v1/runs")
                        .param("runnerId", "runner-f04")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[?(@.runnerId == 'runner-f04')]").exists());
    }

    @Test
    void listRuns_NoFilter_ReturnsAll() throws Exception {
        mockMvc.perform(get("/api/v1/runs")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // ============================================================
    // Run detail
    // ============================================================

    @Test
    void getRun_ReturnsRunWithF04Fields() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A)
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RUN_A))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.agentId").value("agent-f04"))
                .andExpect(jsonPath("$.traceId").value("trace-f04-aaa"))
                .andExpect(jsonPath("$.runAttempt").value(1))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.maxRetries").value(3))
                .andExpect(jsonPath("$.output").value("world"))
                .andExpect(jsonPath("$.input").value("hello f04"));
    }

    // ============================================================
    // logs endpoint
    // ============================================================

    @Test
    void getLogs_ReturnsEmptyListForRunWithNoLogs() throws Exception {
        // Run A has no run_logs rows (only run_events)
        mockMvc.perform(get("/api/v1/runs/" + RUN_A + "/logs")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ============================================================
    // F04 resource ACL
    // ============================================================

    @Test
    void ownerCanReadOwnRunButCannotSeeOtherOwnersInList() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A)
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RUN_A));

        mockMvc.perform(get("/api/v1/runs")
                        .header("Authorization", "Bearer " + jwtCreator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(RUN_A));
    }

    @Test
    void nonOwnerCannotReadEventsAssignmentsLogsOrControlRun() throws Exception {
        String token = jwtCreator2();
        String path = "/api/v1/runs/" + RUN_A;

        mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("10004"));
        mockMvc.perform(get(path + "/events").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path + "/assignments").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(path + "/logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path + "/retry").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReadAnyRunAndCancelAnotherUsersQueuedRun() throws Exception {
        mockMvc.perform(get("/api/v1/runs/" + RUN_A)
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/runs/" + RUN_B + "/cancel")
                        .header("Authorization", "Bearer " + jwtOperator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.terminalReason").value("USER_CANCELLED"));
    }
}
