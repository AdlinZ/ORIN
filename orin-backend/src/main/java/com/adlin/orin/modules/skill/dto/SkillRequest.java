package com.adlin.orin.modules.skill.dto;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 技能创建/更新请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillRequest {

    private String skillName;
    private SkillEntity.SkillType skillType;
    private String description;

    // MCP 元数据
    private Map<String, Object> mcpMetadata;

    // API 类型配置
    private String apiEndpoint;
    private String apiMethod;
    private Map<String, String> apiHeaders;

    // 知识库类型配置
    private Long knowledgeConfigId;

    // 复合技能配置
    private Long workflowId;

    // 外部平台配置
    private String externalPlatform;
    private String externalReference;

    // Schema 定义
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;

    // 元信息
    private String version;
    private String createdBy;
}
