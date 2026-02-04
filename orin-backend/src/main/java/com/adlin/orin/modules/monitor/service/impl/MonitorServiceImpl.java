package com.adlin.orin.modules.monitor.service.impl;

import com.adlin.orin.modules.monitor.entity.AgentHealthStatus;
import com.adlin.orin.modules.monitor.entity.AgentStatus;
import com.adlin.orin.modules.monitor.entity.AgentMetric;
import com.adlin.orin.modules.monitor.entity.PrometheusConfig;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.monitor.repository.AgentMetricRepository;
import com.adlin.orin.modules.monitor.repository.PrometheusConfigRepository;
import com.adlin.orin.modules.monitor.service.MonitorService;
import com.adlin.orin.modules.monitor.service.PrometheusService;
import com.adlin.orin.modules.audit.repository.AuditLogRepository;
import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.adlin.orin.modules.audit.entity.AuditLog;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorServiceImpl implements MonitorService {

        private final AgentHealthStatusRepository healthStatusRepository;
        private final AgentMetricRepository metricRepository;
        private final AuditLogRepository auditLogRepository;
        private final DifyIntegrationService difyIntegrationService;
        private final PrometheusService prometheusService;
        private final PrometheusConfigRepository prometheusConfigRepository;

        // Dedicated thread pool for Prometheus queries to avoid using the common
        // ForkJoinPool
        // which might have limited parallelism (e.g. 1 thread) in some containerized
        // environments.
        // Dedicated thread pool for Prometheus queries with a fixed size to prevent
        // exhaustion
        private final java.util.concurrent.ExecutorService prometheusExecutor = new java.util.concurrent.ThreadPoolExecutor(
                        5, 10, 60L, java.util.concurrent.TimeUnit.SECONDS,
                        new java.util.concurrent.LinkedBlockingQueue<>(50),
                        new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        private Map<String, Object> cachedHardwareStatus;
        private long lastHardwareUpdate = 0;
        private static final long HARDWARE_CACHE_TTL = 10000; // 10 seconds cache

        @jakarta.annotation.PostConstruct
        public void init() {
                if (prometheusConfigRepository.findById("DEFAULT").isEmpty()) {
                        PrometheusConfig defaultConfig = PrometheusConfig.builder()
                                        .id("DEFAULT")
                                        .enabled(false)
                                        .prometheusUrl("")
                                        .build();
                        prometheusConfigRepository.save(defaultConfig);
                }
        }

        @Override
        public Map<String, Object> getGlobalSummary() {
                Map<String, Object> summary = new HashMap<>();

                List<AgentHealthStatus> allAgents = healthStatusRepository.findAll();

                // 统计各状态的Agent数量
                long runningCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.RUNNING)
                                .count();
                long stoppedCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.STOPPED)
                                .count();
                long highLoadCount = allAgents.stream()
                                .filter(a -> a.getStatus() == AgentStatus.HIGH_LOAD)
                                .count();

                summary.put("total_agents", allAgents.size());
                summary.put("online_agents", runningCount + highLoadCount);
                summary.put("stoppedAgents", stoppedCount);
                summary.put("highLoadAgents", highLoadCount);
                summary.put("system_status", highLoadCount > 0 ? "高负载" : "正常");

                // 计算平均健康分数
                double avgHealthScore = allAgents.stream()
                                .mapToInt(AgentHealthStatus::getHealthScore)
                                .average()
                                .orElse(0.0);
                summary.put("averageHealthScore", Math.round(avgHealthScore * 100.0) / 100.0);

                // 获取最近一条审计日志判断 Dify 连接状态 (仅限业务日志)
                LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
                List<com.adlin.orin.modules.audit.entity.AuditLog> todayLogs = auditLogRepository
                                .findBusinessLogsByCreatedAtBetween(startOfDay, LocalDateTime.now());

                // Frontend expects: daily_requests, total_tokens, avg_latency
                summary.put("daily_requests", todayLogs.size());

                int totalTokens = todayLogs.stream()
                                .mapToInt(log -> log.getTotalTokens() != null ? log.getTotalTokens() : 0)
                                .sum();
                summary.put("total_tokens", totalTokens);

                // Calculate average latency
                double avgLatency = todayLogs.stream()
                                .mapToLong(log -> log.getResponseTime() != null ? log.getResponseTime() : 0L)
                                .average()
                                .orElse(0.0);
                summary.put("avg_latency", Math.round(avgLatency) + "ms");

                double totalCost = todayLogs.stream()
                                .mapToDouble(log -> log.getEstimatedCost() != null ? log.getEstimatedCost() : 0.0)
                                .sum();
                summary.put("todayCost", Math.round(totalCost * 10000.0) / 10000.0);

                return summary;
        }

        @Override
        public AgentHealthStatus getAgentStatus(String agentId) {
                return healthStatusRepository.findById(agentId)
                                .orElseThrow(() -> new RuntimeException("Agent not found: " + agentId));
        }

        @Override
        public List<AgentMetric> getAgentMetrics(String agentId, Long startTime, Long endTime, String interval) {
                // 1. 获取持久化的指标数据
                List<AgentMetric> metrics = metricRepository
                                .findByAgentIdAndTimestampBetweenOrderByTimestampAsc(agentId, startTime, endTime);

                // 2. 将 AuditLog 转换为实时指标数据点（如果持久化数据较少）
                LocalDateTime start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
                LocalDateTime end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endTime), ZoneId.systemDefault());

                List<com.adlin.orin.modules.audit.entity.AuditLog> logs = auditLogRepository
                                .findByProviderIdAndCreatedAtBetweenOrderByCreatedAtAsc(agentId, start, end);

                if (!logs.isEmpty()) {
                        List<AgentMetric> logMetrics = logs.stream().map(l -> AgentMetric.builder()
                                        .agentId(agentId)
                                        .timestamp(l.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                                                        .toEpochMilli())
                                        .responseLatency(l.getResponseTime() != null ? l.getResponseTime().intValue()
                                                        : 0)
                                        .tokenCost(l.getTotalTokens())
                                        .cpuUsage(0.0) // Don't fetch real-time physical stats for every historical log
                                        .memoryUsage(0.0)
                                        .build()).collect(Collectors.toList());

                        metrics.addAll(logMetrics);
                }

                // 按照时间排序
                metrics.sort((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()));

                return metrics;
        }

        @Override
        public List<AgentHealthStatus> getAgentList() {
                return healthStatusRepository.findAll();
        }

        @Override
        public void triggerMockDataGeneration() {
                log.info("Triggering mock performance data generation...");
                List<AgentHealthStatus> agents = healthStatusRepository.findAll();
                Random random = new Random();
                long now = System.currentTimeMillis();

                List<AgentMetric> mockData = new ArrayList<>();
                for (AgentHealthStatus agent : agents) {
                        // 为每个代理生成过去 10 分钟内每分钟一个数据点
                        for (int i = 10; i >= 0; i--) {
                                long ts = now - (i * 60 * 1000L);
                                mockData.add(AgentMetric.builder()
                                                .agentId(agent.getAgentId())
                                                .timestamp(ts)
                                                .cpuUsage(10 + random.nextDouble() * 40)
                                                .memoryUsage(128 + random.nextDouble() * 512)
                                                .responseLatency(200 + random.nextInt(1800))
                                                .tokenCost(random.nextInt(1000))
                                                .dailyRequests(10 + random.nextInt(100))
                                                .build());
                        }
                }
                metricRepository.saveAll(mockData);
                log.info("Successfully generated {} mock metric points.", mockData.size());
        }

        @Override
        public boolean testDifyConnection(String endpointUrl, String apiKey) {
                return difyIntegrationService.testConnection(endpointUrl, apiKey);
        }

        @Override
        public Object getDifyApps(String endpointUrl, String apiKey) {
                return difyIntegrationService.getApplications(endpointUrl, apiKey)
                                .orElse(null);
        }

        @Override
        public Map<String, Object> getTokenStats() {
                Map<String, Object> stats = new HashMap<>();
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);

                // Token 统计
                stats.put("daily", orZero(auditLogRepository.sumTotalTokensAfter(startOfDay)));
                stats.put("weekly", orZero(auditLogRepository.sumTotalTokensAfter(startOfWeek)));
                stats.put("monthly", orZero(auditLogRepository.sumTotalTokensAfter(startOfMonth)));
                stats.put("total", orZero(auditLogRepository.sumTotalTokensAll()));

                // 成本统计
                stats.put("daily_cost", orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfDay, now)));
                stats.put("weekly_cost",
                                orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfWeek, now)));
                stats.put("monthly_cost",
                                orZero(auditLogRepository.sumCostByUserIdAndDateRange(null, startOfMonth, now)));
                stats.put("total_cost", orZero(auditLogRepository.sumCostByUserIdAndDateRange(null,
                                LocalDateTime.of(2000, 1, 1, 0, 0), now)));

                return stats;
        }

        private Long orZero(Long val) {
                return val != null ? val : 0L;
        }

        private Double orZero(Double val) {
                return val != null ? val : 0.0;
        }

        @Override
        public List<Map<String, Object>> getTokenTrend(String period) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start;
                String formatPattern;
                ChronoUnit groupingUnit;

                if ("weekly".equals(period)) {
                        start = end.minusWeeks(12); // Past 12 weeks
                        formatPattern = "yyyy-MM-dd"; // 使用周一开始的日期
                        groupingUnit = ChronoUnit.WEEKS;
                } else if ("monthly".equals(period)) {
                        start = end.minusMonths(12); // Past 12 months
                        formatPattern = "yyyy-MM";
                        groupingUnit = ChronoUnit.MONTHS;
                } else {
                        // Default to daily
                        start = end.minusDays(30); // Past 30 days
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.DAYS;
                }

                // 获取时间范围内的所有业务日志 (排除系统日志)
                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                // 在内存中分组统计
                Map<String, Long> groupedData = new TreeMap<>(); // 使用 TreeMap 保持顺序
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);

                // 初始化所有时间点为 0，防止只有数据的日期才显示
                LocalDateTime current = start;
                while (!current.isAfter(end)) {
                        String key = current.format(formatter);
                        groupedData.put(key, 0L);

                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.WEEKS)
                                current = current.plusWeeks(1);
                        else if (groupingUnit == ChronoUnit.MONTHS)
                                current = current.plusMonths(1);
                }

                // 填充实际数据
                for (AuditLog log : logs) {
                        if (log.getTotalTokens() != null) {
                                String key = log.getCreatedAt().format(formatter);
                                if (groupedData.containsKey(key)) {
                                        groupedData.put(key, groupedData.get(key) + log.getTotalTokens());
                                }
                        }
                }

                // 转换为 List
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Long> entry : groupedData.entrySet()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("date", entry.getKey());
                        item.put("tokens", entry.getValue());
                        result.add(item);
                }

                return result;
        }

        @Override
        public Page<AuditLog> getTokenHistory(int page, int size, Long startDate, Long endDate) {
                Pageable pageable = PageRequest.of(page, size);
                LocalDateTime start;
                LocalDateTime end;

                if (startDate != null && endDate != null) {
                        start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.systemDefault());
                        end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault());
                } else {
                        // 默认最近 30 天
                        end = LocalDateTime.now();
                        start = end.minusDays(30);
                }

                return auditLogRepository.findBusinessLogsByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
        }

        @Override
        public List<Map<String, Object>> getTokenDistribution(Long startDate, Long endDate) {
                return getDistributionData(startDate, endDate, true);
        }

        @Override
        public List<Map<String, Object>> getCostDistribution(Long startDate, Long endDate) {
                return getDistributionData(startDate, endDate, false);
        }

        private List<Map<String, Object>> getDistributionData(Long startDate, Long endDate, boolean isToken) {
                LocalDateTime start;
                LocalDateTime end;

                if (startDate != null && endDate != null) {
                        start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startDate), ZoneId.systemDefault());
                        end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endDate), ZoneId.systemDefault());
                } else {
                        end = LocalDateTime.now();
                        start = end.minusDays(30);
                }

                List<Object[]> rawData = isToken
                                ? auditLogRepository.sumTokensByProviderIdBetween(start, end)
                                : auditLogRepository.sumCostByProviderIdBetween(start, end);

                Map<String, String> agentNames = healthStatusRepository.findAll().stream()
                                .collect(Collectors.toMap(AgentHealthStatus::getAgentId,
                                                AgentHealthStatus::getAgentName, (a, b) -> a));

                List<Map<String, Object>> result = new ArrayList<>();
                for (Object[] row : rawData) {
                        String providerId = (String) row[0];
                        Number value = (Number) row[1];

                        Map<String, Object> item = new HashMap<>();
                        String name = agentNames.getOrDefault(providerId, "Unknown Agent ("
                                        + (providerId != null ? (providerId.length() > 8 ? providerId.substring(0, 8)
                                                        : providerId) : "null")
                                        + ")");

                        item.put("name", name);
                        item.put("value", value);
                        item.put("agentId", providerId);

                        result.add(item);
                }
                return result;
        }

        @Override
        public Map<String, Object> getLatencyStats() {
                Map<String, Object> stats = new HashMap<>();
                LocalDateTime now = LocalDateTime.now();

                // Today Avg
                LocalDateTime startOfDay = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
                Double daily = auditLogRepository.avgResponseTimeAfter(startOfDay);
                stats.put("daily", daily != null ? Math.round(daily) : 0L);

                // Week Avg
                LocalDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
                Double weekly = auditLogRepository.avgResponseTimeAfter(startOfWeek);
                stats.put("weekly", weekly != null ? Math.round(weekly) : 0L);

                // Month Avg
                LocalDateTime startOfMonth = startOfDay.withDayOfMonth(1);
                Double monthly = auditLogRepository.avgResponseTimeAfter(startOfMonth);
                stats.put("monthly", monthly != null ? Math.round(monthly) : 0L);

                // Max
                Long max = auditLogRepository.maxResponseTimeAll();
                stats.put("max", max != null ? max : 0L);

                return stats;
        }

        @Override
        public List<Map<String, Object>> getLatencyTrend(String period) {
                LocalDateTime end = LocalDateTime.now();
                LocalDateTime start;
                String formatPattern;
                ChronoUnit groupingUnit;

                if ("weekly".equals(period)) {
                        start = end.minusWeeks(12);
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.WEEKS;
                } else if ("monthly".equals(period)) {
                        start = end.minusMonths(12);
                        formatPattern = "yyyy-MM";
                        groupingUnit = ChronoUnit.MONTHS;
                } else {
                        start = end.minusDays(30);
                        formatPattern = "yyyy-MM-dd";
                        groupingUnit = ChronoUnit.DAYS;
                }

                List<AuditLog> logs = auditLogRepository.findBusinessLogsByCreatedAtBetween(start, end);

                Map<String, Long> sumMap = new TreeMap<>();
                Map<String, Integer> countMap = new HashMap<>();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(formatPattern);

                // Initialize
                LocalDateTime current = start;
                while (!current.isAfter(end)) {
                        String key = current.format(formatter);
                        sumMap.put(key, 0L);
                        countMap.put(key, 0);
                        if (groupingUnit == ChronoUnit.DAYS)
                                current = current.plusDays(1);
                        else if (groupingUnit == ChronoUnit.WEEKS)
                                current = current.plusWeeks(1);
                        else if (groupingUnit == ChronoUnit.MONTHS)
                                current = current.plusMonths(1);
                }

                // Aggregate
                for (AuditLog log : logs) {
                        if (log.getResponseTime() != null) {
                                String key = log.getCreatedAt().format(formatter);
                                if (sumMap.containsKey(key)) {
                                        sumMap.put(key, sumMap.get(key) + log.getResponseTime());
                                        countMap.put(key, countMap.get(key) + 1);
                                }
                        }
                }

                // Result
                List<Map<String, Object>> result = new ArrayList<>();
                for (Map.Entry<String, Long> entry : sumMap.entrySet()) {
                        String key = entry.getKey();
                        Integer count = countMap.get(key);
                        Map<String, Object> item = new HashMap<>();
                        item.put("date", key);
                        item.put("latency", count > 0 ? entry.getValue() / count : 0);
                        result.add(item);
                }

                return result;
        }

        @Override
        public Page<AuditLog> getLatencyHistory(int page, int size, Long startDate, Long endDate) {
                return getTokenHistory(page, size, startDate, endDate);
        }

        @Override
        public Map<String, Object> testPrometheusConnection() {
                PrometheusConfig config = getPrometheusConfig();
                Map<String, Object> status = new HashMap<>();

                if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                        String url = config.getPrometheusUrl();
                        status.put("probedUrl", url); // Base URL from config

                        try {
                                log.info("Testing Prometheus Connection: {}", url);
                                // Use supplyAsync + get to handle timeouts safely
                                CompletableFuture<Map<String, String>> probeTask = CompletableFuture
                                                .supplyAsync(() -> prometheusService.probe(url), prometheusExecutor);

                                // 5 seconds timeout for test is plenty since we fixed proxy
                                Map<String, String> probeResult = probeTask.get(5,
                                                java.util.concurrent.TimeUnit.SECONDS);

                                status.put("online", true);
                                status.put("probeUrl", probeResult.get("targetUrl"));
                                status.put("probeResponse", probeResult.get("response"));
                                status.put("message", "Connection Successful");
                        } catch (Exception e) {
                                status.put("online", false);
                                String errorMsg = e.getMessage();
                                if (e.getCause() != null)
                                        errorMsg = e.getCause().getMessage();
                                status.put("error", errorMsg);
                        }
                } else {
                        status.put("online", false);
                        status.put("error", "Prometheus is disabled in settings");
                }
                return status;
        }

        @Override
        public Map<String, Object> getServerHardware() {
                long now = System.currentTimeMillis();
                if (cachedHardwareStatus != null && (now - lastHardwareUpdate) < HARDWARE_CACHE_TTL) {
                        return cachedHardwareStatus;
                }

                PrometheusConfig config = getPrometheusConfig();
                Map<String, Object> status = new HashMap<>();

                if (config != null && Boolean.TRUE.equals(config.getEnabled())) {
                        String url = config.getPrometheusUrl();
                        status.put("probedUrl", url);

                        try {
                                log.info("Probing Prometheus URL: {}", url);
                                // First probe if Prometheus is actually reachable with a timeout (25s > 20s
                                // read timeout for metadata)
                                CompletableFuture<Map<String, String>> probeTask = CompletableFuture
                                                .supplyAsync(() -> prometheusService.probe(url), prometheusExecutor);
                                Map<String, String> probeResult = probeTask.get(25,
                                                java.util.concurrent.TimeUnit.SECONDS);

                                status.put("online", true);
                                status.put("probeUrl", probeResult.get("targetUrl"));
                                status.put("probeResponse", probeResult.get("response"));

                                CompletableFuture<Double> cpuFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> memFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getMemoryUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> diskFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getDiskUsage(url),
                                                                prometheusExecutor);
                                CompletableFuture<Integer> coresFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuCores(url),
                                                                prometheusExecutor);
                                CompletableFuture<Long> totalMemFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getTotalMemory(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> netInFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getNetworkReceiveRate(url),
                                                                prometheusExecutor);
                                CompletableFuture<Double> netOutFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getNetworkTransmitRate(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> osFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getOsName(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> diskTotalFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getTotalDiskSpace(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> cpuModelFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getCpuModel(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> gpuUsageFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuUsage(url),
                                                                prometheusExecutor);

                                CompletableFuture<Double> gpuMemFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuMemoryUsage(url),
                                                                prometheusExecutor);

                                CompletableFuture<String> gpuModelFuture = CompletableFuture
                                                .supplyAsync(() -> prometheusService.getGpuModel(url),
                                                                prometheusExecutor);

                                // Wait for all with a strict timeout (5s)
                                CompletableFuture.allOf(cpuFuture, memFuture, diskFuture, coresFuture, totalMemFuture,
                                                netInFuture, netOutFuture, osFuture, diskTotalFuture, cpuModelFuture,
                                                gpuUsageFuture, gpuMemFuture, gpuModelFuture)
                                                .get(5, java.util.concurrent.TimeUnit.SECONDS);

                                // Safe retrieval using getNow to avoid exceptions if any failed
                                status.put("cpuUsage", cpuFuture.getNow(0.0));
                                status.put("memoryUsage", memFuture.getNow(0.0));
                                status.put("diskUsage", diskFuture.getNow(0.0));
                                status.put("cpuCores", coresFuture.getNow(0));
                                status.put("os", osFuture.getNow("Unknown"));
                                status.put("cpuModel", cpuModelFuture.getNow("Unknown"));

                                // GPU
                                status.put("gpuUsage", gpuUsageFuture.getNow(0.0));
                                status.put("gpuMemoryUsage", gpuMemFuture.getNow(0.0));
                                status.put("gpuModel", gpuModelFuture.getNow("Unknown"));

                                // Format Memory
                                long totalMemBytes = totalMemFuture.getNow(0L);
                                status.put("memoryTotal", formatBytes(totalMemBytes));

                                // Calculate Used Memory
                                double memPct = memFuture.getNow(0.0);
                                long usedMemBytes = (long) (totalMemBytes * (memPct / 100.0));
                                status.put("memoryUsed", formatBytes(usedMemBytes));

                                // Format Disk
                                long totalDiskBytes = diskTotalFuture.getNow(0.0).longValue();
                                status.put("diskTotal", formatBytes(totalDiskBytes));
                                double diskPct = diskFuture.getNow(0.0);
                                long usedDiskBytes = (long) (totalDiskBytes * (diskPct / 100.0));
                                status.put("diskUsed", formatBytes(usedDiskBytes));

                                // Format Network
                                status.put("networkDownload", formatSpeed(netInFuture.getNow(0.0)));
                                status.put("networkUpload", formatSpeed(netOutFuture.getNow(0.0)));

                        } catch (java.util.concurrent.TimeoutException e) {
                                log.warn("Prometheus connection timed out for url: {}", url);
                                // If we already probed successfully, don't flap the online status just because
                                // metrics were slow. And don't show error message.
                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("online", false);
                                        status.put("error", "连接超时 (响应过慢，请检查网络或端点)");
                                        setEmptyStatus(status);
                                }
                        } catch (Exception e) {
                                log.error("Error fetching Prometheus metrics: {}", e.getMessage(), e);

                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("online", false);
                                        setEmptyStatus(status);
                                }

                                String errorMsg = e.getMessage();
                                // Unwrap cause if available
                                if (e.getCause() != null && e.getCause().getMessage() != null) {
                                        errorMsg = e.getCause().getMessage();
                                }
                                if (errorMsg == null)
                                        errorMsg = e.getClass().getSimpleName();

                                // Only report error if we failed the initial probe
                                if (!Boolean.TRUE.equals(status.get("online"))) {
                                        status.put("error", errorMsg);
                                }

                                // Friendly error messages
                                if (errorMsg.contains("Connect timed out")) {
                                        errorMsg = "连接超时 (Connect Timed Out)";
                                } else if (errorMsg.contains("Read timed out")) {
                                        errorMsg = "读取超时 (数据量过大或服务端响应慢)";
                                } else if (errorMsg.contains("Connection refused")) {
                                        errorMsg = "连接被拒绝 (端口未开放或服务未启动)";
                                } else if (errorMsg.contains("host")) { // Unknown host
                                        errorMsg = "无法解析主机地址";
                                }

                                status.put("error", "连接失败: " + errorMsg);
                                setEmptyStatus(status);
                        }

                        cachedHardwareStatus = status;
                        lastHardwareUpdate = now;
                } else {
                        status.put("online", false);
                        status.put("error", "Prometheus monitoring is disabled or not configured.");
                }
                return status;
        }

        private void setEmptyStatus(Map<String, Object> status) {
                status.put("cpuUsage", 0.0);
                status.put("memoryUsage", 0.0);
                status.put("diskUsage", 0.0);
                status.put("cpuCores", 0);
                status.put("memoryTotal", "N/A");
                status.put("memoryUsed", "N/A");
                status.put("diskTotal", "N/A");
                status.put("diskUsed", "N/A");
                status.put("networkDownload", "0 KB/s");
                status.put("networkUpload", "0 KB/s");
                status.put("cpuModel", "Unknown");
                status.put("gpuModel", "N/A");
        }

        private String formatBytes(long bytes) {
                if (bytes <= 0)
                        return "0 B";
                String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
                int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
                return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        private String formatSpeed(Double bytesPerSec) {
                if (bytesPerSec == null || bytesPerSec < 0)
                        return "0 B/s";
                String[] units = new String[] { "B/s", "KB/s", "MB/s", "GB/s" };
                int digitGroups = (int) (Math.log10(bytesPerSec) / Math.log10(1024));
                if (digitGroups < 0)
                        digitGroups = 0;
                if (digitGroups >= units.length)
                        digitGroups = units.length - 1;
                return String.format("%.1f %s", bytesPerSec / Math.pow(1024, digitGroups), units[digitGroups]);
        }

        @Override
        @org.springframework.transaction.annotation.Transactional
        public void updatePrometheusConfig(PrometheusConfig config) {
                config.setId("DEFAULT");
                log.info("Updating Prometheus config: enabled={}, url={}", config.getEnabled(),
                                config.getPrometheusUrl());
                prometheusConfigRepository.save(config);

                // Invalidate hardware cache so changes take effect immediately
                this.cachedHardwareStatus = null;
        }

        @Override
        public PrometheusConfig getPrometheusConfig() {
                return prometheusConfigRepository.findById("DEFAULT").orElse(null);
        }

}
