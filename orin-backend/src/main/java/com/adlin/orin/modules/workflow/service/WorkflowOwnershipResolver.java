package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 工作流资源级归属解析器(资源级 ACL 第 3 刀)。
 *
 * 镜像 KnowledgeOwnershipResolver / AgentOwnershipResolver 模板(本项目内三个模块各持一份,
 * 3 刀后抽 common 合并)。语义:
 * - admin / super_admin / platform_admin / operator: 看/改全部 workflow
 * - 普通用户: 仅看/改 owner_user_id == currentUserId 的 workflow
 *
 * 保留 MCP 暴露设置只让 admin 改的语义:
 * - isCurrentUserAdmin(): 只 admin 集合(用于 MCP 暴露校验)
 * - isCurrentUserPrivileged(): admin + operator(用于普通读/写/执行)
 */
@Component
public class WorkflowOwnershipResolver {

    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN", "ROLE_SUPER_ADMIN", "ROLE_PLATFORM_ADMIN", "ADMIN");

    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_SUPER_ADMIN",
            "ROLE_PLATFORM_ADMIN",
            "ADMIN",
            "ROLE_OPERATOR",
            "OPERATOR"
    );

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

    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_ROLES.contains(authority.getAuthority()));
    }

    public boolean isCurrentUserPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> PRIVILEGED_ROLES.contains(authority.getAuthority()));
    }

    /**
     * 资源级 ACL 通用校验: admin / operator 直接通过; 普通用户需 owner == currentUserId。
     * 用于普通读 / 改 / 删 / 执行 workflow 与 instance 路径。
     * MCP 暴露设置仍走 {@link #assertCanManageMcpExposure(WorkflowEntity)} 保留 admin 专属语义。
     */
    public void assertCanManage(WorkflowEntity workflow) {
        if (workflow == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在");
        }
        if (isCurrentUserPrivileged()) {
            return;
        }
        Long currentUserId = resolveFromCurrentRequest();
        if (workflow.getOwnerUserId() != null && workflow.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该工作流");
    }

    /**
     * MCP 暴露设置只让 admin 改(保留旧语义, 不受资源级 ACL 引入影响)。
     */
    public void assertCanManageMcpExposure(WorkflowEntity workflow) {
        Long currentUserId = resolveFromCurrentRequest();
        if (!isCurrentUserAdmin() && !currentUserId.equals(workflow.getOwnerUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该工作流的 MCP 暴露设置");
        }
    }
}
