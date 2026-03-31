package com.adlin.orin.modules.gateway.repository;

import com.adlin.orin.modules.gateway.entity.GatewayAclRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GatewayAclRuleRepository extends JpaRepository<GatewayAclRule, Long> {

    List<GatewayAclRule> findByEnabledOrderByPriorityDesc(Boolean enabled);

    List<GatewayAclRule> findAllByOrderByPriorityDesc();

    List<GatewayAclRule> findByTypeOrderByPriorityDesc(String type);

    long countByEnabled(Boolean enabled);

    long countByType(String type);
}
