package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档分片实体
 * 用于存储文档的切片内容，支持关键词检索 (Hybrid Search)
 */
@Entity
@Table(name = "kb_document_chunks", indexes = {
        @Index(name = "idx_doc_id", columnList = "document_id"),
        @Index(name = "idx_chunk_index", columnList = "chunk_index")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    /**
     * 所属文档 ID
     */
    @Column(name = "document_id", nullable = false, length = 50)
    private String documentId;

    /**
     * 分片索引 (0, 1, 2...)
     */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    /**
     * 分片内容
     */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 向量化后的 ID (Metric ID in Milvus)
     * Optional: 如果需要精确对应 Milvus ID
     */
    @Column(name = "vector_id", length = 100)
    private String vectorId;

    /**
     * 字符数
     */
    @Column(name = "char_count")
    private Integer charCount;

    @PrePersist
    protected void onCreate() {
        if (charCount == null && content != null) {
            charCount = content.length();
        }
    }
}
