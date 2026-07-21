package com.adlin.orin.modules.runner.dto;

import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;

import java.time.Instant;

/**
 * Runner 列表项视图对象。
 *
 * <p>F01 列表只暴露核心字段；资源/心跳详情通过 {@code /api/v1/runners/{id}} 拿。
 */
public record RunnerListItem(
        String id,
        String name,
        RunnerStatus status,
        String version,
        String hostname,
        Long lastHeartbeatAt,
        Long lastHeartbeatAgeSec,
        Integer cpuCores,
        Long memoryTotal,
        Long diskTotal,
        Integer activeRuns,
        Integer queuedRuns,
        Integer maxConcurrency,
        Boolean drainRequested,
        String createdBy,
        Long createdAt,
        Long updatedAt) {

    public static RunnerListItem from(Runner runner) {
        Long lastHb = runner.getLastHeartbeatAt();
        Long age = lastHb == null ? null : Math.max(0L,
                (Instant.now().toEpochMilli() - lastHb) / 1000L);
        return new RunnerListItem(
                runner.getId(),
                runner.getName(),
                runner.getStatus(),
                runner.getVersion(),
                runner.getHostname(),
                lastHb,
                age,
                runner.getCpuCores(),
                runner.getMemoryTotal(),
                runner.getDiskTotal(),
                runner.getActiveRuns(),
                runner.getQueuedRuns(),
                runner.getMaxConcurrency(),
                runner.getDrainRequested(),
                runner.getCreatedBy(),
                runner.getCreatedAt(),
                runner.getUpdatedAt());
    }
}
