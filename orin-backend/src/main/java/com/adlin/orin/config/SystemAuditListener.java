package com.adlin.orin.config;

import com.adlin.orin.modules.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Slf4j
@Component
@RequiredArgsConstructor
public class SystemAuditListener {

    private final AuditLogService auditLogService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            String hostName = InetAddress.getLocalHost().getHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();

            log.info("System started successfully. Logging startup event to audit log.");

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
                    0L,
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
