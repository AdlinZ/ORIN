package com.adlin.orin.modules.endpoint.repository;

import com.adlin.orin.modules.endpoint.entity.AgentEndpoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AgentEndpoint 数据访问层（F05）。
 */
@Repository
public interface AgentEndpointRepository extends JpaRepository<AgentEndpoint, String> {

    Page<AgentEndpoint> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<AgentEndpoint> findByAgentId(String agentId);

    Optional<AgentEndpoint> findByEndpointPath(String endpointPath);

    long countByAgentIdAndStatus(String agentId, com.adlin.orin.modules.endpoint.entity.EndpointStatus status);

    /** F05 ACL：按创建者分页查询。 */
    Page<AgentEndpoint> findByCreatedByOrderByCreatedAtDesc(String createdBy, Pageable pageable);
}
