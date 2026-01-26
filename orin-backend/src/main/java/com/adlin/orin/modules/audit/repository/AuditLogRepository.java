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
         * 统计用户的总成本
         */
        @Query("SELECT SUM(a.estimatedCost) FROM AuditLog a WHERE a.userId = ?1 AND a.createdAt BETWEEN ?2 AND ?3")
        Double sumCostByUserIdAndDateRange(String userId, LocalDateTime start, LocalDateTime end);

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
         * 统计指定时间之后的总Token使用量
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a WHERE a.createdAt >= ?1")
        Long sumTotalTokensAfter(LocalDateTime after);

        /**
         * 统计所有时间的总Token使用量
         */
        @Query("SELECT SUM(a.totalTokens) FROM AuditLog a")
        Long sumTotalTokensAll();

        /**
         * 分页查询指定时间范围内的日志
         */
        Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end,
                        Pageable pageable);

        /**
         * 统计指定时间之后的平均响应时间
         */
        @Query("SELECT AVG(a.responseTime) FROM AuditLog a WHERE a.createdAt >= ?1")
        Double avgResponseTimeAfter(LocalDateTime after);

        /**
         * 统计所有时间的平均响应时间
         */
        @Query("SELECT AVG(a.responseTime) FROM AuditLog a")
        Double avgResponseTimeAll();

        /**
         * 获取历史最大响应时间
         */
        @Query("SELECT MAX(a.responseTime) FROM AuditLog a")
        Long maxResponseTimeAll();

        /**
         * Find recent logs by provider ID (for chat history context)
         */
        Page<AuditLog> findByProviderIdOrderByCreatedAtDesc(String providerId, Pageable pageable);

        /**
         * Find all logs in a conversation by conversation ID (ordered chronologically)
         */
        List<AuditLog> findByConversationIdOrderByCreatedAtAsc(String conversationId);

        /**
         * Find all logs in a conversation by conversation ID (paginated)
         */
        Page<AuditLog> findByConversationIdOrderByCreatedAtAsc(String conversationId, Pageable pageable);
}
