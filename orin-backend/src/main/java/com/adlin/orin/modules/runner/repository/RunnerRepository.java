package com.adlin.orin.modules.runner.repository;

import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RunnerRepository extends JpaRepository<Runner, String> {

    Page<Runner> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    Optional<Runner> findByNameAndCreatedBy(String name, String createdBy);

    boolean existsByNameAndCreatedBy(String name, String createdBy);

    /**
     * 扫描心跳超时但仍标记为活跃的 Runner（ONLINE / DEGRADED / DRAINING），
     * 由 {@code RunnerOfflineScanner} 周期调用。
     */
    @Query("SELECT r FROM Runner r WHERE r.status IN :statuses AND r.lastHeartbeatAt < :threshold")
    List<Runner> findStaleActive(@Param("statuses") Collection<RunnerStatus> statuses,
                                 @Param("threshold") long thresholdMillis);

    /**
     * 扫描 ENROLLING 卡死（createdAt 早于阈值）的 Runner。
     */
    @Query("SELECT r FROM Runner r WHERE r.status = :status AND r.createdAt < :threshold")
    List<Runner> findStaleByStatus(@Param("status") RunnerStatus status,
                                   @Param("threshold") long thresholdMillis);

    /** F05：找第一个指定状态的 Runner（用于 Endpoint 自动分配）。 */
    Optional<Runner> findFirstByStatus(RunnerStatus status);
}
