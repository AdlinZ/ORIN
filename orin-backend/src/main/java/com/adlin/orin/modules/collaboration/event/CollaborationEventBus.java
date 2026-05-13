package com.adlin.orin.modules.collaboration.event;

import com.adlin.orin.modules.collaboration.entity.CollabEventLogEntity;
import com.adlin.orin.modules.collaboration.repository.CollabEventLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协作事件总线 - 负责发布和订阅协作事件
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollaborationEventBus {

    private final SimpMessagingTemplate messagingTemplate;
    private final CollabEventLogRepository eventLogRepository;
    private final ObjectMapper objectMapper;

    // 内存事件处理器注册表
    private final Map<String, EventHandler> handlers = new ConcurrentHashMap<>();

    /**
     * 事件处理器接口
     */
    public interface EventHandler {
        void handle(CollaborationEvent event);
    }

    /**
     * 注册事件处理器
     */
    public void registerHandler(String eventType, EventHandler handler) {
        handlers.put(eventType, handler);
        log.info("Registered event handler for: {}", eventType);
    }

    /**
     * 取消注册
     */
    public void unregisterHandler(String eventType) {
        handlers.remove(eventType);
        log.info("Unregistered event handler for: {}", eventType);
    }

    /**
     * 发布事件
     */
    public void publish(CollaborationEvent event) {
        // 设置事件 ID 和时间
        if (event.getEventId() == null) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }

        // 记录到数据库
        saveEventLog(event);

        // 触发内存处理器
        triggerHandlers(event);

        // 通过 WebSocket 推送到前端
        pushToFrontend(event);

        log.debug("Published collaboration event: type={}, packageId={}", event.getEventType(), event.getPackageId());
    }

    /**
     * 发布任务包创建事件
     */
    public void publishPackageCreated(String packageId, String intent, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.PACKAGE_CREATED)
                .packageId(packageId)
                .eventData(Map.of("intent", intent))
                .traceId(traceId)
                .source("orchestrator")
                .build();
        publish(event);
    }

    /**
     * 发布任务包分解完成事件
     */
    public void publishPackageDecomposed(String packageId, int subtaskCount, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.PACKAGE_DECOMPOSED)
                .packageId(packageId)
                .eventData(Map.of("subtaskCount", subtaskCount))
                .traceId(traceId)
                .source("orchestrator")
                .build();
        publish(event);
    }

    /**
     * 发布子任务分配事件
     */
    public void publishSubtaskAssigned(String packageId, String subTaskId, String agentId, String roleType, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.SUBTASK_ASSIGNED)
                .packageId(packageId)
                .subTaskId(subTaskId)
                .agentId(agentId)
                .roleType(roleType)
                .traceId(traceId)
                .source("orchestrator")
                .build();
        publish(event);
    }

    /**
     * 发布子任务完成事件
     */
    public void publishSubtaskCompleted(String packageId, String subTaskId, String agentId, Map<String, Object> result, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.SUBTASK_COMPLETED)
                .packageId(packageId)
                .subTaskId(subTaskId)
                .agentId(agentId)
                .eventData(result)
                .traceId(traceId)
                .source("agent")
                .build();
        publish(event);
    }

    /**
     * 发布子任务失败事件
     */
    public void publishSubtaskFailed(String packageId, String subTaskId, String agentId, String errorMessage, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.SUBTASK_FAILED)
                .packageId(packageId)
                .subTaskId(subTaskId)
                .agentId(agentId)
                .eventData(Map.of("errorMessage", errorMessage))
                .traceId(traceId)
                .source("agent")
                .build();
        publish(event);
    }

    /**
     * 发布子任务跳过事件
     */
    public void publishSubtaskSkipped(String packageId, String subTaskId, String reason, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.SUBTASK_SKIPPED)
                .packageId(packageId)
                .subTaskId(subTaskId)
                .eventData(Map.of("reason", reason != null ? reason : "Skipped by user"))
                .traceId(traceId)
                .source("user")
                .build();
        publish(event);
    }

    /**
     * 发布共识达成事件
     */
    public void publishConsensusReached(String packageId, Object consensusResult, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.CONSENSUS_REACHED)
                .packageId(packageId)
                .eventData(Map.of("consensusResult", consensusResult))
                .traceId(traceId)
                .source("consensus")
                .build();
        publish(event);
    }

    /**
     * 发布回退触发事件
     */
    public void publishFallbackTriggered(String packageId, String reason, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.FALLBACK_TRIGGERED)
                .packageId(packageId)
                .eventData(Map.of("reason", reason))
                .traceId(traceId)
                .source("orchestrator")
                .build();
        publish(event);
    }

    /**
     * 发布任务包状态变更事件（暂停/恢复/取消）
     */
    public void publishPackageStatusChanged(String packageId, String newStatus, String traceId) {
        CollaborationEvent.CollaborationEventType eventType = switch (newStatus) {
            case "PAUSED" -> CollaborationEvent.CollaborationEventType.PACKAGE_PAUSED;
            case "EXECUTING" -> CollaborationEvent.CollaborationEventType.PACKAGE_RESUMED;
            case "CANCELLED" -> CollaborationEvent.CollaborationEventType.PACKAGE_CANCELLED;
            case "COMPLETED" -> CollaborationEvent.CollaborationEventType.PACKAGE_COMPLETED;
            case "FAILED" -> CollaborationEvent.CollaborationEventType.PACKAGE_FAILED;
            default -> CollaborationEvent.CollaborationEventType.PACKAGE_STARTED;
        };

        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(eventType)
                .packageId(packageId)
                .eventData(Map.of("newStatus", newStatus))
                .traceId(traceId)
                .source("orchestrator")
                .build();
        publish(event);
    }

    /**
     * 发布子任务人工接管事件
     */
    public void publishSubtaskManuallyHandled(String packageId, String subTaskId, String handlerInput, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.SUBTASK_MANUAL_HANDLING)
                .packageId(packageId)
                .subTaskId(subTaskId)
                .eventData(Map.of("handlerInput", handlerInput != null ? handlerInput : ""))
                .traceId(traceId)
                .source("user")
                .build();
        publish(event);
    }

    /**
     * 发布检查点保存事件
     */
    public void publishCheckpointSaved(String packageId, String checkpointId, String traceId) {
        CollaborationEvent event = CollaborationEvent.builder()
                .eventType(CollaborationEvent.CollaborationEventType.CHECKPOINT_SAVED)
                .packageId(packageId)
                .eventData(Map.of("checkpointId", checkpointId))
                .traceId(traceId)
                .source("memory")
                .build();
        publish(event);
    }

    /**
     * 保存事件到数据库
     */
    private void saveEventLog(CollaborationEvent event) {
        try {
            CollabEventLogEntity entity = CollabEventLogEntity.builder()
                    .eventId(event.getEventId())
                    .packageId(event.getPackageId())
                    .eventType(event.getEventType().name())
                    .subTaskId(event.getSubTaskId())
                    .agentId(event.getAgentId())
                    .eventData(toJson(event.getEventData()))
                    .traceId(event.getTraceId())
                    .createdAt(event.getTimestamp())
                    .build();

            eventLogRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save event log: {}", event.getEventId(), e);
        }
    }

    /**
     * 触发内存事件处理器
     */
    private void triggerHandlers(CollaborationEvent event) {
        // 触发特定类型处理器
        String eventTypeKey = event.getEventType().name();
        EventHandler handler = handlers.get(eventTypeKey);
        if (handler != null) {
            try {
                handler.handle(event);
            } catch (Exception e) {
                log.error("Error handling event: {}", eventTypeKey, e);
            }
        }

        // 触发通配符处理器（如果有）
        EventHandler wildcardHandler = handlers.get("*");
        if (wildcardHandler != null) {
            try {
                wildcardHandler.handle(event);
            } catch (Exception e) {
                log.error("Error in wildcard handler", e);
            }
        }
    }

    /**
     * 通过 WebSocket 推送到前端
     */
    private void pushToFrontend(CollaborationEvent event) {
        try {
            String destination = "/topic/collaboration/" + event.getPackageId();
            messagingTemplate.convertAndSend(destination, event);
        } catch (Exception e) {
            log.warn("Failed to push event to frontend: {}", event.getEventId(), e);
        }
    }

    /**
     * 获取任务包的事件历史
     */
    public java.util.List<CollaborationEvent> getEventHistory(String packageId) {
        return eventLogRepository.findByPackageIdOrderByCreatedAtAsc(packageId).stream()
                .map(this::toEvent)
                .collect(java.util.stream.Collectors.toList());
    }

    private CollabEventLogEntity toEntity(CollaborationEvent event) {
        return CollabEventLogEntity.builder()
                .eventId(event.getEventId())
                .packageId(event.getPackageId())
                .eventType(event.getEventType().name())
                .subTaskId(event.getSubTaskId())
                .agentId(event.getAgentId())
                .traceId(event.getTraceId())
                .build();
    }

    private CollaborationEvent toEvent(CollabEventLogEntity entity) {
        return CollaborationEvent.builder()
                .eventId(entity.getEventId())
                .packageId(entity.getPackageId())
                .eventType(CollaborationEvent.CollaborationEventType.valueOf(entity.getEventType()))
                .subTaskId(entity.getSubTaskId())
                .agentId(entity.getAgentId())
                .eventData(fromJson(entity.getEventData()))
                .traceId(entity.getTraceId())
                .timestamp(entity.getCreatedAt())
                .build();
    }

    private String toJson(Map<String, Object> data) {
        if (data == null) return null;
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
