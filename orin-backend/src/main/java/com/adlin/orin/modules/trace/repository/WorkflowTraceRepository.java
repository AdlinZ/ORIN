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

    /**
     * 查询最近的追踪记录，用于调用链路入口页聚合摘要
     */
    List<WorkflowTraceEntity> findTop500ByOrderByStartedAtDesc();

    /**
     * 查询指定用户最近的所有追踪记录（按 traceId 去重，按时间倒序）
     */
    @org.springframework.data.jpa.repository.Query("SELECT wt.traceId FROM WorkflowTraceEntity wt WHERE wt.instanceId IN (SELECT wi.id FROM WorkflowInstanceEntity wi WHERE wi.userId = :userId) GROUP BY wt.traceId ORDER BY MAX(wt.startedAt) DESC")
    List<String> findDistinctTraceIdsByUserIdOrderByStartedAtDesc(@org.springframework.lang.NonNull Long userId, org.springframework.data.domain.Pageable pageable);
}
