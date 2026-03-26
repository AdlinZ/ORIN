package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.entity.CollabEventLogEntity;
import com.adlin.orin.modules.collaboration.entity.CollabSubtaskEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.repository.CollabEventLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollabSubtaskRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 协作链服务单元测试
 */
@DataJpaTest
@ActiveProfiles("test")
public class CollaborationServiceTest {

    @Autowired
    private CollaborationPackageRepository packageRepository;

    @Autowired
    private CollabSubtaskRepository subtaskRepository;

    @Autowired
    private CollabEventLogRepository eventLogRepository;

    @Test
    void testCreatePackage() {
        // 创建协作包
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-001")
                .intent("测试意图")
                .intentCategory("TEST")
                .intentPriority("NORMAL")
                .status("PLANNING")
                .createdBy("test-user")
                .build();

        CollaborationPackageEntity saved = packageRepository.save(pkg);

        assertNotNull(saved.getId());
        assertEquals("pkg-test-001", saved.getPackageId());
        assertEquals("PLANNING", saved.getStatus());
    }

    @Test
    void testCreateSubtask() {
        // 先创建协作包
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-002")
                .intent("测试子任务")
                .status("PLANNING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        // 创建子任务
        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("subtask-001")
                .packageId(pkg.getPackageId())
                .description("执行测试任务")
                .status("PENDING")
                .build();

        CollabSubtaskEntity saved = subtaskRepository.save(subtask);

        assertNotNull(saved.getId());
        assertEquals("PENDING", saved.getStatus());
    }

    @Test
    void testPackageStateTransition() {
        // 测试协作包状态机流转
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-003")
                .intent("状态流转测试")
                .status("PLANNING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        // 状态流转: PLANNING -> DECOMPOSING -> EXECUTING -> COMPLETED
        pkg.setStatus("DECOMPOSING");
        packageRepository.save(pkg);
        assertEquals("DECOMPOSING", packageRepository.findById(pkg.getId()).get().getStatus());

        pkg.setStatus("EXECUTING");
        packageRepository.save(pkg);
        assertEquals("EXECUTING", packageRepository.findById(pkg.getId()).get().getStatus());

        pkg.setStatus("COMPLETED");
        packageRepository.save(pkg);
        assertEquals("COMPLETED", packageRepository.findById(pkg.getId()).get().getStatus());
    }

    @Test
    void testSubtaskStateTransition() {
        // 测试子任务状态机流转
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-004")
                .intent("子任务状态测试")
                .status("PLANNING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("subtask-002")
                .packageId(pkg.getPackageId())
                .description("测试")
                .status("PENDING")
                .build();
        subtask = subtaskRepository.save(subtask);

        // 状态流转: PENDING -> RUNNING -> COMPLETED
        subtask.setStatus("RUNNING");
        subtaskRepository.save(subtask);
        assertEquals("RUNNING", subtaskRepository.findById(subtask.getId()).get().getStatus());

        subtask.setStatus("COMPLETED");
        subtaskRepository.save(subtask);
        assertEquals("COMPLETED", subtaskRepository.findById(subtask.getId()).get().getStatus());
    }

    @Test
    void testSubtaskFailureAndRetry() {
        // 测试子任务失败与重试
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-005")
                .intent("失败重试测试")
                .status("EXECUTING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                .subTaskId("subtask-003")
                .packageId(pkg.getPackageId())
                .description("失败重试")
                .status("PENDING")
                .retryCount(0)
                .build();
        subtask = subtaskRepository.save(subtask);

        // 模拟失败
        subtask.setStatus("FAILED");
        subtask.setErrorMessage("执行失败");
        subtaskRepository.save(subtask);
        assertEquals("FAILED", subtaskRepository.findById(subtask.getId()).get().getStatus());

        // 重试
        subtask.setStatus("PENDING");
        subtask.setRetryCount(subtask.getRetryCount() + 1);
        subtaskRepository.save(subtask);
        assertEquals(1, subtaskRepository.findById(subtask.getId()).get().getRetryCount());
        assertEquals("PENDING", subtaskRepository.findById(subtask.getId()).get().getStatus());
    }

    @Test
    void testEventLogging() {
        // 测试事件日志记录
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-006")
                .intent("事件日志测试")
                .status("PLANNING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        // 记录事件
        CollabEventLogEntity event = CollabEventLogEntity.builder()
                .packageId(pkg.getPackageId())
                .eventType("PACKAGE_CREATED")
                .eventData("{\"message\": \"协作包已创建\"}")
                .traceId("trace-001")
                .build();

        CollabEventLogEntity saved = eventLogRepository.save(event);

        assertNotNull(saved.getId());
        assertEquals("PACKAGE_CREATED", saved.getEventType());

        // 查询事件
        List<CollabEventLogEntity> events = eventLogRepository.findByPackageIdOrderByCreatedAtAsc(pkg.getPackageId());
        assertEquals(1, events.size());
    }

    @Test
    void testPackageFallback() {
        // 测试协作包回退
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-007")
                .intent("回退测试")
                .status("EXECUTING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        // 标记为回退
        pkg.setStatus("FALLBACK");
        pkg.setErrorMessage("子任务执行失败，触发回退");
        packageRepository.save(pkg);

        assertEquals("FALLBACK", packageRepository.findById(pkg.getId()).get().getStatus());
    }

    @Test
    void testFindSubtasksByPackageId() {
        // 测试根据 packageId 查询子任务
        CollaborationPackageEntity pkg = CollaborationPackageEntity.builder()
                .packageId("pkg-test-008")
                .intent("查询测试")
                .status("PLANNING")
                .createdBy("test-user")
                .build();
        pkg = packageRepository.save(pkg);

        // 创建多个子任务
        for (int i = 1; i <= 3; i++) {
            CollabSubtaskEntity subtask = CollabSubtaskEntity.builder()
                    .subTaskId("subtask-00" + i)
                    .packageId(pkg.getPackageId())
                    .description("子任务" + i)
                    .status("PENDING")
                    .build();
            subtaskRepository.save(subtask);
        }

        // 查询
        List<CollabSubtaskEntity> subtasks = subtaskRepository.findByPackageId(pkg.getPackageId());
        assertEquals(3, subtasks.size());
    }
}
