package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 知识库文档实体
 * 存储知识库中的文档信息，支持向量化管理
 */
@Entity
@Table(name = "kb_documents", indexes = {
        @Index(name = "idx_kb_id", columnList = "knowledge_base_id"),
        @Index(name = "idx_vector_status", columnList = "vector_status"),
        @Index(name = "idx_upload_time", columnList = "upload_time")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 所属知识库 ID
     */
    @Column(name = "knowledge_base_id", nullable = false, length = 50)
    private String knowledgeBaseId;

    /**
     * 文件名
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * 文件类型 (pdf, txt, docx, md, etc.)
     */
    @Column(name = "file_type", length = 50)
    private String fileType;

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
     * 内容预览 (前 500 字符)
     */
    @Column(name = "content_preview", columnDefinition = "TEXT")
    private String contentPreview;

    /**
     * 向量化状态: PENDING, INDEXING, INDEXED, FAILED
     */
    @Column(name = "vector_status", length = 20)
    @Builder.Default
    private String vectorStatus = "PENDING";

    /**
     * 向量索引 ID (Dify 或其他向量数据库返回的 ID)
     */
    @Column(name = "vector_index_id", length = 100)
    private String vectorIndexId;

    /**
     * 分块数量
     */
    @Column(name = "chunk_count")
    @Builder.Default
    private Integer chunkCount = 0;

    /**
     * 字符数
     */
    @Column(name = "char_count")
    @Builder.Default
    private Integer charCount = 0;

    /**
     * 上传时间
     */
    @Column(name = "upload_time", nullable = false, updatable = false)
    private LocalDateTime uploadTime;

    /**
     * 最后修改时间
     */
    @Column(name = "last_modified")
    private LocalDateTime lastModified;

    /**
     * 上传者
     */
    @Column(name = "uploaded_by", length = 100)
    private String uploadedBy;

    /**
     * 元数据 (JSON 格式，存储额外信息)
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (uploadTime == null) {
            uploadTime = LocalDateTime.now();
        }
        if (lastModified == null) {
            lastModified = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastModified = LocalDateTime.now();
    }
}
