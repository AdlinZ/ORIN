package com.adlin.orin.modules.alert.repository;

import com.adlin.orin.modules.alert.entity.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, String> {

    /**
     * 获取所有启用的规则
     */
    List<AlertRule> findByEnabledTrue();

    /**
     * 按类型查询规则
     */
    List<AlertRule> findByRuleType(String ruleType);
}
