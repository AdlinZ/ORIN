package com.adlin.orin.modules.runner.repository;

import com.adlin.orin.modules.runner.entity.RunnerHeartbeatSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RunnerHeartbeatSnapshotRepository extends JpaRepository<RunnerHeartbeatSnapshot, Long> {

    /**
     * 返回 Runner 最近 N 条快照（按 reported_at DESC）。
     */
    List<RunnerHeartbeatSnapshot> findByRunnerIdOrderByReportedAtDesc(String runnerId, Pageable pageable);

    /**
     * 删除 Runner 超过 N 条的最早快照。返回删除行数。
     */
    @Modifying
    @Query(value = "DELETE FROM runner_heartbeat_snapshots WHERE id IN (" +
            "  SELECT id FROM (" +
            "    SELECT id FROM runner_heartbeat_snapshots WHERE runner_id = :runnerId" +
            "    ORDER BY reported_at DESC LIMIT 18446744073709551615 OFFSET :retain" +
            "  ) AS t" +
            ")", nativeQuery = true)
    int trimOlderThanRetain(@Param("runnerId") String runnerId, @Param("retain") int retain);
}
