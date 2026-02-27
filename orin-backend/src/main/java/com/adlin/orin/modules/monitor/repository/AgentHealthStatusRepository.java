package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentHealthStatusRepository extends JpaRepository<AgentHealthStatus, String> {

    /**
     * 查询非健康状态的 Agent
     */
    List<AgentHealthStatus> findByStatusNot(String status);
}
