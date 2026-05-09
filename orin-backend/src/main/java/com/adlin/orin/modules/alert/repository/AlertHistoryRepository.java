package com.adlin.orin.modules.alert.repository;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, String> {

    /**
     * 分页查询告警历史
     */
    Page<AlertHistory> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    @Query("""
            select h from AlertHistory h
            where h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            order by h.triggeredAt desc
            """)
    Page<AlertHistory> findConfiguredByOrderByTriggeredAtDesc(Pageable pageable);

    /**
     * 按规则 ID 查询
     */
    List<AlertHistory> findByRuleIdOrderByTriggeredAtDesc(String ruleId);

    /**
     * 按智能体 ID 查询
     */
    List<AlertHistory> findByAgentIdOrderByTriggeredAtDesc(String agentId);

    @Query("""
            select h from AlertHistory h
            where h.agentId = :agentId
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            order by h.triggeredAt desc
            """)
    List<AlertHistory> findConfiguredByAgentIdOrderByTriggeredAtDesc(@Param("agentId") String agentId);

    /**
     * 查询指定时间范围内的告警
     */
    List<AlertHistory> findByTriggeredAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 统计未解决的告警数量
     */
    long countByStatus(String status);

    @Query("""
            select count(h) from AlertHistory h
            where h.status = :status
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            """)
    long countConfiguredByStatus(@Param("status") String status);

    @Query("""
            select count(distinct h.fingerprint) from AlertHistory h
            where h.status = :status
              and h.fingerprint is not null
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            """)
    long countConfiguredFingerprintsByStatus(@Param("status") String status);

    @Query("""
            select count(h) from AlertHistory h
            where h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            """)
    long countConfigured();

    /**
     * 查询未解决的告警
     */
    List<AlertHistory> findByStatusOrderByTriggeredAtDesc(String status);

    @Query("""
            select h from AlertHistory h
            where h.status = :status
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            order by h.triggeredAt desc
            """)
    List<AlertHistory> findConfiguredByStatusOrderByTriggeredAtDesc(@Param("status") String status);

    /**
     * 统计未读的告警数量（TRIGGERED 状态）
     */
    long countByStatusIn(List<String> statuses);

    @Query("""
            select count(h) from AlertHistory h
            where h.status in :statuses
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            """)
    long countConfiguredByStatusIn(@Param("statuses") List<String> statuses);

    @Query("""
            select count(distinct h.fingerprint) from AlertHistory h
            where h.status in :statuses
              and h.fingerprint is not null
              and h.ruleId is not null
              and exists (
                select r.id from AlertRule r
                where r.id = h.ruleId
              )
            """)
    long countConfiguredFingerprintsByStatusIn(@Param("statuses") List<String> statuses);

    Optional<AlertHistory> findFirstByFingerprintAndStatusOrderByTriggeredAtDesc(String fingerprint, String status);
}
