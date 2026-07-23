package com.adlin.orin.modules.runner.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerEnrollmentToken;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerCredentialRepository;
import com.adlin.orin.modules.runner.repository.RunnerEnrollmentTokenRepository;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Runner 状态机与生命周期编排服务。
 *
 * <p>F01 覆盖 Enrollment Token 签发、原子接入，以及 Drain / Restore / Revoke 状态编排。
 *
 * <p>审计一律走 {@code AuditHelper.log}，禁止把 token / credential 明文写入 {@code detail} 字段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerService {

    private static final String TOKEN_PREFIX = "sk-enroll-";
    private static final int TOKEN_RANDOM_BYTES = 32;

    private final RunnerRepository runnerRepository;
    private final RunnerCredentialRepository runnerCredentialRepository;
    private final RunnerEnrollmentTokenRepository enrollmentTokenRepository;
    private final RunnerCredentialService runnerCredentialService;
    private final PasswordEncoder passwordEncoder;
    private final AuditHelper auditHelper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${orin.runner.enrollment.default-ttl-min:15}")
    private long defaultTtlMinutes;

    @Value("${orin.runner.enrollment.max-ttl-min:120}")
    private long maxTtlMinutes;

    /**
     * 创建一次性 Enrollment Token；明文 token 仅在响应中返回一次。
     *
     * @param operator 创建者用户 ID（来自 JWT）。
     * @param name Runner 计划名称（写入 token 备注 + 校验同名唯一）。
     * @param ttlMinutes 自定义 TTL（>0 且 <= max-ttl-min）；空时使用 default-ttl-min。
     */
    @Transactional
    public IssuedEnrollmentToken createEnrollmentToken(String operator, String name, Long ttlMinutes) {
        if (operator == null || operator.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS, "缺少操作者");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_REQUIRED_FIELD, "name 不能为空");
        }
        if (runnerRepository.existsByNameAndCreatedBy(name, operator)) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "已存在同名 Runner: " + name);
        }

        long ttl = (ttlMinutes == null || ttlMinutes <= 0) ? defaultTtlMinutes
                : Math.min(ttlMinutes, maxTtlMinutes);
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + ttl * 60_000L;

        String tokenId = "etk_" + UUID.randomUUID().toString().replace("-", "");
        String plaintext = generatePlaintextToken(tokenId);
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(tokenId)
                .tokenHash(passwordEncoder.encode(plaintext))
                .createdBy(operator)
                .createdAt(now)
                .expiresAt(expiresAt)
                .note(name)
                .build();
        token = enrollmentTokenRepository.save(token);

        auditHelper.log(operator, "RUNNER_ENROLLMENT_TOKEN_CREATED",
                "/api/v1/runner-enrollment-tokens",
                "tokenId=" + token.getId() + ", name=" + name + ", ttlMin=" + ttl,
                true, null);
        log.info("Created enrollment token id={} name={} operator={} ttlMin={}",
                token.getId(), name, operator, ttl);
        return new IssuedEnrollmentToken(token, plaintext);
    }

    /**
     * 非破坏性校验 Enrollment Token，供机器鉴权 Filter 建立临时 principal。
     * 真正消费仍由 {@link #enrollRunnerAtomically} 在业务事务内完成。
     */
    @Transactional(readOnly = true)
    public ValidatedEnrollmentToken validateEnrollmentToken(String plaintextToken) {
        String tokenId = parseTokenId(plaintextToken);
        RunnerEnrollmentToken token = enrollmentTokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 无效"));
        validateTokenSecretAndState(token, plaintextToken, Instant.now().toEpochMilli());
        return new ValidatedEnrollmentToken(token.getId(), token.getNote(), token.getCreatedBy());
    }

    /**
     * Enrollment 唯一写事务：锁定并验证 Token，创建 ENROLLING Runner，签发 Credential，
     * 最后标记 Token 已消费。任一步失败都会回滚全部写入。
     */
    @Transactional
    public EnrollmentResult enrollRunnerAtomically(String plaintextToken,
                                                    String requestedName,
                                                    String hostname,
                                                    String os,
                                                    String arch,
                                                    String version,
                                                    String labels,
                                                    String capabilities,
                                                    String gpuInfo,
                                                    Integer cpuCores,
                                                    Long memoryTotal,
                                                    Long diskTotal,
                                                    Integer maxConcurrency) {
        long now = Instant.now().toEpochMilli();
        String tokenId = parseTokenId(plaintextToken);
        RunnerEnrollmentToken token = enrollmentTokenRepository.findByIdForUpdate(tokenId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 无效"));
        validateTokenSecretAndState(token, plaintextToken, now);

        String expectedName = token.getNote();
        if (requestedName == null || !requestedName.equals(expectedName)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID,
                    "Runner 名称与 Enrollment Token 不一致");
        }
        if (runnerRepository.existsByNameAndCreatedBy(expectedName, token.getCreatedBy())) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS,
                    "已存在同名 Runner: " + expectedName);
        }

        Runner runner = Runner.builder()
                .id("run_" + UUID.randomUUID().toString().replace("-", ""))
                .name(expectedName)
                .status(RunnerStatus.ENROLLING)
                .version(version)
                .hostname(hostname)
                .os(os)
                .arch(arch)
                .labels(labels)
                .capabilities(capabilities)
                .gpuInfo(gpuInfo)
                .cpuCores(cpuCores)
                .memoryTotal(memoryTotal)
                .diskTotal(diskTotal)
                .maxConcurrency(maxConcurrency == null ? 1 : maxConcurrency)
                .activeRuns(0)
                .queuedRuns(0)
                .drainRequested(false)
                .createdBy(token.getCreatedBy())
                .build();
        runner = runnerRepository.save(runner);
        RunnerCredentialService.IssuedCredential issued = runnerCredentialService
                .issueNewCredential(runner.getId());

        token.setUsedAt(now);
        token.setRunnerId(runner.getId());
        enrollmentTokenRepository.save(token);

        auditHelper.log(token.getCreatedBy(), "RUNNER_ENROLLED", "/api/system/runners/enroll",
                "runnerId=" + runner.getId() + ", name=" + expectedName + ", hostname=" + hostname,
                true, null);
        return new EnrollmentResult(runner, issued);
    }

    /**
     * 撤销尚未使用且未过期的 Enrollment Token，并记录不含明文的审计事实。
     */
    @Transactional
    public void revokeEnrollmentToken(String tokenId, String operator) {
        RunnerEnrollmentToken token = enrollmentTokenRepository.findById(tokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Enrollment Token 不存在"));
        if (operator == null || !operator.equals(token.getCreatedBy())) {
            throw new BusinessException(ErrorCode.AUTH_INSUFFICIENT_PERMISSIONS,
                    "只能撤销本人创建的 Token");
        }
        if (token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Token 已被使用");
        }
        if (token.getExpiresAt() <= Instant.now().toEpochMilli()) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Token 已过期");
        }
        enrollmentTokenRepository.delete(token);
        auditHelper.log(operator, "RUNNER_ENROLLMENT_TOKEN_REVOKED",
                "/api/v1/runner-enrollment-tokens/" + tokenId,
                "tokenId=" + tokenId + ", name=" + token.getNote(), true, null);
    }

    @Transactional(readOnly = true)
    public Optional<Runner> findById(String id) {
        return runnerRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<RunnerCredential> findActiveCredentialByRunnerId(String runnerId) {
        return runnerCredentialRepository
                .findFirstByRunnerIdAndStatusOrderByCreatedAtDesc(runnerId, RunnerCredential.Status.ACTIVE);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Runner> listRunners(
            org.springframework.data.domain.Pageable pageable) {
        return runnerRepository.findAllByOrderByUpdatedAtDesc(pageable);
    }

    // ============================================================
    // 状态机 ops (Drain / Restore / Revoke)
    // ============================================================

    /**
     * Drain Runner：ONLINE / DEGRADED → DRAINING。
     * Runner 仍应继续发送心跳；收到 drainAck=true 后应停止拉新 Run。
     * 清空 drainAckAt 标记，等待 Runner 通过 command-ack 确认。
     */
    @Transactional
    public Runner drain(String runnerId, String operator) {
        Runner runner = requireRunner(runnerId);
        RunnerStatus old = runner.getStatus();
        if (old == RunnerStatus.OFFLINE) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED,
                    "OFFLINE Runner 无法确认 Drain 指令，请等待心跳恢复");
        }
        old.requireCanTransitionTo(RunnerStatus.DRAINING);
        runner.setStatus(RunnerStatus.DRAINING);
        runner.setDrainRequested(true);
        runner.setDrainAckAt(null);
        runner = runnerRepository.save(runner);
        auditHelper.log(operator, "RUNNER_DRAINED", "/api/v1/runners/" + runnerId + "/drain",
                "runnerId=" + runnerId + ", oldStatus=" + old + ", newStatus=" + runner.getStatus(),
                true, null);
        return runner;
    }

    /**
     * 停止 Drain：DRAINING → ONLINE；若 Runner 已离线，仅清除 Drain 意图并保持 OFFLINE，
     * 等下一次真实心跳恢复 ONLINE。不重置 lastHeartbeatAt（心跳时间线由 Runner 维护）。
     */
    @Transactional
    public Runner restore(String runnerId, String operator) {
        Runner runner = requireRunner(runnerId);
        RunnerStatus old = runner.getStatus();
        if (!Boolean.TRUE.equals(runner.getDrainRequested()) && old != RunnerStatus.DRAINING) {
            throw new BusinessException(ErrorCode.OPERATION_FAILED, "Runner 当前没有 Drain 请求");
        }
        runner.setDrainRequested(false);
        if (old == RunnerStatus.DRAINING) {
            runner.setStatus(RunnerStatus.ONLINE);
        } else if (old != RunnerStatus.OFFLINE) {
            old.requireCanTransitionTo(RunnerStatus.ONLINE);
            runner.setStatus(RunnerStatus.ONLINE);
        }
        runner.setDrainAckAt(null);
        runner = runnerRepository.save(runner);
        auditHelper.log(operator, "RUNNER_RESTORED", "/api/v1/runners/" + runnerId + "/restore",
                "runnerId=" + runnerId + ", oldStatus=" + old + ", newStatus=" + runner.getStatus(),
                true, null);
        return runner;
    }

    /**
     * Revoke Runner：任意状态 → REVOKED（终态）。同时撤销所有 ACTIVE 凭据，后续凭据
     * 校验返回 403 RUNNER_REVOKED。
     */
    @Transactional
    public Runner revoke(String runnerId, String operator) {
        Runner runner = requireRunner(runnerId);
        RunnerStatus old = runner.getStatus();
        runner.getStatus().requireCanTransitionTo(RunnerStatus.REVOKED);
        runner.setStatus(RunnerStatus.REVOKED);
        runner = runnerRepository.save(runner);
        runnerCredentialService.revokeAllForRunner(runnerId, operator);
        auditHelper.log(operator, "RUNNER_REVOKED", "/api/v1/runners/" + runnerId + "/revoke",
                "runnerId=" + runnerId + ", oldStatus=" + old + ", newStatus=" + runner.getStatus(),
                true, null);
        return runner;
    }

    /**
     * 处理 Runner 主动报告 drain ack（PR 3 machine channel 写入；保留此处以便业务通道也能复用）。
     */
    @Transactional
    public Runner recordDrainAck(String runnerId, String operator) {
        Runner runner = requireRunner(runnerId);
        if (runner.getStatus() != RunnerStatus.DRAINING) {
            log.debug("Runner {} drainAck ignored: status={}", runnerId, runner.getStatus());
            return runner;
        }
        runner.setDrainAckAt(Instant.now().toEpochMilli());
        return runnerRepository.save(runner);
    }

    /**
     * 直接持久化 Runner 字段（machine channel heartbeat 复用：保留 caller 已构造的 status、
     * lastHeartbeatAt、version 等，只 save 一次）。状态机允许的转移由 caller 保证。
     */
    @Transactional
    public Runner persistRunnerFields(Runner runner) {
        return runnerRepository.save(runner);
    }

    private Runner requireRunner(String runnerId) {
        return runnerRepository.findById(runnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                        "Runner 不存在: " + runnerId));
    }

    private void validateTokenSecretAndState(RunnerEnrollmentToken token,
                                             String plaintextToken,
                                             long now) {
        if (!matchesHash(plaintextToken, token.getTokenHash())) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 无效");
        }
        if (token.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 已被使用");
        }
        if (token.getExpiresAt() <= now) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_EXPIRED, "Enrollment Token 已过期");
        }
    }

    private String parseTokenId(String plaintextToken) {
        if (plaintextToken == null || !plaintextToken.startsWith(TOKEN_PREFIX)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 格式错误");
        }
        int separator = plaintextToken.indexOf('.', TOKEN_PREFIX.length());
        if (separator <= TOKEN_PREFIX.length() || separator == plaintextToken.length() - 1) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 格式错误");
        }
        String tokenId = plaintextToken.substring(TOKEN_PREFIX.length(), separator);
        if (!tokenId.startsWith("etk_") || tokenId.length() > 40) {
            throw new BusinessException(ErrorCode.ENROLLMENT_TOKEN_INVALID, "Enrollment Token 格式错误");
        }
        return tokenId;
    }

    private boolean matchesHash(String rawSecret, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawSecret, storedHash);
        } catch (Exception ex) {
            log.warn("Failed to verify enrollment token hash: {}", ex.getMessage());
            return false;
        }
    }

    private String generatePlaintextToken(String tokenId) {
        byte[] randomBytes = new byte[TOKEN_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return TOKEN_PREFIX + tokenId + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public record IssuedEnrollmentToken(RunnerEnrollmentToken token, String plaintext) {
    }

    public record ValidatedEnrollmentToken(String tokenId, String expectedName, String createdBy) {
    }

    public record EnrollmentResult(Runner runner,
                                   RunnerCredentialService.IssuedCredential issuedCredential) {
    }
}
