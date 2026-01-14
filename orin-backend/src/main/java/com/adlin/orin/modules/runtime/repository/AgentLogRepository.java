package com.adlin.orin.modules.runtime.repository;

import com.adlin.orin.modules.runtime.entity.AgentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentLogRepository extends JpaRepository<AgentLog, Long> {
    List<AgentLog> findByAgentIdOrderByTimestampDesc(String agentId);
}
