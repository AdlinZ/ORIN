package com.adlin.orin.modules.collaboration.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 协作指标服务 - 追踪 Agent 级指标与成本治理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationMetricsService {

    private final MeterRegistry meterRegistry;

    // Agent 指标缓存
    private final Map<String, AgentMetrics> agentMetrics = new ConcurrentHashMap<>();

    /**
     * Agent 指标数据类
     */
    public static class AgentMetrics {
        private long totalTokens = 0;
        private long inputTokens = 0;
        private long outputTokens = 0;
        private long totalLatencyMs = 0;
        private int totalRequests = 0;
        private int successCount = 0;
        private int failureCount = 0;
        private int retryCount = 0;
        private double totalCost = 0.0;

        // Getters
        public long getTotalTokens() { return totalTokens; }
        public long getInputTokens() { return inputTokens; }
        public long getOutputTokens() { return outputTokens; }
        public long getTotalLatencyMs() { return totalLatencyMs; }
        public int getTotalRequests() { return totalRequests; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public int getRetryCount() { return retryCount; }
        public double getTotalCost() { return totalCost; }

        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successCount / totalRequests : 0.0;
        }

        public double getAverageLatencyMs() {
            return totalRequests > 0 ? (double) totalLatencyMs / totalRequests : 0.0;
        }
    }

    /**
     * 记录 LLM 调用
     */
    public void recordLlmCall(String agentId, long inputTokens, long outputTokens,
                              long latencyMs, boolean success, double cost) {

        // 更新内存缓存
        AgentMetrics metrics = agentMetrics.computeIfAbsent(agentId, k -> new AgentMetrics());
        metrics.inputTokens += inputTokens;
        metrics.outputTokens += outputTokens;
        metrics.totalTokens += (inputTokens + outputTokens);
        metrics.totalLatencyMs += latencyMs;
        metrics.totalRequests++;
        metrics.totalCost += cost;

        if (success) {
            metrics.successCount++;
        } else {
            metrics.failureCount++;
        }

        // 更新 Micrometer 指标
        Counter tokensCounter = Counter.builder("collaboration.llm.tokens")
                .tag("agent_id", agentId)
                .register(meterRegistry);
        tokensCounter.increment(inputTokens + outputTokens);

        Timer timer = Timer.builder("collaboration.llm.latency")
                .tag("agent_id", agentId)
                .register(meterRegistry);
        timer.record(latencyMs, TimeUnit.MILLISECONDS);

        if (success) {
            Counter.builder("collaboration.llm.requests")
                    .tag("agent_id", agentId)
                    .register(meterRegistry)
                    .increment();
        } else {
            Counter.builder("collaboration.llm.errors")
                    .tag("agent_id", agentId)
                    .register(meterRegistry)
                    .increment();
        }

        log.debug("Recorded LLM call for agent {}: {} tokens, {}ms, ${}",
                agentId, inputTokens + outputTokens, latencyMs, cost);
    }

    /**
     * 记录重试
     */
    public void recordRetry(String agentId) {
        AgentMetrics metrics = agentMetrics.get(agentId);
        if (metrics != null) {
            metrics.retryCount++;
        }

        Counter.builder("collaboration.agent.retries")
                .tag("agent_id", agentId)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录工具调用
     */
    public void recordToolCall(String agentId, String toolName, long durationMs, boolean success) {
        Counter.builder("collaboration.tools.calls")
                .tag("agent_id", agentId)
                .tag("tool", toolName)
                .register(meterRegistry)
                .increment();

        Timer.builder("collaboration.tools.latency")
                .tag("agent_id", agentId)
                .tag("tool", toolName)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录子任务执行
     */
    public void recordSubtask(String packageId, String subTaskId, String role,
                              long durationMs, String status) {
        Counter.builder("collaboration.subtasks.total")
                .tag("package_id", packageId)
                .tag("status", status)
                .tag("role", role != null ? role : "unknown")
                .register(meterRegistry)
                .increment();

        Timer.builder("collaboration.subtasks.duration")
                .tag("package_id", packageId)
                .tag("role", role != null ? role : "unknown")
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 获取 Agent 指标
     */
    public AgentMetrics getAgentMetrics(String agentId) {
        return agentMetrics.getOrDefault(agentId, new AgentMetrics());
    }

    /**
     * 获取所有 Agent 指标
     */
    public Map<String, AgentMetrics> getAllAgentMetrics() {
        return new ConcurrentHashMap<>(agentMetrics);
    }

    /**
     * 检查是否超出预算
     */
    public boolean isOverBudget(String agentId, double dailyBudgetLimit) {
        AgentMetrics metrics = agentMetrics.get(agentId);
        if (metrics == null) {
            return false;
        }
        return metrics.totalCost > dailyBudgetLimit;
    }

    /**
     * 检查是否超出 token 限制
     */
    public boolean isOverTokenLimit(String agentId, long tokenLimit) {
        AgentMetrics metrics = agentMetrics.get(agentId);
        if (metrics == null) {
            return false;
        }
        return metrics.totalTokens > tokenLimit;
    }

    /**
     * 检查是否超出延迟阈值
     */
    public boolean isHighLatency(String agentId, long latencyThresholdMs) {
        AgentMetrics metrics = agentMetrics.get(agentId);
        if (metrics == null || metrics.totalRequests == 0) {
            return false;
        }
        return metrics.getAverageLatencyMs() > latencyThresholdMs;
    }

    /**
     * 清除 Agent 指标（用于重置或测试）
     */
    public void clearAgentMetrics(String agentId) {
        agentMetrics.remove(agentId);
    }

    /**
     * 清除所有指标
     */
    public void clearAllMetrics() {
        agentMetrics.clear();
    }

    /**
     * 获取降级建议
     */
    public String getDegradationSuggestion(String agentId, double budgetThreshold,
                                            long latencyThresholdMs) {
        AgentMetrics metrics = agentMetrics.get(agentId);
        if (metrics == null) {
            return "No metrics available";
        }

        StringBuilder suggestion = new StringBuilder();

        if (metrics.totalCost > budgetThreshold) {
            suggestion.append("Cost exceeded ($").append(String.format("%.2f", metrics.totalCost))
                    .append(" > $").append(String.format("%.2f", budgetThreshold)).append("). ");
            suggestion.append("建议降级到轻量模型或减少并发。");
        }

        if (metrics.getAverageLatencyMs() > latencyThresholdMs) {
            if (suggestion.length() > 0) {
                suggestion.append("\n");
            }
            suggestion.append("延迟过高 (").append(String.format("%.0f", metrics.getAverageLatencyMs()))
                    .append("ms > ").append(latencyThresholdMs).append("ms). ");
            suggestion.append("建议优化 prompt 或使用更快模型。");
        }

        if (metrics.getSuccessRate() < 0.8) {
            if (suggestion.length() > 0) {
                suggestion.append("\n");
            }
            suggestion.append("成功率低 (").append(String.format("%.1f", metrics.getSuccessRate() * 100))
                    .append("% < 80%). ");
            suggestion.append("建议增加重试或检查 agent 配置。");
        }

        return suggestion.length() > 0 ? suggestion.toString() : "指标正常";
    }
}