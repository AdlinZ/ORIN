package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayServiceInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface GatewayServiceInstanceRepository extends JpaRepository<GatewayServiceInstance, Long> {

    List<GatewayServiceInstance> findByServiceIdOrderByHost(Long serviceId);

    List<GatewayServiceInstance> findByServiceIdAndEnabledOrderByHost(Long serviceId, Boolean enabled);

    Optional<GatewayServiceInstance> findByIdAndServiceId(Long id, Long serviceId);

    List<GatewayServiceInstance> findByStatusOrderByLastHeartbeatDesc(String status);

    @Query("SELECT i FROM GatewayServiceInstance i WHERE i.status = 'UP' AND i.enabled = true")
    List<GatewayServiceInstance> findAllHealthyInstances();

    @Modifying
    @Query("UPDATE GatewayServiceInstance i SET i.status = :status, i.lastHeartbeat = :lastHeartbeat WHERE i.id = :id")
    void updateStatus(Long id, String status, LocalDateTime lastHeartbeat);

    long countByServiceId(Long serviceId);

    long countByStatus(String status);
}
