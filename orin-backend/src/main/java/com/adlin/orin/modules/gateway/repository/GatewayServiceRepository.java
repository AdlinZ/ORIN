package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayServiceRepository extends JpaRepository<GatewayService, Long> {

    Optional<GatewayService> findByServiceKey(String serviceKey);

    List<GatewayService> findByEnabledOrderByServiceName(Boolean enabled);

    List<GatewayService> findAllByOrderByServiceName();

    long countByEnabled(Boolean enabled);

    boolean existsByServiceKey(String serviceKey);
}
