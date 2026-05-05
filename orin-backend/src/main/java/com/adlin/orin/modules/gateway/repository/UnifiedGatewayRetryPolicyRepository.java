package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayRetryPolicyRepository extends JpaRepository<UnifiedGatewayRetryPolicy, Long> {

    Optional<UnifiedGatewayRetryPolicy> findByName(String name);

    List<UnifiedGatewayRetryPolicy> findByEnabledOrderByName(Boolean enabled);

    List<UnifiedGatewayRetryPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
