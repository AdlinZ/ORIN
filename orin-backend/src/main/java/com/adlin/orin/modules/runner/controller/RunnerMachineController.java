package com.adlin.orin.modules.runner.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerHeartbeatSnapshot;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerHeartbeatSnapshotRepository;
import com.adlin.orin.modules.run.dto.LeaseRunResponse;
import com.adlin.orin.modules.run.dto.BatchEventsRequest;
import com.adlin.orin.modules.run.dto.RenewLeaseResponse;
import com.adlin.orin.modules.run.dto.SecretBindRequest;
import com.adlin.orin.modules.run.dto.SecretBindResponse;
import com.adlin.orin.modules.run.dto.SubmitResultRequest;
import com.adlin.orin.modules.run.service.RunService;
import com.adlin.orin.modules.runner.service.RunnerCredentialService;
import com.adlin.orin.modules.runner.service.RunnerService;
import com.adlin.orin.security.EnrollmentTokenPrincipal;
import com.adlin.orin.security.RunnerPrincipal;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Runner 机器通道 — F01 接入的 3 个端点：enroll / heartbeat / command-ack。
 *
 * <p>enroll 由 {@code EnrollmentTokenAuthFilter} 鉴权；heartbeat / command-ack 由
 * {@code RunnerCredentialAuthFilter} 鉴权。控制面不消费 Runner 的 RabbitMQ 流量，
 * 不连业务库；lease / events / result 端点由后续 F03 实施，本类不在 F01 范围。
 */
@Slf4j
@RestController
@RequestMapping("/api/system/runners")
@RequiredArgsConstructor
@Tag(name = "Runner F01 机器通道", description = "Runner 主动出站接入与心跳")
public class RunnerMachineController {

    private final RunnerService runnerService;
    private final RunService runService;
    private final RunnerHeartbeatSnapshotRepository heartbeatSnapshotRepository;
    private final AuditHelper auditHelper;
    private final ObjectMapper objectMapper;

    @Value("${orin.runner.heartbeat.expected-interval-sec:15}")
    private int expectedIntervalSec;

    // ============================================================
    // 1) enroll — Runner 用一次性 Token 接入并换取 Credential
    // ============================================================

