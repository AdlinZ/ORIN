package com.adlin.orin.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.security.Principal;

/**
 * Enrollment Token principal — 仅用于 {@code POST /api/system/runners/enroll} 端点。
 *
 * <p>不暴露任何业务角色；Filter 只做非破坏性校验，真正消费由 Enrollment 业务事务完成。
 */
@Data
@AllArgsConstructor
public class EnrollmentTokenPrincipal implements Principal, Serializable {

    private final String tokenId;
    private final String expectedRunnerName;
    private final String createdBy;

    @Override
    public String getName() {
        return "enrollment-token:" + tokenId;
    }
}
