package com.adlin.orin.modules.agent.freeze.repository;

import com.adlin.orin.modules.agent.freeze.entity.AgentVersionFreezeIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface AgentVersionFreezeIdempotencyRepository
        extends JpaRepository<AgentVersionFreezeIdempotency, AgentVersionFreezeIdempotency.PK> {

    Optional<AgentVersionFreezeIdempotency> findByAgentIdAndIdempotencyKeyHash(String agentId, String idempotencyKeyHash);

    long deleteByExpiresAtBefore(LocalDateTime cutoff);
}
