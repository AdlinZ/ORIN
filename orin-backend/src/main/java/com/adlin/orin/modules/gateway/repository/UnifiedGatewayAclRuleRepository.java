package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.UnifiedGatewayAclRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnifiedGatewayAclRuleRepository extends JpaRepository<UnifiedGatewayAclRule, Long> {

    List<UnifiedGatewayAclRule> findByEnabledOrderByPriorityDesc(Boolean enabled);

    List<UnifiedGatewayAclRule> findAllByOrderByPriorityDesc();

    List<UnifiedGatewayAclRule> findByTypeOrderByPriorityDesc(String type);

    long countByEnabled(Boolean enabled);

    long countByType(String type);
}
