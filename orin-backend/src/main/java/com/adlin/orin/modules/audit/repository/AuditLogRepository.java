package com.adlin.orin.modules.audit.repository;

import com.adlin.orin.modules.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志仓库
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {

        /**
         * 获取用户的审计日志
         */
        Page<AuditLog> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

        /**
         * 获取API密钥的审计日志
         */
        Page<AuditLog> findByApiKeyIdOrderByCreatedAtDesc(String apiKeyId, Pageable pageable);

        /**
         * 获取时间范围内的审计日志
         */
        List<AuditLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 统计用户的总Token使用量
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.userId = ?1 AND a.createdAt >= ?2")
        Long sumTokensByUserIdAndCreatedAtAfter(String userId, LocalDateTime after);

        /**
         * 统计API密钥的总Token使用量
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.apiKeyId = ?1 AND a.createdAt >= ?2")
        Long sumTokensByApiKeyIdAndCreatedAtAfter(String apiKeyId, LocalDateTime after);

        /**
         * 统计用户的总成本 (支持排除系统日志)
         */
        @Query("SELECT SUM(a.estimatedCost) FROM AuditLog a WHERE (?1 IS NULL OR a.userId = ?1) AND a.createdAt BETWEEN ?2 AND ?3 AND a.providerId != 'ORIN_CORE'")
        Double sumCostByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);

        @Query("SELECT SUM(a.estimatedCost) FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE'")
        Double sumTotalCostBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 获取指定智能体时间范围内的审计日志
         */
        List<AuditLog> findByProviderIdAndCreatedAtBetweenOrderByCreatedAtAsc(String providerId, LocalDateTime start,
                        LocalDateTime end);

        /**
         * 获取指定智能体时间范围内的审计日志 (最近时间在前)
         */
        List<AuditLog> findByProviderIdAndCreatedAtBetweenOrderByCreatedAtDesc(String providerId, LocalDateTime start,
                        LocalDateTime end);

        /**
         * 删除指定时间之前的日志
         */
        @Modifying(clearAutomatically = true)
        @Query("DELETE FROM AuditLog a WHERE a.createdAt < ?1")
        int deleteByCreatedAtBefore(LocalDateTime cutoff);

        /**
         * 统计指定时间之后的总Token使用量 (排除系统日志)
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.createdAt >= ?1 AND a.providerId != 'ORIN_CORE'")
        Long sumTotalTokensAfter(LocalDateTime after);

        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE'")
        Long sumTotalTokensBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 统计所有时间的总Token使用量 (排除系统日志)
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.providerId != 'ORIN_CORE'")
        Long sumTotalTokensAll();

        /**
         * 分页查询指定时间范围内的日志 (业务日志，排除系统日志)
         */
        @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE' ORDER BY a.createdAt DESC")
        Page<AuditLog> findBusinessLogsByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end,
                        Pageable pageable);

        /**
         * 分页查询指定时间范围内的日志
         */
        Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end,
                        Pageable pageable);

        /**
         * 统计指定时间之后的平均响应时间 (排除系统日志)
         */
        @Query("SELECT AVG(a.responseTime) FROM AuditLog a WHERE a.createdAt >= ?1 AND a.providerId != 'ORIN_CORE'")
        Double avgResponseTimeAfter(LocalDateTime after);

        /**
         * 统计所有时间的平均响应时间 (排除系统日志)
         */
        @Query("SELECT AVG(a.responseTime) FROM AuditLog a WHERE a.providerId != 'ORIN_CORE'")
        Double avgResponseTimeAll();

        /**
         * 获取历史最大响应时间 (排除系统日志)
         */
        @Query("SELECT MAX(a.responseTime) FROM AuditLog a WHERE a.providerId != 'ORIN_CORE'")
        Long maxResponseTimeAll();

        /**
         * Find recent logs by provider ID (for chat history context)
         */
        Page<AuditLog> findByProviderIdOrderByCreatedAtDesc(String providerId, Pageable pageable);

        /**
         * Find all logs in a conversation by conversation ID (paginated)
         */
        Page<AuditLog> findByConversationId(String conversationId, Pageable pageable);

        /**
         * 统计时间范围内各智能体的Token总消耗 (排除系统日志)
         * 
         * @return List of [providerId, totalTokens]
         */
        @Query("SELECT a.providerId, SUM(a.totalTokens) FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE' GROUP BY a.providerId")
        List<Object[]> sumTokensByProviderIdBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 统计时间范围内各智能体的成本总消耗 (排除系统日志)
         * 
         * @return List of [providerId, estimatedCost]
         */
        @Query("SELECT a.providerId, SUM(a.estimatedCost) FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE' GROUP BY a.providerId")
        List<Object[]> sumCostByProviderIdBetween(LocalDateTime start, LocalDateTime end);

        /**
         * Find logs where providerType is IN the given list
         */
        Page<AuditLog> findByProviderTypeInOrderByCreatedAtDesc(List<String> providerTypes, Pageable pageable);

        /**
         * Find logs where providerType is NOT IN the given list
         */
        Page<AuditLog> findByProviderTypeNotInOrderByCreatedAtDesc(List<String> providerTypes, Pageable pageable);

        /**
         * Find recent logs by conversation ID (for chat history context)
         */
        Page<AuditLog> findByConversationIdOrderByCreatedAtDesc(String conversationId, Pageable pageable);

        @Query(value = "SELECT * FROM audit_logs " +
                        "WHERE id IN (" +
                        "  SELECT latest_id FROM (" +
                        "    SELECT MAX(id) as latest_id FROM audit_logs " +
                        "    WHERE (?1 = 'ALL' OR provider_type = ?1) " +
                        "    GROUP BY CASE WHEN conversation_id IS NOT NULL THEN conversation_id ELSE id END" +
                        "  ) sub" +
                        ") " +
                        "ORDER BY created_at DESC", countQuery = "SELECT COUNT(DISTINCT CASE WHEN conversation_id IS NOT NULL THEN conversation_id ELSE id END) FROM audit_logs WHERE (?1 = 'ALL' OR provider_type = ?1)", nativeQuery = true)
        Page<AuditLog> findGroupedByConversationLatestEntry(String type, Pageable pageable);

        /**
         * 获取业务日志 (排除系统日志)
         */
        @Query("SELECT a FROM AuditLog a WHERE a.createdAt BETWEEN ?1 AND ?2 AND a.providerId != 'ORIN_CORE'")
        List<AuditLog> findBusinessLogsByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
