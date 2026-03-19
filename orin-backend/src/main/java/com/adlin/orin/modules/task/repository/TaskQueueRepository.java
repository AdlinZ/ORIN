package com.adlin.orin.modules.task.repository;

import com.adlin.orin.modules.task.entity.TaskQueue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskQueueRepository extends JpaRepository<TaskQueue, Long> {

    /**
     * 按优先级和状态查询任务
     */
    @Query("SELECT t FROM TaskQueue t WHERE t.status = :status ORDER BY t.priority DESC, t.createdAt ASC")
    List<TaskQueue> findByStatusOrderByPriority(String status);

    /**
     * 分页查询用户任务
     */
    Page<TaskQueue> findByCreatedBy(String createdBy, Pageable pageable);

    /**
     * 统计各状态任务数量
     */
    long countByStatus(String status);

    /**
     * 统计用户各状态任务数量
     */
    long countByCreatedByAndStatus(String createdBy, String status);
}
