package com.adlin.orin.modules.collaboration.service.selection;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class StaticSelectorImpl implements StaticSelector {

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
                        .candidates(List.of(Map.of("agentId", matched.get().getAgentId(), "reason", "configured_static_default")))
                        .build();
            }
        }

        AgentMetadata first = agents.get(0);
        return AgentSelectionResult.builder()
                .selectedAgentId(first.getAgentId())
                .selectionMode("static")
                .selectionReason("fallback_first_available")
                .scoreBreakdown(Map.of("reasoning", 0.5, "speed", 0.5, "cost", 0.5, "total", 0.5))
                .candidates(List.of(Map.of("agentId", first.getAgentId(), "reason", "fallback_first_available")))
                .build();
    }
}
