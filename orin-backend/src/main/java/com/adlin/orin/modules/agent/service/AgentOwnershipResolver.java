package com.adlin.orin.modules.agent.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentOwnershipResolver {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_PLATFORM_ADMIN", "ADMIN");

    private final SysUserRoleRepository userRoleRepository;
    private final SysUserRepository userRepository;

    public Long resolveFromCurrentRequest() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前请求缺少用户上下文");
        }
        try {
            return Long.parseLong(auth.getPrincipal().toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前用户标识无效");
        }
    }

    public Long resolveForSystemSeed() {
        return userRoleRepository.findSystemAdminOwnerCandidates().stream()
                .findFirst()
                .orElseGet(() -> userRepository.findAll().stream()
                        .map(user -> user.getUserId())
                        .min(Long::compareTo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "缺少系统管理员用户")));
    }

    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_ROLES.contains(authority.getAuthority()));
    }

    /**
     * Admin sees all; non-admin only own resources. Missing owner is admin-only.
     */
    public boolean canAccessOwnedResource(Long ownerUserId) {
        if (isCurrentUserAdmin()) {
            return true;
        }
        if (ownerUserId == null) {
            return false;
        }
        try {
            return resolveFromCurrentRequest().equals(ownerUserId);
        } catch (BusinessException ex) {
            return false;
        }
    }

    public void assertCanAccessOwnedResource(Long ownerUserId, String resourceLabel) {
        if (!canAccessOwnedResource(ownerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    (resourceLabel == null || resourceLabel.isBlank() ? "资源" : resourceLabel) + "不存在或无权限");
        }
    }

    public void assertCanAccessAgent(AgentMetadata metadata) {
        if (metadata == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "智能体不存在或无权限");
        }
        assertCanAccessOwnedResource(metadata.getOwnerUserId(), "智能体");
    }

    public void assertCanManageMcpExposure(AgentMetadata metadata) {
        assertCanAccessAgent(metadata);
    }
}
