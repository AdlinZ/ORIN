package com.adlin.orin.modules.trace.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.workflow.entity.WorkflowInstanceEntity;
import com.adlin.orin.modules.workflow.repository.WorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Restricts user-facing trace queries to traces initiated by the current JWT user. */
@Component
@RequiredArgsConstructor
public class TraceOwnershipService extends BaseOwnershipResolver {

    private final WorkflowInstanceRepository instanceRepository;
    private final CollaborationPackageRepository collaborationPackageRepository;
    private final AuditLogRepository auditLogRepository;

    public Long currentUserId() {
        return resolveFromCurrentRequest();
    }

    public void assertCanReadInstance(Long instanceId) {
        WorkflowInstanceEntity instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流实例不存在"));
        if (isCurrentUserPrivileged() || currentUserId().equals(instance.getUserId())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该工作流实例的调用链路");
    }

    public void assertCanReadTrace(String traceId) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        String currentUserKey = currentUserId().toString();
        boolean workflowOwned = instanceRepository.findByTraceId(traceId)
                .map(instance -> currentUserId().equals(instance.getUserId()))
                .orElse(false);
        boolean collaborationOwned = collaborationPackageRepository.findByTraceId(traceId).stream()
                .anyMatch(pkg -> currentUserKey.equals(pkg.getCreatedBy()));
        boolean auditOwned = auditLogRepository.findByTraceIdOrderByCreatedAtAsc(traceId).stream()
                .anyMatch(log -> currentUserKey.equals(log.getUserId()));
        if (workflowOwned || collaborationOwned || auditOwned) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权查看该调用链路");
    }
}
