package com.adlin.orin.modules.knowledge.listener;

import com.adlin.orin.common.enums.TaskStatus;
import com.adlin.orin.modules.knowledge.component.VectorStoreProvider;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeTask;
import com.adlin.orin.modules.knowledge.event.TaskCreatedEvent;
import com.adlin.orin.modules.knowledge.repository.KnowledgeTaskRepository;
import com.adlin.orin.modules.knowledge.service.GraphExtractionService;
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
    private final GraphExtractionService graphExtractionService;

    @Async("taskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTaskCreated(TaskCreatedEvent event) {
        String taskId = event.getTaskId();
        String traceId = event.getTraceId();
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

    private void handleFailure(KnowledgeTask task, Exception e, long startTime) {
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(e.getMessage());
        task.setCompletedAt(LocalDateTime.now());
        task.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        taskRepository.save(task);
    }

    private void processCaptioning(KnowledgeTask task) {
        MultimodalFile file = fileRepository.findById(task.getAssetId()).orElseThrow();

        String imageUrl = file.getStoragePath();
        String summary;

        try {
            if (imageUrl != null && (imageUrl.startsWith("http") || imageUrl.startsWith("https"))) {
                summary = visualAnalysisService.analyzeImage(imageUrl);
            } else if (imageUrl != null) {
                Path path = Paths.get(imageUrl);
                if (Files.exists(path)) {
                    byte[] fileContent = Files.readAllBytes(path);
                    String base64 = Base64.getEncoder().encodeToString(fileContent);
                    summary = visualAnalysisService.analyzeImage("data:image/png;base64," + base64);
                } else {
                    summary = "[Image file not found]";
                }
            } else {
                summary = "[No image URL provided]";
            }
        } catch (Exception e) {
            summary = "[Error: " + e.getMessage() + "]";
        }

        // 保存 caption 到 aiSummary 字段
        file.setAiSummary(summary);
        fileRepository.save(file);

        task.setStatus(TaskStatus.SUCCESS);
        taskRepository.save(task);

        log.info("Caption generated for file {}: {}", file.getId(), summary);
    }

    private void processEmbedding(KnowledgeTask task) {
        log.info("Embedding task {} processing", task.getId());
        try {
            MultimodalFile file = fileRepository.findById(task.getAssetId()).orElseThrow();

            String textToEmbed = "";
            // 使用 AI summary 或 OCR 结果
            if (file.getAiSummary() != null && !file.getAiSummary().isEmpty()) {
                textToEmbed = file.getAiSummary();
            } else if (file.getOcrText() != null && !file.getOcrText().isEmpty()) {
                textToEmbed = file.getOcrText();
            } else if (file.getStoragePath() != null) {
                Path path = Paths.get(file.getStoragePath());
                if (Files.exists(path)) {
                    textToEmbed = Files.readString(path);
                }
            }

            if (textToEmbed.isEmpty()) {
                textToEmbed = "[Empty content]";
            }

            KnowledgeDocument doc = KnowledgeDocument.builder()
                    .id(file.getId())
                    .contentPreview(textToEmbed)
                    .build();

            vectorStoreProvider.addDocuments("multimodal", Collections.singletonList(doc));

            file.setEmbeddingStatus("SUCCESS");
            fileRepository.save(file);

            task.setStatus(TaskStatus.SUCCESS);
            taskRepository.save(task);

            log.info("Embedding task {} completed", task.getId());

        } catch (Exception e) {
            log.error("Embedding task {} failed: {}", task.getId(), e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
            taskRepository.save(task);
        }
    }

    /**
     * 处理图谱构建任务
     */
    private void processGraphBuilding(KnowledgeTask task) {
        log.info("Graph building task {} started for asset {}", task.getId(), task.getAssetId());

        try {
            String assetId = task.getAssetId();
            Optional<MultimodalFile> fileOpt = fileRepository.findById(assetId);
            
            String textContent = "";
            
            if (fileOpt.isPresent()) {
                MultimodalFile file = fileOpt.get();
                // 优先使用 AI summary
                if (file.getAiSummary() != null && !file.getAiSummary().isEmpty()) {
                    textContent = file.getAiSummary();
                } 
                // 使用 OCR 结果
                else if (file.getOcrText() != null && !file.getOcrText().isEmpty()) {
                    textContent = file.getOcrText();
                }
                // 读取文件
                else if (file.getStoragePath() != null) {
                    Path path = Paths.get(file.getStoragePath());
                    if (Files.exists(path)) {
                        textContent = Files.readString(path);
                    }
                }
            }

            if (textContent.isEmpty()) {
                throw new IllegalStateException("No content found for graph building");
            }

            // 构建图谱
            graphExtractionService.buildGraph("default", assetId, textContent);

            task.setStatus(TaskStatus.SUCCESS);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);

            log.info("Graph building task {} completed", task.getId());

        } catch (Exception e) {
            log.error("Graph building failed for task {}: {}", task.getId(), e.getMessage(), e);
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
        onTaskCreated(new TaskCreatedEvent(this, newTask.getId(), MDC.get("traceId")));
    }
}
