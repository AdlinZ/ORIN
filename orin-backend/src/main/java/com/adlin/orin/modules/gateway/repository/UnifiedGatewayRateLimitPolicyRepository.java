package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRateLimitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayRateLimitPolicyRepository extends JpaRepository<UnifiedGatewayRateLimitPolicy, Long> {

    Optional<UnifiedGatewayRateLimitPolicy> findByName(String name);

    List<UnifiedGatewayRateLimitPolicy> findByEnabledOrderByName(Boolean enabled);

    List<UnifiedGatewayRateLimitPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
