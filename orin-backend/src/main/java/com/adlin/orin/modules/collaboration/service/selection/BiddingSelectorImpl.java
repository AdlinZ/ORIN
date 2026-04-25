package com.adlin.orin.modules.collaboration.service.selection;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BiddingSelectorImpl implements BiddingSelector {

    private final CollaborationMetricsService metricsService;

    @Value("${collab.main_agent.bid_whitelist:}")
    private String whitelistConfig;

    @Value("${collab.main_agent.bid_weights.reasoning:0.6}")
    private double weightReasoning;

    @Value("${collab.main_agent.bid_weights.speed:0.3}")
    private double weightSpeed;

    @Value("${collab.main_agent.bid_weights.cost:0.1}")
    private double weightCost;

    private static final Map<String, List<String>> ROLE_KEYWORDS = Map.of(
            "PLANNER", List.of("plan", "planner", "strategy", "orchestrator", "规划"),
            "RESEARCH", List.of("research", "analy", "multi", "vision", "调研", "多模态", "image", "图片"),
            "SPECIALIST", List.of("expert", "specialist", "coding", "generation", "专项", "image", "vision", "图片", "绘图"),
            "REVIEWER", List.of("review", "quality", "critic", "审查", "评审"),
            "CRITIC", List.of("critic", "debate", "critique", "批判", "辩论")
    );
    private static final List<String> IMAGE_INTENT_KEYWORDS = List.of(
            "image", "photo", "picture", "poster", "thumbnail", "illustration", "draw", "design",
            "图片", "照片", "海报", "封面", "插画", "绘图", "画图", "出图", "生成图"
    );
    private static final List<String> IMAGE_AGENT_KEYWORDS = List.of(
            "image", "photo", "vision", "diffusion", "dalle", "sdxl", "stable-diffusion", "flux", "gpt-image",
            "图片", "照片", "绘图", "图像", "视觉", "文生图", "画图", "tti"
    );

    @Override
    public AgentSelectionResult select(List<AgentMetadata> agents,
                                       AgentSelectionContext context,
                                       CollaborationPackage.ExecutionStrategy strategy,
                                       String excludeAgentId) {
        if (agents == null || agents.isEmpty()) {
            return AgentSelectionResult.builder()
                    .selectionMode("bid")
                    .selectionReason("no_available_agents")
                    .candidates(Collections.emptyList())
                    .build();
        }

        Weights weights = resolveWeights(strategy);
        Set<String> whitelist = resolveWhitelist(strategy);

        List<AgentMetadata> candidates = agents.stream()
                .filter(a -> excludeAgentId == null || !excludeAgentId.equalsIgnoreCase(a.getAgentId()))
                .filter(a -> whitelist.isEmpty() || whitelist.contains(a.getAgentId()))
                .collect(Collectors.toList());

        List<AgentMetadata> tagMatched = filterByTags(candidates, context);
        if (!tagMatched.isEmpty()) {
            candidates = tagMatched;
        }

        if (candidates.isEmpty()) {
            return AgentSelectionResult.builder()
                    .selectionMode("bid")
                    .selectionReason("no_candidates_after_whitelist_or_tag_filter")
                    .candidates(Collections.emptyList())
                    .build();
        }

        List<Map<String, Object>> scored = new ArrayList<>();
        for (AgentMetadata candidate : candidates) {
            double reasoning = scoreReasoning(candidate);
            double speed = scoreSpeed(candidate);
            double cost = scoreCost(candidate);
            double total = reasoning * weights.reasoning + speed * weights.speed + cost * weights.cost;

            Map<String, Object> item = new HashMap<>();
            item.put("agentId", candidate.getAgentId());
            item.put("modelName", candidate.getModelName() != null ? candidate.getModelName() : "");
            item.put("agentName", candidate.getName() != null ? candidate.getName() : "");
            item.put("reasoning", reasoning);
            item.put("speed", speed);
            item.put("cost", cost);
            item.put("total", total);
            scored.add(item);
        }

        scored.sort(Comparator.comparingDouble(m -> -((Number) m.getOrDefault("total", 0.0)).doubleValue()));
        Map<String, Object> winner = scored.get(0);

        @SuppressWarnings("unchecked")
        Map<String, Double> scoreBreakdown = Map.of(
                "reasoning", ((Number) winner.get("reasoning")).doubleValue(),
                "speed", ((Number) winner.get("speed")).doubleValue(),
                "cost", ((Number) winner.get("cost")).doubleValue(),
                "total", ((Number) winner.get("total")).doubleValue()
        );

        return AgentSelectionResult.builder()
                .selectedAgentId((String) winner.get("agentId"))
                .selectionMode("bid")
                .selectionReason("dynamic_bidding")
                .scoreBreakdown(scoreBreakdown)
                .candidates(scored)
                .build();
    }

    private Set<String> resolveWhitelist(CollaborationPackage.ExecutionStrategy strategy) {
        if (strategy != null && strategy.getBidWhitelist() != null && !strategy.getBidWhitelist().isEmpty()) {
            return strategy.getBidWhitelist().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .collect(Collectors.toSet());
        }

        if (whitelistConfig == null || whitelistConfig.isBlank()) {
            return Collections.emptySet();
        }

        return Arrays.stream(whitelistConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private Weights resolveWeights(CollaborationPackage.ExecutionStrategy strategy) {
        double wr = strategy != null && strategy.getBidWeightReasoning() != null
                ? strategy.getBidWeightReasoning() : weightReasoning;
        double ws = strategy != null && strategy.getBidWeightSpeed() != null
                ? strategy.getBidWeightSpeed() : weightSpeed;
        double wc = strategy != null && strategy.getBidWeightCost() != null
                ? strategy.getBidWeightCost() : weightCost;

        // 强制推理能力 > 速度 > 成本
        if (!(wr > ws && ws > wc)) {
            return new Weights(0.6, 0.3, 0.1);
        }

        double sum = wr + ws + wc;
        if (sum <= 0.0) {
            return new Weights(0.6, 0.3, 0.1);
        }

        return new Weights(wr / sum, ws / sum, wc / sum);
    }

    private List<AgentMetadata> filterByTags(List<AgentMetadata> candidates, AgentSelectionContext context) {
        if (context == null) {
            return Collections.emptyList();
        }

        String role = context.getExpectedRole() != null ? context.getExpectedRole().toUpperCase(Locale.ROOT) : "SPECIALIST";
        List<String> keywords = new ArrayList<>(ROLE_KEYWORDS.getOrDefault(role, ROLE_KEYWORDS.get("SPECIALIST")));
        String description = context.getDescription() != null ? context.getDescription().toLowerCase(Locale.ROOT) : "";
        boolean imageIntent = IMAGE_INTENT_KEYWORDS.stream().anyMatch(description::contains);
        if (imageIntent) {
            keywords.addAll(IMAGE_AGENT_KEYWORDS);
        }

        return candidates.stream()
                .filter(a -> {
                    String haystack = ((a.getName() != null ? a.getName() : "") + " "
                            + (a.getDescription() != null ? a.getDescription() : "") + " "
                            + (a.getModelName() != null ? a.getModelName() : "") + " "
                            + (a.getMode() != null ? a.getMode() : "") + " "
                            + (a.getViewType() != null ? a.getViewType() : "")).toLowerCase(Locale.ROOT);
                    boolean keywordMatch = keywords.stream().anyMatch(kw -> haystack.contains(kw.toLowerCase(Locale.ROOT)));
                    if (imageIntent) {
                        return keywordMatch;
                    }
                    // 非图像任务时，若上下文中包含角色关键词则允许放宽，避免误过滤
                    boolean contextRoleMatch = keywords.stream().anyMatch(description::contains);
                    return keywordMatch || contextRoleMatch;
                })
                .collect(Collectors.toList());
    }

    private double scoreReasoning(AgentMetadata candidate) {
        String model = candidate.getModelName() != null
                ? candidate.getModelName().toLowerCase(Locale.ROOT)
                : "";

        if (model.contains("opus") || model.contains("o3") || model.contains("o1")
                || model.contains("gpt-4") || model.contains("gemini-pro") || model.contains("reason")) {
            return 0.95;
        }
        if (model.contains("sonnet") || model.contains("gemini") || model.contains("gpt-4o")) {
            return 0.8;
        }
        if (model.contains("mini") || model.contains("flash") || model.contains("lite")) {
            return 0.55;
        }
        return 0.65;
    }

    private double scoreSpeed(AgentMetadata candidate) {
        CollaborationMetricsService.AgentMetrics metrics = metricsService.getAgentMetrics(candidate.getAgentId());
        if (metrics.getTotalRequests() <= 0) {
            return 0.6;
        }

        double avgLatency = metrics.getAverageLatencyMs();
        return 1.0 / (1.0 + avgLatency / 1000.0);
    }

    private double scoreCost(AgentMetadata candidate) {
        CollaborationMetricsService.AgentMetrics metrics = metricsService.getAgentMetrics(candidate.getAgentId());
        if (metrics.getTotalRequests() <= 0) {
            return heuristicCostScore(candidate.getModelName());
        }

        double avgCost = metrics.getTotalCost() / Math.max(1, metrics.getTotalRequests());
        if (avgCost <= 0) {
            return heuristicCostScore(candidate.getModelName());
        }
        return 1.0 / (1.0 + avgCost * 10.0);
    }

    private double heuristicCostScore(String modelName) {
        String model = modelName != null ? modelName.toLowerCase(Locale.ROOT) : "";
        if (model.contains("mini") || model.contains("flash") || model.contains("lite")) {
            return 0.95;
        }
        if (model.contains("opus") || model.contains("o1") || model.contains("o3")) {
            return 0.35;
        }
        return 0.65;
    }

    private record Weights(double reasoning, double speed, double cost) {}
}
