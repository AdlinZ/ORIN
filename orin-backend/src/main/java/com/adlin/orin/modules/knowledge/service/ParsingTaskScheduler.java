package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeParsingTask;
import com.adlin.orin.modules.knowledge.repository.KnowledgeBaseRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeParsingTaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 解析任务调度器
 * 定时扫描并处理待解析的任务，支持并发处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParsingTaskScheduler {

    private final KnowledgeParsingTaskRepository taskRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ParsingPipelineService parsingPipelineService;
    private final DocumentManageService documentManageService;

    @Value("${knowledge.parsing.enabled:true}")
    private boolean parsingEnabled;

    @Value("${knowledge.parsing.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${knowledge.parsing.retry-max:3}")
    private int maxRetry;

    private ExecutorService executorService;
    private final AtomicInteger runningTasks = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // 创建线程池，核心线程数为 maxConcurrent
        executorService = new ThreadPoolExecutor(
                maxConcurrent,
                maxConcurrent,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000),
                Executors.defaultThreadFactory(),
                new java.util.concurrent.RejectedExecutionHandler() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                        log.warn("Parsing task rejected, queue is full");
                    }
                });
        log.info("ParsingTaskScheduler initialized with maxConcurrent={}", maxConcurrent);
    }

    @PreDestroy
    public void shutdown() {
        if (executorService != null) {
            log.info("Shutting down ParsingTaskScheduler...");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 定时扫描并处理待解析任务
     * 每 10 秒执行一次
     */
    @Scheduled(fixedDelay = 10000)
    public void processPendingTasks() {
        if (!parsingEnabled) {
            return;
        }

        try {
            // 获取待处理的任务
            List<KnowledgeParsingTask> pendingTasks = taskRepository
                    .findPendingTasks(KnowledgeParsingTask.STATUS_PENDING);

            if (pendingTasks.isEmpty()) {
                return;
            }

            log.info("Found {} pending parsing tasks, currently running: {}",
                    pendingTasks.size(), runningTasks.get());

            // 限制每次获取的任务数量，避免一次性提交过多
            int availableSlots = maxConcurrent - runningTasks.get();
            if (availableSlots <= 0) {
                log.debug("All parsing slots occupied, waiting for free slots");
                return;
            }

            int tasksToProcess = Math.min(pendingTasks.size(), availableSlots);
            List<KnowledgeParsingTask> tasksToSubmit = pendingTasks.subList(0, tasksToProcess);

            // 并行提交任务
            for (KnowledgeParsingTask task : tasksToSubmit) {
                if (task.getRetryCount() >= maxRetry) {
                    log.warn("Task {} exceeded max retries, marking as FAILED", task.getId());
                    task.fail("Max retries exceeded");
                    taskRepository.save(task);
                    continue;
                }

                // 提交到线程池
                runningTasks.incrementAndGet();
                executorService.submit(() -> {
                    try {
                        processTask(task);
                    } catch (Exception e) {
                        log.error("Failed to process task: {}", task.getId(), e);
                        task.incrementRetry();
                        taskRepository.save(task);
                    } finally {
                        runningTasks.decrementAndGet();
                    }
                });
            }

        } catch (Exception e) {
            log.error("Error in parsing task scheduler", e);
        }
    }

    /**
     * 处理单个解析任务
     */
    private void processTask(KnowledgeParsingTask task) {
        log.info("Processing parsing task: {}, type: {}, document: {}",
                task.getId(), task.getTaskType(), task.getDocumentId());

        // 从知识库配置读取 OCR/ASR 提供商
        java.util.Map<String, String> config = new java.util.HashMap<>();
        try {
            // 获取文档所属的知识库配置
            KnowledgeDocument doc = documentManageService.getDocument(task.getDocumentId());
            if (doc != null && doc.getKnowledgeBaseId() != null) {
                KnowledgeBase kb = knowledgeBaseRepository.findById(doc.getKnowledgeBaseId()).orElse(null);
                if (kb != null) {
                    config.put("ocr_provider", kb.getOcrProvider() != null ? kb.getOcrProvider() : "local");
                    config.put("asr_provider", kb.getAsrProvider() != null ? kb.getAsrProvider() : "local");
                    // 传递 VLM/OCR 模型名称
                    if (kb.getOcrModel() != null && !kb.getOcrModel().isEmpty()) {
                        config.put("ocr_model", kb.getOcrModel());
                        log.info("Using OCR model from knowledge base: {}", kb.getOcrModel());
                    }
                    if (kb.getAsrModel() != null && !kb.getAsrModel().isEmpty()) {
                        config.put("asr_model", kb.getAsrModel());
                        log.info("Using ASR model from knowledge base: {}", kb.getAsrModel());
                    }
                    log.debug("Loaded OCR/ASR config from knowledge base {}: ocr={}, asr={}, ocr_model={}, asr_model={}",
                            kb.getId(), config.get("ocr_provider"), config.get("asr_provider"),
                            config.get("ocr_model"), config.get("asr_model"));
                } else {
                    // 使用默认配置
                    config.put("ocr_provider", "local");
                    config.put("asr_provider", "local");
                }
            } else {
                config.put("ocr_provider", "local");
                config.put("asr_provider", "local");
            }
        } catch (Exception e) {
            log.warn("Failed to load knowledge base config, using defaults: {}", e.getMessage());
            config.put("ocr_provider", "local");
            config.put("asr_provider", "local");
        }

        // 执行解析任务
        var result = parsingPipelineService.executeParsingTask(task, config);

        // 解析成功后触发向量化
        if (result.isSuccess()) {
            log.info("Parsing completed successfully, triggering vectorization for document: {}",
                    task.getDocumentId());
            try {
                documentManageService.triggerVectorization(task.getDocumentId());
            } catch (Exception e) {
                log.error("Failed to trigger vectorization after parsing: {}",
                        task.getDocumentId(), e);
            }
        }
    }

    /**
     * 获取待处理任务数量
     */
    public long getPendingTaskCount() {
        return taskRepository.countByStatus(KnowledgeParsingTask.STATUS_PENDING);
    }

    /**
     * 获取正在处理的任务数量
     */
    public long getProcessingTaskCount() {
        return taskRepository.countByStatus(KnowledgeParsingTask.STATUS_PROCESSING);
    }

    /**
     * 获取失败任务数量
     */
    public long getFailedTaskCount() {
        return taskRepository.countByStatus(KnowledgeParsingTask.STATUS_FAILED);
    }

    /**
     * 获取当前运行中的任务数
     */
    public int getRunningTaskCount() {
        return runningTasks.get();
    }
}
