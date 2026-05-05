package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayServiceInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnifiedGatewayServiceInstanceRepository extends JpaRepository<UnifiedGatewayServiceInstance, Long> {

    List<UnifiedGatewayServiceInstance> findByServiceIdOrderByHost(Long serviceId);

    List<UnifiedGatewayServiceInstance> findByServiceIdInOrderByHost(List<Long> serviceIds);

    List<UnifiedGatewayServiceInstance> findByServiceIdAndEnabledOrderByHost(Long serviceId, Boolean enabled);

    Optional<UnifiedGatewayServiceInstance> findByIdAndServiceId(Long id, Long serviceId);

    List<UnifiedGatewayServiceInstance> findByStatusOrderByLastHeartbeatDesc(String status);

    @Query("SELECT i FROM UnifiedGatewayServiceInstance i WHERE i.status = 'UP' AND i.enabled = true")
    List<UnifiedGatewayServiceInstance> findAllHealthyInstances();

    @Modifying
    @Query("UPDATE UnifiedGatewayServiceInstance i SET i.status = :status, i.lastHeartbeat = :lastHeartbeat WHERE i.id = :id")
    void updateStatus(Long id, String status, LocalDateTime lastHeartbeat);

    long countByServiceId(Long serviceId);

    long countByStatus(String status);
}
