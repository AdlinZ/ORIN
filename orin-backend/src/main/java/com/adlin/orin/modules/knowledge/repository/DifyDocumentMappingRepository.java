package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.DifyDocumentMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DifyDocumentMappingRepository extends JpaRepository<DifyDocumentMapping, Long> {

    Optional<DifyDocumentMapping> findByLocalDocIdAndIntegrationId(String localDocId, Long integrationId);

    Optional<DifyDocumentMapping> findByDifyDatasetIdAndDifyDocId(String difyDatasetId, String difyDocId);

    List<DifyDocumentMapping> findByIntegrationId(Long integrationId);

    List<DifyDocumentMapping> findByLocalKbId(String localKbId);

    List<DifyDocumentMapping> findBySyncStatus(String syncStatus);

    Optional<DifyDocumentMapping> findByIdempotencyKey(String idempotencyKey);

    List<DifyDocumentMapping> findByLocalDocId(String localDocId);

    @Modifying
    @Query("UPDATE DifyDocumentMapping m SET m.syncStatus = :status, m.lastSyncedAt = :syncTime " +
            "WHERE m.idempotencyKey = :key")
    int updateSyncStatus(@Param("key") String idempotencyKey, @Param("status") String status,
                        @Param("syncTime") LocalDateTime syncTime);

    @Modifying
    @Query("UPDATE DifyDocumentMapping m SET m.syncStatus = 'FAILED', m.errorMessage = :error, " +
            "m.lastSyncedAt = :syncTime WHERE m.idempotencyKey = :key")
    int markSyncFailed(@Param("key") String idempotencyKey, @Param("error") String error,
                      @Param("syncTime") LocalDateTime syncTime);

    @Modifying
    @Query("UPDATE DifyDocumentMapping m SET m.syncStatus = 'DELETED', m.deletedOnDify = true, " +
            "m.lastSyncedAt = :syncTime WHERE m.difyDocId = :difyDocId AND m.difyDatasetId = :datasetId")
    int markDeletedOnDify(@Param("difyDocId") String difyDocId, @Param("datasetId") String difyDatasetId,
                          @Param("syncTime") LocalDateTime syncTime);
}
