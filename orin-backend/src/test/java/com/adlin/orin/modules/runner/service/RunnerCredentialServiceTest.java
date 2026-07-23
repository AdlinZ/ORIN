package com.adlin.orin.modules.runner.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.repository.RunnerCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunnerCredentialServiceTest {

    @Mock
    private RunnerCredentialRepository runnerCredentialRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private RunnerCredentialService service;

    @BeforeEach
    void setUp() {
        service = new RunnerCredentialService(runnerCredentialRepository, passwordEncoder);
    }

    @Test
    void issueNewCredentialProducesValidPrefixAndHashesPlaintext() {
        when(runnerCredentialRepository.save(any(RunnerCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerCredentialService.IssuedCredential issued = service.issueNewCredential("run_abc");

        assertNotNull(issued.credential());
        assertEquals("run_abc", issued.credential().getRunnerId());
        assertTrue(issued.plaintext().startsWith(
                RunnerCredentialService.CREDENTIAL_PREFIX
                        + issued.credential().getCredentialId() + "."));
        assertEquals(issued.plaintext().substring(0, 16), issued.credential().getKeyPrefix());
        assertEquals(issued.plaintext().substring(issued.plaintext().length() - 4),
                issued.credential().getLast4());
        verify(passwordEncoder, times(1)).encode(issued.plaintext());
    }

    @Test
    void validateCredentialReturnsActiveMatch() {
        RunnerCredential stored = RunnerCredential.builder()
                .id("rcid_1")
                .runnerId("run_1")
                .credentialId("rcred_1")
                .credentialHash("$2a$hashed")
                .status(RunnerCredential.Status.ACTIVE)
                .build();
        when(runnerCredentialRepository.findByCredentialId("rcred_1"))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("sk-runner-rcred_1.secret", "$2a$hashed"))
                .thenReturn(true);

        Optional<RunnerCredential> result = service.validateCredential(
                "sk-runner-rcred_1.secret");

        assertTrue(result.isPresent());
        assertSame(stored, result.get());
    }

    @Test
    void validateCredentialRejectsPrefixMismatch() {
        Optional<RunnerCredential> result = service.validateCredential("not-prefixed");
        assertTrue(result.isEmpty());
        verify(runnerCredentialRepository, never()).findByCredentialId(any());
    }

    @Test
    void validateCredentialReturnsRevokedSoFilterCanRespond403() {
        // validateCredential now returns REVOKED credentials so the auth filter
        // can distinguish 401 (invalid) from 403 (valid but revoked).
        RunnerCredential revoked = RunnerCredential.builder()
                .id("rcid_1")
                .credentialId("rcred_1")
                .credentialHash("$2a$hashed")
                .status(RunnerCredential.Status.REVOKED)
                .build();
        when(runnerCredentialRepository.findByCredentialId("rcred_1"))
                .thenReturn(Optional.of(revoked));
        when(passwordEncoder.matches("sk-runner-rcred_1.secret", "$2a$hashed"))
                .thenReturn(true);

        Optional<RunnerCredential> result = service.validateCredential(
                "sk-runner-rcred_1.secret");
        assertTrue(result.isPresent());
        assertEquals(RunnerCredential.Status.REVOKED, result.get().getStatus());
    }

    @Test
    void validateCredentialReturnsEmptyOnNoMatch() {
        when(runnerCredentialRepository.findByCredentialId("rcred_1"))
                .thenReturn(Optional.empty());
        Optional<RunnerCredential> result = service.validateCredential(
                "sk-runner-rcred_1.secret");
        assertTrue(result.isEmpty());
    }

    @Test
    void revokeAllForRunnerUpdatesActiveCredentialsOnly() {
        RunnerCredential active = RunnerCredential.builder()
                .id("rcid_1")
                .runnerId("run_1")
                .credentialId("rcred_1")
                .credentialHash("h")
                .status(RunnerCredential.Status.ACTIVE)
                .createdAt(0L)
                .updatedAt(0L)
                .build();
        RunnerCredential revoked = RunnerCredential.builder()
                .id("rcid_2")
                .runnerId("run_1")
                .credentialId("rcred_2")
                .credentialHash("h")
                .status(RunnerCredential.Status.REVOKED)
                .createdAt(0L)
                .updatedAt(0L)
                .build();
        when(runnerCredentialRepository.findByRunnerId("run_1"))
                .thenReturn(List.of(active, revoked));
        when(runnerCredentialRepository.save(any(RunnerCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        long before = Instant.now().toEpochMilli();
        service.revokeAllForRunner("run_1", "admin-1");
        long after = Instant.now().toEpochMilli();

        ArgumentCaptor<RunnerCredential> captor = ArgumentCaptor.forClass(RunnerCredential.class);
        verify(runnerCredentialRepository, times(1)).save(captor.capture());
        RunnerCredential saved = captor.getValue();
        assertEquals(RunnerCredential.Status.REVOKED, saved.getStatus());
        assertEquals("admin-1", saved.getRevokedBy());
        assertNotNull(saved.getRevokedAt());
        assertTrue(saved.getRevokedAt() >= before && saved.getRevokedAt() <= after);
    }

    @Test
    void requireNotRevokedThrowsBusinessExceptionWhenRevoked() {
        RunnerCredential revoked = RunnerCredential.builder()
                .id("rcid_1")
                .status(RunnerCredential.Status.REVOKED)
                .build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireNotRevoked(revoked));
        assertEquals("RUNNER_REVOKED", ex.getErrorCode().name());
    }

    @Test
    void requireNotRevokedThrowsWhenCredentialNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requireNotRevoked(null));
        assertEquals("RUNNER_CREDENTIAL_INVALID", ex.getErrorCode().name());
    }

    @Test
    void requireNotRevokedAllowsActive() {
        RunnerCredential active = RunnerCredential.builder()
                .id("rcid_1")
                .status(RunnerCredential.Status.ACTIVE)
                .build();
        // 不应抛异常
        service.requireNotRevoked(active);
        assertFalse(false);
    }
}
