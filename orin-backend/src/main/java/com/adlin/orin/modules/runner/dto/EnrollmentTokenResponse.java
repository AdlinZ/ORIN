package com.adlin.orin.modules.runner.dto;

import com.adlin.orin.modules.runner.entity.RunnerEnrollmentToken;

import java.time.Instant;

/**
 * 创建 Enrollment Token 响应。
 *
 * <p>明文 token 仅在创建响应里出现一次；后续 GET 列表只返回元数据。
 */
public record EnrollmentTokenResponse(
        String id,
        String name,
        String token,
        String enrollmentEndpoint,
        long expiresAt,
        long ttlSec,
        Long createdAt) {

    public static EnrollmentTokenResponse from(RunnerEnrollmentToken token,
                                               String plaintext,
                                               String enrollmentEndpoint) {
        long now = Instant.now().toEpochMilli();
        long ttlSec = Math.max(0L, (token.getExpiresAt() - now) / 1000L);
        return new EnrollmentTokenResponse(
                token.getId(),
                token.getNote(),
                plaintext,
                enrollmentEndpoint,
                token.getExpiresAt(),
                ttlSec,
                token.getCreatedAt());
    }
}
