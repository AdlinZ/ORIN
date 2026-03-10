package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeParsingTask;
import com.adlin.orin.modules.knowledge.repository.KnowledgeParsingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 解析任务调度器
 * 定时扫描并处理待解析的任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParsingTaskScheduler {

    private final KnowledgeParsingTaskRepository taskRepository;
    private final ParsingPipelineService parsingPipelineService;
    private final DocumentManageService documentManageService;

    @Value("${knowledge.parsing.enabled:true}")
    private boolean parsingEnabled;

    @Value("${knowledge.parsing.max-concurrent:3}")
    private int maxConcurrent;

    @Value("${knowledge.parsing.retry-max:3}")
    private int maxRetry;

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

            log.info("Found {} pending parsing tasks", pendingTasks.size());

            // 处理每个任务
            for (KnowledgeParsingTask task : pendingTasks) {
                if (task.getRetryCount() >= maxRetry) {
                    log.warn("Task {} exceeded max retries, marking as FAILED", task.getId());
                    task.fail("Max retries exceeded");
                    taskRepository.save(task);
                    continue;
                }

                try {
                    processTask(task);
                } catch (Exception e) {
                    log.error("Failed to process task: {}", task.getId(), e);
                    task.incrementRetry();
                    taskRepository.save(task);
                }
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

        // 获取知识库配置（这里可以后续从数据库读取知识库的配置）
        // 目前使用默认配置
        java.util.Map<String, String> config = new java.util.HashMap<>();
        config.put("ocr_provider", "local");
        config.put("asr_provider", "local");

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
}
