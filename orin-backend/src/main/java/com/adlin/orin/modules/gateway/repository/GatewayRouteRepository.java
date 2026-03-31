package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayRouteRepository extends JpaRepository<GatewayRoute, Long> {

    List<GatewayRoute> findByEnabledOrderByPriorityDesc(Boolean enabled);

    List<GatewayRoute> findAllByOrderByPriorityDesc();

    Optional<GatewayRoute> findByPathPatternAndMethod(String pathPattern, String method);

    @Query("SELECT r FROM GatewayRoute r WHERE r.enabled = true ORDER BY r.priority DESC")
    List<GatewayRoute> findActiveRoutesOrderByPriority();

    long countByEnabled(Boolean enabled);
}
