package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.dto.CollaborationPackage;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.audit.service.AuditHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * F2.1 CollaborationOrchestrator 端到端回归测试
 *
 * 测试目标：验证协作编排器全生命周期
 * - 创建协作包 -> 分解为子任务 -> 调度执行 -> 完成/失败 -> 事件可查
 * - 状态机转换正确性
 * - 依赖驱动调度（串行/并行）
 * - 暂停/恢复/取消语义
 *
 * 运行方式：mvn test -Dtest=CollaborationOrchestratorTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CollaborationOrchestratorTest {

    @Mock
    private CollaborationPackageRepository packageRepository;
    @Mock
    private CollabSubtaskRepository subtaskRepository;
    @Mock
    private CollaborationMemoryService memoryService;
    @Mock
    private CollaborationEventBus eventBus;
    @Mock
    private AuditHelper auditHelper;

    private CollaborationOrchestrator orchestrator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        orchestrator = new CollaborationOrchestrator(
                packageRepository, subtaskRepository, memoryService,
                eventBus, auditHelper, objectMapper
        );
    }

    // ==================== 包生命周期 ====================

    @Test
    @DisplayName("F2.1 - 创建协作包：状态为 PLANNING，包含 intent 和策略")
    void testCreatePackage_setsCorrectInitialState() {
        when(packageRepository.save(any(CollaborationPackageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CollaborationPackage pkg = orchestrator.createPackage(
                "分析这个代码库的结构",
                "ANALYSIS",
                "NORMAL",
                "COMPLEX",
                "SEQUENTIAL",
                "test-user",
                "trace-001"
        );

        assertNotNull(pkg);
        assertNotNull(pkg.getPackageId());
        assertEquals("PLANNING", pkg.getStatus());
        assertEquals("分析这个代码库的结构", pkg.getIntent());
        assertEquals("ANALYSIS", pkg.getIntentTag().getCategory());

        verify(packageRepository).save(any(CollaborationPackageEntity.class));
        verify(eventBus).publishPackageCreated(anyString(), eq("分析这个代码库的结构"), eq("trace-001"));
    }

    @Test
    @DisplayName("F2.1 - 创建协作包支持主 Agent 策略参数")
    void testCreatePackage_withMainAgentStrategyOverrides() {
        when(packageRepository.save(any(CollaborationPackageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CollaborationPackage pkg = orchestrator.createPackage(
                "生成方案草案",
                "GENERATION",
                "HIGH",
                "COMPLEX",
                "PARALLEL",
                "test-user",
                "trace-override-001",
                Map.of(
                        "mainAgentPolicy", "STATIC_THEN_BID",
                        "qualityThreshold", 0.9,
                        "maxCritiqueRounds", 5,
                        "draftParallelism", 6,
                        "bidWhitelist", List.of("agent-a", "agent-b"),
                        "bidWeightReasoning", 0.7,
                        "bidWeightSpeed", 0.2,
                        "bidWeightCost", 0.1
                )
        );

        assertNotNull(pkg.getStrategy());
        assertEquals("STATIC_THEN_BID", pkg.getStrategy().getMainAgentPolicy());
        assertEquals(0.9, pkg.getStrategy().getQualityThreshold());
        assertEquals(5, pkg.getStrategy().getMaxCritiqueRounds());
        assertEquals(6, pkg.getStrategy().getDraftParallelism());
        assertEquals(List.of("agent-a", "agent-b"), pkg.getStrategy().getBidWhitelist());
    }

    @Test
    @DisplayName("F2.1 - 分解任务包：ANALYSIS 类生成 3 个子任务（收集、分析、总结）")
    void testDecompose_generatesCorrectSubtasks() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-001")
                .intent("分析Q1财报")
                .intentCategory("ANALYSIS")
                .status("PLANNING")
                .traceId("trace-002")
                .createdBy("test-user")
                .build();

        when(packageRepository.findByPackageId("pkg-e2e-001")).thenReturn(Optional.of(entity));
        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subtaskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        CollaborationPackage pkg = orchestrator.decompose("pkg-e2e-001", List.of("analysis", "review"));

        assertNotNull(pkg);
        verify(subtaskRepository).saveAll(argThat(subtasks -> {
            List<CollabSubtaskEntity> list = (List<CollabSubtaskEntity>) subtasks;
            return list.size() == 3
                    && "收集相关信息和数据".equals(list.get(0).getDescription())
                    && "分析数据并提取洞察".equals(list.get(1).getDescription())
                    && "总结分析结果".equals(list.get(2).getDescription());
        }));
        verify(eventBus).publishPackageDecomposed(eq("pkg-e2e-001"), eq(3), eq("trace-002"));
    }

    @Test
    @DisplayName("F2.1 - 获取可执行子任务：依赖满足时返回 PENDING 子任务")
    void testGetExecutableSubtasks_returnsReadyTasks() {
        List<CollabSubtaskEntity> subtasks = List.of(
                CollabSubtaskEntity.builder()
                        .subTaskId("1").packageId("pkg-e2e-002")
                        .description("Task 1").status("COMPLETED").dependsOn("[]")
                        .build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("2").packageId("pkg-e2e-002")
                        .description("Task 2").status("PENDING").dependsOn("[\"1\"]")
                        .build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("3").packageId("pkg-e2e-002")
                        .description("Task 3").status("PENDING").dependsOn("[]")
                        .build()
        );
        when(subtaskRepository.findByPackageId("pkg-e2e-002")).thenReturn(subtasks);

        List<CollabSubtaskEntity> executable = orchestrator.getExecutableSubtasks("pkg-e2e-002");

        // Task 3 无依赖可直接执行；Task 2 依赖 Task 1（已完成）也可执行
        assertEquals(2, executable.size());
        assertTrue(executable.stream().anyMatch(s -> s.getSubTaskId().equals("2")));
        assertTrue(executable.stream().anyMatch(s -> s.getSubTaskId().equals("3")));
    }

    @Test
    @DisplayName("F2.1 - 自动调度：PARALLEL 模式调度所有可执行任务，SEQUENTIAL 只调度第一个")
    void testAutoSchedule_respectsCollaborationMode() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-003")
                .collaborationMode("SEQUENTIAL")
                .status("EXECUTING")
                .traceId("trace-003")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-003")).thenReturn(Optional.of(entity));

        CollabSubtaskEntity subtask1 = CollabSubtaskEntity.builder()
                .subTaskId("1").packageId("pkg-e2e-003")
                .description("Task 1").status("PENDING").dependsOn("[]")
                .build();
        CollabSubtaskEntity subtask2 = CollabSubtaskEntity.builder()
                .subTaskId("2").packageId("pkg-e2e-003")
                .description("Task 2").status("PENDING").dependsOn("[]")
                .build();

        when(subtaskRepository.findByPackageId("pkg-e2e-003")).thenReturn(List.of(subtask1, subtask2));
        when(subtaskRepository.findByPackageIdAndSubTaskId("pkg-e2e-003", "1"))
                .thenReturn(Optional.of(subtask1));
        when(subtaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // SEQUENTIAL 模式：只调度一个
        List<CollabSubtaskEntity> seqResult = orchestrator.autoScheduleIfPossible("pkg-e2e-003");
        assertEquals(1, seqResult.size());
        assertEquals("RUNNING", seqResult.get(0).getStatus());

        // 模拟第一个完成，再调度第二个
        entity.setCollaborationMode("PARALLEL");
        subtask1 = CollabSubtaskEntity.builder()
                .subTaskId("1").packageId("pkg-e2e-003")
                .description("Task 1").status("COMPLETED").dependsOn("[]")
                .build();
        CollabSubtaskEntity subtask2New = CollabSubtaskEntity.builder()
                .subTaskId("2").packageId("pkg-e2e-003")
                .description("Task 2").status("PENDING").dependsOn("[]")
                .build();
        when(subtaskRepository.findByPackageId("pkg-e2e-003")).thenReturn(List.of(subtask1, subtask2New));
        when(subtaskRepository.findByPackageIdAndSubTaskId("pkg-e2e-003", "2"))
                .thenReturn(Optional.of(subtask2New));

        // PARALLEL 模式：调度所有可执行的
        List<CollabSubtaskEntity> parResult = orchestrator.autoScheduleIfPossible("pkg-e2e-003");
        assertEquals(1, parResult.size());
        assertEquals("RUNNING", parResult.get(0).getStatus());
    }

    // ==================== 状态机转换 ====================

    @Test
    @DisplayName("F2.1 - 子任务状态转换：PENDING -> RUNNING -> COMPLETED 合法")
    void testUpdateSubtaskStatus_validTransitions() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-004")
                .status("EXECUTING")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-004")).thenReturn(Optional.of(entity));

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("1").packageId("pkg-e2e-004")
                .description("Test").status("PENDING")
                .build();
        when(subtaskRepository.findByPackageIdAndSubTaskId("pkg-e2e-004", "1"))
                .thenReturn(Optional.of(subtask));
        when(subtaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // PENDING -> RUNNING
        CollabSubtaskEntity r1 = orchestrator.updateSubtaskStatus("pkg-e2e-004", "1", "RUNNING", null, null);
        assertEquals("RUNNING", r1.getStatus());
        assertNotNull(r1.getStartedAt());

        // RUNNING -> COMPLETED
        CollabSubtaskEntity r2 = orchestrator.updateSubtaskStatus("pkg-e2e-004", "1", "COMPLETED", "task result", null);
        assertEquals("COMPLETED", r2.getStatus());
        assertEquals("task result", r2.getResult());
        assertNotNull(r2.getCompletedAt());

        verify(memoryService).writeToBlackboard(eq("pkg-e2e-004"), eq("subtask_1_result"), eq("task result"));
    }

    @Test
    @DisplayName("F2.1 - 子任务状态转换：非法转换抛出 IllegalStateException")
    void testUpdateSubtaskStatus_invalidTransition_throws() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-005")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-005")).thenReturn(Optional.of(entity));

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("1").packageId("pkg-e2e-005")
                .description("Test").status("COMPLETED")
                .build();
        when(subtaskRepository.findByPackageIdAndSubTaskId("pkg-e2e-005", "1"))
                .thenReturn(Optional.of(subtask));

        // COMPLETED -> PENDING 是非法转换
        assertThrows(IllegalStateException.class, () ->
                orchestrator.updateSubtaskStatus("pkg-e2e-005", "1", "PENDING", null, null)
        );
    }

    @Test
    @DisplayName("F2.1 - 暂停/恢复：只有 EXECUTING 可暂停，只有 PAUSED 可恢复")
    void testPauseResume_stateGuards() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-006")
                .status("EXECUTING")
                .createdBy("test-user")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-006")).thenReturn(Optional.of(entity));
        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // EXECUTING -> PAUSED 合法
        CollaborationPackage paused = orchestrator.pause("pkg-e2e-006");
        assertEquals("PAUSED", paused.getStatus());

        // PAUSED -> RESUMING 合法
        CollaborationPackage resumed = orchestrator.resume("pkg-e2e-006");
        assertEquals("EXECUTING", resumed.getStatus());

        // COMPLETED 状态不能暂停
        entity.setStatus("COMPLETED");
        assertThrows(IllegalStateException.class, () -> orchestrator.pause("pkg-e2e-006"));
    }

    @Test
    @DisplayName("F2.1 - 取消：取消所有 PENDING/RUNNING 子任务")
    void testCancel_cancelsAllActiveSubtasks() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-007")
                .status("EXECUTING")
                .createdBy("test-user")
                .traceId("trace-007")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-007")).thenReturn(Optional.of(entity));

        List<CollabSubtaskEntity> subtasks = List.of(
                CollabSubtaskEntity.builder()
                        .subTaskId("1").packageId("pkg-e2e-007").status("PENDING").build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("2").packageId("pkg-e2e-007").status("RUNNING").build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("3").packageId("pkg-e2e-007").status("COMPLETED").build()
        );
        when(subtaskRepository.findByPackageId("pkg-e2e-007")).thenReturn(subtasks);
        when(subtaskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollaborationPackage cancelled = orchestrator.cancel("pkg-e2e-007");

        assertEquals("CANCELLED", cancelled.getStatus());
        verify(subtaskRepository).saveAll(argThat(list ->
                ((List<CollabSubtaskEntity>) list).stream()
                        .filter(s -> !"COMPLETED".equals(s.getStatus()))
                        .allMatch(s -> "CANCELLED".equals(s.getStatus()))
        ));
    }

    // ==================== 运行时状态 ====================

    @Test
    @DisplayName("F2.1 - 运行时状态：返回包状态、子任务进度统计、执行统计")
    void testGetRuntimeStatus_returnsDetailedStatus() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-008")
                .status("EXECUTING")
                .intent("Test intent")
                .collaborationMode("SEQUENTIAL")
                .createdAt(java.time.LocalDateTime.now())
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-008")).thenReturn(Optional.of(entity));

        List<CollabSubtaskEntity> subtasks = List.of(
                CollabSubtaskEntity.builder()
                        .subTaskId("1").packageId("pkg-e2e-008").status("COMPLETED").build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("2").packageId("pkg-e2e-008").status("RUNNING").build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("3").packageId("pkg-e2e-008").status("PENDING").build(),
                CollabSubtaskEntity.builder()
                        .subTaskId("4").packageId("pkg-e2e-008").status("FAILED").build()
        );
        when(subtaskRepository.findByPackageId("pkg-e2e-008")).thenReturn(subtasks);
        when(memoryService.getPackageStats("pkg-e2e-008")).thenReturn(java.util.Map.of("activeTasks", 1));

        java.util.Map<String, Object> runtime = orchestrator.getRuntimeStatus("pkg-e2e-008");

        assertEquals("pkg-e2e-008", runtime.get("packageId"));
        assertEquals("EXECUTING", runtime.get("status"));
        assertEquals("SEQUENTIAL", runtime.get("collaborationMode"));

        @SuppressWarnings("unchecked")
        java.util.Map<String, Number> progress =
                (java.util.Map<String, Number>) runtime.get("progress");
        assertEquals(4, progress.get("total").intValue());
        assertEquals(1, progress.get("completed").intValue());
        assertEquals(1, progress.get("running").intValue());
        assertEquals(1, progress.get("pending").intValue());
        assertEquals(1, progress.get("failed").intValue());
    }

    // ==================== 人工接管 ====================

    @Test
    @DisplayName("F2.1 - 人工接管：PENDING/RUNNING 子任务可被接管")
    void testManuallyHandleSubtask_acceptsValidSubtask() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-009")
                .status("EXECUTING")
                .traceId("trace-009")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-009")).thenReturn(Optional.of(entity));

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("1").packageId("pkg-e2e-009")
                .description("Human task").status("RUNNING")
                .build();
        when(subtaskRepository.findByPackageIdAndSubTaskId("pkg-e2e-009", "1"))
                .thenReturn(Optional.of(subtask));
        when(subtaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollabSubtaskEntity result = orchestrator.manuallyHandleSubtask(
                "pkg-e2e-009", "1", "User provided input");

        assertEquals("MANUAL_HANDLING", result.getStatus());
        assertTrue(result.getResult().contains("User provided input"));
        verify(eventBus).publishSubtaskManuallyHandled(
                eq("pkg-e2e-009"), eq("1"), eq("User provided input"), eq("trace-009"));
    }

    // ==================== 事件链路 ====================

    @Test
    @DisplayName("F2.1 - 事件记录：完整链路中所有关键节点均触发事件")
    void testEventPublishing_onKeyLifecycleSteps() {
        // 1. 创建包
        when(packageRepository.save(any(CollaborationPackageEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        orchestrator.createPackage("intent", "GENERAL", "NORMAL", "SIMPLE",
                "SEQUENTIAL", "user", "trace-full");
        verify(eventBus).publishPackageCreated(anyString(), eq("intent"), eq("trace-full"));

        // 2. 分解
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-event-001")
                .intent("intent").intentCategory("GENERAL")
                .status("PLANNING").traceId("trace-full").createdBy("user")
                .build();
        when(packageRepository.findByPackageId("pkg-event-001")).thenReturn(Optional.of(entity));
        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(subtaskRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.decompose("pkg-event-001", List.of());
        verify(eventBus).publishPackageDecomposed(eq("pkg-event-001"), eq(3), eq("trace-full"));

        // 3. 完成
        entity.setStatus("EXECUTING");
        when(packageRepository.findByPackageId("pkg-event-001")).thenReturn(Optional.of(entity));
        when(packageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orchestrator.complete("pkg-event-001", "final result");
        verify(eventBus).publishPackageStatusChanged(eq("pkg-event-001"), eq("COMPLETED"), any());
    }

    // ==================== 完成判定 ====================

    @Test
    @DisplayName("F2.1 - 完成判定：所有子任务 COMPLETED 时 isAllSubtasksCompleted 返回 true")
    void testIsAllSubtasksCompleted_returnsTrueWhenAllDone() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-010")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-010")).thenReturn(Optional.of(entity));

        List<CollabSubtaskEntity> subtasks = List.of(
                CollabSubtaskEntity.builder().subTaskId("1").status("COMPLETED").build(),
                CollabSubtaskEntity.builder().subTaskId("2").status("COMPLETED").build()
        );
        when(subtaskRepository.findByPackageId("pkg-e2e-010")).thenReturn(subtasks);

        assertTrue(orchestrator.isAllSubtasksCompleted("pkg-e2e-010"));
    }

    @Test
    @DisplayName("F2.1 - 完成判定：有子任务非 COMPLETED 时返回 false")
    void testIsAllSubtasksCompleted_returnsFalseWhenSomePending() {
        CollaborationPackageEntity entity = CollaborationPackageEntity.builder()
                .packageId("pkg-e2e-011")
                .build();
        when(packageRepository.findByPackageId("pkg-e2e-011")).thenReturn(Optional.of(entity));

        List<CollabSubtaskEntity> subtasks = List.of(
                CollabSubtaskEntity.builder().subTaskId("1").status("COMPLETED").build(),
                CollabSubtaskEntity.builder().subTaskId("2").status("FAILED").build()
        );
        when(subtaskRepository.findByPackageId("pkg-e2e-011")).thenReturn(subtasks);

        assertFalse(orchestrator.isAllSubtasksCompleted("pkg-e2e-011"));
        assertTrue(orchestrator.hasFailedSubtask("pkg-e2e-011"));
    }
}
