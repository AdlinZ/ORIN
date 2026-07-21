package com.adlin.orin.modules.runner.repository;

import com.adlin.orin.modules.runner.entity.RunnerEnrollmentToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunnerEnrollmentTokenRepository extends JpaRepository<RunnerEnrollmentToken, String> {

    Optional<RunnerEnrollmentToken> findByTokenHash(String tokenHash);

    List<RunnerEnrollmentToken> findByCreatedByOrderByCreatedAtDesc(String createdBy);

    /**
     * Token 明文携带不可伪造秘密之外的公开 selector（token ID），按 ID 锁定后再校验 BCrypt。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RunnerEnrollmentToken t WHERE t.id = :id")
    Optional<RunnerEnrollmentToken> findByIdForUpdate(@Param("id") String id);

    @Modifying
    @Query("DELETE FROM RunnerEnrollmentToken t WHERE t.usedAt IS NOT NULL AND t.usedAt < :olderThanMillis")
    int deleteConsumedOlderThan(@Param("olderThanMillis") long olderThanMillis);

    @Modifying
    @Query("DELETE FROM RunnerEnrollmentToken t WHERE t.expiresAt < :olderThanMillis")
    int deleteExpiredOlderThan(@Param("olderThanMillis") long olderThanMillis);
}
