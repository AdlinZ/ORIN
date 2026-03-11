package com.adlin.orin.modules.knowledge.dto;

import lombok.Data;

/**
 * 知识库检索请求
 */
@Data
public class RetrievalRequest {
    
    private String query;
    private String knowledgeBaseId;
    private Integer topK = 5;
    private Double threshold = 0.7;
    private String embeddingModel;
    private Boolean enableRerank = false;
    private String rerankModel;
}
