package com.adlin.orin.modules.collaboration.consumer;

import com.adlin.orin.modules.collaboration.dto.CollabTaskMessage;
import com.adlin.orin.modules.collaboration.dto.CollabTaskResult;
import com.adlin.orin.modules.collaboration.event.CollaborationEventBus;
import com.adlin.orin.modules.collaboration.metrics.CollaborationMetricsService;
import com.adlin.orin.modules.collaboration.producer.CollaborationMQProducer;
import com.adlin.orin.modules.collaboration.service.CollaborationOrchestrator;
import com.adlin.orin.modules.collaboration.service.CollaborationRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 协作任务结果监听器 - 接收 AI Engine 执行结果，处理重试逻辑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollaborationResultListener {

    private final Map<String, CompletableFuture<String>> callbacks = new java.util.concurrent.ConcurrentHashMap<>();
    private final CollaborationOrchestrator orchestrator;
    private final CollaborationEventBus eventBus;
    private final CollaborationMetricsService metricsService;
    private final CollaborationMQProducer mqProducer;
    private final CollaborationRedisService redisService;

    /**
     * 注册回调
     */
    public void registerCallback(String correlationKey, CompletableFuture<String> future) {
        callbacks.put(correlationKey, future);
        log.debug("Registered callback for: {}", correlationKey);
    }

    /**
     * 取消注册
     */
    public void unregisterCallback(String correlationKey) {
        callbacks.remove(correlationKey);
        log.debug("Unregistered callback for: {}", correlationKey);
    }

    /**
     * 监听结果队列，处理 AI Engine 回写的执行结果
     */
    @RabbitListener(queues = "${orin.collaboration.result.queue:collaboration-task-result-queue}")
    public void handleResult(CollabTaskResult result) {
        String callbackKey = result.getPackageId() + ":" + result.getSubTaskId();
        CompletableFuture<String> future = callbacks.remove(callbackKey);

        log.info("Received collab task result: packageId={}, subTaskId={}, status={}",
                result.getPackageId(), result.getSubTaskId(), result.getStatus());

        if (future == null) {
            log.warn("No callback found for result: {}, processing anyway", callbackKey);
            // 即使没有 callback，也更新状态
            processResult(result);
            return;
        }

        if ("COMPLETED".equals(result.getStatus())) {
            // 成功完成
            future.complete(result.getResult());

            // 更新 subtask 状态
            orchestrator.updateSubtaskStatus(
                    result.getPackageId(),
                    result.getSubTaskId(),
                    "COMPLETED",
                    result.getResult(),
                    null
            );

            // 并行 fan-in：原子写入分支结果并递增计数
            long branchCounter = redisService.writeBranchResultAndIncrement(
                    result.getPackageId(),
                    result.getSubTaskId(),
                    Map.of(
                            "result", result.getResult(),
                            "status", result.getStatus(),
                            "attempt", result.getAttempt() != null ? result.getAttempt() : 0
                    )
            );

            // 记录 metrics
            recordMetrics(result);

            log.info("Subtask completed via MQ: packageId={}, subTaskId={}",
                    result.getPackageId(), result.getSubTaskId());
            log.debug("Branch counter updated: packageId={}, counter={}", result.getPackageId(), branchCounter);

        } else {
            // 失败或超时 - 检查是否需要重试
            String errorMsg = result.getErrorMessage() != null
                    ? result.getErrorMessage()
                    : "Task failed with status: " + result.getStatus();

            int currentAttempt = result.getAttempt() != null ? result.getAttempt() : 0;
            int maxRetries = getMaxRetriesFromContext(result);

            if (currentAttempt < maxRetries) {
                // 需要重试
                log.warn("Subtask failed, scheduling retry: packageId={}, subTaskId={}, attempt={}/{}, error={}",
                        result.getPackageId(), result.getSubTaskId(), currentAttempt + 1, maxRetries, errorMsg);

                // 取消原 callback
                callbacks.remove(callbackKey);
                future.cancel(false);

                // 发送重试消息（需要构建 CollabTaskMessage，这里简化处理）
                scheduleRetry(result);
            } else {
                // 达到最大重试次数，标记失败
                log.error("Subtask failed permanently: packageId={}, subTaskId={}, after {} attempts",
                        result.getPackageId(), result.getSubTaskId(), currentAttempt);

                future.completeExceptionally(new RuntimeException(errorMsg));

                // 更新 subtask 状态为失败
                orchestrator.updateSubtaskStatus(
                        result.getPackageId(),
                        result.getSubTaskId(),
                        "FAILED",
                        null,
                        errorMsg
                );

                // 记录失败 metrics
                recordMetrics(result);
            }
        }
    }

    /**
     * 处理结果（无 callback 的情况）
     */
    private void processResult(CollabTaskResult result) {
        try {
            orchestrator.updateSubtaskStatus(
                    result.getPackageId(),
                    result.getSubTaskId(),
                    result.getStatus(),
                    result.getResult(),
                    result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("Failed to process result without callback: {}", result.getPackageId(), e);
        }
    }

    /**
     * 从结果的 metadata 中获取最大重试次数
     */
    private int getMaxRetriesFromContext(CollabTaskResult result) {
        if (result.getMetadata() != null && result.getMetadata().containsKey("maxRetries")) {
            Object maxRetries = result.getMetadata().get("maxRetries");
            if (maxRetries instanceof Number) {
                return ((Number) maxRetries).intValue();
            }
        }
        return 3; // 默认最大重试次数
    }

    /**
     * 调度重试 - 从 Redis 获取原始任务信息并发送重试消息
     */
    private void scheduleRetry(CollabTaskResult result) {
        try {
            String packageId = result.getPackageId();
            String subTaskId = result.getSubTaskId();

            // 从 Redis 获取原始任务上下文。按 subTaskId 隔离，避免并行任务重试串线。
            redisService.getContextField(packageId, buildPendingTaskField(subTaskId))
                    .ifPresentOrElse(
                            pendingTask -> {
                                if (pendingTask instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> taskData = (Map<String, Object>) pendingTask;
                                    // 构建重试消息
                                    CollabTaskMessage retryMessage = CollabTaskMessage.builder()
                                            .packageId(packageId)
                                            .sessionId(result.getSessionId())
                                            .turnId(result.getTurnId())
                                            .subTaskId(subTaskId)
                                            .traceId(result.getTraceId())
                                            .attempt((result.getAttempt() != null ? result.getAttempt() : 0) + 1)
                                            .stage(result.getStage())
                                            .selectionMeta(result.getSelectionMeta())
                                            .collaborationMode((String) taskData.getOrDefault("collaborationMode", "PARALLEL"))
                                            .expectedRole((String) taskData.getOrDefault("expectedRole", "SPECIALIST"))
                                            .description((String) taskData.get("description"))
                                            .inputData((String) taskData.get("inputData"))
                                            .maxRetries(getMaxRetriesFromContext(result))
                                            .timeoutMillis(300000L)
                                            .executionStrategy((String) taskData.getOrDefault("executionStrategy", "AGENT"))
                                            .replyTo("collaboration-task-result-queue")
                                            .correlationId(packageId + ":" + subTaskId)
                                            .contextSnapshot((Map<String, Object>) taskData.get("contextSnapshot"))
                                            .enqueuedAt(System.currentTimeMillis())
                                            .retryInitialInterval(1000L)
                                            .retryMultiplier(2.0)
                                            .retryMaxInterval(30000L)
                                            .delayedRetry(true)
                                            .build();

                                    mqProducer.sendToRetry(retryMessage);
                                    log.info("Retry scheduled: packageId={}, subTaskId={}, attempt={}",
                                            packageId, subTaskId, retryMessage.getAttempt());
                                }
                            },
                            () -> log.warn("No pending task found in Redis for retry: packageId={}, subTaskId={}",
                                    packageId, subTaskId)
                    );
        } catch (Exception e) {
            log.error("Failed to schedule retry: packageId={}, subTaskId={}",
                    result.getPackageId(), result.getSubTaskId(), e);
        }
    }

    /**
     * 记录指标
     */
    private void recordMetrics(CollabTaskResult result) {
        try {
            if (metricsService != null) {
                String role = result.getMetadata() != null
                        ? (String) result.getMetadata().getOrDefault("expectedRole", "AGENT")
                        : "AGENT";
                metricsService.recordSubtask(
                        result.getPackageId(),
                        result.getSubTaskId(),
                        role,
                        result.getLatencyMs() != null ? result.getLatencyMs() : 0,
                        result.getStatus()
                );
            }
        } catch (Exception e) {
            log.warn("Failed to record metrics: {}", e.getMessage());
        }
    }

    private String buildPendingTaskField(String subTaskId) {
        return "pending_task:" + subTaskId;
    }
}
