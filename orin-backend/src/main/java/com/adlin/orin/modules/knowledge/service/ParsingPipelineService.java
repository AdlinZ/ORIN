package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeParsingTask;
import com.adlin.orin.modules.knowledge.parser.DocumentParser;
import com.adlin.orin.modules.knowledge.parser.ParserFactory;
import com.adlin.orin.modules.knowledge.parser.ParsingResult;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeParsingTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * 解析流程服务
 * 编排文档解析流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingPipelineService {

    private final ParserFactory parserFactory;
    private final KnowledgeParsingTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final StorageManagementService storageService;

    /**
     * 创建解析任务
     * 根据文档类型创建对应的解析任务
     */
    @Transactional
    public KnowledgeParsingTask createParsingTask(String documentId, String knowledgeBaseId, String fileType) {
        // 获取文档信息
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        // 确定任务类型
        String taskType = determineTaskType(fileType);

        // 创建解析任务
        KnowledgeParsingTask task = KnowledgeParsingTask.builder()
                .id(UUID.randomUUID().toString())
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .taskType(taskType)
                .status(KnowledgeParsingTask.STATUS_PENDING)
                .inputPath(document.getStoragePath())
                .priority(0)
                .build();

        task = taskRepository.save(task);

        // 更新文档解析状态
        document.setParseStatus("PENDING");
        documentRepository.save(document);

        log.info("Created parsing task: {} for document: {}, type: {}", task.getId(), documentId, taskType);

        return task;
    }

    /**
     * 执行解析任务
     */
    @Transactional
    public ParsingResult executeParsingTask(KnowledgeParsingTask task, Map<String, String> config) {
        log.info("Executing parsing task: {}, type: {}", task.getId(), task.getTaskType());

        task.start();
        taskRepository.save(task);

        try {
            // 获取解析器
            DocumentParser parser = parserFactory.getParserByTaskType(task.getTaskType());
            if (parser == null) {
                throw new RuntimeException("No parser found for task type: " + task.getTaskType());
            }

            // 确定输出路径
            String outputPath = task.getInputPath().replaceFirst("data/", "parsed/");
            outputPath = outputPath.replaceFirst("\\.[^.]+$", ".txt");

            // 执行解析
            ParsingResult result = parser.parse(task.getInputPath(), outputPath, config);

            if (result.isSuccess()) {
                task.setOutputPath(outputPath);
                task.complete();
                taskRepository.save(task);

                // 更新文档状态
                updateDocumentParseStatus(task.getDocumentId(), "SUCCESS", outputPath, null);

                log.info("Parsing task completed: {}, output: {}", task.getId(), outputPath);
            } else {
                task.fail(result.getErrorMessage());
                taskRepository.save(task);

                // 更新文档状态
                updateDocumentParseStatus(task.getDocumentId(), "FAILED", null, result.getErrorMessage());

                log.warn("Parsing task failed: {}, error: {}", task.getId(), result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Parsing task error: {}", task.getId(), e);

            task.fail(e.getMessage());
            taskRepository.save(task);

            // 更新文档状态
            updateDocumentParseStatus(task.getDocumentId(), "FAILED", null, e.getMessage());

            return ParsingResult.failure(e.getMessage());
        }
    }

    /**
     * 确定任务类型
     */
    private String determineTaskType(String fileType) {
        String category = ParserFactory.getMediaCategory(fileType);

        return switch (category) {
            case "image" -> KnowledgeParsingTask.TASK_TYPE_OCR;
            case "audio", "video" -> KnowledgeParsingTask.TASK_TYPE_ASR;
            case "pdf" -> KnowledgeParsingTask.TASK_TYPE_PDF_EXTRACT;
            default -> KnowledgeParsingTask.TASK_TYPE_TEXT_EXTRACT;
        };
    }

    /**
     * 更新文档解析状态
     */
    @Transactional
    public void updateDocumentParseStatus(String documentId, String status, String parsedPath, String error) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setParseStatus(status);
            if (parsedPath != null) {
                doc.setParsedPath(parsedPath);
            }
            if (error != null) {
                doc.setParseError(error);
            }
            documentRepository.save(doc);
        });
    }

    /**
     * 检查解析是否需要额外处理
     * 对于图片和音视频，需要 OCR/ASR 处理
     * 对于文本和 PDF，可以直接提取
     */
    public boolean requiresAsyncParsing(String fileType) {
        String category = ParserFactory.getMediaCategory(fileType);
        // 图片、音视频需要异步解析（OCR/ASR）
        // 文本、PDF 可以同步处理
        return category.equals("image") || category.equals("audio") || category.equals("video");
    }
}
