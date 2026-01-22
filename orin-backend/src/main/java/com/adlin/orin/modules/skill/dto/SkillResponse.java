package com.adlin.orin.modules.skill.dto;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 技能响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillResponse {

    private Long id;
    private String skillName;
    private SkillEntity.SkillType skillType;
    private String description;

    // MCP 元数据
    private Map<String, Object> mcpMetadata;
    private String skillMdContent;

    // API 配置
    private String apiEndpoint;
    private String apiMethod;
    private Map<String, String> apiHeaders;

    // 知识库配置
    private Long knowledgeConfigId;

    // 复合技能配置
    private Long workflowId;

    // 外部平台
    private String externalPlatform;
    private String externalReference;

    // Schema
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;

    // 状态
    private SkillEntity.SkillStatus status;
    private String version;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从实体转换为响应 DTO
     */
    public static SkillResponse fromEntity(SkillEntity entity) {
        return SkillResponse.builder()
                .id(entity.getId())
                .skillName(entity.getSkillName())
                .skillType(entity.getSkillType())
                .description(entity.getDescription())
                .mcpMetadata(entity.getMcpMetadata())
                .skillMdContent(entity.getSkillMdContent())
                .apiEndpoint(entity.getApiEndpoint())
                .apiMethod(entity.getApiMethod())
                .apiHeaders(entity.getApiHeaders())
                .knowledgeConfigId(entity.getKnowledgeConfigId())
                .workflowId(entity.getWorkflowId())
                .externalPlatform(entity.getExternalPlatform())
                .externalReference(entity.getExternalReference())
                .inputSchema(entity.getInputSchema())
                .outputSchema(entity.getOutputSchema())
                .status(entity.getStatus())
                .version(entity.getVersion())
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
