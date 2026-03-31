package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayRetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayRetryPolicyRepository extends JpaRepository<GatewayRetryPolicy, Long> {

    Optional<GatewayRetryPolicy> findByName(String name);

    List<GatewayRetryPolicy> findByEnabledOrderByName(Boolean enabled);

    List<GatewayRetryPolicy> findAllByOrderByName();

    boolean existsByName(String name);

    long countByEnabled(Boolean enabled);
}
