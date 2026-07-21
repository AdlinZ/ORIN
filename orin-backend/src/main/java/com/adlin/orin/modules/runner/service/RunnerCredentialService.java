package com.adlin.orin.modules.runner.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.repository.RunnerCredentialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Runner Credential 签发、校验与撤销。
 *
 * <p>凭据格式：{@code sk-runner-<credentialId>.<43 字符 base64url 随机>}，credentialId 是公开
 * selector，秘密部分只用于 BCrypt 校验。明文仅在 enroll 响应里返回一次；DB 只保留
 * BCrypt hash + {@code keyPrefix/last4} 用于 UI 展示。R3 落地 AEAD 时由
 * {@code RunnerCredential.encryptedValue} 字段承担 envelope，本服务不参与加密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RunnerCredentialService {

    public static final String CREDENTIAL_PREFIX = "sk-runner-";
    private static final int CREDENTIAL_RANDOM_BYTES = 32;

    private final RunnerCredentialRepository runnerCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 签发新凭据并入库；返回明文 + 数据库实体（仅本方法返回明文，其它路径一律不返回）。
     *
     * @param runnerId 关联 Runner。
     * @return 包含明文 + 持久化实体的封装。
     */
    @Transactional
    public IssuedCredential issueNewCredential(String runnerId) {
        String credentialId = "rcred_" + UUID.randomUUID().toString().replace("-", "");
        String plaintext = generatePlaintextCredential(credentialId);

        RunnerCredential credential = RunnerCredential.builder()
                .id("rcid_" + UUID.randomUUID().toString().replace("-", ""))
                .runnerId(runnerId)
                .credentialId(credentialId)
                .credentialHash(passwordEncoder.encode(plaintext))
                .keyPrefix(plaintext.substring(0, Math.min(16, plaintext.length())))
                .last4(plaintext.substring(Math.max(0, plaintext.length() - 4)))
                .status(RunnerCredential.Status.ACTIVE)
                .build();
        credential = runnerCredentialRepository.save(credential);
        log.info("Issued new Runner credential id={} runnerId={}", credential.getCredentialId(), runnerId);
        return new IssuedCredential(credential, plaintext);
    }

    /**
     * 校验明文凭据；返回绑定到 Runner 的凭据实体（含 ACTIVE 和 REVOKED）。
     *
     * <p>Filter 根据返回的 status 决定 401 还是 403：
     * <ul>
     *   <li>empty → 凭据不存在 / 格式错 → 401</li>
     *   <li>ACTIVE → 认证通过，再由 filter 检查 Runner 身份状态</li>
     *   <li>REVOKED → 403（凭据曾有效但已被撤销）</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public Optional<RunnerCredential> validateCredential(String plaintext) {
        if (plaintext == null || !plaintext.startsWith(CREDENTIAL_PREFIX)) {
            return Optional.empty();
        }
        String credentialId = parseCredentialId(plaintext);
        if (credentialId == null) {
            return Optional.empty();
        }
        return runnerCredentialRepository.findByCredentialId(credentialId)
                .filter(credential -> matchesHash(plaintext, credential.getCredentialHash()));
    }

    @Transactional
    public void revokeAllForRunner(String runnerId, String operator) {
        long now = Instant.now().toEpochMilli();
        for (RunnerCredential credential : runnerCredentialRepository.findByRunnerId(runnerId)) {
            if (credential.getStatus() == RunnerCredential.Status.ACTIVE) {
                credential.setStatus(RunnerCredential.Status.REVOKED);
                credential.setRevokedAt(now);
                credential.setRevokedBy(operator);
                runnerCredentialRepository.save(credential);
            }
        }
    }

    private boolean matchesHash(String rawSecret, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawSecret, storedHash);
        } catch (Exception ex) {
            log.warn("Failed to verify Runner credential hash: {}", ex.getMessage());
            return false;
        }
    }

    private String parseCredentialId(String plaintext) {
        int separator = plaintext.indexOf('.', CREDENTIAL_PREFIX.length());
        if (separator <= CREDENTIAL_PREFIX.length() || separator == plaintext.length() - 1) {
            return null;
        }
        String credentialId = plaintext.substring(CREDENTIAL_PREFIX.length(), separator);
        if (!credentialId.startsWith("rcred_") || credentialId.length() > 80) {
            return null;
        }
        return credentialId;
    }

    private String generatePlaintextCredential(String credentialId) {
        byte[] randomBytes = new byte[CREDENTIAL_RANDOM_BYTES];
        secureRandom.nextBytes(randomBytes);
        return CREDENTIAL_PREFIX + credentialId + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 校验 credentialId 是否存在；用于机器通道审计 / 撤销前置校验。
     */
    @Transactional(readOnly = true)
    public Optional<RunnerCredential> findByCredentialId(String credentialId) {
        return runnerCredentialRepository.findByCredentialId(credentialId);
    }

    /**
     * 业务异常工具：撤销已被撤销的凭据。外部 service 调用。
     */
    public void requireNotRevoked(RunnerCredential credential) {
        if (credential == null) {
            throw new BusinessException(ErrorCode.RUNNER_CREDENTIAL_INVALID, "Runner credential not found");
        }
        if (credential.getStatus() != RunnerCredential.Status.ACTIVE) {
            throw new BusinessException(ErrorCode.RUNNER_REVOKED, "Runner credential revoked");
        }
    }

    /**
     * signAndIssue 的结果包装：实体（已持久化）+ 明文（仅此处返回）。
     */
    public record IssuedCredential(RunnerCredential credential, String plaintext) {
    }
}
