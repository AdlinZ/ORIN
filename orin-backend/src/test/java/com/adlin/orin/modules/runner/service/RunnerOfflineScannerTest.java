package com.adlin.orin.modules.runner.service;

import com.adlin.orin.modules.audit.service.AuditHelper;
import com.adlin.orin.modules.runner.entity.Runner;
import com.adlin.orin.modules.runner.entity.RunnerStatus;
import com.adlin.orin.modules.runner.repository.RunnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RunnerOfflineScannerTest {

    @Mock
    private RunnerRepository runnerRepository;

    @Mock
    private AuditHelper auditHelper;

    private RunnerOfflineScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new RunnerOfflineScanner(runnerRepository, auditHelper);
        ReflectionTestUtils.setField(scanner, "offlineThresholdSec", 60L);
        ReflectionTestUtils.setField(scanner, "stuckEnrollmentTimeoutMin", 30L);
    }

    @Test
    void marksOnlineStaleRunnerAsOfflineAndAudits() {
        long now = Instant.now().toEpochMilli();
        Runner onlineStale = Runner.builder()
                .id("run_1").name("r-a").status(RunnerStatus.ONLINE)
                .lastHeartbeatAt(now - 90_000L)
                .createdAt(now - 600_000L).updatedAt(now - 600_000L)
                .build();
        when(runnerRepository.findStaleActive(anySet(), anyLong()))
                .thenReturn(List.of(onlineStale));
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scanner.scanStaleRunners();

        ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
        verify(runnerRepository, times(1)).save(captor.capture());
        assertEquals(RunnerStatus.OFFLINE, captor.getValue().getStatus());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("system"),
                org.mockito.ArgumentMatchers.eq("RUNNER_STATUS_CHANGED"),
                any(String.class), any(String.class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void marksDrainingStaleRunnerOfflineAndPreservesDrainIntent() {
        long now = Instant.now().toEpochMilli();
        Runner drainingStale = Runner.builder()
                .id("run_3").name("r-c").status(RunnerStatus.DRAINING)
                .drainRequested(true)
                .lastHeartbeatAt(now - 90_000L)
                .createdAt(now - 600_000L).updatedAt(now - 600_000L)
                .build();
        when(runnerRepository.findStaleActive(anySet(), anyLong()))
                .thenReturn(List.of(drainingStale));
        when(runnerRepository.findStaleByStatus(any(RunnerStatus.class), anyLong()))
                .thenReturn(List.of());
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scanner.scanStaleRunners();

        ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
        verify(runnerRepository).save(captor.capture());
        assertEquals(RunnerStatus.OFFLINE, captor.getValue().getStatus());
        assertEquals(true, captor.getValue().getDrainRequested());
    }

    @Test
    void marksStuckEnrollingRunnerAsOffline() {
        long now = Instant.now().toEpochMilli();
        Runner stuck = Runner.builder()
                .id("run_2").name("r-b").status(RunnerStatus.ENROLLING)
                .createdAt(now - 31L * 60_000L)
                .updatedAt(now - 31L * 60_000L)
                .build();
        when(runnerRepository.findStaleByStatus(
                org.mockito.ArgumentMatchers.eq(RunnerStatus.ENROLLING), anyLong()))
                .thenReturn(List.of(stuck));
        when(runnerRepository.findStaleActive(anySet(), anyLong()))
                .thenReturn(List.of());
        when(runnerRepository.save(any(Runner.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        scanner.scanStaleRunners();

        ArgumentCaptor<Runner> captor = ArgumentCaptor.forClass(Runner.class);
        verify(runnerRepository, times(1)).save(captor.capture());
        assertEquals(RunnerStatus.OFFLINE, captor.getValue().getStatus());
        verify(auditHelper, times(1)).log(
                org.mockito.ArgumentMatchers.eq("system"),
                org.mockito.ArgumentMatchers.eq("RUNNER_STATUS_CHANGED"),
                org.mockito.ArgumentMatchers.any(String.class),
                org.mockito.ArgumentMatchers.contains("enrollment-stuck"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void handlesEmptyScan() {
        when(runnerRepository.findStaleActive(anySet(), anyLong()))
                .thenReturn(List.of());
        when(runnerRepository.findStaleByStatus(any(RunnerStatus.class), anyLong()))
                .thenReturn(List.of());

        scanner.scanStaleRunners();

        verify(runnerRepository, never()).save(any());
        verify(auditHelper, never()).log(any(), any(), any(), any(),
                any(Boolean.class), any());
    }
}
