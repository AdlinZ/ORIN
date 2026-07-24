package com.adlin.orin.modules.run.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.run.dto.CreateRunRequest;
import com.adlin.orin.modules.run.dto.LeaseRunResponse;
import com.adlin.orin.modules.run.dto.BatchEventsRequest;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.dto.SecretBindResponse;
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
                .traceId(UUID.randomUUID().toString())
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
        run.setTerminalReason("USER_CANCELLED");
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
                .retryOfRunId(runId)
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
     * Runner 轮询领取排队的 Run（ADR-001 /lease/claim）。
     * <p>MVP 不做真正的 long-poll；无可用 Run 时返回 acquired=false。
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

        // 分配 lease（MVP：leaseToken 充当 lease 标识 + 鉴权，R2 引入独立 leaseId）
        String leaseToken = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        run.setStatus(RunStatus.LEASED);
        run.setLeaseToken(leaseToken);
        run.setLeasedAt(now);
        run.setLeaseExpiresAt(now + LEASE_TIMEOUT_MS);
        run.setRunnerId(runnerId); // 以实际领取的 Runner 为准
        run.setRunAttempt(run.getRunAttempt() + 1);
        runRepository.save(run);

        log.info("Run leased: {} → runner={} expires={}",
                run.getId(), runnerId, run.getLeaseExpiresAt());

        return LeaseRunResponse.builder()
                .acquired(true)
                .runId(run.getId())
                .leaseToken(leaseToken)
                .configSnapshot(run.getConfigSnapshot())
                .input(run.getInput())
                .leaseExpiresAt(run.getLeaseExpiresAt())
                .traceId(run.getTraceId())
                .build();
    }

    /**
     * Runner 提交最终执行结果（ADR-001 /result）。
     * <p>合并旧 completeRun + failRun 为单一端点。
     * 首次 /result 时原子将 LEASED→RUNNING→COMPLETED/FAILED。
     */
    @Transactional
    public void submitResult(String runId, String leaseToken, String resultStatus,
                             String output, String errorMessage, String errorCode) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }

        boolean success = "COMPLETED".equalsIgnoreCase(resultStatus);
        long now = Instant.now().toEpochMilli();

        // 如果在 LEASED 状态收到 result，自动过渡到 RUNNING 再终结
        if (run.getStatus() == RunStatus.LEASED) {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(now);
        }

        if (run.getStatus() != RunStatus.RUNNING) {
            throw new BusinessException(ErrorCode.RUN_INVALID_STATE,
                    "Run 不在可执行状态: " + run.getStatus());
        }

        if (success) {
            run.setStatus(RunStatus.COMPLETED);
            run.setOutput(output);
        } else {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(errorMessage);
        }
        run.setCompletedAt(now);
        runRepository.save(run);

        log.info("Run result: {} status={} {}", runId, resultStatus,
                errorMessage != null ? "error=" + errorMessage : "");
    }

    /**
     * Runner 获取物化 secrets（ADR-001/ADR-002 /secret-bind）。
     * <p>R2 实现前返回 501 FEATURE_NOT_AVAILABLE。
     * 当前不存在 run_assignment 表与 lease 持久化，
     * 无法校验 assignmentId 与 run/runner/lease 的关联。
     */
    public SecretBindResponse bindSecrets(String runId, String assignmentId) {
        throw new BusinessException(ErrorCode.RUN_FEATURE_NOT_AVAILABLE,
                "secret-bind 在 R2 run_assignment 持久化之前不可用。"
                        + "当前无法校验 assignmentId=" + assignmentId
                        + " 与 run=" + runId + " 的关联。");
    }

    // ============================================================
    // F04 事件推送 / 日志拉取
    // ============================================================

    /**
     * Runner 批量推送中间态事件 / 日志（ADR-001 /events）。
     * <p>使用 Runner 提供的 seq 作为 event 序号；
     * 幂等键 run:idemp:{runId}:{leaseToken}:{runAttempt}:{eventSeq}。
     * R2 接入 run_events 表 + UNIQUE 约束后实现真正的幂等抑制。
     */
    @Transactional
    public void appendEvents(String runId, String leaseToken,
                             List<BatchEventsRequest.EventEntry> events) {
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));
        if (!leaseToken.equals(run.getLeaseToken())) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease token 不匹配");
        }
        if (!run.getStatus().isActive()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Run 已终结，不可写事件");
        }

        for (BatchEventsRequest.EventEntry event : events) {
            RunLog entry = RunLog.builder()
                    .runId(runId)
                    .sequence(event.getSeq() != null ? event.getSeq() : 0)
                    .level(event.getLevel() != null ? event.getLevel() : "INFO")
                    .message(event.getMessage())
                    .build();
            runLogRepository.save(entry);
        }
        if (!events.isEmpty()) {
            log.debug("Run events appended: run={} count={}", runId, events.size());
        }
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
     * 将超时未完成的 Run 标记为 FAILED（ADR-001 D-1.4.3: NETWORK_LOST）。
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
                run.setTerminalReason("NETWORK_LOST");
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
