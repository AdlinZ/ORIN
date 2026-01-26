package com.adlin.orin.modules.workflow.repository;

import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工作流实例仓储接口
 */
@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, Long> {

    List<WorkflowInstanceEntity> findByWorkflowIdOrderByStartedAtDesc(Long workflowId);

    Optional<WorkflowInstanceEntity> findByTraceId(String traceId);

    List<WorkflowInstanceEntity> findByStatus(WorkflowInstanceEntity.InstanceStatus status);

    void deleteByWorkflowId(Long workflowId);
}
