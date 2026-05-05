package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayServiceRepository extends JpaRepository<UnifiedGatewayService, Long> {

    Optional<UnifiedGatewayService> findByServiceKey(String serviceKey);

    List<UnifiedGatewayService> findByEnabledOrderByServiceName(Boolean enabled);

    List<UnifiedGatewayService> findAllByOrderByServiceName();

    long countByEnabled(Boolean enabled);

    boolean existsByServiceKey(String serviceKey);
}
