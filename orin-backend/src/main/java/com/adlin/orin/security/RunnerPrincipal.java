package com.adlin.orin.security;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.security.Principal;

/**
 * Runner 机器身份 principal。
 *
 * <p>由 {@code RunnerCredentialAuthFilter} 写入 SecurityContext，承载当前请求对应的
 * {@code runnerId} 与 {@code credentialId}。不实现 {@code UserDetails}，避免与
 * 业务用户角色混淆。
 */
@Data
@AllArgsConstructor
public class RunnerPrincipal implements Principal, Serializable {

    private final String runnerId;
    private final String credentialId;

    @Override
    public String getName() {
        return "runner:" + runnerId;
    }
}
