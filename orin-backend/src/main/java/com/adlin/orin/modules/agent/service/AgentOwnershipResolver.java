package com.adlin.orin.modules.agent.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.agent.entity.AgentMetadata;
import com.adlin.orin.modules.system.repository.SysUserRepository;
import com.adlin.orin.modules.system.repository.SysUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 智能体资源级归属解析器(资源级 ACL 第 2 刀 Agent)。
 *
 * 继承 {@link BaseOwnershipResolver}, 保留两个针对 AgentMetadata 的入口转发
 * (assertCanManage / assertCanManageMcpExposure)。其余逻辑(角色集合、SecurityContext
 * 解析、通用 ACL 模板)全部在基类。
 *
 * 自身仍持有 SysUserRepository / SysUserRoleRepository 是因为 {@link #resolveForSystemSeed()}
 * 仍需这两个依赖查 system admin owner, 未抽到基类(各模块使用场景不同)。
 */
@Component
@RequiredArgsConstructor
public class AgentOwnershipResolver extends BaseOwnershipResolver {

    private final SysUserRoleRepository userRoleRepository;
    private final SysUserRepository userRepository;

    public Long resolveForSystemSeed() {
        return userRoleRepository.findSystemAdminOwnerCandidates().stream()
                .findFirst()
                .orElseGet(() -> userRepository.findAll().stream()
                        .map(user -> user.getUserId())
                        .min(Long::compareTo)
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_ERROR, "缺少系统管理员用户")));
    }

    /**
     * 资源级 ACL 通用校验(普通读/写/chat): 转发到基类。
     */
    public void assertCanManage(AgentMetadata metadata) {
        if (metadata == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "智能体不存在");
        }
        checkOwnership(metadata.getOwnerUserId());
    }

    /**
     * MCP 暴露设置校验(只 admin / owner): 转发到基类 checkMcpExposureOwnership(Long)。
     */
    public void assertCanManageMcpExposure(AgentMetadata metadata) {
        checkMcpExposureOwnership(metadata.getOwnerUserId());
    }
}
