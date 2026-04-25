package com.adlin.orin.modules.playground.repository;

import com.adlin.orin.modules.playground.entity.PlaygroundRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaygroundRunRepository extends JpaRepository<PlaygroundRunEntity, String> {
    List<PlaygroundRunEntity> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);
}
