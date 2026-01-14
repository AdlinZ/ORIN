package com.adlin.orin.modules.alert.repository;

import com.adlin.orin.modules.alert.entity.AlertHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertHistoryRepository extends JpaRepository<AlertHistory, String> {

    /**
     * 分页查询告警历史
     */
    Page<AlertHistory> findAllByOrderByTriggeredAtDesc(Pageable pageable);

    /**
     * 按规则 ID 查询
     */
    List<AlertHistory> findByRuleIdOrderByTriggeredAtDesc(String ruleId);

    /**
     * 按智能体 ID 查询
     */
    List<AlertHistory> findByAgentIdOrderByTriggeredAtDesc(String agentId);

    /**
     * 查询指定时间范围内的告警
     */
    List<AlertHistory> findByTriggeredAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 统计未解决的告警数量
     */
    long countByStatus(String status);
}
