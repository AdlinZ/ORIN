package com.adlin.orin.modules.integrationsync.repository;

import com.adlin.orin.modules.integrationsync.entity.SyncCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncCursorRepository extends JpaRepository<SyncCursor, Long> {
    Optional<SyncCursor> findByIntegrationIdAndResourceTypeAndDirection(
            Long integrationId, String resourceType, String direction);
}
