package com.adlin.orin.modules.monitor.service.impl;

import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.monitor.repository.AgentMetricRepository;
import com.adlin.orin.gateway.service.ProviderRegistry;
import com.adlin.orin.gateway.adapter.ProviderAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 真实监控数据采集服务
 * 替代MetricsMockService，采集真实的系统和Provider指标
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealMetricsCollector {

    private final AgentHealthStatusRepository healthStatusRepository;
    private final AgentMetricRepository metricRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.adlin.orin.modules.agent.repository.AgentMetadataRepository metadataRepository;
    private final ProviderRegistry providerRegistry;

    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * 定时采集系统指标
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void collectSystemMetrics() {
        long now = System.currentTimeMillis();

        // 获取所有已注册的Provider
        List<ProviderAdapter> providers = providerRegistry.getAllProviders();

        for (ProviderAdapter provider : providers) {
            String providerId = provider.getProviderName();

            String providerType = provider.getProviderType();
            String mode = "chat";
            var metaOpt = metadataRepository.findById(providerId);
            if (metaOpt.isPresent()) {
                providerType = metaOpt.get().getProviderType();
                mode = metaOpt.get().getMode();
            }
            final String finalProviderType = providerType;
            final String finalMode = mode;

            // 检查或创建健康状态记录
            AgentHealthStatus healthStatus = healthStatusRepository.findById(providerId)
                    .orElseGet(() -> createHealthStatus(providerId, finalProviderType, finalMode));

            // 同步元数据（如果缺失或过期）
            boolean changed = false;
            if (healthStatus.getProviderType() == null && finalProviderType != null) {
                healthStatus.setProviderType(finalProviderType);
                changed = true;
            }
            if (healthStatus.getMode() == null && finalMode != null) {
                healthStatus.setMode(finalMode);
                changed = true;
            }

            // 采集指标
            AgentMetric metric = collectProviderMetrics(providerId, now, healthStatus);
            metricRepository.save(metric);

            // 更新健康状态
            updateHealthStatus(healthStatus, metric);
            healthStatusRepository.save(healthStatus);

            log.debug("Collected metrics for provider: {}", providerId);
        }
    }

    /**
     * 采集Provider指标
     */
    private AgentMetric collectProviderMetrics(String providerId, long timestamp, AgentHealthStatus healthStatus) {
        // 获取系统CPU使用率
        double cpuUsage = 0.0;
        double memoryUsage = 0.0;

        String providerType = "Unknown";
        var metadataOpt = metadataRepository.findById(providerId);
        if (metadataOpt.isPresent()) {
            providerType = metadataOpt.get().getProviderType();
        }

        // 仅当是本地模型时，才采集宿主机的 CPU/内存指标
        if ("Local".equalsIgnoreCase(providerType) || providerType == null || "Unknown".equals(providerType)) {
            cpuUsage = osBean.getSystemLoadAverage() * 100 / Runtime.getRuntime().availableProcessors();
            cpuUsage = Math.max(0, Math.min(100, cpuUsage)); // 限制在0-100之间

            Runtime runtime = Runtime.getRuntime();
            memoryUsage = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024); // MB
        }

        // 从审计日志统计最近的响应时间
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        List<com.adlin.orin.modules.audit.entity.AuditLog> recentLogs = auditLogRepository
                .findByCreatedAtBetween(oneMinuteAgo, LocalDateTime.now());

        int avgLatency = calculateAverageLatency(recentLogs, providerId);
        int tokenCost = calculateTokenCost(recentLogs, providerId);
        int dailyRequests = countDailyRequests(providerId);

        return AgentMetric.builder()
                .agentId(providerId)
                .timestamp(timestamp)
                .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                .memoryUsage(memoryUsage)
                .responseLatency(avgLatency)
                .tokenCost(tokenCost)
                .dailyRequests(dailyRequests)
                .build();
    }

    /**
     * 计算平均响应时间
     */
    private int calculateAverageLatency(List<com.adlin.orin.modules.audit.entity.AuditLog> logs, String providerId) {
        if (logs.isEmpty()) {
            return 0;
        }

        long totalLatency = logs.stream()
                .filter(log -> providerId.equals(log.getProviderId()))
                .filter(log -> log.getResponseTime() != null)
                .mapToLong(com.adlin.orin.modules.audit.entity.AuditLog::getResponseTime)
                .sum();

        long count = logs.stream()
                .filter(log -> providerId.equals(log.getProviderId()))
                .filter(log -> log.getResponseTime() != null)
                .count();

        return count > 0 ? (int) (totalLatency / count) : 0;
    }

    /**
     * 计算Token消耗
     */
    private int calculateTokenCost(List<com.adlin.orin.modules.audit.entity.AuditLog> logs, String providerId) {
        return logs.stream()
                .filter(log -> providerId.equals(log.getProviderId()))
                .mapToInt(log -> log.getTotalTokens() != null ? log.getTotalTokens() : 0)
                .sum();
    }

    /**
     * 统计今日请求数
     */
    private int countDailyRequests(String providerId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime now = LocalDateTime.now();

        List<com.adlin.orin.modules.audit.entity.AuditLog> todayLogs = auditLogRepository
                .findByCreatedAtBetween(startOfDay, now);

        return (int) todayLogs.stream()
                .filter(log -> providerId.equals(log.getProviderId()))
                .count();
    }

    /**
     * 创建健康状态记录
     */
    private AgentHealthStatus createHealthStatus(String providerId, String providerType, String mode) {
        AgentHealthStatus status = new AgentHealthStatus();
        status.setAgentId(providerId);
        status.setAgentName(providerId);
        status.setStatus(AgentHealthStatus.Status.RUNNING);
        status.setHealthScore(100);
        status.setLastHeartbeat(System.currentTimeMillis());
        status.setProviderType(providerType);
        status.setMode(mode);

        return healthStatusRepository.save(status);
    }

    /**
     * 更新健康状态
     */
    private void updateHealthStatus(AgentHealthStatus healthStatus, AgentMetric metric) {
        healthStatus.setLastHeartbeat(metric.getTimestamp());

        // 计算健康分数
        int score = 100;

        // 响应时间影响
        if (metric.getResponseLatency() > 2000) {
            score -= 40;
        } else if (metric.getResponseLatency() > 1000) {
            score -= 20;
        } else if (metric.getResponseLatency() > 500) {
            score -= 10;
        }

        // CPU使用率影响
        if (metric.getCpuUsage() > 90) {
            score -= 30;
        } else if (metric.getCpuUsage() > 70) {
            score -= 15;
        }

        // 内存使用影响
        if (metric.getMemoryUsage() > 2000) {
            score -= 20;
        } else if (metric.getMemoryUsage() > 1500) {
            score -= 10;
        }

        healthStatus.setHealthScore(Math.max(0, Math.min(100, score)));

        // 根据健康分数更新状态
        if (score < 50) {
            healthStatus.setStatus(AgentHealthStatus.Status.HIGH_LOAD);
        } else if (score >= 80 && healthStatus.getStatus() == AgentHealthStatus.Status.HIGH_LOAD) {
            healthStatus.setStatus(AgentHealthStatus.Status.RUNNING);
        }
    }

    /**
     * 清理旧指标数据
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldMetrics() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        long timestamp = thirtyDaysAgo.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        // 删除30天前的指标数据
        List<AgentMetric> oldMetrics = metricRepository.findAll().stream()
                .filter(m -> m.getTimestamp() < timestamp)
                .toList();

        metricRepository.deleteAll(oldMetrics);
        log.info("Cleaned up {} old metrics", oldMetrics.size());
    }
}
