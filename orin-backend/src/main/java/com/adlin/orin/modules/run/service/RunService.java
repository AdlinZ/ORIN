package com.adlin.orin.modules.run.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.run.dto.CreateRunRequest;
import com.adlin.orin.modules.run.dto.LeaseRunResponse;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.entity.RunStatus;
import com.adlin.orin.modules.run.entity.RunLog;
import com.adlin.orin.modules.run.repository.RunLogRepository;
import com.adlin.orin.modules.run.repository.RunRepository;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Run 核心服务（F03）。
 *
 * <p>生命周期：create → lease → start → complete/fail/cancel。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RunService {

    private static final long LEASE_TIMEOUT_MS = 30_000; // 30s
    private static final int LEASE_BATCH_SIZE = 1;

    private final RunRepository runRepository;
    private final RunLogRepository runLogRepository;
    private final AgentMetadataRepository agentMetadataRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final RunnerRepository runnerRepository;

    // ============================================================
    // 业务 API
    // ============================================================

    /**
     * 创建 Run：选择已冻结 Agent + 可用 Runner → QUEUED。
     */
    @Transactional
    public RunResponse createRun(CreateRunRequest request, String createdBy) {
        // 1. 校验 Agent 存在
        AgentMetadata agent = agentMetadataRepository.findById(request.getAgentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 不存在: " + request.getAgentId()));

        // 2. 校验 AgentVersion 存在且为 FROZEN
        AgentVersion version = agentVersionRepository.findById(request.getAgentVersionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_VERSION_NOT_FROZEN,
                        "AgentVersion 不存在: " + request.getAgentVersionId()));
        if (version.getStatus() != AgentVersion.Status.FROZEN) {
            throw new BusinessException(ErrorCode.RUN_VERSION_NOT_FROZEN,
                    "AgentVersion 未冻结，不可执行: " + request.getAgentVersionId());
        }
        if (!version.getAgentId().equals(request.getAgentId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "AgentVersion 不属于该 Agent");
        }

        // 3. 校验 Runner 存在且 ONLINE
        Runner runner = runnerRepository.findById(request.getRunnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_NOT_FOUND,
                        "Runner 不存在: " + request.getRunnerId()));
        if (runner.getStatus() != RunnerStatus.ONLINE && runner.getStatus() != RunnerStatus.DEGRADED) {
            throw new BusinessException(ErrorCode.RUNNER_OFFLINE,
                    "Runner 不可用: " + runner.getStatus());
        }

        // 4. 创建 Run
        Run run = Run.builder()
                .id("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .agentId(agent.getAgentId())
                .agentVersionId(version.getId())
                .runnerId(runner.getId())
                .status(RunStatus.QUEUED)
                .configSnapshot(version.getConfigSnapshot())
                .input(request.getInput())
                .createdBy(createdBy)
                .build();

        run = runRepository.save(run);
        log.info("Run created: {} agent={} version={} runner={}",
                run.getId(), agent.getAgentId(), version.getId(), runner.getId());

        return RunResponse.from(run);
    }

    /**
     * 取消 Run。
     */
    @Transactional
    public RunResponse cancelRun(String runId, String operator) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (!run.isCancellable()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Run 当前状态不可取消: " + run.getStatus());
        }
        run.setStatus(RunStatus.CANCELLED);
        run.setCompletedAt(Instant.now().toEpochMilli());
        run = runRepository.save(run);
        log.info("Run cancelled: {} by {}", runId, operator);
        return RunResponse.from(run);
    }

    /**
     * 重试 Run（从失败/取消状态创建新 Run）。
     */
    @Transactional
    public RunResponse retryRun(String runId, String operator) {
        Run original = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (!original.isRetryable()) {
            throw new BusinessException(ErrorCode.RUN_RETRY_EXHAUSTED,
                    "Run 不可重试: status=" + original.getStatus()
                            + " retryCount=" + original.getRetryCount());
        }

        Run retry = Run.builder()
                .id("run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32))
                .agentId(original.getAgentId())
                .agentVersionId(original.getAgentVersionId())
                .runnerId(original.getRunnerId())
                .status(RunStatus.QUEUED)
                .configSnapshot(original.getConfigSnapshot())
                .input(original.getInput())
                .createdBy(operator)
                .retryCount(original.getRetryCount() + 1)
                .maxRetries(original.getMaxRetries())
                .originalRunId(runId)
                .build();

        retry = runRepository.save(retry);
        log.info("Run retried: {} → {} by {}", runId, retry.getId(), operator);
        return RunResponse.from(retry);
    }

    // ============================================================
    // 查询 API
    // ============================================================

    @Transactional(readOnly = true)
    public RunResponse getRun(String runId) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        return RunResponse.from(run);
    }

    @Transactional(readOnly = true)
    public Page<RunResponse> listRuns(Pageable pageable) {
        return runRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(RunResponse::from);
    }

    // ============================================================
    // 机器通道 API（Runner 侧调用）
    // ============================================================

    /**
     * Runner 轮询领取排队的 Run。
     */
    @Transactional
    public LeaseRunResponse leaseRun(String runnerId) {
        Runner runner = runnerRepository.findById(runnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_NOT_FOUND));

        if (runner.getStatus() != RunnerStatus.ONLINE && runner.getStatus() != RunnerStatus.DEGRADED) {
            return LeaseRunResponse.empty();
        }

        // 找最早排队的 Run
        List<Run> queued = runRepository.findOldestQueuedForLease(
                PageRequest.of(0, LEASE_BATCH_SIZE));
        if (queued.isEmpty()) {
            return LeaseRunResponse.empty();
        }

        Run run = queued.get(0);

        // 分配 lease
        String leaseToken = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        run.setStatus(RunStatus.LEASED);
        run.setLeaseToken(leaseToken);
        run.setLeasedAt(now);
        run.setLeaseExpiresAt(now + LEASE_TIMEOUT_MS);
        run.setRunnerId(runnerId); // 以实际领取的 Runner 为准
        runRepository.save(run);

        log.info("Run leased: {} → runner={} expires={}", run.getId(), runnerId, run.getLeaseExpiresAt());

        return LeaseRunResponse.builder()
                .acquired(true)
                .runId(run.getId())
                .leaseToken(leaseToken)
                .configSnapshot(run.getConfigSnapshot())
                .input(run.getInput())
                .leaseExpiresAt(run.getLeaseExpiresAt())
                .build();
    }

    /**
     * Runner 确认开始执行 Run。
     */
    @Transactional
    public void startRun(String runId, String leaseToken) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        if (run.getStatus() != RunStatus.LEASED) {
            throw new BusinessException(ErrorCode.RUN_INVALID_STATE,
                    "Run 不在 LEASED 状态: " + run.getStatus());
        }
        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }
        long now = Instant.now().toEpochMilli();
        if (run.getLeaseExpiresAt() != null && now > run.getLeaseExpiresAt()) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 已过期");
        }

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(now);
        runRepository.save(run);
        log.info("Run started: {}", runId);
    }

    /**
     * Runner 上报执行完成。
     */
    @Transactional
    public void completeRun(String runId, String leaseToken, String output) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        if (run.getStatus() != RunStatus.RUNNING) {
            throw new BusinessException(ErrorCode.RUN_INVALID_STATE,
                    "Run 不在 RUNNING 状态: " + run.getStatus());
        }
        // Re-verify lease token for security
        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }

        run.setStatus(RunStatus.COMPLETED);
        run.setOutput(output);
        run.setCompletedAt(Instant.now().toEpochMilli());
        runRepository.save(run);
        log.info("Run completed: {}", runId);
    }

    /**
     * Runner 上报执行失败。
     */
    @Transactional
    public void failRun(String runId, String leaseToken, String errorMessage) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        if (run.getStatus() != RunStatus.RUNNING && run.getStatus() != RunStatus.LEASED) {
            throw new BusinessException(ErrorCode.RUN_INVALID_STATE,
                    "Run 不可标记失败: " + run.getStatus());
        }
        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }

        run.setStatus(RunStatus.FAILED);
        run.setErrorMessage(errorMessage);
        run.setCompletedAt(Instant.now().toEpochMilli());
        runRepository.save(run);
        log.info("Run failed: {} — {}", runId, errorMessage);
    }

    // ============================================================
    // F04 日志推送 / 拉取
    // ============================================================

    /**
     * Runner 推送一条日志行。
     */
    @Transactional
    public void appendLog(String runId, String leaseToken, String level, String message) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }
        if (!run.getStatus().isActive()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Run 已终结，不可写日志");
        }

        long count = runLogRepository.countByRunId(runId);
        RunLog entry = RunLog.builder()
                .runId(runId)
                .sequence((int) count)
                .level(level != null ? level : "INFO")
                .message(message)
                .build();
        runLogRepository.save(entry);
    }

    /**
     * 增量拉取日志（afterSeq 之后的新行）。
     */
    @Transactional(readOnly = true)
    public List<RunLog> getLogs(String runId, Integer afterSeq) {
        if (afterSeq != null && afterSeq >= 0) {
            return runLogRepository.findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(runId, afterSeq);
        }
        return runLogRepository.findByRunIdOrderBySequenceAsc(runId);
    }

    // ============================================================
    // F04 超时检测
    // ============================================================

    /**
     * 将超过 5 分钟仍 RUNNING 的 Run 标记为 FAILED。
     * 由定时任务调用。
     */
    @Transactional
    public int timeoutStaleRuns() {
        long now = Instant.now().toEpochMilli();
        long timeoutThreshold = now - 300_000; // 5 min
        List<Run> stale = runRepository.findByStatusOrderByCreatedAtAsc(
                RunStatus.RUNNING, PageRequest.of(0, 100));
        int count = 0;
        for (Run run : stale) {
            if (run.getStartedAt() != null && run.getStartedAt() < timeoutThreshold) {
                run.setStatus(RunStatus.FAILED);
                run.setErrorMessage("Run 执行超时（超过 5 分钟无响应）");
                run.setCompletedAt(now);
                runRepository.save(run);
                count++;
            }
        }
        if (count > 0) {
            log.info("超时检测：{} 个 Run 标记为 FAILED", count);
        }
        return count;
    }
}
