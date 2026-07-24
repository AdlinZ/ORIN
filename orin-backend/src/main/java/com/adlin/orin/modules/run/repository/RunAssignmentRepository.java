package com.adlin.orin.modules.run.repository;

import com.adlin.orin.modules.run.entity.AssignmentStatus;
import com.adlin.orin.modules.run.entity.RunAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * RunAssignment 数据访问层（R2 F03）。
 */
@Repository
public interface RunAssignmentRepository extends JpaRepository<RunAssignment, String> {

    /** 按 lease_id 查找 assignment（renew / result / events 端点用）。 */
    Optional<RunAssignment> findByLeaseId(String leaseId);

    /** 按 run_id 查所有 assignment 历史（按创建时间倒序）。 */
    List<RunAssignment> findByRunIdOrderByCreatedAtDesc(String runId);

    /** 查 Runner 上活跃的 assignment（调度与容量检查用）。 */
    List<RunAssignment> findByRunnerIdAndStatusIn(String runnerId, List<AssignmentStatus> statuses);

    /** 查找超时未完成的 assignment（timeoutStaleRuns 用）。 */
    List<RunAssignment> findByStatusInAndLeaseExpiresAtBefore(
            List<AssignmentStatus> statuses, Long beforeEpochMs);
}
