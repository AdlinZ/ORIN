package com.adlin.orin.modules.collaboration.repository;

import com.adlin.orin.modules.collaboration.entity.CollabEventLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollabEventLogRepository extends JpaRepository<CollabEventLogEntity, Long> {

    List<CollabEventLogEntity> findByPackageIdOrderByCreatedAtAsc(String packageId);

    List<CollabEventLogEntity> findByPackageIdAndEventType(String packageId, String eventType);

    List<CollabEventLogEntity> findByTraceId(String traceId);

    List<CollabEventLogEntity> findByAgentId(String agentId);
}