    @PostMapping("/enroll")
    @Operation(summary = "Runner 一次性接入：消费 Enrollment Token 并签发 Runner Credential")
    public ResponseEntity<EnrollResponse> enroll(@Valid @RequestBody EnrollRequest request) {
        org.springframework.security.core.Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (!(principal instanceof EnrollmentTokenPrincipal token)) {
            throw new BusinessException(ErrorCode.RUNNER_CREDENTIAL_INVALID,
                    "缺少 Enrollment Token principal");
        }

        Object credentials = authentication.getCredentials();
        if (!(credentials instanceof String plaintextToken) || plaintextToken.isBlank()) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID,
                    "缺少 Enrollment Token credentials");
        }

        RunnerService.EnrollmentResult enrolled = runnerService.enrollRunnerAtomically(
                plaintextToken, request.name(),
                request.hostname(), request.os(), request.arch(), request.version(),
                serializeJson(request.labels()), serializeJson(request.capabilities()),
                serializeJson(request.gpuInfo()),
                request.cpuCores(), request.memoryTotal(), request.diskTotal(),
                request.maxConcurrency());
        Runner runner = enrolled.runner();
        RunnerCredentialService.IssuedCredential issued = enrolled.issuedCredential();
        // 默认期望 Runner 按 expected-interval-sec 上报心跳，response 允许覆盖
        long serverTime = Instant.now().toEpochMilli();
        EnrollResponse response = new EnrollResponse(
                runner.getId(),
                issued.credential().getCredentialId(),
                issued.plaintext(),
                runner.getStatus().name(),
                expectedIntervalSec,
                serverTime,
                issued.credential().getKeyPrefix(),
                issued.credential().getLast4());
        auditHelper.log(runner.getCreatedBy(), "RUNNER_ENROLL_RESPONSE",
                "/api/system/runners/enroll",
                "runnerId=" + runner.getId() + ", name=" + runner.getName()
                        + ", credentialId=" + issued.credential().getCredentialId(),
                true, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ============================================================
    // 2) heartbeat — Runner 周期上报资源 + 接受 DRAINING 指令
    // ============================================================

    @PostMapping("/{runnerId}/heartbeat")
    @Operation(summary = "Runner 周期心跳 + 资源快照 + DRAINING 状态同步")
    public HeartbeatResponse heartbeat(@PathVariable String runnerId,
                                      @Valid @RequestBody HeartbeatRequest request) {
        RunnerPrincipal principal = requireRunnerPrincipal(runnerId);

        Runner runner = runnerService.findById(runnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RUNNER_NOT_ENROLLED,
                        "Runner 不存在: " + runnerId));
        if (runner.getStatus() == RunnerStatus.REVOKED) {
            throw new BusinessException(ErrorCode.RUNNER_REVOKED,
                    "Runner 已被撤销，凭据已失效");
        }

        long now = Instant.now().toEpochMilli();
        RunnerStatus oldStatus = runner.getStatus();

        if (oldStatus == RunnerStatus.NEW) {
            throw new BusinessException(ErrorCode.RUNNER_NOT_ENROLLED,
                    "Runner 尚未完成 enrollment，不能发送心跳");
        }
        // ENROLLING：首次心跳即是接入完成信号；若已被要求 Drain，则保持不接单。
        if (oldStatus == RunnerStatus.ENROLLING) {
            RunnerStatus firstHeartbeatStatus = Boolean.TRUE.equals(runner.getDrainRequested())
                    ? RunnerStatus.DRAINING : RunnerStatus.ONLINE;
            runner.setStatus(firstHeartbeatStatus);
            auditHelper.log("system", "RUNNER_STATUS_CHANGED",
                    "/api/system/runners/" + runnerId + "/heartbeat",
                    "runnerId=" + runnerId + ", oldStatus=" + oldStatus
                            + ", newStatus=" + firstHeartbeatStatus
                            + ", reason=first-heartbeat-after-enroll",
                    true, null);
        }
        if (oldStatus == RunnerStatus.OFFLINE) {
            // 联通恢复不覆盖维护意图。
            runner.setStatus(Boolean.TRUE.equals(runner.getDrainRequested())
                    ? RunnerStatus.DRAINING : RunnerStatus.ONLINE);
        }
        runner.setLastHeartbeatAt(now);
        runner.setVersion(request.version() != null ? request.version() : runner.getVersion());
        if (request.dependencyHealth() != null) {
            runner.setLastDependencyHealth(request.dependencyHealth());
        }
        if (request.activeRuns() != null) {
            runner.setActiveRuns(request.activeRuns());
        }
        if (request.queuedRuns() != null) {
            runner.setQueuedRuns(request.queuedRuns());
        }

        // 写最新 heartbeat snapshot
        RunnerHeartbeatSnapshot snapshot = RunnerHeartbeatSnapshot.builder()
                .runnerId(runnerId)
                .cpuUsage(request.cpuUsage() != null ? BigDecimal.valueOf(request.cpuUsage()) : null)
                .memoryUsed(request.memoryUsed())
                .diskUsed(request.diskUsed())
                .gpuUsage(request.gpuUsage() != null ? BigDecimal.valueOf(request.gpuUsage()) : null)
                .memoryTotal(request.memoryTotal())
                .diskTotal(request.diskTotal())
                .dependencyHealth(request.dependencyHealth())
                .reportedAt(now)
                .rawPayload(serializeJson(request))
                .build();
        heartbeatSnapshotRepository.save(snapshot);

        // OFFLINE → ONLINE 心跳恢复写审计；DRAINING 状态保持由 Runner 主动 command-ack 回报。
        if (oldStatus == RunnerStatus.OFFLINE) {
            auditHelper.log("system", "RUNNER_STATUS_CHANGED",
                    "/api/system/runners/" + runnerId + "/heartbeat",
                    "runnerId=" + runnerId + ", oldStatus=" + oldStatus
                            + ", newStatus=" + runner.getStatus() + ", reason=heartbeat-recovered",
                    true, null);
        }
        runnerService.persistRunnerFields(runner);

        HeartbeatResponse response = new HeartbeatResponse(
                runner.getStatus().name(),
                new HeartbeatResponse.Commands(
                        Boolean.TRUE.equals(runner.getDrainRequested()),
                        expectedIntervalSec),
                new HeartbeatResponse.Config(expectedIntervalSec),
                now,
                principal.getCredentialId());
        return response;
    }

    // ============================================================
    // 3) command-ack — Runner 回报收到 DRAINING 控制帧
    // ============================================================

    @PostMapping("/{runnerId}/command-ack")
    @Operation(summary = "Runner 回报 DRAINING 等控制帧已接收并切换")
    public ResponseEntity<Void> commandAck(@PathVariable String runnerId,
                                          @RequestBody(required = false) CommandAckRequest request) {
        requireRunnerPrincipal(runnerId);
        String command = request == null ? "UNKNOWN" : request.command();
        String operator = "system";
        if ("DRAIN".equalsIgnoreCase(command)) {
            runnerService.recordDrainAck(runnerId, operator);
        }
        auditHelper.log(operator, "RUNNER_COMMAND_ACK", "/api/system/runners/" + runnerId + "/command-ack",
                "runnerId=" + runnerId + ", command=" + command, true, null);
        return ResponseEntity.noContent().build();
    }

    // ============================================================
    // F03 Runner 机器通道 — ADR-001 端点（R2 全六端点）
    //
    // 已实现：/lease/claim、/renew、/result、/events、/secret-bind + heartbeat/command-ack。
    // R2 新增 /renew 端点 + leaseId 支持。
    // ============================================================

    @PostMapping("/{runnerId}/lease/claim")
    @Operation(summary = "Runner 轮询领取排队的 Run（ADR-001 /lease/claim）")
    public LeaseRunResponse claimLease(@PathVariable String runnerId) {
        requireRunnerPrincipal(runnerId);
        return runService.leaseRun(runnerId);
    }

    @PostMapping("/{runnerId}/lease/{leaseId}/renew")
    @Operation(summary = "Runner 续租（ADR-001 /lease/{leaseId}/renew）")
    public RenewLeaseResponse renewLease(@PathVariable String runnerId,
                                          @PathVariable String leaseId) {
        requireRunnerPrincipal(runnerId);
        return runService.renewLease(runnerId, leaseId);
    }

    @PostMapping("/{runnerId}/runs/{runId}/result")
    @Operation(summary = "Runner 提交最终结果（ADR-001 /result）")
    public ResponseEntity<Void> submitResult(@PathVariable String runnerId,
                                              @PathVariable String runId,
                                              @Valid @RequestBody SubmitResultRequest request) {
        requireRunnerPrincipal(runnerId);
        String leaseId = request.getLeaseId() != null ? request.getLeaseId() : request.getLeaseToken();
        runService.submitResult(runnerId, runId, leaseId,
                request.getStatus(), request.getOutput(),
                request.getErrorMessage(), request.getErrorCode());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runnerId}/runs/{runId}/events")
    @Operation(summary = "Runner 批量推送事件/日志（ADR-001 /events）")
    public ResponseEntity<Void> submitEvents(@PathVariable String runnerId,
                                              @PathVariable String runId,
                                              @Valid @RequestBody BatchEventsRequest request) {
        requireRunnerPrincipal(runnerId);
        String leaseId = request.getLeaseId() != null ? request.getLeaseId() : request.getLeaseToken();
        runService.appendEvents(runnerId, runId, leaseId, request.getEvents());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{runnerId}/runs/{runId}/secret-bind")
    @Operation(summary = "Runner 获取物化 secrets（ADR-001/ADR-002 /secret-bind）")
    public SecretBindResponse bindSecrets(@PathVariable String runnerId,
                                          @PathVariable String runId,
                                          @Valid @RequestBody SecretBindRequest request) {
        requireRunnerPrincipal(runnerId);
        return runService.bindSecrets(runnerId, runId, request.getAssignmentId());
    }

    // ============================================================
    // helpers
    // ============================================================

    private RunnerPrincipal requireRunnerPrincipal(String expectedRunnerId) {
        Object principal = currentPrincipalOrThrow();
        if (!(principal instanceof RunnerPrincipal runnerPrincipal)) {
            throw new BusinessException(ErrorCode.RUNNER_CREDENTIAL_INVALID,
                    "缺少 Runner principal");
        }
        if (!runnerPrincipal.getRunnerId().equals(expectedRunnerId)) {
            // URL 中的 runnerId 与凭据绑定 runnerId 不一致 → 403（Runner 不能跨身份操作）
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS,
                    "Runner 身份与请求路径不一致");
        }
        return runnerPrincipal;
    }

    private Object currentPrincipalOrThrow() {
        return SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed to serialize Runner JSON payload: {}", ex.getMessage());
            return null;
        }
    }

    // ============================================================
    // DTOs
    // ============================================================

    public record EnrollRequest(
            @NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
            String hostname,
            String os,
            String arch,
            String version,
            Object labels,
            Object capabilities,
            Object gpuInfo,
            Integer cpuCores,
            Long memoryTotal,
            Long diskTotal,
            @jakarta.validation.constraints.Min(1) Integer maxConcurrency) {
    }

    public record EnrollResponse(
            @JsonProperty("runnerId") String runnerId,
            @JsonProperty("credentialId") String credentialId,
            @JsonProperty("credential") String credential,
            @JsonProperty("status") String status,
            @JsonProperty("heartbeatIntervalSec") int heartbeatIntervalSec,
            @JsonProperty("serverTime") long serverTime,
            @JsonProperty("keyPrefix") String keyPrefix,
            @JsonProperty("last4") String last4) {
    }

    public record HeartbeatRequest(
            Double cpuUsage,
            Long memoryUsed,
            Long diskUsed,
            Double gpuUsage,
            Long memoryTotal,
            Long diskTotal,
            String dependencyHealth,
            Integer activeRuns,
            Integer queuedRuns,
            String version) {
    }

    public record HeartbeatResponse(
            String status,
            Commands commands,
            Config config,
            long serverTime,
            String credentialId) {

        public record Commands(boolean drainAck, int expectedIntervalSec) {
        }

        public record Config(int heartbeatIntervalSec) {
        }
    }

    public record CommandAckRequest(String command) {
    }

}
