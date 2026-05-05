package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UnifiedGatewayAuditLogRepository extends JpaRepository<UnifiedGatewayAuditLog, Long> {

    Page<UnifiedGatewayAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    Page<UnifiedGatewayAuditLog> findByRouteIdOrderByCreatedAtDesc(Long routeId, Pageable pageable);

    Page<UnifiedGatewayAuditLog> findByResultOrderByCreatedAtDesc(String result, Pageable pageable);

    @Query("SELECT a FROM UnifiedGatewayAuditLog a WHERE " +
           "(:routeId IS NULL OR a.routeId = :routeId) AND " +
           "(:result IS NULL OR a.result = :result) AND " +
           "(:startDate IS NULL OR a.createdAt >= :startDate) AND " +
           "(:endDate IS NULL OR a.createdAt <= :endDate) " +
           "ORDER BY a.createdAt DESC")
    Page<UnifiedGatewayAuditLog> findByFilters(Long routeId, String result,
                                         LocalDateTime startDate, LocalDateTime endDate,
                                         Pageable pageable);

    List<UnifiedGatewayAuditLog> findTop100ByOrderByCreatedAtDesc();

    List<UnifiedGatewayAuditLog> findTop20ByResultNotOrderByCreatedAtDesc(String result);

    @Query("SELECT AVG(a.latencyMs) FROM UnifiedGatewayAuditLog a WHERE a.createdAt > :since AND a.result = 'SUCCESS'")
    Double findAverageLatencySince(LocalDateTime since);

    @Query("SELECT COUNT(a) FROM UnifiedGatewayAuditLog a WHERE a.result = :result AND a.createdAt > :since")
    long countByResultSince(String result, LocalDateTime since);

    long countByCreatedAtAfter(LocalDateTime since);
}
