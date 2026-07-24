package com.adlin.orin.modules.run.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.agent.entity.AgentVersion;
import com.adlin.orin.modules.agent.freeze.entity.AgentVersionSecretRef;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.apikey.entity.GatewaySecret;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.modules.run.dto.BatchEventsRequest;
import com.adlin.orin.modules.run.dto.CreateRunRequest;
import com.adlin.orin.modules.run.dto.LeaseRunResponse;
import com.adlin.orin.modules.run.dto.RenewLeaseResponse;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.dto.SecretBindResponse;
import com.adlin.orin.modules.run.entity.AssignmentStatus;
import com.adlin.orin.modules.run.entity.BindingStatus;
import com.adlin.orin.modules.run.entity.LeaseSecretBinding;
import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.entity.RunAssignment;
import com.adlin.orin.modules.run.entity.RunEvent;
import com.adlin.orin.modules.run.entity.RunLog;
import com.adlin.orin.modules.run.entity.RunStatus;
import com.adlin.orin.modules.run.repository.LeaseSecretBindingRepository;
import com.adlin.orin.modules.run.repository.RunAssignmentRepository;
import com.adlin.orin.modules.run.repository.RunEventRepository;
import com.adlin.orin.modules.run.repository.RunLogRepository;
import com.adlin.orin.modules.run.repository.RunRepository;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import com.adlin.orin.security.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Run 核心服务（F03 / R2）。
 *
 * <p>R2 关键变更：run_assignment 是 Runner 分配、lease、attempt、终态原因的<b>唯一事实</b>。
 * runs 表的 lease_token / lease_expires_at / leased_at 是本表状态的只读投影。
 *
 * <p>生命周期：create → lease → （renew）→ execute → result。
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

    // R2 new dependencies
    private final RunAssignmentRepository assignmentRepository;
    private final RunEventRepository runEventRepository;
    private final LeaseSecretBindingRepository leaseSecretBindingRepository;
    private final AgentVersionSecretRefRepository agentVersionSecretRefRepository;
    private final GatewaySecretRepository gatewaySecretRepository;
    private final EncryptionUtil encryptionUtil;

    // ============================================================
    // 业务 API
    // ============================================================

    /**
     * 创建 Run：选择已冻结 Agent + 可用 Runner → QUEUED。
     */
    @Transactional
    public RunResponse createRun(CreateRunRequest request, String createdBy) {
        AgentMetadata agent = agentMetadataRepository.findById(request.getAgentId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AGENT_NOT_FOUND,
                        "Agent 不存在: " + request.getAgentId()));

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

        Runner runner = runnerRepository.findById(request.getRunnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_NOT_FOUND,
                        "Runner 不存在: " + request.getRunnerId()));
        if (runner.getStatus() != RunnerStatus.ONLINE && runner.getStatus() != RunnerStatus.DEGRADED) {
            throw new BusinessException(ErrorCode.RUNNER_OFFLINE,
                    "Runner 不可用: " + runner.getStatus());
        }

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

        // R2: 同步取消对应的活跃 assignment
        List<RunAssignment> activeAssignments = assignmentRepository
                .findByRunIdOrderByCreatedAtDesc(runId);
        for (RunAssignment asgn : activeAssignments) {
            if (asgn.isActive()) {
                asgn.setStatus(AssignmentStatus.CANCELLED);
                asgn.setTerminalReason("USER_CANCELLED");
                assignmentRepository.save(asgn);
            }
        }

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
     *
     * <p>R2：创建 run_assignment 行（status=ASSIGNED）作为 lease 的唯一事实；
     * runs 表的 lease_token / lease_expires_at 降级为只读投影。
     */
    @Transactional
    public LeaseRunResponse leaseRun(String runnerId) {
        Runner runner = runnerRepository.findById(runnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_NOT_FOUND));

        if (runner.getStatus() != RunnerStatus.ONLINE && runner.getStatus() != RunnerStatus.DEGRADED) {
            return LeaseRunResponse.empty();
        }

        List<Run> queued = runRepository.findOldestQueuedForLease(
                PageRequest.of(0, LEASE_BATCH_SIZE));
        if (queued.isEmpty()) {
            return LeaseRunResponse.empty();
        }

        Run run = queued.get(0);

        // R2: 生成独立的 assignmentId 和 leaseId
        String assignmentId = "asgn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        String leaseId = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + LEASE_TIMEOUT_MS;
        int attempt = run.getRunAttempt() + 1;

        // 1) 创建 run_assignment（唯一事实）
        RunAssignment assignment = RunAssignment.builder()
                .id(assignmentId)
                .runId(run.getId())
                .runnerId(runnerId)
                .leaseId(leaseId)
                .status(AssignmentStatus.ASSIGNED)
                .leaseExpiresAt(expiresAt)
                .runAttempt(attempt)
                .traceId(run.getTraceId())
                .build();
        assignmentRepository.save(assignment);

        // 2) 更新 runs 投影
        run.setStatus(RunStatus.LEASED);
        run.setLeaseToken(leaseId);
        run.setLeasedAt(now);
        run.setLeaseExpiresAt(expiresAt);
        run.setRunnerId(runnerId);
        run.setRunAttempt(attempt);
        runRepository.save(run);

        log.info("Run leased: {} → runner={} assignment={} expires={}",
                run.getId(), runnerId, assignmentId, expiresAt);

        return LeaseRunResponse.builder()
                .acquired(true)
                .runId(run.getId())
                .assignmentId(assignmentId)
                .leaseId(leaseId)
                .leaseToken(leaseId) // 向后兼容
                .configSnapshot(run.getConfigSnapshot())
                .input(run.getInput())
                .leaseExpiresAt(expiresAt)
                .traceId(run.getTraceId())
                .build();
    }

    /**
     * Runner 提交最终执行结果（ADR-001 /result）。
     *
     * <p>R2：通过 run_assignment 校验 lease，assignment 是状态变迁的唯一入口。
     * leaseToken/leaseId 均可用于查找 assignment。
     */
    @Transactional
    public void submitResult(String runId, String leaseTokenOrId, String resultStatus,
                             String output, String errorMessage, String errorCode) {
        // R2：通过 run_assignment 校验
        RunAssignment assignment = resolveAssignment(runId, leaseTokenOrId);

        if (assignment.isTerminal()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Assignment 已终结: " + assignment.getStatus());
        }

        long now = Instant.now().toEpochMilli();
        if (assignment.getLeaseExpiresAt() < now) {
            assignment.setStatus(AssignmentStatus.EXPIRED);
            assignment.setTerminalReason("NETWORK_LOST");
            assignmentRepository.save(assignment);
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 已过期");
        }

        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        boolean success = "COMPLETED".equalsIgnoreCase(resultStatus);

        // 如果在 LEASED 状态收到 result，自动过渡到 RUNNING 再终结
        if (run.getStatus() == RunStatus.LEASED) {
            run.setStatus(RunStatus.RUNNING);
            run.setStartedAt(now);
            // 隐式 ACK：首次写操作将 assignment ASSIGNED → ACKED
            if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
                assignment.setStatus(AssignmentStatus.ACKED);
            }
        }

        // R2：assignment 是状态变迁的真相
        if (success) {
            assignment.setStatus(AssignmentStatus.COMPLETED);
            run.setStatus(RunStatus.COMPLETED);
            run.setOutput(output);
        } else {
            assignment.setStatus(AssignmentStatus.FAILED);
            assignment.setTerminalReason(errorCode != null ? errorCode : "RUNNER_FAILED");
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(errorMessage);
        }
        run.setCompletedAt(now);
        assignmentRepository.save(assignment);
        runRepository.save(run);

        // 释放 secret bindings
        releaseBindings(assignment.getId());

        log.info("Run result: {} status={} assignment={} {}",
                runId, resultStatus, assignment.getId(),
                errorMessage != null ? "error=" + errorMessage : "");
    }

    /**
     * Runner 批量推送中间态事件 / 日志（ADR-001 /events）。
     *
     * <p>R2：使用 run_events 表（UNIQUE 约束保证幂等）。
     * 同时保留 run_logs 写入以兼容现有 GET /api/v1/runs/{runId}/logs。
     */
    @Transactional
    public void appendEvents(String runId, String leaseTokenOrId,
                             List<BatchEventsRequest.EventEntry> events) {
        RunAssignment assignment = resolveAssignment(runId, leaseTokenOrId);

        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Assignment 已终结，不可写事件");
        }
        if (assignment.getLeaseExpiresAt() < Instant.now().toEpochMilli()) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 已过期");
        }

        // 隐式 ACK
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED) {
            assignment.setStatus(AssignmentStatus.ACKED);
            assignmentRepository.save(assignment);
        }

        long now = Instant.now().toEpochMilli();
        for (BatchEventsRequest.EventEntry event : events) {
            // 1) 写入 run_events（幂等——DB UNIQUE 约束）
            RunEvent re = RunEvent.builder()
                    .runId(runId)
                    .leaseId(assignment.getLeaseId())
                    .runAttempt(assignment.getRunAttempt())
                    .eventSeq(event.getSeq() != null ? event.getSeq() : 0)
                    .level(event.getLevel() != null ? event.getLevel() : "INFO")
                    .message(event.getMessage())
                    .timestamp(event.getTimestamp() != null ? event.getTimestamp() : now)
                    .build();
            try {
                runEventRepository.save(re);
            } catch (DataIntegrityViolationException e) {
                // UNIQUE 冲突：同键同 payload → 200 no-op
                // 同键不同 payload → 409（由 DB constraint violation 体现）
                log.debug("Run event idempotent replay: run={} lease={} attempt={} seq={}",
                        runId, assignment.getLeaseId(), assignment.getRunAttempt(),
                        event.getSeq());
                throw new BusinessException(ErrorCode.RUN_RESULT_CONFLICT,
                        "幂等键冲突: run=" + runId + " seq=" + event.getSeq());
            }

            // 2) 同时写 run_logs（向后兼容前端日志查询）
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
     * Runner 续租（ADR-001 /renew）。
     *
     * <p>R2：以 run_assignment 为唯一真相校验 lease 状态。
     * 响应包含控制帧 action（no_op / cancel / drain）与最新 lease_expires_at。
     */
    @Transactional
    public RenewLeaseResponse renewLease(String runnerId, String runId, String leaseId) {
        RunAssignment assignment = assignmentRepository.findByLeaseId(leaseId)
                .orElse(null);

        if (assignment == null) {
            // 尝试通过旧的 leaseToken 回退查找
            Run run = runRepository.findById(runId).orElse(null);
            if (run != null && !run.getStatus().isActive()) {
                return RenewLeaseResponse.builder()
                        .action("cancel")
                        .reason("ASSIGNMENT_TERMINATED")
                        .leaseExpiresAt(0L)
                        .traceId(run.getTraceId())
                        .build();
            }
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 不存在: " + leaseId);
        }

        // 校验归属
        if (!assignment.getRunnerId().equals(runnerId)) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 不属于该 Runner");
        }
        if (!assignment.getRunId().equals(runId)) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 不属于该 Run");
        }

        long now = Instant.now().toEpochMilli();
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        // 终态 assignment
        if (assignment.isTerminal()) {
            return RenewLeaseResponse.builder()
                    .action("cancel")
                    .reason("ASSIGNMENT_TERMINATED")
                    .leaseExpiresAt(assignment.getLeaseExpiresAt())
                    .traceId(run.getTraceId())
                    .build();
        }

        // Lease 已过期
        if (assignment.getLeaseExpiresAt() < now) {
            assignment.setStatus(AssignmentStatus.EXPIRED);
            assignment.setTerminalReason("NETWORK_LOST");
            assignmentRepository.save(assignment);
            releaseBindings(assignment.getId());
            return RenewLeaseResponse.builder()
                    .action("cancel")
                    .reason("LEASE_EXPIRED")
                    .leaseExpiresAt(assignment.getLeaseExpiresAt())
                    .traceId(run.getTraceId())
                    .build();
        }

        // 检查 secret binding 是否有被撤销的
        List<LeaseSecretBinding> bindings = leaseSecretBindingRepository
                .findByAssignmentId(assignment.getId());
        boolean hasRevoked = bindings.stream()
                .anyMatch(b -> b.getStatus() == BindingStatus.INVALIDATED);
        if (hasRevoked) {
            return RenewLeaseResponse.builder()
                    .action("cancel")
                    .reason("SECRET_REVOKED")
                    .leaseExpiresAt(assignment.getLeaseExpiresAt())
                    .traceId(run.getTraceId())
                    .build();
        }

        // Run 已被用户取消
        if (run.getStatus() == RunStatus.CANCELLED) {
            return RenewLeaseResponse.builder()
                    .action("cancel")
                    .reason("USER_CANCELLED")
                    .leaseExpiresAt(assignment.getLeaseExpiresAt())
                    .traceId(run.getTraceId())
                    .build();
        }

        // 正常续租：延长过期时间
        long newExpiresAt = now + LEASE_TIMEOUT_MS;
        assignment.setLeaseExpiresAt(newExpiresAt);
        assignment.setStatus(AssignmentStatus.ACKED); // 首次 renew 时隐式 ACK
        assignmentRepository.save(assignment);

        // 同步 runs 投影
        run.setLeaseExpiresAt(newExpiresAt);
        runRepository.save(run);

        log.debug("Lease renewed: assignment={} expires={}", assignment.getId(), newExpiresAt);

        return RenewLeaseResponse.builder()
                .action("no_op")
                .reason(null)
                .leaseExpiresAt(newExpiresAt)
                .traceId(run.getTraceId())
                .build();
    }

    /**
     * Runner 获取物化 secrets（ADR-001/ADR-002 /secret-bind）。
     *
     * <p>R2 实现 ADR-002 D-2.8.3：从 run_assignment → AgentVersion → AgentVersionSecretRef
     * → GatewaySecret 物化 CONTROL_PLANE secrets。
     * required=true 的 secret 不可为空——缺失即拒绝。
     */
    @Transactional
    public SecretBindResponse bindSecrets(String runId, String assignmentId) {
        // 1) 校验 assignment
        RunAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_ASSIGNMENT_NOT_FOUND,
                        "Assignment 不存在: " + assignmentId));
        if (!assignment.getRunId().equals(runId)) {
            throw new BusinessException(ErrorCode.RUN_ASSIGNMENT_NOT_FOUND,
                    "Assignment 不属于该 Run");
        }
        if (!assignment.isActive()) {
            throw new BusinessException(ErrorCode.RUN_ALREADY_TERMINAL,
                    "Assignment 已终结: " + assignment.getStatus());
        }
        if (assignment.getLeaseExpiresAt() < Instant.now().toEpochMilli()) {
            // Lease 已过期，标记 assignment 为 EXPIRED
            assignment.setStatus(AssignmentStatus.EXPIRED);
            assignment.setTerminalReason("NETWORK_LOST");
            assignmentRepository.save(assignment);
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 已过期");
        }

        // 2) 加载 Run → AgentVersion
        Run run = runRepository.findById(runId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUN_NOT_FOUND));

        // 3) 加载 AgentVersion 的 CONTROL_PLANE secret refs
        List<AgentVersionSecretRef> refs = agentVersionSecretRefRepository
                .findByAgentVersionIdOrderByAliasAsc(run.getAgentVersionId());

        Map<String, String> materializedSecrets = new LinkedHashMap<>();
        Map<String, String> secretRevisionBindings = new LinkedHashMap<>();

        // 4) 遍历 refs，物化 CONTROL_PLANE secrets
        for (AgentVersionSecretRef ref : refs) {
            if (!"CONTROL_PLANE".equals(ref.getSource())) {
                // RUNNER_LOCAL → 不在 Control Plane 物化
                continue;
            }
            if (ref.getSecretId() == null || ref.getSecretId().isBlank()) {
                if (ref.isRequired()) {
                    throw new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                            "required secret 缺少 secretId: alias=" + ref.getAlias()
                                    + " injectAs=" + ref.getInjectAs());
                }
                continue; // optional secret 无 secretId → 跳过
            }

            // 查 GatewaySecret
            GatewaySecret gs = gatewaySecretRepository.findBySecretId(ref.getSecretId())
                    .orElse(null);
            if (gs == null || !gs.isActive()) {
                if (ref.isRequired()) {
                    throw new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                            "required secret 不可用: secretId=" + ref.getSecretId()
                                    + " injectAs=" + ref.getInjectAs());
                }
                continue;
            }

            // 解密
            String plaintext;
            try {
                plaintext = encryptionUtil.decrypt(gs.getEncryptedSecret());
            } catch (Exception e) {
                log.error("Failed to decrypt secret {} for assignment {}: {}",
                        ref.getSecretId(), assignmentId, e.getMessage());
                if (ref.isRequired()) {
                    throw new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                            "required secret 解密失败: secretId=" + ref.getSecretId()
                                    + " injectAs=" + ref.getInjectAs());
                }
                continue;
            }

            // required secret 不可为空
            if (ref.isRequired() && (plaintext == null || plaintext.isBlank())) {
                throw new BusinessException(ErrorCode.SECRET_REFERENCE_NOT_FOUND,
                        "required secret 解密后为空: secretId=" + ref.getSecretId()
                                + " injectAs=" + ref.getInjectAs());
            }

            // 5) 写入 lease_secret_binding
            String revision = "v1"; // TODO R3: 改为 gateway_secret_revisions.revision
            try {
                LeaseSecretBinding binding = LeaseSecretBinding.builder()
                        .assignmentId(assignmentId)
                        .injectAs(ref.getInjectAs())
                        .secretId(ref.getSecretId())
                        .revision(revision)
                        .status(BindingStatus.ACTIVE)
                        .build();
                leaseSecretBindingRepository.save(binding);
            } catch (DataIntegrityViolationException e) {
                // DuplicateKey → 读已有 binding（保留旧 revision）
                log.debug("Secret binding already exists: assignment={} injectAs={}",
                        assignmentId, ref.getInjectAs());
            }

            materializedSecrets.put(ref.getInjectAs(), plaintext);
            secretRevisionBindings.put(ref.getInjectAs(),
                    ref.getSecretId() + "@" + revision);
        }

        log.info("Secret bind: assignment={} run={} materialized={} secrets",
                assignmentId, runId, materializedSecrets.size());

        return SecretBindResponse.builder()
                .leaseId(assignment.getLeaseId())
                .runId(runId)
                .materializedSecrets(materializedSecrets)
                .secretRevisionBindings(secretRevisionBindings)
                .expiresAtEpochMs(assignment.getLeaseExpiresAt())
                .build();
    }

    // ============================================================
    // F04 事件推送 / 日志拉取
    // ============================================================

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
     *
     * <p>R2：以 run_assignment.lease_expires_at + status 为判断依据。
     * 由定时任务调用。
     */
    @Transactional
    public int timeoutStaleRuns() {
        long now = Instant.now().toEpochMilli();
        List<RunAssignment> stale = assignmentRepository
                .findByStatusInAndLeaseExpiresAtBefore(
                        List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.ACKED),
                        now);
        int count = 0;
        for (RunAssignment asgn : stale) {
            asgn.setStatus(AssignmentStatus.EXPIRED);
            asgn.setTerminalReason("NETWORK_LOST");
            assignmentRepository.save(asgn);

            // 同步 runs 投影
            runRepository.findById(asgn.getRunId()).ifPresent(run -> {
                if (run.getStatus().isActive()) {
                    run.setStatus(RunStatus.FAILED);
                    run.setErrorMessage("Run 执行超时（Runner 失联或 lease 过期）");
                    run.setTerminalReason("NETWORK_LOST");
                    run.setCompletedAt(now);
                    runRepository.save(run);
                }
            });

            // 释放 bindings
            releaseBindings(asgn.getId());
            count++;
        }
        if (count > 0) {
            log.info("超时检测：{} 个 assignment 标记为 EXPIRED", count);
        }
        return count;
    }

    // ============================================================
    // helpers
    // ============================================================

    /**
     * 解析 lease 标识：优先用 leaseId，其次用 leaseToken。
     * 返回 run_assignment（若找不到则抛 RUN_LEASE_EXPIRED）。
     */
    private RunAssignment resolveAssignment(String runId, String leaseTokenOrId) {
        if (leaseTokenOrId == null || leaseTokenOrId.isBlank()) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "缺少 lease 标识");
        }
        RunAssignment assignment = assignmentRepository.findByLeaseId(leaseTokenOrId)
                .orElse(null);
        if (assignment == null) {
            // 兼容：旧客户端可能只用 leaseToken（与 leaseId 同值）
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 不存在或已过期: " + leaseTokenOrId);
        }
        if (!assignment.getRunId().equals(runId)) {
            throw new BusinessException(ErrorCode.RUN_LEASE_EXPIRED,
                    "Lease 不属于该 Run");
        }
        return assignment;
    }

    /**
     * 释放 assignment 下所有 ACTIVE binding 为 RELEASED。
     */
    private void releaseBindings(String assignmentId) {
        List<LeaseSecretBinding> bindings = leaseSecretBindingRepository
                .findByAssignmentId(assignmentId);
        for (LeaseSecretBinding b : bindings) {
            if (b.getStatus() == BindingStatus.ACTIVE) {
                b.setStatus(BindingStatus.RELEASED);
                b.setInvalidatedAt(Instant.now().toEpochMilli());
                b.setInvalidationReason("ASSIGNMENT_TERMINATED");
                leaseSecretBindingRepository.save(b);
            }
        }
    }
}
