package com.adlin.orin.modules.monitor.service;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import org.springframework.data.domain.Page;

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
     * 获取系统环境变量 (从 application-dev.properties 解析)
     */
    Map<String, String> getSystemProperties();

    /**
     * 更新系统环境变量
     */
    void updateSystemProperties(Map<String, String> properties);

    /**
     * 测试与Dify服务的连接
     */
    boolean testDifyConnection(String endpointUrl, String apiKey);

    /**
     * 获取Dify应用信息
     */
    Object getDifyApps(String endpointUrl, String apiKey);

    /**
     * 获取消耗统计数据 (包含 Token 和 成本)
     */
    Map<String, Object> getTokenStats();

    /**
     * 获取Token消耗趋势
     */
    List<Map<String, Object>> getTokenTrend(String period);

    /**
     * 获取Token消耗历史记录
     */
    Page<AuditLog> getTokenHistory(int page, int size, Long startDate, Long endDate);

    /**
     * 获取Token消耗分布（按Agent）
     */
    List<Map<String, Object>> getTokenDistribution(Long startDate, Long endDate);

    /**
     * 获取成本消耗分布（按Agent）
     */
    List<Map<String, Object>> getCostDistribution(Long startDate, Long endDate);

    /**
     * 获取延迟统计数据
     */
    Map<String, Object> getLatencyStats();

    /**
     * 获取延迟趋势
     */
    List<Map<String, Object>> getLatencyTrend(String period);

    /**
     * 获取延迟历史记录
     */
    Page<AuditLog> getLatencyHistory(int page, int size, Long startDate, Long endDate);

    /**
     * 获取服务器硬件监控数据
     */
    Map<String, Object> getServerHardware();

    /**
     * 更新 Prometheus 配置
     */
    void updatePrometheusConfig(com.adlin.orin.modules.monitor.entity.PrometheusConfig config);

    /**
     * 获取 Prometheus 配置
     */
    com.adlin.orin.modules.monitor.entity.PrometheusConfig getPrometheusConfig();

    /**
     * 测试 Prometheus 连接 (轻量级)
     */
    Map<String, Object> testPrometheusConnection();
}
