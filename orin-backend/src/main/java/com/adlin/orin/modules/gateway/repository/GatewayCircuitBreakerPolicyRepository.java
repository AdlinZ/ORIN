package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayCircuitBreakerPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayCircuitBreakerPolicyRepository extends JpaRepository<GatewayCircuitBreakerPolicy, Long> {

    Optional<GatewayCircuitBreakerPolicy> findByName(String name);

    List<GatewayCircuitBreakerPolicy> findByEnabledOrderByName(Boolean enabled);

    List<GatewayCircuitBreakerPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
