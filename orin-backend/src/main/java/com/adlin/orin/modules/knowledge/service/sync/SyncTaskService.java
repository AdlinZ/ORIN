package com.adlin.orin.modules.knowledge.service.sync;

import com.adlin.orin.modules.knowledge.entity.ExternalIntegration;
import com.adlin.orin.modules.knowledge.entity.SyncChangeLog;
import com.adlin.orin.modules.knowledge.entity.SyncRecord;
import com.adlin.orin.modules.knowledge.repository.ExternalIntegrationRepository;
import com.adlin.orin.modules.knowledge.repository.SyncChangeLogRepository;
import com.adlin.orin.modules.knowledge.repository.SyncRecordRepository;
import com.adlin.orin.modules.knowledge.service.ExternalIntegrationService;
import com.adlin.orin.modules.task.entity.TaskEntity;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskCategory;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskPriority;
import com.adlin.orin.modules.task.entity.TaskEntity.TaskStatus;
import com.adlin.orin.modules.task.producer.TaskQueueProducer;
import com.adlin.orin.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 同步任务服务
 * 将同步操作队列化，支持异步执行、失败重试、幂等键防重
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskService {

    private final TaskRepository taskRepository;
    private final TaskQueueProducer taskQueueProducer;
    private final SyncChangeLogRepository changeLogRepository;
    private final SyncRecordRepository syncRecordRepository;
    private final ExternalIntegrationRepository integrationRepository;
    private final ExternalIntegrationService integrationService;

    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 提交双向同步任务到队列（幂等）
     */
    @Transactional
    public String submitBidirectionalSync(Long integrationId, String triggeredBy) {
        String idempotencyKey = String.format("sync_bidirectional_%d", integrationId);

        // 幂等检查：已有 QUEUED/RUNNING/RETRYING 状态的任务则跳过
        Optional<TaskEntity> existing = taskRepository.findByTaskId(idempotencyKey);
        if (existing.isPresent()) {
            TaskStatus status = existing.get().getStatus();
            if (status == TaskStatus.QUEUED || status == TaskStatus.RUNNING || status == TaskStatus.RETRYING) {
                log.info("Sync task already in progress for integration {}, status={}", integrationId, status);
                return idempotencyKey;
            }
        }

        ExternalIntegration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new RuntimeException("Integration not found: " + integrationId));

        // 创建队列任务记录
        TaskEntity task = TaskEntity.builder()
                .taskId(idempotencyKey)
                .taskCategory(TaskCategory.SYNC)
                .priority(TaskPriority.NORMAL)
                .status(TaskStatus.QUEUED)
                .inputData(Map.of(
                        "integrationId", integrationId,
                        "syncDirection", integration.getSyncDirection() != null ? integration.getSyncDirection() : "BIDIRECTIONAL"
                ))
                .triggeredBy(triggeredBy)
                .triggerSource("MANUAL")
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .queuedAt(LocalDateTime.now())
                .build();
        taskRepository.save(task);

        // 发送到 RabbitMQ 队列
        com.adlin.orin.modules.task.dto.TaskMessage msg = com.adlin.orin.modules.task.dto.TaskMessage.builder()
                .taskId(idempotencyKey)
                .priority(TaskPriority.NORMAL)
                .inputData(task.getInputData())
                .triggeredBy(triggeredBy)
                .triggerSource("MANUAL")
                .retryCount(0)
                .maxRetries(DEFAULT_MAX_RETRIES)
                .build();

        taskQueueProducer.sendTask(msg);
        log.info("Sync task submitted: taskId={}, integrationId={}", idempotencyKey, integrationId);

        return idempotencyKey;
    }

    /**
     * 查询同步任务状态
     */
    public Optional<TaskEntity> getTaskStatus(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 获取集成的最近同步记录
     */
    public List<SyncRecord> getRecentSyncRecords(Long integrationId, int limit) {
        return syncRecordRepository
                .findTopByAgentIdOrderByEndTimeDesc(String.valueOf(integrationId),
                        org.springframework.data.domain.PageRequest.of(0, limit));
    }
}
