package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.collaboration.entity.CollabSessionEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationPackageEntity;
import com.adlin.orin.modules.collaboration.entity.CollaborationTask;
import com.adlin.orin.modules.collaboration.repository.CollabSessionRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationPackageRepository;
import com.adlin.orin.modules.collaboration.repository.CollaborationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Collaboration resource ownership boundary.
 *
 * <p>New user-facing collaboration requests derive their owner from the JWT principal. Historical
 * {@code X-User-Id} values are never trusted for authorization.</p>
 */
@Component
@RequiredArgsConstructor
public class CollaborationOwnershipService extends BaseOwnershipResolver {

    private final CollaborationPackageRepository packageRepository;
    private final CollaborationTaskRepository taskRepository;
    private final CollabSessionRepository sessionRepository;

    public String currentUserKey() {
        return resolveFromCurrentRequest().toString();
    }

    public void assertCanManagePackage(String packageId) {
        CollaborationPackageEntity entity = packageRepository.findByPackageId(packageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "协作任务包不存在"));
        assertOwner(entity.getCreatedBy());
    }

    public void assertCanManageTask(Long taskId) {
        CollaborationTask entity = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "协作任务不存在"));
        assertOwner(entity.getCreatedBy());
    }

    public void assertCanManageSession(String sessionId) {
        CollabSessionEntity entity = sessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "协作会话不存在"));
        assertOwner(entity.getCreatedBy());
    }

    private void assertOwner(String createdBy) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        if (createdBy != null && createdBy.equals(currentUserKey())) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该协作资源");
    }
}
