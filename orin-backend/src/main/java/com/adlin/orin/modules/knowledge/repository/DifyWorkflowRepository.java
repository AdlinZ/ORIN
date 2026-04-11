package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.DifyWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DifyWorkflowRepository extends JpaRepository<DifyWorkflow, String> {
    List<DifyWorkflow> findByAgentId(String agentId);
}
