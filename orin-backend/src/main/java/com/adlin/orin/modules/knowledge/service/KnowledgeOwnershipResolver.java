package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import org.springframework.stereotype.Component;

/**
 * 知识库资源级归属解析器(资源级 ACL 第 1 刀 KB)。
 *
 * 继承 {@link BaseOwnershipResolver}, 仅保留针对 KnowledgeBase 的入口转发。
 * 角色集合与模板方法见基类 — 资源级 ACL 4 刀已抽 common 统一。
 */
@Component
public class KnowledgeOwnershipResolver extends BaseOwnershipResolver {

    /**
     * 资源级 ACL 通用校验: 转发到基类 assertCanManage(Long), 仅多一层 null 检查给出 RESOURCE_NOT_FOUND。
     */
    public void assertCanManage(KnowledgeBase kb) {
        if (kb == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "知识库不存在");
        }
        checkOwnership(kb.getOwnerUserId());
    }
}
