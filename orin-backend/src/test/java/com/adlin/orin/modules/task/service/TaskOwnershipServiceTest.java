package com.adlin.orin.modules.task.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class TaskOwnershipServiceTest {

    private TaskRepository taskRepository;
    private WorkflowRepository workflowRepository;
    private WorkflowInstanceRepository instanceRepository;
    private TaskOwnershipService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        workflowRepository = mock(WorkflowRepository.class);
        instanceRepository = mock(WorkflowInstanceRepository.class);
        service = new TaskOwnershipService(taskRepository, workflowRepository, instanceRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assertCanManageTaskAdminBypassesOwnership() {
        authenticate("1", "ROLE_ADMIN");
        when(taskRepository.findByTaskId("task-1"))
                .thenReturn(Optional.of(task("task-1", 99L, 200L, "anyone")));
        assertDoesNotThrow(() -> service.assertCanManageTask("task-1"));
    }

    @Test
    void assertCanManageTaskByInstanceOwnerSucceeds() {
        authenticate("42", "ROLE_USER");
        TaskEntity task = task("task-1", null, 200L, "anyone");
        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task));
        when(instanceRepository.findById(200L))
                .thenReturn(Optional.of(WorkflowInstanceEntity.builder().id(200L).userId(42L).build()));

        assertDoesNotThrow(() -> service.assertCanManageTask("task-1"));
    }

    @Test
    void assertCanManageTaskByWorkflowOwnerSucceeds() {
        authenticate("42", "ROLE_USER");
        TaskEntity task = task("task-1", 100L, null, "anyone");
        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task));
        when(workflowRepository.findById(100L))
                .thenReturn(Optional.of(WorkflowEntity.builder().id(100L).ownerUserId(42L).build()));

        assertDoesNotThrow(() -> service.assertCanManageTask("task-1"));
    }

    @Test
    void assertCanManageTaskByTriggeredBySucceeds() {
        authenticate("42", "ROLE_USER");
        TaskEntity task = task("task-1", null, null, "42");
        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task));

        assertDoesNotThrow(() -> service.assertCanManageTask("task-1"));
    }

    @Test
    void assertCanManageTaskNonOwnerThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        TaskEntity task = task("task-1", 100L, 200L, "99");
        when(taskRepository.findByTaskId("task-1")).thenReturn(Optional.of(task));
        when(instanceRepository.findById(200L))
                .thenReturn(Optional.of(WorkflowInstanceEntity.builder().id(200L).userId(99L).build()));
        when(workflowRepository.findById(100L))
                .thenReturn(Optional.of(WorkflowEntity.builder().id(100L).ownerUserId(99L).build()));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanManageTask("task-1"));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void assertCanManageWorkflowOwnerPasses() {
        authenticate("42", "ROLE_USER");
        when(workflowRepository.findById(100L))
                .thenReturn(Optional.of(WorkflowEntity.builder().id(100L).ownerUserId(42L).build()));
        assertDoesNotThrow(() -> service.assertCanManageWorkflow(100L));
    }

    @Test
    void assertCanManageWorkflowNonOwnerThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        when(workflowRepository.findById(100L))
                .thenReturn(Optional.of(WorkflowEntity.builder().id(100L).ownerUserId(99L).build()));
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.assertCanManageWorkflow(100L));
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void requirePrivilegedQueueViewNonAdminThrowsForbidden() {
        authenticate("42", "ROLE_USER");
        BusinessException error = assertThrows(BusinessException.class,
                service::requirePrivilegedQueueView);
        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
    }

    @Test
    void requirePrivilegedQueueViewAdminPasses() {
        authenticate("1", "ROLE_ADMIN");
        assertDoesNotThrow(service::requirePrivilegedQueueView);
    }

    private void authenticate(String principal, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private TaskEntity task(String taskId, Long workflowId, Long instanceId, String triggeredBy) {
        return TaskEntity.builder()
                .taskId(taskId)
                .workflowId(workflowId)
                .workflowInstanceId(instanceId)
                .triggeredBy(triggeredBy)
                .build();
    }
}