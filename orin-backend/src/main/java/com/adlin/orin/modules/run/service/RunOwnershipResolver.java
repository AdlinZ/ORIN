package com.adlin.orin.modules.run.service;

import com.adlin.orin.common.exception.BusinessException;
import com.adlin.orin.common.exception.ErrorCode;
import com.adlin.orin.common.security.BaseOwnershipResolver;
import com.adlin.orin.modules.run.entity.Run;
import org.springframework.stereotype.Component;

/**
 * Run 的资源级访问控制。
 *
 * <p>新建 Run 的 {@code created_by} 保存 JWT principal（用户 ID）。普通用户只能读取、
 * 取消或重试自己的 Run；Operator/Admin 可处理所有 Run。历史上 created_by 不是用户 ID 的
 * 记录视为无主资源，仅特权角色可访问，避免把旧数据意外暴露给任意用户。
 */
@Component
public class RunOwnershipResolver extends BaseOwnershipResolver {

    public void assertCanManage(Run run) {
        if (run == null) {
            throw new BusinessException(ErrorCode.RUN_NOT_FOUND, "Run 不存在");
        }
        String createdBy = run.getCreatedBy();
        if (createdBy == null || createdBy.isBlank()) {
            checkOwnership(null);
            return;
        }
        try {
            checkOwnership(Long.parseLong(createdBy));
        } catch (NumberFormatException ignored) {
            // 旧记录没有稳定 owner ID；只让 operator/admin 管理。
            checkOwnership(null);
        }
    }

    /** 当前普通用户在 runs.created_by 中对应的稳定 owner ID。 */
    public String currentOwnerId() {
        return String.valueOf(resolveFromCurrentRequest());
    }
}
