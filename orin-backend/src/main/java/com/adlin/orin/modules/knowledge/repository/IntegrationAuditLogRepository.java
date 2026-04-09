package com.adlin.orin.modules.knowledge.repository;

import com.adlin.orin.modules.knowledge.entity.IntegrationAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IntegrationAuditLogRepository extends JpaRepository<IntegrationAuditLog, Long> {

    Page<IntegrationAuditLog> findByIntegrationIdOrderByCreatedAtDesc(Long integrationId, Pageable pageable);

    List<IntegrationAuditLog> findTop10ByIntegrationIdOrderByCreatedAtDesc(Long integrationId);
}
