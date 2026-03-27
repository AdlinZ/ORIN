package com.adlin.orin.modules.knowledge.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_bases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBase {

    @Id
    @Column(name = "id")
    private String id; // Dify Dataset ID

    @Column(name = "name")
    private String name;

    @Column(name = "type")
    @jakarta.persistence.Convert(converter = KnowledgeTypeConverter.class)
    @Builder.Default
    private KnowledgeType type = KnowledgeType.UNSTRUCTURED;

    @Column(name = "description")
    private String description;

    @Column(name = "description_model")
    private String descriptionModel;

    @Column(name = "doc_count")
    private Integer docCount;

    @Column(name = "total_size_mb")
    private Double totalSizeMb;

    @Column(name = "status")
    private String status; // ENABLED, DISABLED

    @Column(name = "source_agent_id")
    private String sourceAgentId; // ID of the agent this KB was synced from (optional binding)

    @Column(name = "sync_time")
    private LocalDateTime syncTime;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Parsing configuration (multimodal)
    @Column(name = "parsing_enabled")
    @Builder.Default
    private Boolean parsingEnabled = true;

    @Column(name = "ocr_provider")
    @Builder.Default
    private String ocrProvider = "local";

    @Column(name = "asr_provider")
    @Builder.Default
    private String asrProvider = "local";

    @Column(name = "ocr_model")
    private String ocrModel;

    @Column(name = "asr_model")
    @Builder.Default
    private String asrModel = "base";

    @Column(name = "rich_text_enabled")
    @Builder.Default
    private Boolean richTextEnabled = true;

    // ========== 检索配置 ==========
    @Column(name = "chunk_size")
    private Integer chunkSize; // null 表示使用系统默认

    @Column(name = "chunk_overlap")
    private Integer chunkOverlap; // null 表示使用系统默认

    @Column(name = "top_k")
    private Integer topK; // null 表示使用系统默认

    @Column(name = "similarity_threshold")
    private Double similarityThreshold; // null 表示使用系统默认

    @Column(name = "alpha")
    @Builder.Default
    private Double alpha = 0.7; // 向量检索权重，1-alpha 为关键词权重，默认 0.7

    // Rerank 配置
    @Column(name = "enable_rerank")
    @Builder.Default
    private Boolean enableRerank = false; // 是否启用 Rerank

    @Column(name = "rerank_model")
    private String rerankModel; // Rerank 模型名称，null 表示使用系统默认

    /**
     * Store type-specific configuration (JSON).
     * e.g., SQL Connection String, Graph DB Endpoint, or custom Tool definitions.
     */
    @Column(name = "configuration", columnDefinition = "TEXT")
    private String configuration;
}
