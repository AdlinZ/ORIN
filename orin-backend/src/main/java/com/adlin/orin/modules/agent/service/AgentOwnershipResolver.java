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

    /**
     * 资源级 ACL 豁免集合: 管理员 + 运维(operator)。
     * 与 KnowledgeOwnershipResolver.PRIVILEGED_ROLES 对齐(资源级 ACL 第 1 刀 KB 引入)。
     * 跟 ADMIN_ROLES 区别: ADMIN_ROLES 仅 admin(保留 MCP 暴露设置等敏感操作只让 admin 改),
     * PRIVILEGED_ROLES 还含 OPERATOR, 用于普通读 / 改 / 删 agent。
     */
    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_SUPER_ADMIN",
            "ROLE_PLATFORM_ADMIN",
            "ADMIN",
            "ROLE_OPERATOR",
            "OPERATOR"
    );

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
     * 资源级 ACL 豁免判断: 管理员 + 运维。
     * 用于普通读 / 改 / 删 agent 与 chat 路径; MCP 暴露设置等敏感操作仍走 {@link #isCurrentUserAdmin}。
     */
    public boolean isCurrentUserPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> PRIVILEGED_ROLES.contains(authority.getAuthority()));
    }

    public void assertCanManageMcpExposure(AgentMetadata metadata) {
        Long currentUserId = resolveFromCurrentRequest();
        if (!isCurrentUserAdmin() && !currentUserId.equals(metadata.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该智能体的 MCP 暴露设置");
        }
    }

    /**
     * 资源级 ACL 通用校验(资源级 ACL 第 2 刀 Agent):
     * - admin / operator: 直接通过
     * - owner_user_id == currentUserId: 通过
     * - 其他: 抛 FORBIDDEN
     * 镜像 {@code KnowledgeOwnershipResolver.assertCanManage(KnowledgeBase)} 语义。
     */
    public void assertCanManage(AgentMetadata metadata) {
        if (metadata == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "智能体不存在");
        }
        if (isCurrentUserPrivileged()) {
            return;
        }
        Long currentUserId = resolveFromCurrentRequest();
        if (metadata.getOwnerUserId() != null && metadata.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该智能体");
    }
}
