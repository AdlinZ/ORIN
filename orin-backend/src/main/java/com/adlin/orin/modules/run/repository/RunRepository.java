package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.entity.RunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Run 数据访问层（F03）。
 */
@Repository
public interface RunRepository extends JpaRepository<Run, String> {

    /** 按创建时间倒序分页。 */
    Page<Run> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** 按创建人分页。 */
    Page<Run> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);

    /** 按状态 + 创建时间排序（调度器用）。 */
    List<Run> findByStatusOrderByCreatedAtAsc(RunStatus status, Pageable pageable);

    /** 按 runnerId 查活跃 Run。 */
    List<Run> findByRunnerIdAndStatusIn(String runnerId, List<RunStatus> statuses);

    /** 查找最早排队的 Run（lease 候选），加悲观写锁。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Run r WHERE r.status = 'QUEUED' ORDER BY r.createdAt ASC")
    List<Run> findOldestQueuedForLease(Pageable pageable);

    /** 根据 lease token 查找 Run。 */
    Optional<Run> findByLeaseToken(String leaseToken);

    /** 统计某状态的 Run 数量。 */
    long countByStatus(RunStatus status);

    /** 统计某 Runner 上活跃的 Run 数。 */
    long countByRunnerIdAndStatusIn(String runnerId, List<RunStatus> statuses);

    /** 查原始 Run 的所有重试。 */
    List<Run> findByRetryOfRunIdOrderByCreatedAtAsc(String retryOfRunId);
}
