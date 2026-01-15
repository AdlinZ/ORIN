package com.adlin.orin.modules.audit.service;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.system.service.LogConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 审计日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final LogConfigService logConfigService;

    /**
     * 异步记录审计日志
     */
    @Async
    public void logApiCall(String userId, String apiKeyId, String providerId, String providerType,
            String endpoint, String method, String model, String ipAddress,
            String userAgent, String requestParams, String responseContent, Integer statusCode,
            Long responseTime, Integer promptTokens, Integer completionTokens,
            Double estimatedCost, Boolean success, String errorMessage) {

        // Check config
        if (!logConfigService.isAuditEnabled()) {
            return;
        }

        String logLevel = logConfigService.getLogLevel();
        if ("NONE".equalsIgnoreCase(logLevel)) {
            return;
        }
        if ("ERROR_ONLY".equalsIgnoreCase(logLevel) && Boolean.TRUE.equals(success)) {
            return;
        }

        try {
            AuditLog auditLog = AuditLog.builder()
                    .userId(userId)
                    .apiKeyId(apiKeyId)
                    .providerId(providerId)
                    .providerType(providerType)
                    .endpoint(endpoint)
                    .method(method)
                    .model(model)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .requestParams(requestParams)
                    .responseContent(responseContent)
                    .statusCode(statusCode)
                    .responseTime(responseTime)
                    .promptTokens(promptTokens != null ? promptTokens : 0)
                    .completionTokens(completionTokens != null ? completionTokens : 0)
                    .totalTokens((promptTokens != null ? promptTokens : 0) +
                            (completionTokens != null ? completionTokens : 0))
                    .estimatedCost(estimatedCost != null ? estimatedCost : 0.0)
                    .success(success)
                    .errorMessage(errorMessage)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(auditLog);
            log.info("Audit log saved successfully: id={}, providerId={}, endpoint={}", auditLog.getId(), providerId,
                    endpoint);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * 定时清理过期日志 (每天凌晨 2 点)
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOldLogs() {
        int retentionDays = logConfigService.getRetentionDays();
        if (retentionDays <= 0) {
            log.info("Scheduled cleanup skipped. Retention days: {}", retentionDays);
            return; // 0 or negative means no deletion or disabled
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        log.info("Starting scheduled audit log cleanup. Retention days: {}, Cutoff: {}", retentionDays, cutoffDate);

        try {
            int deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
            log.info("Scheduled audit log cleanup completed. Deleted {} records", deletedCount);
        } catch (Exception e) {
            log.error("Failed to cleanup audit logs: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取用户的审计日志
     */
    public Page<AuditLog> getUserAuditLogs(String userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 获取API密钥的审计日志
     */
    public Page<AuditLog> getApiKeyAuditLogs(String apiKeyId, Pageable pageable) {
        return auditLogRepository.findByApiKeyIdOrderByCreatedAtDesc(apiKeyId, pageable);
    }

    /**
     * 统计用户的Token使用量
     */
    public Long getUserTokenUsage(String userId, LocalDateTime since) {
        Long total = auditLogRepository.sumTokensByUserIdAndCreatedAtAfter(userId, since);
        return total != null ? total : 0L;
    }

    /**
     * 统计API密钥的Token使用量
     */
    public Long getApiKeyTokenUsage(String apiKeyId, LocalDateTime since) {
        Long total = auditLogRepository.sumTokensByApiKeyIdAndCreatedAtAfter(apiKeyId, since);
        return total != null ? total : 0L;
    }

    /**
     * 分页获取所有审计日志
     */
    public Page<AuditLog> getAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * 手动清理指定天数之前的日志
     */
    @Transactional
    public int manualCleanup(int days) {
        if (days < 0) {
            log.warn("Invalid days parameter: {}. Must be >= 0", days);
            return 0;
        }

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        log.info("Manual log cleanup requested. Days: {}, Cutoff date: {}", days, cutoffDate);

        try {
            int deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
            log.info("Manual log cleanup completed. Deleted {} records", deletedCount);
            return deletedCount;
        } catch (Exception e) {
            log.error("Failed to manually cleanup audit logs: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to cleanup logs: " + e.getMessage(), e);
        }
    }

    /**
     * 获取日志统计信息
     */
    public java.util.Map<String, Object> getLogStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        long count = auditLogRepository.count();
        stats.put("totalCount", count);
        // 粗略估算大小 (假设每条 2KB)
        stats.put("estimatedSizeMb", Math.round((count * 2.0 / 1024.0) * 100.0) / 100.0);

        // 获取最早记录时间
        auditLogRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 1,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC,
                        "createdAt")))
                .getContent().stream().findFirst().ifPresent(oldest -> stats.put("oldestLog", oldest.getCreatedAt()));

        return stats;
    }
}
