package com.adlin.orin.modules.workflow.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import org.springframework.stereotype.Component;

/**
 * 工作流资源级归属解析器(资源级 ACL 第 3 刀 Workflow)。
 *
 * 继承 {@link BaseOwnershipResolver}, 保留两个针对 WorkflowEntity 的入口转发
 * (assertCanManage / assertCanManageMcpExposure)。其余逻辑(角色集合、SecurityContext
 * 解析、通用 ACL 模板)全部在基类。
 */
@Component
public class WorkflowOwnershipResolver extends BaseOwnershipResolver {

    /**
     * 资源级 ACL 通用校验(普通读/写/执行): 转发到基类。
     */
    public void assertCanManage(WorkflowEntity workflow) {
        if (workflow == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "工作流不存在");
        }
        checkOwnership(workflow.getOwnerUserId());
    }

    /**
     * MCP 暴露设置校验(只 admin / owner): 转发到基类 checkMcpExposureOwnership(Long)。
     */
    public void assertCanManageMcpExposure(WorkflowEntity workflow) {
        checkMcpExposureOwnership(workflow.getOwnerUserId());
    }
}
