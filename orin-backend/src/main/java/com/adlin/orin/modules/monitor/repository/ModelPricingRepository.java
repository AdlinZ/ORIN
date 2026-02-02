package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.ModelPricing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModelPricingRepository extends JpaRepository<ModelPricing, Long> {

    Optional<ModelPricing> findByProviderIdAndTenantGroup(String providerId, String tenantGroup);

    boolean existsByProviderIdAndTenantGroup(String providerId, String tenantGroup);
}
