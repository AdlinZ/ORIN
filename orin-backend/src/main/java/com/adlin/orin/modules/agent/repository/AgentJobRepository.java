package com.adlin.orin.modules.agent.repository;

import com.adlin.orin.modules.agent.entity.AgentJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentJobRepository extends JpaRepository<AgentJobEntity, Long> {

    Optional<AgentJobEntity> findByJobId(String jobId);

    List<AgentJobEntity> findByAgentIdOrderByCreatedAtDesc(String agentId);

    List<AgentJobEntity> findByJobTypeOrderByCreatedAtDesc(String jobType);
}