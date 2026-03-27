package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalIntegrationRepository extends JpaRepository<ExternalIntegration, Long> {

    List<ExternalIntegration> findByKnowledgeBaseId(String knowledgeBaseId);

    List<ExternalIntegration> findByIntegrationType(String integrationType);

    List<ExternalIntegration> findByStatus(String status);

    List<ExternalIntegration> findByKnowledgeBaseIdAndStatus(String knowledgeBaseId, String status);
}