package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayCircuitBreakerPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayCircuitBreakerPolicyRepository extends JpaRepository<UnifiedGatewayCircuitBreakerPolicy, Long> {

    Optional<UnifiedGatewayCircuitBreakerPolicy> findByName(String name);

    List<UnifiedGatewayCircuitBreakerPolicy> findByEnabledOrderByName(Boolean enabled);

    List<UnifiedGatewayCircuitBreakerPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
