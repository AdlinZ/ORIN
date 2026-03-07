package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库文档分片实体
 * 支持 Parent-Child Hierarchical Chunking
 *
 * chunk_type:
 * - 'child': 子分片，用于向量检索 (~200 tokens)
 * - 'parent': 父分片，存储完整语义段落 (~1000 tokens)
 */
@Entity
@Table(name = "kb_document_chunks", indexes = {
        @Index(name = "idx_doc_id", columnList = "document_id"),
        @Index(name = "idx_chunk_index", columnList = "chunk_index"),
        @Index(name = "idx_parent_id", columnList = "parent_id"),
        @Index(name = "idx_chunk_type", columnList = "chunk_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentChunk {

    @Id
    // 使用手动设置的 ID (来自 HierarchicalTextSplitter 的 deterministic ID)
    // 不使用 @GeneratedValue，否则会覆盖代码设置的 ID
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
     */
    @Column(name = "vector_id", length = 100)
    private String vectorId;

    /**
     * 字符数
     */
    @Column(name = "char_count")
    private Integer charCount;

    // ========== Parent-Child Hierarchical Chunking Fields ==========

    /**
     * 分片类型: 'child' or 'parent'
     * 只有 child 类型会进行向量嵌入
     */
    @Column(name = "chunk_type", length = 20)
    @Builder.Default
    private String chunkType = "child";

    /**
     * 父分片 ID (仅 child 类型使用)
     * 格式: p_{hash}
     */
    @Column(name = "parent_id", length = 64)
    private String parentId;

    /**
     * 子分片 IDs (仅 parent 类型使用)
     * JSON 格式: ["c_123", "c_456", ...]
     */
    @Column(name = "children_ids", columnDefinition = "TEXT")
    private String childrenIds;

    /**
     * 标题/摘要 (用于检索结果展示)
     */
    @Column(name = "title", length = 500)
    private String title;

    /**
     * 来源文档标题 (用于 metadata)
     */
    @Column(name = "source", length = 500)
    private String source;

    /**
     * 位置索引 (在父分片内的位置)
     */
    @Column(name = "position")
    private Integer position;

    @PrePersist
    protected void onCreate() {
        if (charCount == null && content != null) {
            charCount = content.length();
        }
        if (chunkType == null) {
            chunkType = "child";
        }
    }
}
