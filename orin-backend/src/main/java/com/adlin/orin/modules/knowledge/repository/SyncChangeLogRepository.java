package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SyncChangeLogRepository extends JpaRepository<SyncChangeLog, Long> {

    List<SyncChangeLog> findByAgentIdAndSyncedFalseOrderByChangedAtAsc(String agentId);

    List<SyncChangeLog> findByIntegrationIdAndSyncedFalseOrderByChangedAtAsc(Long integrationId);

    List<SyncChangeLog> findByIntegrationIdAndSyncStatusOrderByChangedAtAsc(Long integrationId, String syncStatus);

    Page<SyncChangeLog> findByAgentIdOrderByChangedAtDesc(String agentId, Pageable pageable);

    Page<SyncChangeLog> findByAgentIdAndChangedAtBetweenOrderByChangedAtDesc(
            String agentId, LocalDateTime startTime, LocalDateTime endTime, Pageable pageable);

    @Query("SELECT MAX(s.changedAt) FROM SyncChangeLog s WHERE s.agentId = :agentId")
    LocalDateTime findLatestChangeTime(@Param("agentId") String agentId);

    @Modifying
    @Query("UPDATE SyncChangeLog s SET s.synced = true WHERE s.agentId = :agentId AND s.synced = false")
    int markAllSynced(@Param("agentId") String agentId);

    long countByAgentIdAndSyncedFalse(String agentId);

    long countByIntegrationIdAndSyncedFalse(Long integrationId);

    Optional<SyncChangeLog> findByIdempotencyKey(String idempotencyKey);
}
