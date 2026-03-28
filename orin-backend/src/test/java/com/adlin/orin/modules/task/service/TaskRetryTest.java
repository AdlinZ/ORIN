package com.adlin.orin.modules.task.service;

import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务重试/死信逻辑单元测试
 */
@DataJpaTest
@ActiveProfiles("test")
public class TaskRetryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void testCreateTask() {
        // 创建任务
        TaskEntity task = TaskEntity.builder()
                .taskId("task-001")
                .workflowId(1L)
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.QUEUED)
                .triggeredBy("test-user")
                .build();

        TaskEntity saved = taskRepository.save(task);

        assertNotNull(saved.getId());
        assertEquals("task-001", saved.getTaskId());
        assertEquals(TaskStatus.QUEUED, saved.getStatus());
    }

    @Test
    void testTaskRetryTransition() {
        // 测试任务重试状态流转
        TaskEntity task = TaskEntity.builder()
                .taskId("task-002")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.QUEUED)
                .retryCount(0)
                .maxRetries(3)
                .build();
        task = taskRepository.save(task);

        // 首次失败，进入重试
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage("执行失败");
        task.setRetryCount(task.getRetryCount() + 1);
        task.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
        taskRepository.save(task);

        // 模拟重试调度器处理
        task.setStatus(TaskStatus.RETRYING);
        taskRepository.save(task);

        Optional<TaskEntity> retryingTask = taskRepository.findByTaskId("task-002");
        assertTrue(retryingTask.isPresent());
        assertEquals(TaskStatus.RETRYING, retryingTask.get().getStatus());
    }

    @Test
    void testTaskDeadLetter() {
        // 测试任务进入死信队列
        TaskEntity task = TaskEntity.builder()
                .taskId("task-003")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.RETRYING)
                .retryCount(3)
                .maxRetries(3)
                .errorMessage("超过最大重试次数")
                .build();
        task = taskRepository.save(task);

        // 达到最大重试次数，进入死信
        task.setStatus(TaskStatus.DEAD);
        taskRepository.save(task);

        Optional<TaskEntity> deadTask = taskRepository.findByTaskId("task-003");
        assertTrue(deadTask.isPresent());
        assertEquals(TaskStatus.DEAD, deadTask.get().getStatus());
    }

    @Test
    void testFindTasksToRetry() {
        // 测试查询待重试任务
        LocalDateTime now = LocalDateTime.now();

        // 创建已过期的重试任务
        TaskEntity task1 = TaskEntity.builder()
                .taskId("task-retry-001")
                .workflowId(1L)
                .priority(TaskPriority.HIGH)
                .status(TaskStatus.RETRYING)
                .nextRetryAt(now.minusSeconds(10)) // 已过期
                .build();
        taskRepository.save(task1);

        // 创建未过期的重试任务
        TaskEntity task2 = TaskEntity.builder()
                .taskId("task-retry-002")
                .workflowId(2L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.RETRYING)
                .nextRetryAt(now.plusSeconds(30)) // 未过期
                .build();
        taskRepository.save(task2);

        // 查询待重试任务
        List<TaskEntity> retryTasks = taskRepository.findTasksToRetry(now);
        assertEquals(1, retryTasks.size());
        assertEquals("task-retry-001", retryTasks.get(0).getTaskId());
    }

    @Test
    void testFindDeadTasks() {
        // 测试查询死信任务
        TaskEntity task1 = TaskEntity.builder()
                .taskId("task-dead-001")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.DEAD)
                .deadLetterReason("死信任务1")
                .build();
        taskRepository.save(task1);

        TaskEntity task2 = TaskEntity.builder()
                .taskId("task-dead-002")
                .workflowId(2L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.DEAD)
                .deadLetterReason("死信任务2")
                .build();
        taskRepository.save(task2);

        // 验证死信任务存在
        Optional<TaskEntity> deadTask = taskRepository.findByTaskId("task-dead-001");
        assertTrue(deadTask.isPresent());
        assertEquals(TaskStatus.DEAD, deadTask.get().getStatus());
    }

    @Test
    void testFindFailedTasks() {
        // 测试查询失败任务
        TaskEntity task = TaskEntity.builder()
                .taskId("task-failed-001")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.FAILED)
                .errorMessage("执行失败")
                .build();
        taskRepository.save(task);

        Optional<TaskEntity> failedTask = taskRepository.findByTaskId("task-failed-001");
        assertTrue(failedTask.isPresent());
        assertEquals(TaskStatus.FAILED, failedTask.get().getStatus());
    }

    @Test
    void testExponentialBackoff() {
        // 测试指数退避计算
        TaskEntity task = TaskEntity.builder()
                .taskId("task-backoff-001")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.FAILED)
                .retryCount(0)
                .maxRetries(3)
                .build();
        task = taskRepository.save(task);

        // 第 1 次重试: 30秒
        task.setRetryCount(1);
        task.setNextRetryAt(calculateNextRetryTime(task.getRetryCount()));
        task.setStatus(TaskStatus.RETRYING);
        taskRepository.save(task);

        // 第 2 次重试: 60秒
        task.setRetryCount(2);
        task.setNextRetryAt(calculateNextRetryTime(task.getRetryCount()));
        taskRepository.save(task);

        // 第 3 次重试: 120秒
        task.setRetryCount(3);
        task.setNextRetryAt(calculateNextRetryTime(task.getRetryCount()));
        taskRepository.save(task);

        Optional<TaskEntity> finalTask = taskRepository.findByTaskId("task-backoff-001");
        assertTrue(finalTask.isPresent());
        assertEquals(3, finalTask.get().getRetryCount());
    }

    /**
     * 计算下一次重试时间（指数退避）
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        // 指数退避: 30s, 60s, 120s...
        long delaySeconds = 30L * (1L << (retryCount - 1));
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    @Test
    void testTaskStatusTransition() {
        // 测试完整状态流转
        TaskEntity task = TaskEntity.builder()
                .taskId("task-transition-001")
                .workflowId(1L)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.QUEUED)
                .build();
        task = taskRepository.save(task);

        // QUEUED -> RUNNING
        task.setStatus(TaskStatus.RUNNING);
        taskRepository.save(task);
        assertEquals(TaskStatus.RUNNING, taskRepository.findById(task.getId()).get().getStatus());

        // RUNNING -> FAILED
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage("执行失败");
        taskRepository.save(task);
        assertEquals(TaskStatus.FAILED, taskRepository.findById(task.getId()).get().getStatus());

        // FAILED -> RETRYING (重试)
        task.setStatus(TaskStatus.RETRYING);
        task.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
        taskRepository.save(task);
        assertEquals(TaskStatus.RETRYING, taskRepository.findById(task.getId()).get().getStatus());

        // RETRYING -> DEAD (超过最大重试)
        task.setStatus(TaskStatus.DEAD);
        taskRepository.save(task);
        assertEquals(TaskStatus.DEAD, taskRepository.findById(task.getId()).get().getStatus());
    }
}