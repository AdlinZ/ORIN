package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库解析任务实体
 * 用于异步处理文档解析（OCR、ASR 等）
 */
@Entity
@Table(name = "kb_parsing_tasks", indexes = {
        @Index(name = "idx_document_id", columnList = "document_id"),
        @Index(name = "idx_knowledge_base_id", columnList = "knowledge_base_id"),
        @Index(name = "idx_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeParsingTask {

    @Id
    private String id;

    /**
     * 关联的文档 ID
     */
    @Column(name = "document_id", nullable = false)
    private String documentId;

    /**
     * 所属知识库 ID
     */
    @Column(name = "knowledge_base_id", nullable = false, length = 50)
    private String knowledgeBaseId;

    /**
     * 任务类型
     * OCR - 图片文字识别
     * ASR - 语音识别
     * PDF_EXTRACT - PDF 文本提取
     * TEXT_EXTRACT - 文本提取
     */
    @Column(name = "task_type", nullable = false, length = 20)
    private String taskType;

    /**
     * 任务状态
     * PENDING - 待处理
     * PROCESSING - 处理中
     * SUCCESS - 成功
     * FAILED - 失败
     */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    /**
     * 输入文件路径
     */
    @Column(name = "input_path", nullable = false, length = 500)
    private String inputPath;

    /**
     * 输出文件路径（解析后的文本）
     */
    @Column(name = "output_path", length = 500)
    private String outputPath;

    /**
     * 优先级（数字越大优先级越高）
     */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 0;

    /**
     * 错误信息
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 重试次数
     */
    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    /**
     * 开始处理时间
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * 完成时间
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 创建时间
     */
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 任务类型常量
    public static final String TASK_TYPE_OCR = "OCR";
    public static final String TASK_TYPE_ASR = "ASR";
    public static final String TASK_TYPE_PDF_EXTRACT = "PDF_EXTRACT";
    public static final String TASK_TYPE_TEXT_EXTRACT = "TEXT_EXTRACT";

    // 任务状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 开始处理
     */
    public void start() {
        this.status = STATUS_PROCESSING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * 成功完成
     */
    public void complete() {
        this.status = STATUS_SUCCESS;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 失败
     */
    public void fail(String errorMessage) {
        this.status = STATUS_FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 增加重试次数
     */
    public void incrementRetry() {
        this.retryCount++;
    }
}
