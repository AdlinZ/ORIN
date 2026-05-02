package com.adlin.orin.modules.integrationsync.repository;

import com.adlin.orin.modules.integrationsync.entity.ExternalResourceMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExternalResourceMappingRepository extends JpaRepository<ExternalResourceMapping, Long> {
    Optional<ExternalResourceMapping> findByIntegrationIdAndOrinResourceTypeAndOrinResourceId(
            Long integrationId, String orinResourceType, String orinResourceId);

    Optional<ExternalResourceMapping> findByIntegrationIdAndPlatformTypeAndExternalResourceTypeAndExternalResourceId(
            Long integrationId, String platformType, String externalResourceType, String externalResourceId);

    List<ExternalResourceMapping> findByIntegrationId(Long integrationId);
}
