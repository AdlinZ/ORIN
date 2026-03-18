package com.adlin.orin.modules.task.repository;

import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 任务仓储层
 */
@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, Long> {

    Optional<TaskEntity> findByTaskId(String taskId);

    List<TaskEntity> findByWorkflowId(Long workflowId);

    List<TaskEntity> findByWorkflowInstanceId(Long workflowInstanceId);

    Page<TaskEntity> findByStatus(TaskStatus status, Pageable pageable);

    Page<TaskEntity> findByPriority(TaskPriority priority, Pageable pageable);

    /**
     * 查询待执行的任务（队列中等待的）
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<TaskEntity> findPendingTasks(@Param("status") TaskStatus status, Pageable pageable);

    /**
     * 查询需要重试的任务
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.status = 'RETRYING' AND t.nextRetryAt <= :now")
    List<TaskEntity> findTasksToRetry(@Param("now") LocalDateTime now);

    /**
     * 查询死信任务
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.status = 'DEAD' ORDER BY t.updatedAt DESC")
    Page<TaskEntity> findDeadTasks(Pageable pageable);

    /**
     * 查询失败的任务
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.status = 'FAILED' ORDER BY t.updatedAt DESC")
    Page<TaskEntity> findFailedTasks(Pageable pageable);

    /**
     * 统计各状态的任务数量
     */
    @Query("SELECT t.status, COUNT(t) FROM TaskEntity t GROUP BY t.status")
    List<Object[]> countByStatus();

    /**
     * 统计各优先级的任务数量
     */
    @Query("SELECT t.priority, COUNT(t) FROM TaskEntity t WHERE t.status IN ('QUEUED', 'RUNNING', 'RETRYING') GROUP BY t.priority")
    List<Object[]> countPendingByPriority();

    /**
     * 查询工作流最新的实例任务
     */
    @Query("SELECT t FROM TaskEntity t WHERE t.workflowId = :workflowId ORDER BY t.createdAt DESC LIMIT 1")
    Optional<TaskEntity> findLatestByWorkflowId(@Param("workflowId") Long workflowId);
}
