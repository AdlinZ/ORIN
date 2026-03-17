package com.adlin.orin.modules.monitor.controller;

import com.adlin.orin.modules.audit.entity.AuditLog;
import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.service.MonitorService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@RequiredArgsConstructor
@Tag(name = "Phase 3: Resource & Health Monitoring", description = "智能体监控相关接口")
public class MonitorController {

    private final MonitorService monitorService;

    @Operation(summary = "获取全局监控看板数据")
    @GetMapping("/dashboard/summary")
    public Map<String, Object> getGlobalSummary() {
        return monitorService.getGlobalSummary();
    }

    @Operation(summary = "获取所有受监控的智能体列表")
    @GetMapping("/agents/list")
    public List<AgentHealthStatus> getAgentList() {
        return monitorService.getAgentList();
    }

    @Operation(summary = "获取指定智能体的实时状态")
    @GetMapping("/agents/{agentId}/status")
    public AgentHealthStatus getAgentStatus(@PathVariable String agentId) {
        return monitorService.getAgentStatus(agentId);
    }

    @Operation(summary = "获取指定智能体的历史趋势")
    @GetMapping("/agents/{agentId}/metrics")
    public List<AgentMetric> getAgentMetrics(
            @PathVariable String agentId,
            @RequestParam(name = "start") Long startTime,
            @RequestParam(name = "end") Long endTime,
            @RequestParam(defaultValue = "1m") String interval) {
        return monitorService.getAgentMetrics(agentId, startTime, endTime, interval);
    }

    @Operation(summary = "触发模拟数据生成 (调试用)")
    @PostMapping("/mock/trigger")
    public void triggerMockData() {
        monitorService.triggerMockDataGeneration();
    }

    @Operation(summary = "测试Dify连接")
    @PostMapping("/dify/test-connection")
    public boolean testDifyConnection(@RequestParam String endpointUrl, @RequestParam String apiKey) {
        return monitorService.testDifyConnection(endpointUrl, apiKey);
    }

    @Operation(summary = "获取Dify应用列表")
    @GetMapping("/dify/apps")
    public Object getDifyApps(@RequestParam String endpointUrl, @RequestParam String apiKey) {
        return monitorService.getDifyApps(endpointUrl, apiKey);
    }

    @Operation(summary = "获取Token消耗统计")
    @GetMapping("/tokens/stats")
    public Map<String, Object> getTokenStats() {
        return monitorService.getTokenStats();
    }

    @Operation(summary = "获取Token消耗趋势")
    @GetMapping("/tokens/trend")
    public List<Map<String, Object>> getTokenTrend(@RequestParam(defaultValue = "daily") String period) {
        return monitorService.getTokenTrend(period);
    }

