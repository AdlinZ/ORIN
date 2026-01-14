package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;

import java.util.List;
import java.util.Map;

public interface MonitorService {

    /**
     * 获取全局监控概览数据
     */
    Map<String, Object> getGlobalSummary();

    /**
     * 获取指定 Agent 的实时健康状态
     */
    AgentHealthStatus getAgentStatus(String agentId);

    /**
     * 获取指定 Agent 的历史监控指标
     * 
     * @param agentId  智能体ID
     * @param interval 粒度 (1m, 5m, 1h) - 当前版本可暂时忽略，直接返回原始数据
     */
    List<AgentMetric> getAgentMetrics(String agentId, Long startTime, Long endTime, String interval);

    /**
     * 获取所有受监控的智能体列表 (简易版)
     */
    List<AgentHealthStatus> getAgentList();

    /**
     * 触发模拟数据生成 (开发调试用)
     */
    void triggerMockDataGeneration();
    
    /**
     * 测试与Dify服务的连接
     */
    boolean testDifyConnection(String endpointUrl, String apiKey);
    
    /**
     * 获取Dify应用信息
     */
    Object getDifyApps(String endpointUrl, String apiKey);
}
