package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayRouteRepository extends JpaRepository<UnifiedGatewayRoute, Long> {

    List<UnifiedGatewayRoute> findByEnabledOrderByPriorityDesc(Boolean enabled);

    List<UnifiedGatewayRoute> findAllByOrderByPriorityDesc();

    Optional<UnifiedGatewayRoute> findByPathPatternAndMethod(String pathPattern, String method);

    @Query("SELECT r FROM UnifiedGatewayRoute r WHERE r.enabled = true ORDER BY r.priority DESC")
    List<UnifiedGatewayRoute> findActiveRoutesOrderByPriority();

    long countByEnabled(Boolean enabled);
}
