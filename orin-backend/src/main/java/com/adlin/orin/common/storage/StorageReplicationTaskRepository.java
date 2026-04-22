package com.adlin.orin.common.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StorageReplicationTaskRepository extends JpaRepository<StorageReplicationTask, String> {
    List<StorageReplicationTask> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
            List<String> statuses, LocalDateTime now);

    long countByStatus(String status);

    @Query("select count(t) from StorageReplicationTask t where t.status in ('PENDING_REPAIR','REPAIR_FAILED')")
    long countPendingOrFailed();
}

