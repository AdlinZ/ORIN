package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.service.runtime.AgentRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CollaborationArbiterServiceTest {

    private final CollaborationArbiterService arbiterService = new CollaborationArbiterService();

    @Test
    void shouldPickHighestWeightedBranch() {
        AgentRuntimeState branchA = AgentRuntimeState.builder()
                .branchId("1")
                .summary("实现方案，包含测试步骤与验证说明，附带链接 https://example.com")
                .evidenceRefs(List.of("claim:1:1", "url:1:2"))
                .build();

        AgentRuntimeState branchB = AgentRuntimeState.builder()
                .branchId("2")
                .summary("简短回答")
                .evidenceRefs(List.of())
                .build();

        CollaborationArbiterService.ArbiterDecision decision = arbiterService.arbitrate(List.of(branchA, branchB));

        assertEquals("1", decision.getWinnerBranchId());
        assertNotNull(decision.getScoreboard());
        assertEquals(2, decision.getScoreboard().size());
    }

    @Test
    void shouldHandleEmptyBranches() {
        CollaborationArbiterService.ArbiterDecision decision = arbiterService.arbitrate(List.of());
        assertNull(decision.getWinnerBranchId());
        assertEquals(0.0, decision.getWinnerScore());
        assertEquals("no_candidate", decision.getReason());
    }

    @Test
    void scoreBreakdownShouldContainExpectedKeys() {
        AgentRuntimeState branch = AgentRuntimeState.builder()
                .branchId("3")
                .summary("带测试的实现方案与回归验证")
                .evidenceRefs(List.of("claim:3:1"))
                .build();

        CollaborationArbiterService.ArbiterDecision decision = arbiterService.arbitrate(List.of(branch));
        Map<String, Double> breakdown = decision.getScoreboard().get(0).getBreakdown();

        assertTrue(breakdown.containsKey("correctness"));
        assertTrue(breakdown.containsKey("evidence"));
        assertTrue(breakdown.containsKey("testability"));
        assertTrue(breakdown.containsKey("cost"));
        assertTrue(breakdown.containsKey("weightedTotal"));
    }
}
