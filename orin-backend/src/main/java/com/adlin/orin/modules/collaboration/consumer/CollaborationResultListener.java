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

    public int getPendingCallbackCount() {
        return callbacks.size();
    }

    /**
     * 监听结果队列，处理 AI Engine 回写的执行结果
     */
    @RabbitListener(
            queues = "${orin.collaboration.result.queue:collaboration-task-result-queue}",
            containerFactory = "collabRabbitListenerContainerFactory"
    )
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

            // 更新 subtask 状态（幂等：重复回执时忽略 COMPLETED->COMPLETED）
            safeUpdateSubtaskStatus(
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
                    buildBranchPayload(result)
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

                // 关键修复：重试期间必须保留同一个回调 future，不能 cancel。
                // 否则上游 executeSubtask 会收到 CancellationException 并立刻触发降级。
                if (!future.isDone()) {
                    callbacks.put(callbackKey, future);
                }

                // 发送重试消息（需要构建 CollabTaskMessage，这里简化处理）
                scheduleRetry(result);
                return;
            } else {
                // 达到最大重试次数，标记失败
                log.error("Subtask failed permanently: packageId={}, subTaskId={}, after {} attempts",
                        result.getPackageId(), result.getSubTaskId(), currentAttempt);

                // 失败也写入 branch_result，避免上游轮询长时间超时。
                redisService.writeBranchResultAndIncrement(
                        result.getPackageId(),
                        result.getSubTaskId(),
                        Map.of(
                                "status", result.getStatus(),
                                "errorMessage", errorMsg,
                                "attempt", currentAttempt
                        )
                );

                future.completeExceptionally(new RuntimeException(errorMsg));

                // 更新 subtask 状态为失败（幂等）
                safeUpdateSubtaskStatus(
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
            if ("COMPLETED".equals(result.getStatus())) {
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("result", result.getResult());
                payload.put("status", result.getStatus());
                payload.put("attempt", result.getAttempt() != null ? result.getAttempt() : 0);
                payload.put("duplicateCallback", true);
                appendToolTrace(result, payload);
                redisService.writeBranchResultAndIncrement(
                        result.getPackageId(),
                        result.getSubTaskId(),
                        payload
                );
            } else {
                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("status", result.getStatus() != null ? result.getStatus() : "FAILED");
                payload.put("errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "Task failed");
                payload.put("attempt", result.getAttempt() != null ? result.getAttempt() : 0);
                payload.put("duplicateCallback", true);
                redisService.writeBranchResultAndIncrement(
                        result.getPackageId(),
                        result.getSubTaskId(),
                        payload
                );
            }
            safeUpdateSubtaskStatus(
                    result.getPackageId(),
                    result.getSubTaskId(),
                    normalizeSubtaskStatus(result.getStatus()),
                    result.getResult(),
                    result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("Failed to process result without callback: {}", result.getPackageId(), e);
        }
    }

    private Map<String, Object> buildBranchPayload(CollabTaskResult result) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("result", result.getResult());
        payload.put("status", result.getStatus());
        payload.put("attempt", result.getAttempt() != null ? result.getAttempt() : 0);
        appendToolTrace(result, payload);
        return payload;
    }

    private void appendToolTrace(CollabTaskResult result, Map<String, Object> payload) {
        if (result.getMetadata() != null && result.getMetadata().get("toolTrace") != null) {
            payload.put("toolTrace", result.getMetadata().get("toolTrace"));
        }
    }

    @RabbitListener(
            queues = "${orin.collaboration.dlq.name:collaboration-task-dlq}",
            containerFactory = "collabRabbitListenerContainerFactory"
    )
    public void handleDeadLetter(CollabTaskMessage message) {
        String packageId = message.getPackageId();
        String subTaskId = message.getSubTaskId();
        String errorMessage = "Message moved to collaboration DLQ";
        log.error("Collaboration task dead-lettered: packageId={}, subTaskId={}, attempt={}",
                packageId, subTaskId, message.getAttempt());

        redisService.writeBranchResultAndIncrement(
                packageId,
                subTaskId,
                Map.of(
                        "status", "DEAD_LETTER",
                        "errorMessage", errorMessage,
                        "attempt", message.getAttempt() != null ? message.getAttempt() : 0,
                        "traceId", message.getTraceId() != null ? message.getTraceId() : ""
                )
        );
        safeUpdateSubtaskStatus(packageId, subTaskId, "FAILED", null, errorMessage);
        recordMetrics(CollabTaskResult.builder()
                .packageId(packageId)
                .subTaskId(subTaskId)
                .traceId(message.getTraceId())
                .attempt(message.getAttempt())
                .status("DEAD_LETTER")
                .errorMessage(errorMessage)
                .metadata(Map.of("expectedRole", message.getExpectedRole() != null ? message.getExpectedRole() : "AGENT"))
                .build());
    }

    private void safeUpdateSubtaskStatus(String packageId,
                                         String subTaskId,
                                         String targetStatus,
                                         String result,
                                         String errorMessage) {
        try {
            orchestrator.updateSubtaskStatus(packageId, subTaskId, targetStatus, result, errorMessage);
        } catch (IllegalStateException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Invalid status transition")) {
                log.debug("Ignore duplicate subtask status transition: packageId={}, subTaskId={}, target={}, msg={}",
                        packageId, subTaskId, targetStatus, msg);
                return;
            }
            throw e;
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
            if (maxRetries instanceof String value) {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                }
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

    private String normalizeSubtaskStatus(String status) {
        if ("COMPLETED".equals(status)) {
            return "COMPLETED";
        }
        return "FAILED";
    }
}
