package com.adlin.orin.modules.task.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.repository.TaskRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import com.adlin.orin.modules.workflow.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owner ACL for JWT-facing workflow task history and recovery operations. */
@Component
@RequiredArgsConstructor
public class TaskOwnershipService extends BaseOwnershipResolver {

    private final TaskRepository taskRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowInstanceRepository instanceRepository;

    public String currentUserKey() {
        return resolveFromCurrentRequest().toString();
    }

    public void assertCanManageTask(String taskId) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        TaskEntity task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流任务不存在"));
        Long currentUserId = resolveFromCurrentRequest();
        if (task.getWorkflowInstanceId() != null) {
            WorkflowInstanceEntity instance = instanceRepository.findById(task.getWorkflowInstanceId()).orElse(null);
            if (instance != null && currentUserId.equals(instance.getUserId())) {
                return;
            }
        }
        if (task.getWorkflowId() != null) {
            WorkflowEntity workflow = workflowRepository.findById(task.getWorkflowId()).orElse(null);
            if (workflow != null && currentUserId.equals(workflow.getOwnerUserId())) {
                return;
            }
        }
        if (currentUserKey().equals(task.getTriggeredBy())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看或恢复该工作流任务");
    }

    public void assertCanManageWorkflow(Long workflowId) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在"));
        if (!resolveFromCurrentRequest().equals(workflow.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该工作流的任务");
        }
    }

    public void requirePrivilegedQueueView() {
        if (!isCurrentUserPrivileged()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "全局任务队列仅限管理员查看");
        }
    }
}
