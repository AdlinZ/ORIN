package com.adlin.orin.modules.workflow.repository;

import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工作流仓储接口
 */
@Repository
public interface WorkflowRepository extends JpaRepository<WorkflowEntity, Long> {

    Optional<WorkflowEntity> findByWorkflowName(String workflowName);

    List<WorkflowEntity> findByStatus(WorkflowEntity.WorkflowStatus status);

    List<WorkflowEntity> findByWorkflowType(WorkflowEntity.WorkflowType workflowType);

    boolean existsByWorkflowName(String workflowName);
}
