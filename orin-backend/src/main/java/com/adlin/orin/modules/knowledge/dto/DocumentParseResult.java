package com.adlin.orin.modules.knowledge.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 文档解析结果
 */
@Data
public class DocumentParseResult {
    
    private String documentId;
    private String status; // PENDING, PARSING, PARSED, FAILED
    private String content;
    private List<String> chunks;
    private Map<String, Object> metadata;
    private String errorMessage;
}