    @Operation(summary = "获取Token消耗历史")
    @GetMapping("/tokens/history")
    public Page<AuditLog> getTokenHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return monitorService.getTokenHistory(page, size, startDate, endDate);
    }

    @Operation(summary = "获取Token分布")
    @GetMapping("/tokens/distribution")
    public List<Map<String, Object>> getTokenDistribution(
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return monitorService.getTokenDistribution(startDate, endDate);
    }

    @Operation(summary = "获取Token按星期分布")
    @GetMapping("/tokens/by-day-of-week")
    public List<Map<String, Object>> getTokenByDayOfWeek() {
        return monitorService.getTokenByDayOfWeek();
    }

    @Operation(summary = "获取Token按小时分布")
    @GetMapping("/tokens/by-hour")
    public List<Map<String, Object>> getTokenByHour() {
        return monitorService.getTokenByHour();
    }

    @Operation(summary = "获取Token类型分布")
    @GetMapping("/tokens/by-type")
    public Map<String, Object> getTokenByType() {
        return monitorService.getTokenByType();
    }

    @Operation(summary = "获取会话列表")
    @GetMapping("/sessions")
    public List<Map<String, Object>> getSessions(
            @RequestParam(defaultValue = "20") int limit) {
        return monitorService.getSessions(limit);
    }

    @Operation(summary = "获取成本分布")
    @GetMapping("/costs/distribution")
    public List<Map<String, Object>> getCostDistribution(
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return monitorService.getCostDistribution(startDate, endDate);
    }

    @Operation(summary = "获取延迟统计")
    @GetMapping("/latency/stats")
    public Map<String, Object> getLatencyStats() {
        return monitorService.getLatencyStats();
    }

    @Operation(summary = "获取延迟趋势")
    @GetMapping("/latency/trend")
    public List<Map<String, Object>> getLatencyTrend(@RequestParam(defaultValue = "daily") String period) {
        return monitorService.getLatencyTrend(period);
    }

    @Operation(summary = "获取延迟历史")
    @GetMapping("/latency/history")
    public Page<AuditLog> getLatencyHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long startDate,
            @RequestParam(required = false) Long endDate) {
        return monitorService.getLatencyHistory(page, size, startDate, endDate);
    }

    @Operation(summary = "获取服务器硬件状态")
    @GetMapping("/server-hardware")
    public Map<String, Object> getServerHardware() {
        return monitorService.getServerHardware();
    }

    @Operation(summary = "获取Prometheus配置")
    @GetMapping("/prometheus/config")
    public com.adlin.orin.modules.monitor.entity.PrometheusConfig getPrometheusConfig() {
        return monitorService.getPrometheusConfig();
    }

    @Operation(summary = "更新Prometheus配置")
    @PostMapping("/prometheus/config")
    public void updatePrometheusConfig(@RequestBody com.adlin.orin.modules.monitor.entity.PrometheusConfig config) {
        monitorService.updatePrometheusConfig(config);
    }

    @Operation(summary = "测试Prometheus连接")
    @GetMapping("/prometheus/test")
    public Map<String, Object> testPrometheusConnection() {
        return monitorService.testPrometheusConnection();
    }

    @Operation(summary = "测试Milvus连接")
    @GetMapping("/milvus/test")
    public Map<String, Object> testMilvusConnection(
            @RequestParam(defaultValue = "localhost") String host,
            @RequestParam(defaultValue = "19530") int port,
            @RequestParam(defaultValue = "") String token) {
        return monitorService.testMilvusConnection(host, port, token);
    }

    @Operation(summary = "获取系统环境变量配置")
    @GetMapping("/system/properties")
    public Map<String, String> getSystemProperties() {
        return monitorService.getSystemProperties();
    }

    @Operation(summary = "更新系统环境变量配置")
    @PostMapping("/system/properties")
    public void updateSystemProperties(@RequestBody Map<String, String> properties) {
        monitorService.updateSystemProperties(properties);
    }

    @Operation(summary = "手动触发硬件监控数据采集")
    @PostMapping("/server-hardware/collect")
    public void collectServerHardware() {
        monitorService.saveServerHardwareMetric();
    }

    @Operation(summary = "获取硬件监控历史数据")
    @GetMapping("/server-hardware/history")
    public Page<com.adlin.orin.modules.monitor.entity.ServerHardwareMetric> getServerHardwareHistory(
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return monitorService.getServerHardwareHistory(startTime, endTime, page, size);
    }

    @Operation(summary = "获取硬件监控趋势数据")
    @GetMapping("/server-hardware/trend")
    public List<Map<String, Object>> getServerHardwareTrend(@RequestParam(defaultValue = "1h") String period) {
        return monitorService.getServerHardwareTrend(period);
    }

    @Operation(summary = "获取硬件监控统计信息")
    @GetMapping("/server-hardware/stats")
    public Map<String, Object> getServerHardwareStats() {
        return monitorService.getServerHardwareStats();
    }

    @Operation(summary = "获取服务器静态信息列表")
    @GetMapping("/server-info/list")
    public List<com.adlin.orin.modules.monitor.entity.ServerInfo> getServerInfoList() {
        return monitorService.getServerInfoList();
    }

    @Operation(summary = "获取指定服务器的静态信息")
    @GetMapping("/server-info/{serverId}")
    public com.adlin.orin.modules.monitor.entity.ServerInfo getServerInfo(@PathVariable String serverId) {
        return monitorService.getServerInfo(serverId);
    }

    @Operation(summary = "更新服务器静态信息")
    @PutMapping("/server-info")
    public void updateServerInfo(@RequestBody com.adlin.orin.modules.monitor.entity.ServerInfo serverInfo) {
        monitorService.updateServerInfo(serverInfo);
    }

    @Operation(summary = "删除服务器静态信息")
    @DeleteMapping("/server-info/{serverId}")
    public void deleteServerInfo(@PathVariable String serverId) {
        monitorService.deleteServerInfo(serverId);
    }

    @Operation(summary = "获取本地服务器信息 (通过 OSHI 采集)")
    @GetMapping("/local-server-info")
    public Map<String, Object> getLocalServerInfo() {
        return monitorService.getLocalServerInfo();
    }

    @Operation(summary = "通过 Prometheus 获取远程服务器状态")
    @GetMapping("/prometheus/server-status")
    public Map<String, Object> getPrometheusServerStatus() {
        return monitorService.getPrometheusServerStatus();
    }

    @Operation(summary = "调试：查询 Prometheus 原始数据")
    @GetMapping("/debug/prometheus")
    public Map<String, Object> debugQueryPrometheus(@RequestParam String query) {
        return monitorService.debugQueryPrometheus(query);
    }
}
