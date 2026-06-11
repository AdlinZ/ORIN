package com.adlin.orin.common.security;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 资源级归属解析器基类(资源级 ACL 4 刀 — 抽 common)。
 *
 * 模板定义所有模块共用的:
 * - 角色集合(ADMIN_ROLES / PRIVILEGED_ROLES)
 * - SecurityContext 解析(resolveFromCurrentRequest)
 * - 角色判断(isCurrentUserAdmin / isCurrentUserPrivileged)
 * - 通用 ACL 模板方法(assertCanManage(Long) / assertCanManageMcpExposure(Long))
 *
 * 资源级 ACL 三方(KnowledgeOwnershipResolver / AgentOwnershipResolver / WorkflowOwnershipResolver)
 * 继承本基类,只保留针对本实体的 null 检查 + 转发。
 *
 * 口径统一定义(避免未来在某个模块被改、其他模块漏改):
 * - admin / super_admin / platform_admin: ADMIN_ROLES(MCP 暴露设置等敏感操作)
 * - admin / operator: PRIVILEGED_ROLES(普通读/写/执行)
 * - 普通用户: 需 owner_user_id == currentUserId
 */
public abstract class BaseOwnershipResolver {

    /**
     * 资源级 ACL 严格集: 仅 admin 角色。
     * 用于 MCP 暴露设置等"敏感"操作, 运维不能改。
     */
    private static final Set<String> ADMIN_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_SUPER_ADMIN",
            "ROLE_PLATFORM_ADMIN",
            "ADMIN"
    );

    /**
     * 资源级 ACL 豁免集: admin + 运维(operator)。
     * 用于普通读 / 写 / 删 / 执行 workflow 等路径。
     */
    private static final Set<String> PRIVILEGED_ROLES = Set.of(
            "ROLE_ADMIN",
            "ROLE_SUPER_ADMIN",
            "ROLE_PLATFORM_ADMIN",
            "ADMIN",
            "ROLE_OPERATOR",
            "OPERATOR"
    );

    /**
     * 从当前 SecurityContext 解析用户 ID。
     */
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
     * 通用资源级 ACL 模板(基类 protected, 子类 public assertCanManage(实体) 入口转发):
     * - admin / operator: 通过
     * - ownerUserId == currentUserId: 通过
     * - 其他: 抛 FORBIDDEN
     *
     * 接受 {@code Long ownerUserId}(可为 null,表示系统级/无主资源,普通用户不可见)。
     * final 防止子类乱覆盖破坏语义。protected 防止外部直接传 Long 调用造成方法重载歧义。
     */
    protected final void checkOwnership(Long ownerUserId) {
        if (isCurrentUserPrivileged()) {
            return;
        }
        Long currentUserId = resolveFromCurrentRequest();
        if (ownerUserId != null && ownerUserId.equals(currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该资源");
    }

    /**
     * MCP 暴露设置模板: 仅 admin / owner 可改(运维不能改)。
     * protected 同上。
     */
    protected final void checkMcpExposureOwnership(Long ownerUserId) {
        Long currentUserId = resolveFromCurrentRequest();
        if (!isCurrentUserAdmin() && !currentUserId.equals(ownerUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改该资源的 MCP 暴露设置");
        }
    }
}
