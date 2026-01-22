package com.adlin.orin.modules.workflow.repository;

import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 工作流步骤仓储接口
 */
@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStepEntity, Long> {

    List<WorkflowStepEntity> findByWorkflowIdOrderByStepOrderAsc(Long workflowId);

    void deleteByWorkflowId(Long workflowId);
}
