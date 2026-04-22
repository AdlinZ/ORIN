package com.adlin.orin.common.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageReplicationRepairService {

    private final StorageProperties storageProperties;
    private final StorageProviderRegistry providerRegistry;
    private final StorageReplicationTaskRepository taskRepository;

    @Scheduled(fixedDelayString = "${storage.repair.fixed-delay-ms:30000}")
    public void processRepairQueue() {
        if (!storageProperties.getRepair().isEnabled()) {
            return;
        }
        List<StorageReplicationTask> tasks = taskRepository
                .findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        List.of(ReplicationStatus.PENDING_REPAIR.name(), ReplicationStatus.REPAIR_FAILED.name()),
                        LocalDateTime.now());
        for (StorageReplicationTask task : tasks) {
            processOne(task);
        }
    }

    public void processOne(StorageReplicationTask task) {
        task.setStatus(ReplicationStatus.REPAIRING.name());
        task.setLastAttemptAt(LocalDateTime.now());
        taskRepository.save(task);

        StorageBackend sourceBackend = StorageBackend.from(task.getSourceBackend(), StorageBackend.LOCAL);
        StorageBackend targetBackend = StorageBackend.from(task.getTargetBackend(), StorageBackend.MINIO);
        ObjectStorageProvider source = providerRegistry.provider(sourceBackend);
        ObjectStorageProvider target = providerRegistry.provider(targetBackend);

        try (InputStream in = source.get(task.getSourceLocator())) {
            String targetLocator = target.put(
                    task.getObjectKey(),
                    in,
                    -1,
                    "application/octet-stream",
                    Map.of("repair", "true"));
            task.setTargetLocator(targetLocator);
            task.setStatus(ReplicationStatus.SYNCED.name());
            task.setLastError(null);
            task.setNextRetryAt(null);
            taskRepository.save(task);
        } catch (Exception ex) {
            int retries = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
            task.setRetryCount(retries);
            task.setLastError(ex.getMessage() == null ? "unknown error" : ex.getMessage());
            if (retries >= (task.getMaxRetries() == null ? storageProperties.getRepair().getMaxRetries() : task.getMaxRetries())) {
                task.setStatus(ReplicationStatus.REPAIR_FAILED.name());
            } else {
                task.setStatus(ReplicationStatus.PENDING_REPAIR.name());
            }
            task.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(300, (long) Math.pow(2, retries))));
            taskRepository.save(task);
            log.warn("Repair failed for task {}: {}", task.getId(), ex.getMessage());
        }
    }

    public long pendingCount() {
        return taskRepository.countByStatus(ReplicationStatus.PENDING_REPAIR.name());
    }

    public long failedCount() {
        return taskRepository.countByStatus(ReplicationStatus.REPAIR_FAILED.name());
    }

    public List<StorageReplicationTask> listRepairTasks() {
        return taskRepository.findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of(ReplicationStatus.PENDING_REPAIR.name(), ReplicationStatus.REPAIR_FAILED.name(), ReplicationStatus.REPAIRING.name()),
                LocalDateTime.now().plusYears(100));
    }

    public void retryNow(String taskId) {
        StorageReplicationTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Repair task not found: " + taskId));
        task.setNextRetryAt(LocalDateTime.now());
        task.setStatus(ReplicationStatus.PENDING_REPAIR.name());
        taskRepository.save(task);
    }
}

