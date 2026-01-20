package com.adlin.orin.modules.workflow.repository;

import com.adlin.orin.modules.workflow.entity.WorkflowNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowNodeRepository extends JpaRepository<WorkflowNode, String> {
    List<WorkflowNode> findByWorkflowId(String workflowId);
}
