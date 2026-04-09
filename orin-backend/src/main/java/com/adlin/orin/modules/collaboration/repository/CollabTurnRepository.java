package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollabTurnEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CollabTurnRepository extends JpaRepository<CollabTurnEntity, Long> {
    Optional<CollabTurnEntity> findByTurnId(String turnId);
    List<CollabTurnEntity> findBySessionIdOrderByStartedAtDesc(String sessionId);
    Optional<CollabTurnEntity> findFirstBySessionIdOrderByStartedAtDesc(String sessionId);
    List<CollabTurnEntity> findByStartedAtAfter(LocalDateTime startedAt);
}
