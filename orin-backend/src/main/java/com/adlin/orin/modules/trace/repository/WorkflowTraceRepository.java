package com.adlin.orin.modules.trace.repository;

import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 追踪仓储接口
 */
@Repository
public interface WorkflowTraceRepository extends JpaRepository<WorkflowTraceEntity, Long> {

    /**
     * 根据追踪 ID 查找所有追踪记录
     */
    List<WorkflowTraceEntity> findByTraceIdOrderByStartedAtAsc(String traceId);

    /**
     * 根据实例 ID 查找所有追踪记录
     */
    List<WorkflowTraceEntity> findByInstanceIdOrderByStartedAtAsc(Long instanceId);

    /**
     * 根据步骤 ID 查找追踪记录
     */
    List<WorkflowTraceEntity> findByStepId(Long stepId);

    /**
     * 根据技能 ID 查找追踪记录
     */
    List<WorkflowTraceEntity> findBySkillId(Long skillId);

    /**
     * 根据状态查找追踪记录
     */
    List<WorkflowTraceEntity> findByStatus(WorkflowTraceEntity.TraceStatus status);
}
