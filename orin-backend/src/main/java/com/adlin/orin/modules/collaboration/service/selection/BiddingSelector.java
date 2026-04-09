package com.adlin.orin.modules.collaboration.service.selection;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;

import java.util.List;

public interface BiddingSelector {
    AgentSelectionResult select(List<AgentMetadata> agents,
                                AgentSelectionContext context,
                                CollaborationPackage.ExecutionStrategy strategy,
                                String excludeAgentId);
}
