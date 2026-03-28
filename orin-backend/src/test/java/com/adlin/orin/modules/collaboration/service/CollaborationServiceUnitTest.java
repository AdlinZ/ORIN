package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.entity.CollaborationTask;
import com.adlin.orin.modules.collaboration.repository.CollaborationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * F1.2 协作模块 service 级核心测试
 *
 * 测试目标：验证 CollaborationService 的核心业务逻辑
 * - 任务创建、查询、状态更新
 * - 串行 agent 执行（executeNextAgent）
 * - 异常路径（任务不存在）
 *
 * 运行方式：mvn test -Dtest=CollaborationServiceUnitTest
 */
@ExtendWith(MockitoExtension.class)
class CollaborationServiceUnitTest {

    @Mock
    private CollaborationTaskRepository taskRepository;

    private CollaborationService collaborationService;

    @BeforeEach
    void setUp() {
        collaborationService = new CollaborationService(taskRepository);
    }

    // ==================== 任务创建 ====================

    @Test
    @DisplayName("F1.2 - 创建协作任务：成功创建并设置初始状态")
    void testCreateTask_Success() {
        // Given
        List<String> agentIds = Arrays.asList("agent-1", "agent-2");
        when(taskRepository.save(any(CollaborationTask.class)))
                .thenAnswer(inv -> {
                    CollaborationTask t = inv.getArgument(0);
                    t.setId(1L);
                    return t;
                });

        // When
        CollaborationTask result = collaborationService.createTask(
                "测试协作任务", "描述", "SEQUENTIAL", agentIds, "test-user");

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("测试协作任务", result.getName());
        assertEquals("PENDING", result.getStatus());
        assertEquals(0, result.getCurrentAgentIndex());
        assertEquals("test-user", result.getCreatedBy());
        verify(taskRepository).save(any(CollaborationTask.class));
    }

    // ==================== 任务查询 ====================

    @Test
    @DisplayName("F1.2 - 查询任务：按用户返回按时间倒序的列表")
    void testGetTasksByUser() {
        // Given
        CollaborationTask t1 = CollaborationTask.builder().id(1L).name("任务1").createdBy("user1").build();
        CollaborationTask t2 = CollaborationTask.builder().id(2L).name("任务2").createdBy("user1").build();
        when(taskRepository.findByCreatedByOrderByCreatedAtDesc("user1"))
                .thenReturn(Arrays.asList(t2, t1));

        // When
        List<CollaborationTask> result = collaborationService.getTasksByUser("user1");

        // Then
        assertEquals(2, result.size());
        assertEquals("任务2", result.get(0).getName()); // 按时间倒序
    }

    @Test
    @DisplayName("F1.2 - 查询所有任务：返回完整列表")
    void testGetAllTasks() {
        // Given
        when(taskRepository.findAll()).thenReturn(Arrays.asList(
                CollaborationTask.builder().id(1L).name("任务1").build(),
                CollaborationTask.builder().id(2L).name("任务2").build()
        ));

        // When
        List<CollaborationTask> result = collaborationService.getAllTasks();

        // Then
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("F1.2 - 查询任务详情：存在时返回")
    void testGetTask_Found() {
        // Given
        CollaborationTask task = CollaborationTask.builder().id(1L).name("任务1").build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // When
        Optional<CollaborationTask> result = collaborationService.getTask(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("任务1", result.get().getName());
    }

    @Test
    @DisplayName("F1.2 - 查询任务详情：不存在时返回空")
    void testGetTask_NotFound() {
        // Given
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        Optional<CollaborationTask> result = collaborationService.getTask(99L);

        // Then
        assertTrue(result.isEmpty());
    }

    // ==================== 状态更新 ====================

    @Test
    @DisplayName("F1.2 - 更新任务状态：COMPLETED 时设置完成时间")
    void testUpdateTaskStatus_Completed() {
        // Given
        CollaborationTask task = CollaborationTask.builder()
                .id(1L)
                .name("任务1")
                .status("RUNNING")
                .build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CollaborationTask result = collaborationService.updateTaskStatus(1L, "COMPLETED", "结果数据", null);

        // Then
        assertEquals("COMPLETED", result.getStatus());
        assertEquals("结果数据", result.getResult());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("F1.2 - 更新任务状态：FAILED 时设置错误信息")
    void testUpdateTaskStatus_Failed() {
        // Given
        CollaborationTask task = CollaborationTask.builder()
                .id(1L)
                .name("任务1")
                .status("RUNNING")
                .build();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CollaborationTask result = collaborationService.updateTaskStatus(1L, "FAILED", null, "执行失败原因");

        // Then
        assertEquals("FAILED", result.getStatus());
        assertEquals("执行失败原因", result.getErrorMessage());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("F1.2 - 更新任务状态：任务不存在时抛出异常")
    void testUpdateTaskStatus_NotFound() {
        // Given
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> collaborationService.updateTaskStatus(99L, "COMPLETED", null, null));
        assertTrue(exception.getMessage().contains("Task not found"));
        verify(taskRepository, never()).save(any());
    }

    // ==================== 串行执行 ====================

    @Test
    @DisplayName("F1.2 - 串行执行：第一个 agent 执行后 index 前进到 1")
    void testExecuteNextAgent_FirstAgent() {
        // Given
        CollaborationTask task = CollaborationTask.builder()
                .id(1L)
                .name("串行任务")
                .status("PENDING")
                .agentIds(Arrays.asList("agent-1", "agent-2"))
                .currentAgentIndex(0)
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CollaborationTask result = collaborationService.executeNextAgent(1L);

        // Then
        assertEquals(1, result.getCurrentAgentIndex());
        assertEquals("RUNNING", result.getStatus());
        verify(taskRepository).save(any(CollaborationTask.class));
    }

    @Test
    @DisplayName("F1.2 - 串行执行：最后一个 agent 执行后状态变为 COMPLETED")
    void testExecuteNextAgent_LastAgentCompletes() {
        // Given
        CollaborationTask task = CollaborationTask.builder()
                .id(1L)
                .name("串行任务")
                .status("RUNNING")
                .agentIds(Arrays.asList("agent-1", "agent-2"))
                .currentAgentIndex(1) // 第二个 agent（最后一个）
                .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When
        CollaborationTask result = collaborationService.executeNextAgent(1L);

        // Then
        assertEquals(2, result.getCurrentAgentIndex()); // 已超出 agentIds.size()
        assertEquals("COMPLETED", result.getStatus());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("F1.2 - 串行执行：任务不存在时抛出异常")
    void testExecuteNextAgent_NotFound() {
        // Given
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> collaborationService.executeNextAgent(99L));
        assertTrue(exception.getMessage().contains("Task not found"));
    }

    @Test
    @DisplayName("F1.2 - 查询运行中任务：返回按时间倒序的 RUNNING 任务")
    void testGetRunningTasks() {
        // Given
        CollaborationTask runningTask = CollaborationTask.builder()
                .id(1L)
                .name("运行中任务")
                .status("RUNNING")
                .build();
        when(taskRepository.findByStatusOrderByCreatedAtDesc("RUNNING"))
                .thenReturn(Arrays.asList(runningTask));

        // When
        List<CollaborationTask> result = collaborationService.getRunningTasks();

        // Then
        assertEquals(1, result.size());
        assertEquals("RUNNING", result.get(0).getStatus());
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("F1.2 - 删除任务：调用 repository 删除")
    void testDeleteTask() {
        // When
        collaborationService.deleteTask(1L);

        // Then
        verify(taskRepository).deleteById(1L);
    }
}
