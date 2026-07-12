package com.adlin.orin.modules.skill.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.skill.entity.McpService;
import com.adlin.orin.modules.skill.repository.McpServiceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Owner ACL for user-managed MCP connections. */
@Component
@RequiredArgsConstructor
public class McpServiceOwnershipService extends BaseOwnershipResolver {

    private final McpServiceRepository repository;

    public List<McpService> visibleServices() {
        if (isCurrentUserPrivileged()) {
            return repository.findAll();
        }
        return repository.findByOwnerUserIdOrOwnerUserIdIsNull(resolveFromCurrentRequest());
    }

    public void assignOwnerForCreate(McpService service) {
        service.setOwnerUserId(isCurrentUserPrivileged() ? null : resolveFromCurrentRequest());
    }

    public void assertCanRead(McpService service) {
        if (isCurrentUserPrivileged() || service.getOwnerUserId() == null) {
            return;
        }
        checkOwnership(service.getOwnerUserId());
    }

    public void assertCanManage(McpService service) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        if (service.getOwnerUserId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "平台级 MCP 服务仅限管理员维护");
        }
        checkOwnership(service.getOwnerUserId());
    }

    public void assertCanUseIds(List<Long> serviceIds) {
        if (serviceIds == null) {
            return;
        }
        for (Long serviceId : serviceIds) {
            if (serviceId == null) {
                continue;
            }
            McpService service = repository.findById(serviceId)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.RESOURCE_NOT_FOUND, "MCP 服务不存在: " + serviceId));
            assertCanRead(service);
        }
    }
}
