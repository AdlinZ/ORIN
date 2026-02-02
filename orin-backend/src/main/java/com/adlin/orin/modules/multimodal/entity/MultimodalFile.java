package com.adlin.orin.modules.multimodal.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 多模态文件实体
 * 支持图片、音频、视频、文档等多种文件类型
 */
@Entity
@Table(name = "multimodal_files", indexes = {
        @Index(name = "idx_file_type", columnList = "file_type"),
        @Index(name = "idx_uploaded_at", columnList = "uploaded_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 文件名
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * 文件类型: IMAGE, AUDIO, VIDEO, DOCUMENT
     */
    @Column(name = "file_type", length = 50)
    private String fileType;

    /**
     * MIME 类型
     */
    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /**
     * 文件大小 (字节)
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 存储路径
     */
    @Column(name = "storage_path", length = 500)
    private String storagePath;

    /**
     * 缩略图路径
     */
    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    /**
     * 音视频时长 (秒)
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * 图片/视频宽度
     */
    @Column(name = "width")
    private Integer width;

    /**
     * 图片/视频高度
     */
    @Column(name = "height")
    private Integer height;

    /**
     * 音频转文字结果
     */
    @Column(name = "transcription", columnDefinition = "TEXT")
    private String transcription;

    /**
     * 图片 OCR 结果
     */
    @Column(name = "ocr_text", columnDefinition = "TEXT")
    private String ocrText;

    /**
     * 元数据 (JSON)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 上传者
     */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /**
     * 上传时间
     */
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /**
     * AI generated summary for multimodal content
     */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    /**
     * Status of the embedding process for this file
     */
    @Column(name = "embedding_status", length = 50)
    private String embeddingStatus; // PENDING, PROCESSING, SUCCESS, FAILED

    /**
     * Number of times the task has been retried
     */
    @Column(name = "task_retry_count", columnDefinition = "INT DEFAULT 0")
    private Integer taskRetryCount;

    @PrePersist
    protected void onCreate() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
    }
}
