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

    public void assertCanManageMcpExposure(AgentMetadata metadata) {
        Long currentUserId = resolveFromCurrentRequest();
        if (!isCurrentUserAdmin() && !currentUserId.equals(metadata.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该智能体的 MCP 暴露设置");
        }
    }
}
