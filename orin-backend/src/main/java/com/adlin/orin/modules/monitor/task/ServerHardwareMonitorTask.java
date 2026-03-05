package com.adlin.orin.modules.monitor.task;

import com.adlin.orin.modules.monitor.service.MonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 服务器硬件监控数据采集定时任务
 * 自动定期采集硬件监控数据并保存到数据库
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "orin.hardware.monitor.enabled", havingValue = "true", matchIfMissing = true)
public class ServerHardwareMonitorTask {

    private final MonitorService monitorService;

    /**
     * 每分钟采集一次硬件监控数据
     * 可通过 orin.hardware.monitor.interval 配置采集间隔（毫秒）
     */
    @Scheduled(fixedRateString = "${orin.hardware.monitor.interval:60000}")
    public void collectHardwareMetrics() {
        try {
            log.debug("Collecting server hardware metrics...");
            monitorService.saveServerHardwareMetric();
        } catch (Exception e) {
            log.error("Failed to collect server hardware metrics", e);
        }
    }
}
