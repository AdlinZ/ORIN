package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollabSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollabSessionRepository extends JpaRepository<CollabSessionEntity, Long> {
    Optional<CollabSessionEntity> findBySessionId(String sessionId);
    List<CollabSessionEntity> findByCreatedByOrderByUpdatedAtDesc(String createdBy);
    List<CollabSessionEntity> findAllByOrderByUpdatedAtDesc();
}
