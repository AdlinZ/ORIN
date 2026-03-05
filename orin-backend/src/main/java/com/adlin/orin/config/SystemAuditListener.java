package com.adlin.orin.config;

import com.adlin.orin.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAuditListener {

    private final AuditLogService auditLogService;

    private long startupStartTime;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationStarted() {
        // 记录启动开始时间
        startupStartTime = System.currentTimeMillis();
        log.info("Application started, recording bootstrap start time: {}ms", startupStartTime);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();

            // 计算启动耗时
            long bootstrapDuration = System.currentTimeMillis() - startupStartTime;

            log.info("System started successfully in {}ms. Logging startup event to audit log.", bootstrapDuration);

            auditLogService.logApiCall(
                    "SYSTEM",
                    "INTERNAL",
                    "ORIN_CORE",
                    "SYSTEM_LIFECYCLE",
                    "BOOTSTRAP",
                    "STARTUP",
                    null,
                    hostAddress + " (" + hostName + ")",
                    "Spring Boot Context",
                    "System Startup",
                    "Application Ready",
                    200,
                    bootstrapDuration,
                    0,
                    0,
                    0.0,
                    true,
                    null);
        } catch (Exception e) {
            log.error("Failed to log system startup event", e);
        }
    }
}
