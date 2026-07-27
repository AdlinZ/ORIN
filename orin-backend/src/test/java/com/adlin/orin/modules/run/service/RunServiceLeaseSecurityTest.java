package com.adlin.orin.modules.run.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.snapshot.Sha256Digest;
import com.adlin.orin.modules.agent.freeze.repository.AgentVersionSecretRefRepository;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.agent.repository.AgentVersionRepository;
import com.adlin.orin.modules.apikey.repository.GatewaySecretRepository;
import com.adlin.orin.modules.run.dto.BatchEventsRequest;
import com.adlin.orin.modules.run.dto.RunResponse;
import com.adlin.orin.modules.run.entity.AssignmentStatus;
import com.adlin.orin.modules.run.entity.Run;
import com.adlin.orin.modules.run.entity.RunAssignment;
import com.adlin.orin.modules.run.entity.RunEvent;
import com.adlin.orin.modules.run.entity.RunStatus;
import com.adlin.orin.modules.run.repository.LeaseSecretBindingRepository;
import com.adlin.orin.modules.run.repository.RunAssignmentRepository;
import com.adlin.orin.modules.run.repository.RunEventRepository;
import com.adlin.orin.modules.run.repository.RunLogRepository;
import com.adlin.orin.modules.run.repository.RunRepository;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.security.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Locks the Runner ownership and event replay semantics required by ADR-001. */
@ExtendWith(MockitoExtension.class)
class RunServiceLeaseSecurityTest {

    @Mock private RunRepository runRepository;
    @Mock private RunLogRepository runLogRepository;
    @Mock private AgentMetadataRepository agentMetadataRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private RunnerRepository runnerRepository;
    @Mock private RunAssignmentRepository assignmentRepository;
    @Mock private RunEventRepository runEventRepository;
    @Mock private LeaseSecretBindingRepository leaseSecretBindingRepository;
    @Mock private AgentVersionSecretRefRepository agentVersionSecretRefRepository;
    @Mock private GatewaySecretRepository gatewaySecretRepository;
    @Mock private EncryptionUtil encryptionUtil;
    @Mock private RunOwnershipResolver ownershipResolver;

    @InjectMocks private RunService service;

