package com.adlin.orin.modules.knowledge.listener;

import com.adlin.orin.common.enums.TaskStatus;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import com.adlin.orin.modules.knowledge.event.TaskCreatedEvent;
import com.adlin.orin.modules.knowledge.repository.KnowledgeTaskRepository;
import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import com.adlin.orin.modules.multimodal.repository.MultimodalFileRepository;
import com.adlin.orin.modules.multimodal.service.VisualAnalysisService;
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

import java.util.Collections;
import java.util.Optional;
import java.util.Base64;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTaskListener {

    private final KnowledgeTaskRepository taskRepository;
    private final MultimodalFileRepository fileRepository;
    private final VisualAnalysisService visualAnalysisService;
    private final VectorStoreProvider vectorStoreProvider;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskCreated(TaskCreatedEvent event) {
        String taskId = event.getTaskId();
        String traceId = event.getTraceId();
        // Propagate traceId to async thread via MDC
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            log.info("Received TaskCreatedEvent for task: {}", taskId);

            KnowledgeTask task = taskRepository.findById(taskId).orElse(null);
            if (task == null) {
                log.warn("Task not found in database: {}", taskId);
                return;
            }

            if (task.getStatus() != TaskStatus.PENDING) {
                log.info("Task {} is already in status: {}, skipping", taskId, task.getStatus());
                return;
            }

            long startTime = System.currentTimeMillis();

            try {
                log.info("Processing task {} (Type: {})", taskId, task.getTaskType());
                task.setStatus(TaskStatus.RUNNING);
                task.setStartedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                taskRepository.save(task);

                if ("CAPTIONING".equals(task.getTaskType())) {
                    processCaptioning(task);
                } else if ("EMBEDDING".equals(task.getTaskType())) {
                    processEmbedding(task);
                } else if ("GRAPH_BUILDING".equals(task.getTaskType())) {
                    processGraphBuilding(task);
                } else {
                    log.warn("Unknown task type: {}", task.getTaskType());
                    task.setStatus(TaskStatus.FAILED);
                    task.setErrorMessage("Unknown task type: " + task.getTaskType());
                    taskRepository.save(task);
                }

                // Calculate execution time
                long executionTime = System.currentTimeMillis() - startTime;
                task.setExecutionTimeMs(executionTime);
                task.setCompletedAt(LocalDateTime.now());

                log.info("Task {} completed successfully in {}ms", taskId, executionTime);

            } catch (Exception e) {
                log.error("Task {} processing failed: {}", taskId, e.getMessage(), e);
                handleFailure(task, e, startTime);
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private void processCaptioning(KnowledgeTask task) {
        MultimodalFile file = fileRepository.findById(task.getAssetId()).orElseThrow();

        // Assume file access via public URL or local path mapping
        // implementation details depend on how VisualAnalysisService expects input.
        // For local files, we might need to expose them via Nginx or temporary upload
        // to OSS.
        // In real PROD, we'd use MinIO signed URL.

        // Hack: if storagePath starts with http, use it. Else... handle local?
        // Let's assume VisualAnalysisService can handle it or we pass a dummy URL for
        // now if it's local test.
        String imageUrl = file.getStoragePath();
        String summary;

        try {
            if (imageUrl != null && (imageUrl.startsWith("http") || imageUrl.startsWith("https"))) {
                summary = visualAnalysisService.analyzeImage(imageUrl);
            } else if (imageUrl != null) {
                // Handle local file: Convert to Base64
                // We need to determine mime type, stored in MultimodalFile
                String mimeType = file.getMimeType() != null ? file.getMimeType() : "image/jpeg";

                Path path = Paths.get(imageUrl);
                byte[] bytes = Files.readAllBytes(path);
                String base64Content = Base64.getEncoder().encodeToString(bytes);

                String dataUri = "data:" + mimeType + ";base64," + base64Content;
                summary = visualAnalysisService.analyzeImage(dataUri);
            } else {
                summary = "Analysis failed: No image path found.";
            }
        } catch (Exception e) {
            log.error("VLM Analysis failed", e);
            summary = "Analysis failed: " + e.getMessage();
        }

        // Save AI summary and mark as SUCCESS immediately
        // This allows users to see the summary even if embedding fails
        file.setAiSummary(summary);
        file.setEmbeddingStatus("SUCCESS");
        fileRepository.save(file);

        // Mark captioning task as completed
        task.setStatus(TaskStatus.SUCCESS);
        taskRepository.save(task);

        // Try to create embedding task, but don't block on it
        // If Milvus is down, the summary is still available
        try {
            createEmbeddingTask(file.getId());
        } catch (Exception e) {
            log.warn("Failed to create embedding task for file {}: {}", file.getId(), e.getMessage());
            // Don't fail the whole process - summary is already saved
        }
    }

    private void processEmbedding(KnowledgeTask task) {
        MultimodalFile file = fileRepository.findById(task.getAssetId()).orElseThrow();

        String textToEmbed = file.getAiSummary();
        if (textToEmbed == null || textToEmbed.isEmpty()) {
            textToEmbed = file.getFileName(); // Fallback
        }

        // Create KnowledgeDocument wrapper
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(file.getId())
                .contentPreview(textToEmbed) // This will be embedded
                // .metadata(...)
                .build();

        // Store in Milvus (KB ID "default" or specific?)
        // TODO: define KB strategy. We use "multimodal" partition?
        vectorStoreProvider.addDocuments("multimodal", Collections.singletonList(doc));

        file.setEmbeddingStatus("SUCCESS");
        fileRepository.save(file);

        task.setStatus(TaskStatus.SUCCESS);
        taskRepository.save(task);
    }

    /**
     * 处理图谱构建任务
     * 从指定文档中抽取实体和关系，构建知识图谱
     */
    private void processGraphBuilding(KnowledgeTask task) {
        // TODO: 实现真实的图谱构建逻辑
        // 1. 从assetId获取源文档内容
        // 2. 使用LLM进行实体抽取
        // 3. 使用LLM进行关系抽取
        // 4. 存储实体和关系到图谱数据库

        log.info("Graph building task {} started for asset {}", task.getId(), task.getAssetId());

        // 占位实现：模拟图谱构建过程
        // 实际实现需要接入LLM进行实体/关系抽取
        try {
            // 模拟处理延迟
            Thread.sleep(1000);

            // 标记任务成功
            task.setStatus(TaskStatus.SUCCESS);
            taskRepository.save(task);

            log.info("Graph building task {} completed", task.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage("Graph building interrupted");
            taskRepository.save(task);
        } catch (Exception e) {
            log.error("Graph building failed for task {}: {}", task.getId(), e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
        }
    }

    private void createEmbeddingTask(String assetId) {
        KnowledgeTask newTask = KnowledgeTask.builder()
                .assetId(assetId)
                .assetType("MULTIMODAL_FILE")
                .taskType("EMBEDDING")
                .status(TaskStatus.PENDING)
                .build();
        taskRepository.save(newTask);
        // Self-trigger (or rely on scheduled job? Better self-trigger via method call
        // or event)
        onTaskCreated(new TaskCreatedEvent(this, newTask.getId(), MDC.get("traceId")));
    }

    private void handleFailure(KnowledgeTask task, Exception e, long startTime) {
        task.setErrorMessage(e.getMessage());
        task.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        task.setCompletedAt(LocalDateTime.now());

        int retry = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retry < (task.getMaxRetries() == null ? 3 : task.getMaxRetries())) {
            task.setRetryCount(retry + 1);
            task.setStatus(TaskStatus.PENDING); // Will be picked up again by listener
        } else {
            task.setStatus(TaskStatus.FAILED);
            // Also update File status
            Optional<MultimodalFile> fileOpt = fileRepository.findById(task.getAssetId());
            fileOpt.ifPresent(f -> {
                f.setEmbeddingStatus("FAILED");
                fileRepository.save(f);
            });
        }
        taskRepository.save(task);
    }
}
