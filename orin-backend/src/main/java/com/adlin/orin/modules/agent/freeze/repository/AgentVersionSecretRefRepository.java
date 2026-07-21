package com.adlin.orin.modules.agent.freeze.repository;

import com.adlin.orin.modules.agent.freeze.entity.AgentVersionSecretRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentVersionSecretRefRepository
        extends JpaRepository<AgentVersionSecretRef, AgentVersionSecretRef.PK> {

    List<AgentVersionSecretRef> findByAgentVersionIdOrderByAliasAsc(String agentVersionId);

    long countByAgentVersionId(String agentVersionId);
}
