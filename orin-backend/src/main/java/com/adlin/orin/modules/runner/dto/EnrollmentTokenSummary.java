package com.adlin.orin.modules.runner.dto;

import com.adlin.orin.modules.runner.entity.RunnerEnrollmentToken;

import java.time.Instant;

/**
 * Enrollment Token 列表 / 详情摘要。
 *
 * <p>不返回明文 token；只展示 id/name/状态/到期时间。
 */
public record EnrollmentTokenSummary(
        String id,
        String name,
        String createdBy,
        Long createdAt,
        Long expiresAt,
        long ttlSec,
        Long usedAt,
        String runnerId,
        boolean active,
        boolean expired) {

    public static EnrollmentTokenSummary from(RunnerEnrollmentToken token) {
        long now = Instant.now().toEpochMilli();
        boolean expired = token.getExpiresAt() <= now;
        long ttlSec = Math.max(0L, (token.getExpiresAt() - now) / 1000L);
        return new EnrollmentTokenSummary(
                token.getId(),
                token.getNote(),
                token.getCreatedBy(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                ttlSec,
                token.getUsedAt(),
                token.getRunnerId(),
                token.isActive(now),
                expired);
    }
}
