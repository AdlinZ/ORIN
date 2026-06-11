package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 知识库资源级归属解析器。
 *
 * 镜像 AgentOwnershipResolver(orin-backend .../agent/service/AgentOwnershipResolver.java),
 * 后续若 Agent / Workflow 接入行级 ACL,可抽到 common 模块。
 *
 * 隔离语义:
 * - admin / super_admin / platform_admin: 看/改全部 KB(含 NULL owner)
 * - operator(运维): 看/改全部 KB(含 NULL owner),与 TODO.md 角色矩阵一致
 * - 普通用户: 只能看/改 owner_user_id = currentUserId 的 KB,看不到系统级(NULL owner)
 */
@Component
@RequiredArgsConstructor
public class KnowledgeOwnershipResolver {

    /**
     * 资源级 ACL 豁免集合:管理员 + 运维。
     * 与 AgentOwnershipResolver.ADMIN_ROLES 对齐,只是多加了运维(OPERATOR)。
     */
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

    public boolean isCurrentUserPrivileged() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(authority -> PRIVILEGED_ROLES.contains(authority.getAuthority()));
    }

    /**
     * 校验当前用户是否能管理该知识库。
     * - 豁免角色(admin / operator): 直接通过
     * - owner_user_id == currentUserId: 通过
     * - 其他: 抛 FORBIDDEN
     */
    public void assertCanManage(KnowledgeBase kb) {
        if (kb == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        if (isCurrentUserPrivileged()) {
            return;
        }
        Long currentUserId = resolveFromCurrentRequest();
        if (kb.getOwnerUserId() != null && kb.getOwnerUserId().equals(currentUserId)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该知识库");
    }
}
