package com.adlin.orin.modules.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseCreateRequest {

    @NotBlank(message = "知识库名称不能为空")
    private String name;

    private String description;

    private String agentId;

    private String type;

    @Builder.Default
    private Boolean enabled = true;

    // 检索配置（可选，不设置则使用系统默认）
    private Integer chunkSize;
    private Integer chunkOverlap;
    private Integer topK;
    private Double similarityThreshold;
    private Double alpha;

    // Rerank 配置
    private Boolean enableRerank;
    private String rerankModel;
}
