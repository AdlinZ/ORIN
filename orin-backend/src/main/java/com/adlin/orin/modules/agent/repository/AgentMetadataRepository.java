package com.adlin.orin.modules.agent.repository;

import com.adlin.orin.modules.agent.entity.AgentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentMetadataRepository extends JpaRepository<AgentMetadata, String> {
}
