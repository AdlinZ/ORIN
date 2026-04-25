package com.adlin.orin.modules.collaboration.service.selection;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class StaticSelectorImpl implements StaticSelector {

    private static final List<String> IMAGE_INTENT_KEYWORDS = List.of(
            "image", "photo", "picture", "poster", "thumbnail", "illustration", "draw", "design",
            "图片", "照片", "海报", "封面", "插画", "绘图", "画图", "出图", "生成图",
            "画一幅", "画个", "画一张", "绘制", "作画", "文生图", "绘画", "画作", "图像创作"
    );
    private static final List<String> IMAGE_AGENT_KEYWORDS = List.of(
            "image", "photo", "vision", "diffusion", "dalle", "sdxl", "stable-diffusion", "flux", "gpt-image",
            "图片", "照片", "绘图", "图像", "视觉", "文生图", "画图"
    );

    @Value("${collab.main_agent.static_default:}")
    private String staticDefault;

    @Override
    public AgentSelectionResult select(List<AgentMetadata> agents,
                                       AgentSelectionContext context,
                                       CollaborationPackage.ExecutionStrategy strategy) {
        if (agents == null || agents.isEmpty()) {
            return AgentSelectionResult.builder()
                    .selectionMode("static")
                    .selectionReason("no_available_agents")
                    .candidates(Collections.emptyList())
                    .build();
        }

        String configured = strategy != null && strategy.getMainAgentStaticDefault() != null
                ? strategy.getMainAgentStaticDefault()
                : staticDefault;

        if (configured != null && !configured.isBlank()) {
            Optional<AgentMetadata> matched = agents.stream()
                    .filter(a -> configured.equalsIgnoreCase(a.getAgentId()))
                    .findFirst();
            if (matched.isPresent()) {
                return AgentSelectionResult.builder()
                        .selectedAgentId(matched.get().getAgentId())
                        .selectionMode("static")
                        .selectionReason("configured_static_default")
                        .scoreBreakdown(Map.of("reasoning", 1.0, "speed", 1.0, "cost", 1.0, "total", 1.0))
                        .candidates(List.of(Map.of(
                                "agentId", matched.get().getAgentId(),
                                "modelName", matched.get().getModelName() != null ? matched.get().getModelName() : "",
                                "reason", "configured_static_default"
                        )))
                        .build();
            }
        }

        AgentMetadata preferred = selectByContextHeuristic(agents, context);
        if (preferred != null) {
            return AgentSelectionResult.builder()
                    .selectedAgentId(preferred.getAgentId())
                    .selectionMode("static")
                    .selectionReason("context_heuristic_match")
                    .scoreBreakdown(Map.of("reasoning", 0.7, "speed", 0.6, "cost", 0.6, "total", 0.66))
                    .candidates(List.of(Map.of(
                            "agentId", preferred.getAgentId(),
                            "modelName", preferred.getModelName() != null ? preferred.getModelName() : "",
                            "reason", "context_heuristic_match"
                    )))
                    .build();
        }

        AgentMetadata first = agents.get(0);
        return AgentSelectionResult.builder()
                .selectedAgentId(first.getAgentId())
                .selectionMode("static")
                .selectionReason("fallback_first_available")
                .scoreBreakdown(Map.of("reasoning", 0.5, "speed", 0.5, "cost", 0.5, "total", 0.5))
                .candidates(List.of(Map.of(
                        "agentId", first.getAgentId(),
                        "modelName", first.getModelName() != null ? first.getModelName() : "",
                        "reason", "fallback_first_available"
                )))
                .build();
    }

    private AgentMetadata selectByContextHeuristic(List<AgentMetadata> agents, AgentSelectionContext context) {
        if (agents == null || agents.isEmpty() || context == null) {
            return null;
        }
        String haystack = (context.getDescription() != null ? context.getDescription() : "")
                .toLowerCase(Locale.ROOT);
        boolean imageIntent = containsAny(haystack, IMAGE_INTENT_KEYWORDS);

        List<String> preferredKeywords = new ArrayList<>();
        if (imageIntent) {
            preferredKeywords.addAll(IMAGE_AGENT_KEYWORDS);
        }

        String role = context.getExpectedRole() != null ? context.getExpectedRole().toUpperCase(Locale.ROOT) : "SPECIALIST";
        if ("PLANNER".equals(role)) {
            preferredKeywords.addAll(List.of("plan", "planner", "strategy", "规划", "编排"));
        } else if ("REVIEWER".equals(role) || "CRITIC".equals(role)) {
            preferredKeywords.addAll(List.of("review", "critic", "quality", "评审", "审查"));
        } else {
            preferredKeywords.addAll(List.of("specialist", "expert", "generation", "专项", "生成"));
        }

        AgentMetadata best = null;
        int bestScore = Integer.MIN_VALUE;
        for (AgentMetadata agent : agents) {
            int score = scoreAgent(agent, preferredKeywords, imageIntent);
            if (score > bestScore) {
                best = agent;
                bestScore = score;
            }
        }
        // 至少要命中一个能力关键词，否则让上层走 fallback 逻辑。
        return bestScore > 0 ? best : null;
    }

    private int scoreAgent(AgentMetadata agent, List<String> preferredKeywords, boolean imageIntent) {
        String name = agent.getName() != null ? agent.getName() : "";
        String description = agent.getDescription() != null ? agent.getDescription() : "";
        String model = agent.getModelName() != null ? agent.getModelName() : "";
        String mode = agent.getMode() != null ? agent.getMode() : "";
        String viewType = agent.getViewType() != null ? agent.getViewType() : "";

        String text = (name + " " + description + " " + model + " " + mode + " " + viewType)
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : preferredKeywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += 3;
            }
        }
        if (imageIntent && isImageCapable(agent)) {
            score += 8;
        }
        return score;
    }

    private boolean isImageCapable(AgentMetadata agent) {
        String viewType = agent.getViewType() != null ? agent.getViewType() : "";
        String mode = agent.getMode() != null ? agent.getMode() : "";
        String model = agent.getModelName() != null ? agent.getModelName() : "";

        String merged = (viewType + " " + mode + " " + model).toLowerCase(Locale.ROOT);
        return merged.contains("tti")
                || merged.contains("image")
                || merged.contains("vision")
                || merged.contains("dalle")
                || merged.contains("sdxl")
                || merged.contains("diffusion")
                || merged.contains("flux");
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank() || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && text.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
