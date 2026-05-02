package com.adlin.orin.modules.integrationsync.repository;

import com.adlin.orin.modules.integrationsync.entity.SyncItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncItemRepository extends JpaRepository<SyncItem, Long> {
    List<SyncItem> findBySyncJobId(Long syncJobId);
}
