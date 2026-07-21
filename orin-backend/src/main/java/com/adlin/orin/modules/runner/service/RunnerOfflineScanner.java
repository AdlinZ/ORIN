package com.adlin.orin.modules.runner.service;

import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Runner 心跳超时离线扫描器。
 *
 * <p>F01 行为：
 * <ul>
 *   <li>每 10s 扫一次（默认 {@code orin.runner.heartbeat.scan-interval-ms=10000}）</li>
 *   <li>状态 ONLINE / DEGRADED / DRAINING 且 {@code now - lastHeartbeatAt > offline-threshold-sec}（默认 60s）
 *       → OFFLINE</li>
 *   <li>状态 ENROLLING 且 {@code now - createdAt > 30min} → OFFLINE（防止 enrollment 卡死）</li>
 *   <li>DRAINING 继续心跳；一旦超时同样转 OFFLINE，但 {@code drainRequested} 意图继续保留</li>
 *   <li>每次状态变更写审计</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RunnerOfflineScanner {

    private final RunnerRepository runnerRepository;
    private final AuditHelper auditHelper;

    @Value("${orin.runner.heartbeat.offline-threshold-sec:60}")
    private long offlineThresholdSec;

    @Value("${orin.runner.enrollment.stuck-timeout-min:30}")
    private long stuckEnrollmentTimeoutMin;

    @Scheduled(fixedDelayString = "${orin.runner.heartbeat.scan-interval-ms:10000}",
            initialDelayString = "${orin.runner.heartbeat.scan-initial-delay-ms:30000}")
    @Transactional
    public void scanStaleRunners() {
        long now = Instant.now().toEpochMilli();
        long heartbeatThreshold = now - offlineThresholdSec * 1000L;
        long enrollmentThreshold = now - stuckEnrollmentTimeoutMin * 60_000L;

        // 1) ONLINE / DEGRADED / DRAINING 且心跳超时 → OFFLINE。
        //    drainRequested 独立保存，Runner 重连后仍回到 DRAINING。
        List<Runner> stale = runnerRepository.findStaleActive(
                Set.of(RunnerStatus.ONLINE, RunnerStatus.DEGRADED, RunnerStatus.DRAINING),
                heartbeatThreshold);
        for (Runner runner : stale) {
            RunnerStatus old = runner.getStatus();
            runner.setStatus(RunnerStatus.OFFLINE);
            runnerRepository.save(runner);
            auditHelper.log("system", "RUNNER_STATUS_CHANGED",
                    "/api/system/runners/scan-offline",
                    "runnerId=" + runner.getId() + ", oldStatus=" + old
                            + ", newStatus=OFFLINE, reason=heartbeat-stale",
                    true, null);
            log.info("Runner {} marked OFFLINE by heartbeat scan (oldStatus={})",
                    runner.getId(), old);
        }

        // 2) ENROLLING 卡死 → OFFLINE
        List<Runner> stuck = runnerRepository.findStaleByStatus(
                RunnerStatus.ENROLLING, enrollmentThreshold);
        for (Runner runner : stuck) {
            RunnerStatus old = runner.getStatus();
            runner.setStatus(RunnerStatus.OFFLINE);
            runnerRepository.save(runner);
            auditHelper.log("system", "RUNNER_STATUS_CHANGED",
                    "/api/system/runners/scan-offline",
                    "runnerId=" + runner.getId() + ", oldStatus=" + old
                            + ", newStatus=OFFLINE, reason=enrollment-stuck",
                    true, null);
            log.info("Runner {} marked OFFLINE (enrollment stuck > {} min)",
                    runner.getId(), stuckEnrollmentTimeoutMin);
        }
    }
}
