package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollabMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface CollabMessageRepository extends JpaRepository<CollabMessageEntity, Long> {
    Page<CollabMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);
    Page<CollabMessageEntity> findBySessionIdAndTurnIdOrderByCreatedAtAsc(String sessionId, String turnId, Pageable pageable);
    long countByStageAndCreatedAtAfter(String stage, LocalDateTime createdAt);
}
