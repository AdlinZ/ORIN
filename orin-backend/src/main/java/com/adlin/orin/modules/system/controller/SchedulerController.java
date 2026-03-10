package com.adlin.orin.modules.system.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 定时任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerController {

    // 简单的内存计数器
    private final AtomicLong syncTaskCount = new AtomicLong(0);
    private final AtomicLong cleanupTaskCount = new AtomicLong(0);

    /**
     * 定时同步知识库
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点
    public void scheduledKnowledgeSync() {
        log.info("Scheduled knowledge base sync started");
        syncTaskCount.incrementAndGet();
        // TODO: 实现知识库同步逻辑
    }

    /**
     * 定时清理日志
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点
    public void scheduledLogCleanup() {
        log.info("Scheduled log cleanup started");
        cleanupTaskCount.incrementAndGet();
        // TODO: 实现日志清理逻辑
    }

    /**
     * 获取定时任务状态
     */
    @GetMapping("/status")
    public Map<String, Object> getSchedulerStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("syncTaskCount", syncTaskCount.get());
        status.put("cleanupTaskCount", cleanupTaskCount.get());
        status.put("nextSyncTime", "02:00");
        status.put("nextCleanupTime", "03:00");
        return status;
    }
}
