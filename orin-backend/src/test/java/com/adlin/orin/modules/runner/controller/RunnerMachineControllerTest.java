package com.adlin.orin.modules.runner.controller;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerCredential;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerHeartbeatSnapshotRepository;
import com.adlin.orin.modules.runner.service.RunnerCredentialService;
import com.adlin.orin.modules.runner.service.RunnerService;
import com.adlin.orin.security.EnrollmentTokenPrincipal;
import com.adlin.orin.security.RunnerPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunnerMachineControllerTest {

    @Mock
    private RunnerService runnerService;
    @Mock
    private RunnerHeartbeatSnapshotRepository heartbeatSnapshotRepository;
    @Mock
    private AuditHelper auditHelper;

    private RunnerMachineController controller;

    @BeforeEach
    void setUp() {
        controller = new RunnerMachineController(
                runnerService, heartbeatSnapshotRepository, auditHelper,
                new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(controller, "expectedIntervalSec", 15);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enrollPassesRequestCredentialIntoTheSingleAtomicServiceCall() {
        String plaintext = "sk-enroll-etk_1234567890abcdef1234567890abcdef.secret";
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new EnrollmentTokenPrincipal(
                                "etk_1234567890abcdef1234567890abcdef", "runner-a", "admin-1"),
                        plaintext,
                        List.of()));
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a").createdBy("admin-1")
                .status(RunnerStatus.ENROLLING).build();
        RunnerCredential credential = RunnerCredential.builder()
                .credentialId("rcred_1").keyPrefix("sk-runner-rcred")
                .last4("cret").build();
        when(runnerService.enrollRunnerAtomically(
                eq(plaintext), eq("runner-a"), eq("host-1"), eq("linux"), eq("x86_64"),
                eq("0.1.0"), eq(null), eq(null), eq(null), eq(8), eq(16L), eq(100L), eq(2)))
                .thenReturn(new RunnerService.EnrollmentResult(
                        runner, new RunnerCredentialService.IssuedCredential(
                                credential, "sk-runner-rcred_1.secret")));

        ResponseEntity<RunnerMachineController.EnrollResponse> response = controller.enroll(
                new RunnerMachineController.EnrollRequest(
                        "runner-a", "host-1", "linux", "x86_64", "0.1.0",
                        null, null, null, 8, 16L, 100L, 2));

        assertEquals(201, response.getStatusCode().value());
        assertEquals("run_1", response.getBody().runnerId());
        assertEquals("ENROLLING", response.getBody().status());
        verify(runnerService).enrollRunnerAtomically(
                plaintext, "runner-a", "host-1", "linux", "x86_64", "0.1.0",
                null, null, null, 8, 16L, 100L, 2);
    }

    @Test
    void firstHeartbeatIsTheOnlyTransitionFromEnrollingToOnline() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new RunnerPrincipal("run_1", "rcred_1"), null, List.of()));
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a").status(RunnerStatus.ENROLLING)
                .drainRequested(false).build();
        when(runnerService.findById("run_1")).thenReturn(Optional.of(runner));
        when(runnerService.persistRunnerFields(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunnerMachineController.HeartbeatResponse response = controller.heartbeat(
                "run_1", new RunnerMachineController.HeartbeatRequest(
                        null, null, null, null, null, null,
                        "OK", 0, 0, "0.1.0"));

        assertEquals("ONLINE", response.status());
        assertFalse(response.commands().drainAck());
        assertEquals(RunnerStatus.ONLINE, runner.getStatus());
        verify(heartbeatSnapshotRepository).save(any());
        verify(runnerService).persistRunnerFields(runner);
    }

    @Test
    void newRunnerWithoutCompletedEnrollmentCannotHeartbeat() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new RunnerPrincipal("run_1", "rcred_1"), null, List.of()));
        Runner runner = Runner.builder()
                .id("run_1").name("runner-a").status(RunnerStatus.NEW).build();
        when(runnerService.findById("run_1")).thenReturn(Optional.of(runner));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.heartbeat(
                        "run_1", new RunnerMachineController.HeartbeatRequest(
                                null, null, null, null, null, null,
                                null, null, null, null)));

        assertEquals("RUNNER_NOT_ENROLLED", ex.getErrorCode().name());
    }
}
