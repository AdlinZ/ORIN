package com.adlin.orin.modules.skill.dto;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 100, message = "技能名称长度不能超过100个字符")
    private String skillName;

    @NotNull(message = "技能类型不能为空")
    private SkillEntity.SkillType skillType;

    @Size(max = 2000, message = "描述长度不能超过2000个字符")
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

    // Shell 类型配置
    private String shellCommand;

    // Schema 定义
    private Map<String, Object> inputSchema;
    private Map<String, Object> outputSchema;

    // 元信息
    private String version;
    private String createdBy;
}
