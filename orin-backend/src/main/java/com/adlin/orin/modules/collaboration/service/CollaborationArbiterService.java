package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.service.runtime.AgentRuntimeState;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CollaborationArbiterService {

    private static final double W_CORRECTNESS = 0.45;
    private static final double W_EVIDENCE = 0.25;
    private static final double W_TESTABILITY = 0.20;
    private static final double W_COST = 0.10;

    public ArbiterDecision arbitrate(List<AgentRuntimeState> branches) {
        if (branches == null || branches.isEmpty()) {
            return ArbiterDecision.builder()
                    .winnerBranchId(null)
                    .winnerSummary("")
                    .winnerScore(0.0)
                    .reason("no_candidate")
                    .scoreboard(List.of())
                    .build();
        }

        List<BranchScore> scores = branches.stream()
                .map(this::scoreBranch)
                .sorted(Comparator.comparingDouble(BranchScore::getTotal).reversed())
                .collect(Collectors.toList());

        BranchScore winner = scores.get(0);
        return ArbiterDecision.builder()
                .winnerBranchId(winner.getBranchId())
                .winnerSummary(winner.getSummary())
                .winnerScore(winner.getTotal())
                .reason("highest_weighted_score")
                .scoreboard(scores)
                .build();
    }

    private BranchScore scoreBranch(AgentRuntimeState branch) {
        String content = branch.getSummary() == null ? "" : branch.getSummary();
        String lower = content.toLowerCase(Locale.ROOT);

        double correctness = baseFromLength(content.length());
        if (lower.contains("error") || lower.contains("failed") || lower.contains("无法")) {
            correctness = Math.max(0.05, correctness - 0.4);
        }

        int evidenceCount = branch.getEvidenceRefs() == null ? 0 : branch.getEvidenceRefs().size();
        double evidence = Math.min(1.0, evidenceCount / 3.0);

        double testability = 0.35;
        if (containsAny(lower, "test", "验证", "assert", "case", "回归")) {
            testability = 0.9;
        } else if (containsAny(lower, "步骤", "plan", "方案", "实现")) {
            testability = 0.7;
        }

        int len = content.length();
        double cost = len <= 600 ? 1.0 : (len <= 1200 ? 0.8 : 0.55);

        double total = correctness * W_CORRECTNESS
                + evidence * W_EVIDENCE
                + testability * W_TESTABILITY
                + cost * W_COST;

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("correctness", round(correctness));
        breakdown.put("evidence", round(evidence));
        breakdown.put("testability", round(testability));
        breakdown.put("cost", round(cost));
        breakdown.put("weightedTotal", round(total));

        return BranchScore.builder()
                .branchId(branch.getBranchId())
                .summary(branch.getSummary())
                .total(round(total))
                .breakdown(breakdown)
                .build();
    }

    private boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private double baseFromLength(int len) {
        if (len >= 1200) {
            return 0.95;
        }
        if (len >= 700) {
            return 0.87;
        }
        if (len >= 350) {
            return 0.78;
        }
        if (len >= 120) {
            return 0.68;
        }
        return 0.52;
    }

    private double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    @Data
    @Builder
    public static class ArbiterDecision {
        private String winnerBranchId;
        private String winnerSummary;
        private Double winnerScore;
        private String reason;
        private List<BranchScore> scoreboard;
    }

    @Data
    @Builder
    public static class BranchScore {
        private String branchId;
        private String summary;
        private Double total;
        private Map<String, Double> breakdown;
    }
}
