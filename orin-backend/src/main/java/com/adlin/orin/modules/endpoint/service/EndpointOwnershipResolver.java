package com.adlin.orin.modules.endpoint.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import org.springframework.stereotype.Component;

/**
 * F05 P0：Endpoint 资源级 ACL。
 *
 * <p>规则：
 * <ul>
 *   <li>admin / operator → 可管理所有 Endpoint</li>
 *   <li>普通用户 → 仅可管理自己创建的 Endpoint（createdBy 匹配）</li>
 *   <li>无登录用户 → 拒绝</li>
 * </ul>
 */
@Component
public class EndpointOwnershipResolver extends BaseOwnershipResolver {

    /** 校验当前用户有权操作此 Endpoint。 */
    public void assertCanManage(AgentEndpoint endpoint) {
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.ENDPOINT_NOT_FOUND);
        }
        if (isCurrentUserPrivileged()) {
            return;
        }
        Long currentUserId = resolveFromCurrentRequest();
        if (endpoint.getCreatedBy() != null
                && endpoint.getCreatedBy().equals(String.valueOf(currentUserId))) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作该 Endpoint");
    }

    /** 当前用户的 owner id（String）。 */
    public String currentOwnerId() {
        return String.valueOf(resolveFromCurrentRequest());
    }
}
