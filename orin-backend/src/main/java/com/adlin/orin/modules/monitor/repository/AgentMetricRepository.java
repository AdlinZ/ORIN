package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.AgentMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentMetricRepository extends JpaRepository<AgentMetric, Long> {

    /**
     * 查询指定 Agent 在一段时间内的监控数据
     */
    List<AgentMetric> findByAgentIdAndTimestampBetweenOrderByTimestampAsc(String agentId, Long startTime, Long endTime);

    /**
     * 获取最新的 N 条记录
     */
    List<AgentMetric> findTop10ByAgentIdOrderByTimestampDesc(String agentId);

    /**
     * 查询指定 Agent 在一段时间内的监控数据（不带排序）
     */
    List<AgentMetric> findByAgentIdAndTimestampBetween(String agentId, Long startTime, Long endTime);

    /**
     * 查询所有 Agent 在一段时间内的监控数据
     */
    List<AgentMetric> findByTimestampBetween(Long startTime, Long endTime);
}
