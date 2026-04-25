package com.adlin.orin.modules.playground.repository;

import com.adlin.orin.modules.playground.entity.PlaygroundWorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaygroundWorkflowRepository extends JpaRepository<PlaygroundWorkflowEntity, String> {
    List<PlaygroundWorkflowEntity> findBySpecialistAgentIdsJsonContaining(String agentId);
}
