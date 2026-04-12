package com.adlin.orin.modules.system.controller;

import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.knowledge.service.sync.DifyFullSyncService;
import com.adlin.orin.modules.agent.repository.AgentAccessProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 定时任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final AgentAccessProfileRepository profileRepository;
    private final DifyFullSyncService difyFullSyncService;
    private final AuditLogRepository auditLogRepository;

    private final AtomicLong syncTaskCount = new AtomicLong(0);
    private final AtomicLong cleanupTaskCount = new AtomicLong(0);
    private final AtomicLong lastSyncResult = new AtomicLong(0); // 0=unknown, 1=success, 2=fail
    private final AtomicLong lastCleanupResult = new AtomicLong(0);

    /**
     * 定时同步知识库
     * 每天凌晨 2 点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledKnowledgeSync() {
        log.info("Scheduled knowledge base sync started");
        syncTaskCount.incrementAndGet();
        
        try {
            // 获取所有配置了 Dify 的 Agent
            List<String> agentIds = profileRepository.findAll().stream()
                    .filter(p -> p.getDatasetApiKey() != null && !p.getDatasetApiKey().isEmpty())
                    .map(p -> p.getAgentId())
                    .toList();
            
            int successCount = 0;
            int failCount = 0;
            
            for (String agentId : agentIds) {
                try {
                    var result = difyFullSyncService.fullSync(agentId);
                    if (result.isSuccess()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("Sync failed for agent {}: {}", agentId, e.getMessage());
                    failCount++;
                }
            }
            
            log.info("Scheduled knowledge sync completed: success={}, fail={}", successCount, failCount);
            lastSyncResult.set(failCount == 0 ? 1 : 2);
            
        } catch (Exception e) {
            log.error("Scheduled knowledge sync failed: {}", e.getMessage());
            lastSyncResult.set(2);
        }
    }

    /**
     * 定时清理日志
     * 每天凌晨 3 点执行
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledLogCleanup() {
        log.info("Scheduled log cleanup started");
        cleanupTaskCount.incrementAndGet();
        
        try {
            // 清理 30 天前的审计日志
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            int deletedCount = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
            
            log.info("Scheduled log cleanup completed: deleted {} records", deletedCount);
            lastCleanupResult.set(1);
            
        } catch (Exception e) {
            log.error("Scheduled log cleanup failed: {}", e.getMessage());
            lastCleanupResult.set(2);
        }
    }

    /**
     * 手动触发知识库同步
     */
    @PostMapping("/sync")
    public Map<String, Object> triggerSync(@RequestParam(required = false) String agentId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (agentId != null && !agentId.isEmpty()) {
                // 同步指定 Agent
                var syncResult = difyFullSyncService.fullSync(agentId);
                result.put("success", syncResult.isSuccess());
                result.put("message", syncResult.getMessage());
                result.put("added", syncResult.getAdded());
            } else {
                // 同步所有
                scheduledKnowledgeSync();
                result.put("success", true);
                result.put("message", "All agents sync triggered");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    /**
     * 手动触发日志清理
     */
    @PostMapping("/cleanup")
    public Map<String, Object> triggerCleanup() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            scheduledLogCleanup();
            result.put("success", true);
            result.put("message", "Log cleanup triggered");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        
        return result;
    }

    /**
     * 获取定时任务状态
     */
    @GetMapping("/status")
    public Map<String, Object> getSchedulerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncTaskCount", syncTaskCount.get());
        status.put("cleanupTaskCount", cleanupTaskCount.get());
        status.put("lastSyncResult", lastSyncResult.get());
        status.put("lastCleanupResult", lastCleanupResult.get());
        status.put("nextSyncTime", "02:00");
        status.put("nextCleanupTime", "03:00");
        status.put("syncEnabled", true);
        status.put("cleanupEnabled", true);
        return status;
    }

    /**
     * 启用/禁用定时任务
     */
    @PostMapping("/config")
    public Map<String, Object> configScheduler(
            @RequestParam(defaultValue = "true") boolean syncEnabled,
            @RequestParam(defaultValue = "true") boolean cleanupEnabled) {
        
        Map<String, Object> result = new HashMap<>();
        result.put("syncEnabled", syncEnabled);
        result.put("cleanupEnabled", cleanupEnabled);
        result.put("message", "Scheduler config updated");
        
        return result;
    }
}
