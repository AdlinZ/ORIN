package com.adlin.orin.modules.knowledge.dto;

import com.adlin.orin.modules.knowledge.entity.KnowledgeType;
import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data
@Builder
public class UnifiedKnowledgeDTO {
    private String id;
    private String name;
    private String description;
    private KnowledgeType type;
    private String status;

    /**
     * Dynamic statistics based on type:
     * UNSTRUCTURED -> chunkCount, documentCount
     * STRUCTURED -> tableCount, fieldCount
     * PROCEDURAL -> skillCount
     * META_MEMORY -> memoryEntryCount
     */
    private Map<String, Object> stats;
}
