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
     * 获取Token按星期分布
     */
    List<Map<String, Object>> getTokenByDayOfWeek();

    /**
     * 获取Token按小时分布
     */
    List<Map<String, Object>> getTokenByHour();

    /**
     * 获取Token类型分布 (input/output/cache)
     */
    Map<String, Object> getTokenByType();

    /**
     * 获取会话列表
     */
    List<Map<String, Object>> getSessions(int limit);

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

    /**
     * 测试 Milvus 连接
     */
    Map<String, Object> testMilvusConnection(String host, int port, String token);

    /**
     * 保存本地服务器硬件监控数据
     */
    void saveServerHardwareMetric();

    /**
     * 保存远程服务器硬件监控数据
     *
     * @param metric 监控指标
     */
    void saveRemoteServerHardwareMetric(com.adlin.orin.modules.monitor.entity.ServerHardwareMetric metric);

    /**
     * 获取服务器硬件监控历史数据
     *
     * @param serverId  服务器ID (如果传null则默认为 local)
     * @param startTime 开始时间戳
     * @param endTime   结束时间戳
     * @param page      页码
     * @param size      每页大小
     */
    Page<com.adlin.orin.modules.monitor.entity.ServerHardwareMetric> getServerHardwareHistory(
            String serverId, Long startTime, Long endTime, int page, int size);

    /**
     * 获取服务器硬件监控趋势数据
     *
     * @param serverId 服务器ID (如果传null则默认为 local)
     * @param period   时间段 (5m, 1h, 24h, 7d)
     */
    List<Map<String, Object>> getServerHardwareTrend(String serverId, String period);

    /**
     * 获取受监控的所有服务器节点列表
     */
    List<Map<String, Object>> getServerNodes();

    /**
     * 获取硬件监控统计信息
     */
    Map<String, Object> getServerHardwareStats();

    /**
     * 获取服务器静态信息列表
     */
    List<com.adlin.orin.modules.monitor.entity.ServerInfo> getServerInfoList();

    /**
     * 获取指定服务器的静态信息
     *
     * @param serverId 服务器 ID
     */
    com.adlin.orin.modules.monitor.entity.ServerInfo getServerInfo(String serverId);

    /**
     * 创建服务器静态信息
     */
    com.adlin.orin.modules.monitor.entity.ServerInfo createServerInfo(com.adlin.orin.modules.monitor.entity.ServerInfo serverInfo);

    /**
     * 更新服务器静态信息
     */
    void updateServerInfo(com.adlin.orin.modules.monitor.entity.ServerInfo serverInfo);

    /**
     * 删除服务器静态信息
     *
     * @param serverId 服务器 ID
     */
    void deleteServerInfo(String serverId);

    /**
     * 获取本地服务器信息 (通过 OSHI 采集)
     */
    Map<String, Object> getLocalServerInfo();

    /**
     * 通过 Prometheus 获取远程服务器状态
     * @param serverId 服务器ID (如果传null则默认为 local)
     */
    Map<String, Object> getPrometheusServerStatus(String serverId);

    /**
     * 调试：查询 Prometheus 原始数据
     *
     * @param query PromQL 查询语句
     */
    Map<String, Object> debugQueryPrometheus(String query);

    /**
     * 根据 traceId 查询链路追踪摘要
     *
     * @param traceId 链路追踪ID
     * @return 链路追踪详情
     */
    Map<String, Object> getTraceById(String traceId);

    /**
     * 获取调用成功率统计
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 调用成功率数据
     */
    Map<String, Object> getCallSuccessRate(Long startTime, Long endTime);

    /**
     * 获取错误分布统计
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 错误分布数据
     */
    List<Map<String, Object>> getErrorDistribution(Long startTime, Long endTime);

    // ========== 限流配置管理 ==========

    /**
     * 获取限流配置
     *
     * @return 限流配置
     */
    com.adlin.orin.modules.monitor.entity.RateLimitConfig getRateLimitConfig();

    /**
     * 更新限流配置
     *
     * @param config 限流配置
     * @param operator 操作者ID（用于审计）
     */
    void updateRateLimitConfig(com.adlin.orin.modules.monitor.entity.RateLimitConfig config, String operator);

    /**
     * 获取当前限流配置（带缓存，用于高性能读取）
     *
     * @return 限流配置
     */
    com.adlin.orin.modules.monitor.entity.RateLimitConfig getRateLimitConfigCached();
}