    @Test
    void degradedRunnerCannotClaimNewWork() {
        when(runnerRepository.findById("runner-a")).thenReturn(Optional.of(
                Runner.builder().id("runner-a").status(RunnerStatus.DEGRADED).build()));

        assertEquals(false, service.leaseRun("runner-a").isAcquired());

        verify(runRepository, never()).findOldestQueuedForLease(
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsEventSubmissionFromAnotherRunner() {
        RunAssignment assignment = activeAssignment("runner-a");
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.appendEvents("runner-b", "run-a", "lease-a", List.of(event(1, "hello", 100L))));

        assertEquals(ErrorCode.RUN_LEASE_EXPIRED, exception.getErrorCode());
        verify(runEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptsIdenticalEventReplayWithoutDuplicatingLogs() {
        RunAssignment assignment = activeAssignment("runner-a");
        BatchEventsRequest.EventEntry event = event(1, "hello", 100L);
        RunEvent existing = RunEvent.builder()
                .runId("run-a").leaseId("lease-a").runAttempt(1).eventSeq(1)
                .level("INFO").message("hello").timestamp(100L)
                .payloadHash(eventHash("INFO", "hello", 100L)).build();
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));
        when(runEventRepository.findByRunIdAndLeaseIdAndRunAttemptAndEventSeq("run-a", "lease-a", 1, 1))
                .thenReturn(Optional.of(existing));

        service.appendEvents("runner-a", "run-a", "lease-a", List.of(event));

        verify(runEventRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(runLogRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReplayWithDifferentEventPayload() {
        RunAssignment assignment = activeAssignment("runner-a");
        RunEvent existing = RunEvent.builder()
                .runId("run-a").leaseId("lease-a").runAttempt(1).eventSeq(1)
                .level("INFO").message("old").timestamp(100L).payloadHash("different").build();
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));
        when(runEventRepository.findByRunIdAndLeaseIdAndRunAttemptAndEventSeq("run-a", "lease-a", 1, 1))
                .thenReturn(Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.appendEvents("runner-a", "run-a", "lease-a", List.of(event(1, "new", 100L))));

        assertEquals(ErrorCode.RUN_RESULT_CONFLICT, exception.getErrorCode());
    }

    @Test
    void acceptsIdenticalFinalResultReplay() {
        RunAssignment assignment = activeAssignment("runner-a");
        assignment.setStatus(AssignmentStatus.COMPLETED);
        assignment.setResultPayloadHash(resultHash("COMPLETED", "done", null, null));
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));

        service.submitResult("runner-a", "run-a", "lease-a", "COMPLETED", "done", null, null);

        verify(runRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void renewRejectsTerminalAssignmentWithProtocolConflict() {
        RunAssignment assignment = activeAssignment("runner-a");
        assignment.setStatus(AssignmentStatus.COMPLETED);
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));
        when(runRepository.findById("run-a")).thenReturn(Optional.of(
                Run.builder().id("run-a").traceId("trace-a").build()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.renewLease("runner-a", "lease-a"));

        assertEquals(ErrorCode.RUN_ASSIGNMENT_TERMINATED, exception.getErrorCode());
    }

    @Test
    void renewMarksExpiredAssignmentAndReturnsGoneProtocolError() {
        RunAssignment assignment = activeAssignment("runner-a");
        assignment.setLeaseExpiresAt(System.currentTimeMillis() - 1);
        when(assignmentRepository.findByLeaseId("lease-a")).thenReturn(Optional.of(assignment));
        when(runRepository.findById("run-a")).thenReturn(Optional.of(
                Run.builder().id("run-a").traceId("trace-a").build()));
        when(leaseSecretBindingRepository.findByAssignmentId("asgn-a")).thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.renewLease("runner-a", "lease-a"));

        assertEquals(ErrorCode.RUN_LEASE_EXPIRED, exception.getErrorCode());
        assertEquals(AssignmentStatus.EXPIRED, assignment.getStatus());
        assertEquals("NETWORK_LOST", assignment.getTerminalReason());
    }

    @Test
    void retryCreatesNewRunWithIndependentTraceId() {
        Run original = Run.builder()
                .id("run-a")
                .agentId("agent-a")
                .agentVersionId("version-a")
                .runnerId("runner-a")
                .status(RunStatus.FAILED)
                .configSnapshot("{}")
                .input("hello")
                .createdBy("creator")
                .retryCount(0)
                .maxRetries(3)
                .traceId("old-trace")
                .build();
        when(runRepository.findById("run-a")).thenReturn(Optional.of(original));
        when(runRepository.save(org.mockito.ArgumentMatchers.any(Run.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RunResponse response = service.retryRun("run-a", "operator");

        assertNotNull(response.getTraceId());
        org.junit.jupiter.api.Assertions.assertNotEquals("old-trace", response.getTraceId());
        org.junit.jupiter.api.Assertions.assertEquals("creator", response.getCreatedBy());
    }

    private RunAssignment activeAssignment(String runnerId) {
        return RunAssignment.builder()
                .id("asgn-a").runId("run-a").runnerId(runnerId).leaseId("lease-a")
                .status(AssignmentStatus.ACKED).leaseExpiresAt(System.currentTimeMillis() + 60_000)
                .runAttempt(1).build();
    }

    private BatchEventsRequest.EventEntry event(int seq, String message, long timestamp) {
        BatchEventsRequest.EventEntry event = new BatchEventsRequest.EventEntry();
        event.setSeq(seq);
        event.setLevel("INFO");
        event.setMessage(message);
        event.setTimestamp(timestamp);
        return event;
    }

    private String eventHash(String level, String message, long timestamp) {
        return Sha256Digest.hex(part(level) + part(message) + part(Long.toString(timestamp)));
    }

    private String resultHash(String status, String output, String errorMessage, String errorCode) {
        return Sha256Digest.hex(part(status) + part(output) + part(errorMessage) + part(errorCode));
    }

    private String part(String value) {
        if (value == null) {
            value = "";
        }
        return value.length() + ":" + value;
    }
}
