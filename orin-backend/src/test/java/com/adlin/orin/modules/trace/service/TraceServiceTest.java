package com.adlin.orin.modules.trace.service;

import com.adlin.orin.modules.trace.entity.WorkflowTraceEntity;
import com.adlin.orin.modules.trace.repository.WorkflowTraceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceServiceTest {

    @Mock
    private WorkflowTraceRepository traceRepository;

    private TraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new TraceService(traceRepository);
    }

    @Test
    void getRecentTraceSummariesShouldGroupSortAndAggregateRecentTraces() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 3, 12, 0);
        when(traceRepository.findTop500ByOrderByStartedAtDesc()).thenReturn(List.of(
                trace("trace-new", 9L, WorkflowTraceEntity.TraceStatus.FAILED, now, now.plusSeconds(2), 2000L),
                trace("trace-new", 9L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusSeconds(5), now.minusSeconds(1), 4000L),
                trace("trace-old", 8L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusMinutes(1), now.minusMinutes(1).plusSeconds(1), 1000L)
        ));

        List<Map<String, Object>> result = traceService.getRecentTraceSummaries(20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0))
                .containsEntry("traceId", "trace-new")
                .containsEntry("instanceId", 9L)
                .containsEntry("status", "FAILED")
                .containsEntry("totalSteps", 2)
                .containsEntry("failedCount", 1L)
                .containsEntry("totalDuration", 6000L)
                .containsEntry("firstStartedAt", now.minusSeconds(5))
                .containsEntry("lastCompletedAt", now.plusSeconds(2));
        assertThat(result.get(1)).containsEntry("traceId", "trace-old");
    }

    @Test
    void getRecentTraceSummariesShouldClampSizeToOneWhenInvalid() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 3, 12, 0);
        when(traceRepository.findTop500ByOrderByStartedAtDesc()).thenReturn(List.of(
                trace("trace-a", 1L, WorkflowTraceEntity.TraceStatus.SUCCESS, now, now, 0L),
                trace("trace-b", 2L, WorkflowTraceEntity.TraceStatus.SUCCESS, now.minusSeconds(1), now, 0L)
        ));

        List<Map<String, Object>> result = traceService.getRecentTraceSummaries(0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("traceId", "trace-a");
    }

    private WorkflowTraceEntity trace(
            String traceId,
            Long instanceId,
            WorkflowTraceEntity.TraceStatus status,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long durationMs) {
        return WorkflowTraceEntity.builder()
                .traceId(traceId)
                .instanceId(instanceId)
                .status(status)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .durationMs(durationMs)
                .build();
    }
}
