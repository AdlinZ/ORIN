package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AgentHealthStatusRepository extends JpaRepository<AgentHealthStatus, String> {
}
