package com.adlin.orin.modules.knowledge.repository.meta;

import com.adlin.orin.modules.knowledge.entity.meta.AgentMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentMemoryRepository extends JpaRepository<AgentMemory, String> {
    List<AgentMemory> findByAgentId(String agentId);

    Optional<AgentMemory> findByAgentIdAndKey(String agentId, String key);

    void deleteByAgentId(String agentId);
}
