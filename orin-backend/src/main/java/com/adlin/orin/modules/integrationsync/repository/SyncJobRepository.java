package com.adlin.orin.modules.integrationsync.repository;

import com.adlin.orin.modules.integrationsync.entity.SyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {
    List<SyncJob> findByIntegrationIdOrderByStartedAtDesc(Long integrationId);
}
