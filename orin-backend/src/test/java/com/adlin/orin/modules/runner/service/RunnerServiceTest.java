package com.adlin.orin.modules.runner.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerEnrollmentToken;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerCredentialRepository;
import com.adlin.orin.modules.runner.repository.RunnerEnrollmentTokenRepository;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunnerServiceTest {

    // Fixture identifiers — original etk_<32hex> format is required by
    // RunnerService.parseTokenId which validates TOKEN_PREFIX/etk_/length.
    // The format triggers Gitleaks' generic-api-key rule; the matching
    // (commit:file:rule:line) fingerprint is whitelisted in .gitleaksignore.
    private static final String TOKEN_ID = "etk_1234567890abcdef1234567890abcdef";
    private static final String PLAINTEXT_TOKEN = "sk-enroll-" + TOKEN_ID + ".secret";

    @Mock
    private RunnerRepository runnerRepository;

    @Mock
    private RunnerCredentialRepository runnerCredentialRepository;

    @Mock
    private RunnerEnrollmentTokenRepository enrollmentTokenRepository;

    @Mock
    private RunnerCredentialService runnerCredentialService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditHelper auditHelper;

    private RunnerService service;

    @BeforeEach
    void setUp() {
        service = new RunnerService(
                runnerRepository,
                runnerCredentialRepository,
                enrollmentTokenRepository,
                runnerCredentialService,
                passwordEncoder,
                auditHelper);
        ReflectionTestUtils.setField(service, "defaultTtlMinutes", 15L);
        ReflectionTestUtils.setField(service, "maxTtlMinutes", 120L);
    }

    @Test
    void createEnrollmentTokenReturnsPlaintextAndPersistsHashedToken() {
        when(runnerRepository.existsByNameAndCreatedBy("runner-a", "admin-1")).thenReturn(false);
        when(enrollmentTokenRepository.save(any(RunnerEnrollmentToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerService.IssuedEnrollmentToken issued = service.createEnrollmentToken(
                "admin-1", "runner-a", 30L);

        assertNotNull(issued);
        assertTrue(issued.plaintext().startsWith("sk-enroll-"));
        assertTrue(issued.plaintext().startsWith("sk-enroll-" + issued.token().getId() + "."));
        assertEquals("runner-a", issued.token().getNote());
        assertNotNull(issued.token().getCreatedAt());
        assertNotNull(issued.token().getExpiresAt());
        assertTrue(issued.token().getExpiresAt() > issued.token().getCreatedAt());
        verify(passwordEncoder, times(1)).encode(issued.plaintext());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_ENROLLMENT_TOKEN_CREATED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void createEnrollmentTokenRejectsDuplicateName() {
        when(runnerRepository.existsByNameAndCreatedBy("runner-a", "admin-1")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createEnrollmentToken("admin-1", "runner-a", 15L));
        assertEquals("RESOURCE_ALREADY_EXISTS", ex.getErrorCode().name());
        verify(enrollmentTokenRepository, never()).save(any());
    }

    @Test
    void createEnrollmentTokenClampsTtlToMax() {
        when(runnerRepository.existsByNameAndCreatedBy("runner-a", "admin-1")).thenReturn(false);
        when(enrollmentTokenRepository.save(any(RunnerEnrollmentToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerService.IssuedEnrollmentToken issued = service.createEnrollmentToken(
                "admin-1", "runner-a", 999L);

        long expected = issued.token().getCreatedAt() + 120L * 60_000L;
        assertEquals(expected, issued.token().getExpiresAt());
    }

    @Test
    void createEnrollmentTokenDefaultsTtlWhenInvalid() {
        when(runnerRepository.existsByNameAndCreatedBy("runner-a", "admin-1")).thenReturn(false);
        when(enrollmentTokenRepository.save(any(RunnerEnrollmentToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerService.IssuedEnrollmentToken issued = service.createEnrollmentToken(
                "admin-1", "runner-a", 0L);

        long expected = issued.token().getCreatedAt() + 15L * 60_000L;
        assertEquals(expected, issued.token().getExpiresAt());
    }

    @Test
    void createEnrollmentTokenRejectsBlankOperator() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createEnrollmentToken("", "runner-a", 15L));
        assertEquals("AUTH_INSUFFICIENT_PERMISSIONS", ex.getErrorCode().name());
    }

    @Test
    void createEnrollmentTokenRejectsBlankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.createEnrollmentToken("admin-1", "", 15L));
        assertEquals("VALIDATION_REQUIRED_FIELD", ex.getErrorCode().name());
    }

    @Test
    void revokeEnrollmentTokenDeletesActiveOwnedTokenAndAudits() {
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID).createdBy("admin-1").note("runner-a")
                .expiresAt(Instant.now().toEpochMilli() + 60_000L).build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        service.revokeEnrollmentToken(TOKEN_ID, "admin-1");

        verify(enrollmentTokenRepository).delete(token);
        verify(auditHelper).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_ENROLLMENT_TOKEN_REVOKED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void revokeEnrollmentTokenRejectsAnotherOwner() {
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID).createdBy("admin-1")
                .expiresAt(Instant.now().toEpochMilli() + 60_000L).build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revokeEnrollmentToken(TOKEN_ID, "admin-2"));

        assertEquals("AUTH_INSUFFICIENT_PERMISSIONS", ex.getErrorCode().name());
        verify(enrollmentTokenRepository, never()).delete(any());
    }

    @Test
    void revokeEnrollmentTokenRejectsExpiredToken() {
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID).createdBy("admin-1")
                .expiresAt(Instant.now().toEpochMilli() - 1L).build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.revokeEnrollmentToken(TOKEN_ID, "admin-1"));

        assertEquals("RESOURCE_CONFLICT", ex.getErrorCode().name());
        verify(enrollmentTokenRepository, never()).delete(any());
    }

    @Test
    void validateEnrollmentTokenDoesNotConsumeActiveToken() {
        RunnerEnrollmentToken stored = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID)
                .tokenHash("$2a$hashed")
                .createdBy("admin-1")
                .createdAt(Instant.now().toEpochMilli() - 60_000L)
                .expiresAt(Instant.now().toEpochMilli() + 600_000L)
                .note("runner-a")
                .build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "$2a$hashed")).thenReturn(true);

        RunnerService.ValidatedEnrollmentToken validated = service.validateEnrollmentToken(PLAINTEXT_TOKEN);

        assertEquals(TOKEN_ID, validated.tokenId());
        assertEquals("runner-a", validated.expectedName());
        assertEquals("admin-1", validated.createdBy());
        assertNull(stored.getUsedAt());
        verify(enrollmentTokenRepository, never()).save(any());
    }

    @Test
    void validateEnrollmentTokenRejectsWrongPrefix() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateEnrollmentToken("bad-prefix-AAA"));
        assertEquals("ENROLLMENT_TOKEN_INVALID", ex.getErrorCode().name());
        verify(enrollmentTokenRepository, never()).save(any());
    }

    @Test
    void validateEnrollmentTokenRejectsAlreadyUsed() {
        RunnerEnrollmentToken used = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID)
                .tokenHash("$2a$hashed")
                .createdBy("admin-1")
                .createdAt(Instant.now().toEpochMilli() - 60_000L)
                .expiresAt(Instant.now().toEpochMilli() + 600_000L)
                .usedAt(Instant.now().toEpochMilli() - 1_000L)
                .build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(used));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "$2a$hashed")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateEnrollmentToken(PLAINTEXT_TOKEN));
        assertEquals("ENROLLMENT_TOKEN_INVALID", ex.getErrorCode().name());
    }

    @Test
    void validateEnrollmentTokenRejectsExpired() {
        RunnerEnrollmentToken expired = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID)
                .tokenHash("$2a$hashed")
                .createdBy("admin-1")
                .createdAt(Instant.now().toEpochMilli() - 1_200_000L)
                .expiresAt(Instant.now().toEpochMilli() - 60_000L)
                .build();
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(expired));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "$2a$hashed")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateEnrollmentToken(PLAINTEXT_TOKEN));
        assertEquals("ENROLLMENT_TOKEN_EXPIRED", ex.getErrorCode().name());
    }

    @Test
    void validateEnrollmentTokenRejectsUnknownToken() {
        when(enrollmentTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.validateEnrollmentToken(PLAINTEXT_TOKEN));
        assertEquals("ENROLLMENT_TOKEN_INVALID", ex.getErrorCode().name());
    }

    @Test
    void enrollRunnerAtomicallyUsesTokenOwnerAndNameAndBindsCredential() {
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID)
                .tokenHash("$2a$hashed")
                .createdBy("admin-1")
                .note("runner-a")
                .expiresAt(Instant.now().toEpochMilli() + 60_000L)
                .build();
        when(enrollmentTokenRepository.findByIdForUpdate(TOKEN_ID))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "$2a$hashed")).thenReturn(true);
        when(runnerRepository.existsByNameAndCreatedBy("runner-a", "admin-1")).thenReturn(false);
        when(enrollmentTokenRepository.save(any(RunnerEnrollmentToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RunnerCredential credential = RunnerCredential.builder()
                .id("rcid_1").credentialId("rcred_1").runnerId("run_pending").build();
        when(runnerCredentialService.issueNewCredential(any(String.class)))
                .thenReturn(new RunnerCredentialService.IssuedCredential(credential, "plaintext"));

        RunnerService.EnrollmentResult result = service.enrollRunnerAtomically(
                PLAINTEXT_TOKEN, "runner-a",
                "host-1", "linux", "x86_64", "0.1.0",
                "[\"gpu:false\"]", "[]", null,
                8, 16L * 1024 * 1024 * 1024, 512L * 1024 * 1024 * 1024,
                4);
        Runner runner = result.runner();

        assertEquals("runner-a", runner.getName());
        assertEquals(RunnerStatus.ENROLLING, runner.getStatus());
        assertEquals("admin-1", runner.getCreatedBy());
        assertNull(runner.getLastHeartbeatAt());
        assertNotNull(token.getUsedAt());
        assertEquals(runner.getId(), token.getRunnerId());
        assertEquals("plaintext", result.issuedCredential().plaintext());
        verify(runnerCredentialService).issueNewCredential(runner.getId());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_ENROLLED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void enrollRunnerAtomicallyRejectsNameMismatchBeforeWriting() {
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID).tokenHash("hash").createdBy("admin-1")
                .note("runner-a").expiresAt(Instant.now().toEpochMilli() + 60_000L).build();
        when(enrollmentTokenRepository.findByIdForUpdate(TOKEN_ID)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "hash")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.enrollRunnerAtomically(
                        PLAINTEXT_TOKEN, "other-runner", "host", "linux", "x86_64", "0.1.0",
                        null, null, null, 1, null, null, 1));

        assertEquals("ENROLLMENT_TOKEN_INVALID", ex.getErrorCode().name());
        assertNull(token.getUsedAt());
        verify(runnerRepository, never()).save(any());
        verify(runnerCredentialService, never()).issueNewCredential(any());
    }

    @Test
    void enrollRunnerAtomicallyDefaultsConcurrencyToOne() {
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        RunnerEnrollmentToken token = RunnerEnrollmentToken.builder()
                .id(TOKEN_ID).tokenHash("hash").createdBy("admin-1")
                .note("runner-a").expiresAt(Instant.now().toEpochMilli() + 60_000L).build();
        when(enrollmentTokenRepository.findByIdForUpdate(TOKEN_ID)).thenReturn(Optional.of(token));
        when(passwordEncoder.matches(PLAINTEXT_TOKEN, "hash")).thenReturn(true);
        when(enrollmentTokenRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runnerCredentialService.issueNewCredential(any())).thenReturn(
                new RunnerCredentialService.IssuedCredential(
                        RunnerCredential.builder().credentialId("rcred_1").build(), "plaintext"));

        Runner runner = service.enrollRunnerAtomically(
                PLAINTEXT_TOKEN, "runner-a",
                "host-1", "linux", "x86_64", "0.1.0",
                null, null, null, null, null, null, null).runner();

        assertEquals(1, runner.getMaxConcurrency());
        assertEquals(0, runner.getActiveRuns());
        assertEquals(0, runner.getQueuedRuns());
        assertEquals(RunnerStatus.ENROLLING, runner.getStatus());
        assertNull(runner.getLastHeartbeatAt());
    }

    @Test
    void findByIdDelegatesToRepository() {
        Runner runner = Runner.builder().id("run_1").name("runner-a").build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        assertEquals(Optional.of(runner), service.findById("run_1"));
    }

    @Test
    void findActiveCredentialByRunnerIdDelegatesToRepository() {
        RunnerCredential credential = RunnerCredential.builder()
                .id("rcid_1").runnerId("run_1")
                .status(RunnerCredential.Status.ACTIVE).build();
        when(runnerCredentialRepository.findFirstByRunnerIdAndStatusOrderByCreatedAtDesc(
                "run_1", RunnerCredential.Status.ACTIVE))
                .thenReturn(Optional.of(credential));
        assertEquals(Optional.of(credential),
                service.findActiveCredentialByRunnerId("run_1"));
    }

    // ============================================================
    // 状态机 ops: drain / restore / revoke
    // ============================================================

    @Test
    void drainTransitionsOnlineToDrainingAndAudits() {
        Runner runner = Runner.builder()
                .id("run_1")
                .name("runner-a")
                .status(RunnerStatus.ONLINE)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner drained = service.drain("run_1", "admin-1");

        assertEquals(RunnerStatus.DRAINING, drained.getStatus());
        assertTrue(drained.getDrainRequested());
        assertNull(drained.getDrainAckAt());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_DRAINED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void drainFromDegradedIsAllowed() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.DEGRADED)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner drained = service.drain("run_1", "admin-1");
        assertEquals(RunnerStatus.DRAINING, drained.getStatus());
        assertTrue(drained.getDrainRequested());
    }

    @Test
    void drainFromRevokedRejected() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.REVOKED)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.drain("run_1", "admin-1"));
        assertEquals("OPERATION_FAILED", ex.getErrorCode().name());
        verify(runnerRepository, never()).save(any());
    }

    @Test
    void drainFromOfflineRejected() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.OFFLINE)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.drain("run_1", "admin-1"));
        assertEquals("OPERATION_FAILED", ex.getErrorCode().name());
    }

    @Test
    void restoreFromDrainingToOnlineAndClearsDrainAck() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.DRAINING)
                .drainRequested(true)
                .drainAckAt(5L)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner restored = service.restore("run_1", "admin-1");

        assertEquals(RunnerStatus.ONLINE, restored.getStatus());
        assertFalse(restored.getDrainRequested());
        // Drain ack should be cleared; lastHeartbeatAt is maintained by Runner heartbeat
        assertNull(restored.getDrainAckAt());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_RESTORED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void restoreFromNewRejected() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.NEW)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.restore("run_1", "admin-1"));
        assertEquals("OPERATION_FAILED", ex.getErrorCode().name());
    }

    @Test
    void restoreFromOfflineRejected() {
        // 没有遗留 Drain 意图的 OFFLINE Runner 不能被管理员伪造为在线。
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.OFFLINE)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.restore("run_1", "admin-1"));
        assertTrue(ex.getMessage().contains("没有 Drain 请求"));
    }

    @Test
    void restoreOfflineRunnerClearsDrainIntentButKeepsItOffline() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.OFFLINE)
                .drainRequested(true)
                .drainAckAt(5L)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner restored = service.restore("run_1", "admin-1");

        assertEquals(RunnerStatus.OFFLINE, restored.getStatus());
        assertFalse(restored.getDrainRequested());
        assertNull(restored.getDrainAckAt());
    }

    @Test
    void revokeTransitionsAnyActiveStateAndRevokesAllCredentials() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.ONLINE)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner revoked = service.revoke("run_1", "admin-1");

        assertEquals(RunnerStatus.REVOKED, revoked.getStatus());
        verify(runnerCredentialService, times(1))
                .revokeAllForRunner(org.mockito.ArgumentMatchers.eq("run_1"),
                        org.mockito.ArgumentMatchers.eq("admin-1"));
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("admin-1"),
                org.mockito.ArgumentMatchers.eq("RUNNER_REVOKED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void revokeFromRevokedIsNoOpButStillAudits() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.REVOKED)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Runner revoked = service.revoke("run_1", "admin-1");
        assertEquals(RunnerStatus.REVOKED, revoked.getStatus());
        verify(runnerCredentialService, times(1))
                .revokeAllForRunner("run_1", "admin-1");
    }

    @Test
    void recordDrainAckUpdatesTimestampWhenDraining() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.DRAINING)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        long before = Instant.now().toEpochMilli();
        Runner acked = service.recordDrainAck("run_1", "admin-1");
        long after = Instant.now().toEpochMilli();

        assertNotNull(acked.getDrainAckAt());
        assertTrue(acked.getDrainAckAt() >= before && acked.getDrainAckAt() <= after);
    }

    @Test
    void recordDrainAckIsIgnoredWhenNotDraining() {
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a")
                .status(RunnerStatus.ONLINE)
                .createdBy("admin-1")
                .createdAt(1L).updatedAt(1L)
                .build();
        when(runnerRepository.findById("run_1")).thenReturn(Optional.of(runner));

        Runner unchanged = service.recordDrainAck("run_1", "admin-1");
        assertNull(unchanged.getDrainAckAt());
        verify(runnerRepository, never()).save(any());
    }

    @Test
    void requireRunnerThrowsNotFound() {
        when(runnerRepository.findById("missing")).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.drain("missing", "admin-1"));
        assertEquals("RESOURCE_NOT_FOUND", ex.getErrorCode().name());
    }
}
