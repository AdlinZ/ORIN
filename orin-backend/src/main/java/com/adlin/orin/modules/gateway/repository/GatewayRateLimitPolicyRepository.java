package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayRateLimitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayRateLimitPolicyRepository extends JpaRepository<GatewayRateLimitPolicy, Long> {

    Optional<GatewayRateLimitPolicy> findByName(String name);

    List<GatewayRateLimitPolicy> findByEnabledOrderByName(Boolean enabled);

    List<GatewayRateLimitPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
