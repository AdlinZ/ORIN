package com.adlin.orin.modules.zeroclaw.task;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawAnalysisRequest;
import com.adlin.orin.modules.zeroclaw.dto.ZeroClawSelfHealingRequest;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawAnalysisReport;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawConfig;
import com.adlin.orin.modules.zeroclaw.entity.ZeroClawSelfHealingLog;
import com.adlin.orin.modules.zeroclaw.service.ZeroClawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * ZeroClaw 定时任务
 * 负责定期生成分析报告和执行主动维护
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZeroClawScheduledTask {

    private final ZeroClawService zeroClawService;
    private final AgentHealthStatusRepository healthStatusRepository;

    /**
     * 每天凌晨 2 点生成 24h 趋势分析报告
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void generateDailyTrendReport() {
        ZeroClawConfig config = zeroClawService.getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnableAnalysis())) {
            log.debug("ZeroClaw daily report skipped - not enabled");
            return;
        }

        log.info("Generating daily trend report via ZeroClaw...");
        try {
            ZeroClawAnalysisReport report = zeroClawService.generateDailyTrendReport();
            if (report != null) {
                log.info("Daily trend report generated: {} - Severity: {}", report.getId(), report.getSeverity());
            } else {
                log.warn("Failed to generate daily trend report");
            }
        } catch (Exception e) {
            log.error("Error generating daily trend report", e);
        }
    }

    /**
     * 每 5 分钟检查一次异常指标，触发智能诊断
     */
    @Scheduled(fixedRate = 300000)
    public void checkAnomaliesAndDiagnose() {
        ZeroClawConfig config = zeroClawService.getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnableAnalysis())) {
            return;
        }

        // 检查是否有异常状态的 Agent
        List<AgentHealthStatus> unhealthyAgents = healthStatusRepository.findByStatusNot("HEALTHY");

        if (unhealthyAgents.isEmpty()) {
            return;
        }

        log.info("Found {} unhealthy agents, triggering ZeroClaw anomaly diagnosis...", unhealthyAgents.size());

        for (AgentHealthStatus agent : unhealthyAgents) {
            try {
                ZeroClawAnalysisRequest request = new ZeroClawAnalysisRequest();
                request.setAnalysisType("ANOMALY_DIAGNOSIS");
                request.setAgentId(agent.getAgentId());
                request.setContext("Agent status: " + agent.getStatus() + ", Latency: " + agent.getLatencyMs() + "ms");

                ZeroClawAnalysisReport report = zeroClawService.performAnalysis(request);
                if (report != null) {
                    log.info("Anomaly diagnosis completed for agent {}: {}", agent.getAgentId(), report.getSeverity());
                }
            } catch (Exception e) {
                log.error("Error performing anomaly diagnosis for agent: {}", agent.getAgentId(), e);
            }
        }
    }

    /**
     * 每 10 分钟执行主动维护检查
     */
    @Scheduled(fixedRate = 600000)
    public void performSelfHealingCheck() {
        ZeroClawConfig config = zeroClawService.getActiveConfig();
        if (config == null || !Boolean.TRUE.equals(config.getEnableSelfHealing())) {
            return;
        }

        log.debug("Running ZeroClaw self-healing check...");

        try {
            // 获取 ZeroClaw 状态
            Map<String, Object> status = zeroClawService.getZeroClawStatus();

            if (Boolean.TRUE.equals(status.get("connected"))) {
                // 检查是否需要清理日志
                checkAndCleanupLogs();

                // 检查是否需要清理缓存
                checkAndCleanupCache();
            }
        } catch (Exception e) {
            log.error("Error during self-healing check", e);
        }
    }

    /**
     * 检查并清理日志
     */
    private void checkAndCleanupLogs() {
        // 模拟检查日志大小，实际需要实现日志检查逻辑
        log.debug("Checking log files for cleanup...");

        // 触发日志清理
        ZeroClawSelfHealingRequest request = new ZeroClawSelfHealingRequest();
        request.setActionType("CLEAR_LOGS");
        request.setTargetResource("orin-system-logs");
        request.setReason("Scheduled maintenance: Log rotation");

        try {
            ZeroClawSelfHealingLog result = zeroClawService.executeSelfHealing(request);
            if (result != null && "SUCCESS".equals(result.getStatus())) {
                log.info("Log cleanup completed successfully");
            }
        } catch (Exception e) {
            log.error("Log cleanup failed", e);
        }
    }

    /**
     * 检查并清理缓存
     */
    private void checkAndCleanupCache() {
        log.debug("Checking cache for cleanup...");

        ZeroClawSelfHealingRequest request = new ZeroClawSelfHealingRequest();
        request.setActionType("CLEANUP_CACHE");
        request.setTargetResource("redis-cache");
        request.setReason("Scheduled maintenance: Cache cleanup");

        try {
            ZeroClawSelfHealingLog result = zeroClawService.executeSelfHealing(request);
            if (result != null && "SUCCESS".equals(result.getStatus())) {
                log.info("Cache cleanup completed successfully");
            }
        } catch (Exception e) {
            log.error("Cache cleanup failed", e);
        }
    }
}
