package com.adlin.orin.modules.runner.dto;

import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerHeartbeatSnapshot;
import com.adlin.orin.modules.runner.entity.RunnerStatus;

import java.time.Instant;
import java.util.List;

/**
 * Runner 详情视图对象。
 *
 * <p>包含最新 heartbeat snapshot 与凭据元数据（last4/keyPrefix/status），**不**返回凭据 hash。
 */
public record RunnerDetail(
        String id,
        String name,
        RunnerStatus status,
        String version,
        String hostname,
        String os,
        String arch,
        String labels,
        String capabilities,
        String gpuInfo,
        Integer cpuCores,
        Long memoryTotal,
        Long diskTotal,
        Integer maxConcurrency,
        Integer activeRuns,
        Integer queuedRuns,
        Long lastHeartbeatAt,
        Long lastHeartbeatAgeSec,
        String lastDependencyHealth,
        Boolean drainRequested,
        Long drainAckAt,
        String createdBy,
        Long createdAt,
        Long updatedAt,
        RunnerHeartbeatSnapshot latestSnapshot,
        List<RunnerHeartbeatSnapshot> recentSnapshots,
        CredentialSummary credential) {

    public static RunnerDetail from(Runner runner,
                                    RunnerHeartbeatSnapshot latest,
                                    List<RunnerHeartbeatSnapshot> recent,
                                    RunnerCredential credential) {
        Long lastHb = runner.getLastHeartbeatAt();
        Long age = lastHb == null ? null : Math.max(0L,
                (Instant.now().toEpochMilli() - lastHb) / 1000L);
        return new RunnerDetail(
                runner.getId(),
                runner.getName(),
                runner.getStatus(),
                runner.getVersion(),
                runner.getHostname(),
                runner.getOs(),
                runner.getArch(),
                runner.getLabels(),
                runner.getCapabilities(),
                runner.getGpuInfo(),
                runner.getCpuCores(),
                runner.getMemoryTotal(),
                runner.getDiskTotal(),
                runner.getMaxConcurrency(),
                runner.getActiveRuns(),
                runner.getQueuedRuns(),
                lastHb,
                age,
                runner.getLastDependencyHealth(),
                runner.getDrainRequested(),
                runner.getDrainAckAt(),
                runner.getCreatedBy(),
                runner.getCreatedAt(),
                runner.getUpdatedAt(),
                latest,
                recent,
                credential == null ? null : CredentialSummary.from(credential));
    }

    public record CredentialSummary(
            String credentialId,
            String keyPrefix,
            String last4,
            RunnerCredential.Status status,
            Long createdAt,
            Long revokedAt) {
        public static CredentialSummary from(RunnerCredential credential) {
            return new CredentialSummary(
                    credential.getCredentialId(),
                    credential.getKeyPrefix(),
                    credential.getLast4(),
                    credential.getStatus(),
                    credential.getCreatedAt(),
                    credential.getRevokedAt());
        }
    }
}
