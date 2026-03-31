package com.adlin.orin.modules.knowledge.service;

import com.adlin.orin.modules.knowledge.entity.KnowledgeDocument;
import com.adlin.orin.modules.knowledge.entity.KnowledgeDocumentChunk;
import com.adlin.orin.modules.knowledge.entity.KnowledgeParsingTask;
import com.adlin.orin.modules.knowledge.parser.DocumentParser;
import com.adlin.orin.modules.knowledge.parser.ParserFactory;
import com.adlin.orin.modules.knowledge.parser.ParsingResult;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentChunkRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeDocumentRepository;
import com.adlin.orin.modules.knowledge.repository.KnowledgeParsingTaskRepository;
import com.adlin.orin.modules.knowledge.util.HierarchicalTextSplitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 解析流程服务
 * 编排文档解析流程：解析 -> 清洗 -> 分块 -> 向量化 -> 入库
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParsingPipelineService {

    private final ParserFactory parserFactory;
    private final KnowledgeParsingTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeDocumentChunkRepository chunkRepository;
    private final StorageManagementService storageService;
    private final MilvusVectorService vectorService;

    // 状态常量
    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_CHUNKING = "CHUNKING";
    public static final String STATUS_VECTORIZING = "VECTORIZING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    // 重试配置
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_INITIAL_INTERVAL_MS = 1000;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long RETRY_MAX_INTERVAL_MS = 30000;

    /**
     * 创建解析任务
     */
    @Transactional
    public KnowledgeParsingTask createParsingTask(String documentId, String knowledgeBaseId, String fileType) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        String taskType = determineTaskType(fileType);

        KnowledgeParsingTask task = KnowledgeParsingTask.builder()
                .id(UUID.randomUUID().toString())
                .documentId(documentId)
                .knowledgeBaseId(knowledgeBaseId)
                .taskType(taskType)
                .status(STATUS_QUEUED)
                .inputPath(document.getStoragePath())
                .priority(0)
                .build();

        task = taskRepository.save(task);

        document.setParseStatus(STATUS_QUEUED);
        documentRepository.save(document);

        log.info("Created parsing task: {} for document: {}, type: {}", task.getId(), documentId, taskType);

        return task;
    }

    /**
     * 执行完整的解析流程
     */
    @Transactional
    public PipelineResult executePipeline(String documentId, Map<String, Object> config) {
        KnowledgeDocument document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));

        int chunkSize = config.containsKey("chunkSize")
                ? (int) config.get("chunkSize") : document.getChunkSize();
        int chunkOverlap = config.containsKey("chunkOverlap")
                ? (int) config.get("chunkOverlap") : document.getChunkOverlap();

        PipelineResult result = new PipelineResult();
        long startTime = System.currentTimeMillis();

        try {
            // 阶段1: 解析
            updateDocumentStatus(document, STATUS_PARSING, null, null);
            ParsingResult parseResult = executeParsingWithRetry(document);
            if (!parseResult.isSuccess()) {
                result.setSuccess(false);
                result.setErrorMessage("解析失败: " + parseResult.getErrorMessage());
                result.setStage(STATUS_PARSING);
                updateDocumentStatus(document, STATUS_FAILED, null, parseResult.getErrorMessage());
                return result;
            }
            result.setParsedTextPath(parseResult.getText());

            // 阶段2: 清洗
            updateDocumentStatus(document, STATUS_CHUNKING, null, null);
            String cleanedText = cleanText(parseResult.getText());
            result.setCharCount(cleanedText.length());

            // 分块
            List<String> chunkTexts = HierarchicalTextSplitter.splitSimple(cleanedText, chunkSize, chunkOverlap);
            result.setChunkCount(chunkTexts.size());
            log.info("Document {} split into {} chunks (size={}, overlap={})",
                    documentId, chunkTexts.size(), chunkSize, chunkOverlap);

            // 保存 chunks
            List<KnowledgeDocumentChunk> chunksToSave = new ArrayList<>();
            for (int i = 0; i < chunkTexts.size(); i++) {
                String chunkText = chunkTexts.get(i);
                KnowledgeDocumentChunk chunk = KnowledgeDocumentChunk.builder()
                        .id(UUID.randomUUID().toString())
                        .documentId(documentId)
                        .content(chunkText)
                        .charCount(chunkText.length())
                        .chunkIndex(i)
                        .chunkType("child")
                        .title(document.getFileName())
                        .source(document.getFileName())
                        .position(i)
                        .build();
                chunksToSave.add(chunk);
            }
            chunkRepository.saveAll(chunksToSave);

            // 阶段3: 向量化
            updateDocumentStatus(document, STATUS_VECTORIZING, null, null);
            vectorService.addChunks(document.getKnowledgeBaseId(), chunksToSave);

            // 完成
            result.setSuccess(true);
            result.setStage(STATUS_SUCCESS);
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            updateDocumentStatus(document, STATUS_SUCCESS, parseResult.getText(), null);

            // 更新文档分块数量
            document.setChunkCount(chunkTexts.size());
            document.setVectorStatus(STATUS_SUCCESS);
            documentRepository.save(document);

            log.info("Pipeline completed for document {}: {} chunks in {}ms",
                    documentId, chunkTexts.size(), result.getProcessingTimeMs());

            return result;

        } catch (Exception e) {
            log.error("Pipeline failed for document {}", documentId, e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            updateDocumentStatus(document, STATUS_FAILED, null, e.getMessage());
            return result;
        }
    }

    private ParsingResult executeParsingWithRetry(KnowledgeDocument document) {
        int attempt = 0;
        long interval = RETRY_INITIAL_INTERVAL_MS;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                KnowledgeParsingTask task = createParsingTask(
                        document.getId(),
                        document.getKnowledgeBaseId(),
                        document.getFileType()
                );
                return executeParsingTask(task, Map.of());
            } catch (Exception e) {
                lastException = e;
                attempt++;
                if (attempt < MAX_RETRIES) {
                    log.warn("Parsing attempt {} failed, retrying in {}ms: {}",
                            attempt, interval, e.getMessage());
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    interval = Math.min((long) (interval * RETRY_MULTIPLIER), RETRY_MAX_INTERVAL_MS);
                }
            }
        }
        return ParsingResult.failure("解析失败，已重试 " + MAX_RETRIES + " 次: " +
                (lastException != null ? lastException.getMessage() : "未知错误"));
    }

    @Transactional
    public ParsingResult executeParsingTask(KnowledgeParsingTask task, Map<String, String> config) {
        log.info("Executing parsing task: {}, type: {}", task.getId(), task.getTaskType());

        task.start();
        taskRepository.save(task);

        try {
            DocumentParser parser = parserFactory.getParserByTaskType(task.getTaskType());
            if (parser == null) {
                throw new RuntimeException("No parser found for task type: " + task.getTaskType());
            }

            String outputPath = task.getInputPath().replaceFirst("data/", "parsed/");
            outputPath = outputPath.replaceFirst("\\.[^.]+$", ".txt");

            ParsingResult result = parser.parse(task.getInputPath(), outputPath, config);

            if (result.isSuccess()) {
                task.setOutputPath(outputPath);
                task.complete();
                taskRepository.save(task);
                updateDocumentParseStatus(task.getDocumentId(), STATUS_SUCCESS, outputPath, null);
                log.info("Parsing task completed: {}, output: {}", task.getId(), outputPath);
            } else {
                task.fail(result.getErrorMessage());
                taskRepository.save(task);
                updateDocumentParseStatus(task.getDocumentId(), STATUS_FAILED, null, result.getErrorMessage());
                log.warn("Parsing task failed: {}, error: {}", task.getId(), result.getErrorMessage());
            }

            return result;

        } catch (Exception e) {
            log.error("Parsing task error: {}", task.getId(), e);
            task.fail(e.getMessage());
            taskRepository.save(task);
            updateDocumentParseStatus(task.getDocumentId(), STATUS_FAILED, null, e.getMessage());
            return ParsingResult.failure(e.getMessage());
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return text.trim();
    }

    private String determineTaskType(String fileType) {
        String category = ParserFactory.getMediaCategory(fileType);

        return switch (category) {
            case "image" -> KnowledgeParsingTask.TASK_TYPE_OCR;
            case "audio", "video" -> KnowledgeParsingTask.TASK_TYPE_ASR;
            case "pdf" -> KnowledgeParsingTask.TASK_TYPE_PDF_EXTRACT;
            default -> KnowledgeParsingTask.TASK_TYPE_TEXT_EXTRACT;
        };
    }

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

    private void updateDocumentStatus(KnowledgeDocument document, String status,
                                       String parsedPath, String error) {
        document.setParseStatus(status);
        if (parsedPath != null) {
            document.setParsedPath(parsedPath);
        }
        if (error != null) {
            document.setParseError(error);
        }
        documentRepository.save(document);
    }

    public boolean requiresAsyncParsing(String fileType) {
        String category = ParserFactory.getMediaCategory(fileType);
        return category.equals("image") || category.equals("audio") || category.equals("video");
    }

    @Transactional
    public ParsingResult retryTask(String taskId) {
        KnowledgeParsingTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new IllegalStateException("只能重试失败的任务");
        }

        task.incrementRetry();
        task.setStatus(STATUS_QUEUED);
        task.setErrorMessage(null);
        taskRepository.save(task);

        log.info("Retrying task: {}, attempt: {}", taskId, task.getRetryCount());

        return executeParsingTask(task, Map.of());
    }

    // Pipeline执行结果
    public static class PipelineResult {
        private boolean success;
        private String stage;
        private String parsedTextPath;
        private int charCount;
        private int chunkCount;
        private String errorMessage;
        private long processingTimeMs;

        public PipelineResult() {}

        public PipelineResult(boolean success, String stage, String parsedTextPath,
                             int charCount, int chunkCount, String errorMessage, long processingTimeMs) {
            this.success = success;
            this.stage = stage;
            this.parsedTextPath = parsedTextPath;
            this.charCount = charCount;
            this.chunkCount = chunkCount;
            this.errorMessage = errorMessage;
            this.processingTimeMs = processingTimeMs;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getStage() { return stage; }
        public void setStage(String stage) { this.stage = stage; }
        public String getParsedTextPath() { return parsedTextPath; }
        public void setParsedTextPath(String parsedTextPath) { this.parsedTextPath = parsedTextPath; }
        public int getCharCount() { return charCount; }
        public void setCharCount(int charCount) { this.charCount = charCount; }
        public int getChunkCount() { return chunkCount; }
        public void setChunkCount(int chunkCount) { this.chunkCount = chunkCount; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public void setProcessingTimeMs(long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
    }
}
