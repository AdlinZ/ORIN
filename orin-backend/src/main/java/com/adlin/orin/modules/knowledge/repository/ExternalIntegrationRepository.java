package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, Long> {

    List<ExternalIntegration> findByKnowledgeBaseId(String knowledgeBaseId);

    List<ExternalIntegration> findByIntegrationType(String integrationType);

    List<ExternalIntegration> findByStatus(String status);

    List<ExternalIntegration> findByKnowledgeBaseIdAndStatus(String knowledgeBaseId, String status);

    @Modifying
    @Query("UPDATE ExternalIntegration e SET e.healthStatus = :status, e.consecutiveFailures = :failures, " +
            "e.lastHealthCheck = :checkTime, e.errorMessage = :error WHERE e.id = :id")
    void updateHealthStatus(@Param("id") Long id, @Param("status") String status,
                           @Param("failures") Integer failures, @Param("checkTime") LocalDateTime checkTime,
                           @Param("error") String error);
}