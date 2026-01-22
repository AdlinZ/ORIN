package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import com.adlin.orin.modules.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 技能服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public SkillResponse createSkill(SkillRequest request) {
        log.info("Creating skill: {}", request.getSkillName());

        // 检查技能名称是否已存在
        if (skillRepository.existsBySkillName(request.getSkillName())) {
            throw new IllegalArgumentException("Skill name already exists: " + request.getSkillName());
        }

        // 验证技能配置
        if (!validateSkillConfig(request)) {
            throw new IllegalArgumentException("Invalid skill configuration");
        }

        // 创建实体
        SkillEntity entity = SkillEntity.builder()
                .skillName(request.getSkillName())
                .skillType(request.getSkillType())
                .description(request.getDescription())
                .mcpMetadata(request.getMcpMetadata())
                .apiEndpoint(request.getApiEndpoint())
                .apiMethod(request.getApiMethod())
                .apiHeaders(request.getApiHeaders())
                .knowledgeConfigId(request.getKnowledgeConfigId())
                .workflowId(request.getWorkflowId())
                .externalPlatform(request.getExternalPlatform())
                .externalReference(request.getExternalReference())
                .inputSchema(request.getInputSchema())
                .outputSchema(request.getOutputSchema())
                .version(request.getVersion() != null ? request.getVersion() : "1.0.0")
                .createdBy(request.getCreatedBy())
                .status(SkillEntity.SkillStatus.ACTIVE)
                .build();

        // 生成 SKILL.md
        entity.setSkillMdContent(generateSkillMdContent(entity));

        // 保存
        SkillEntity saved = skillRepository.save(entity);
        log.info("Skill created successfully with ID: {}", saved.getId());

        return SkillResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public SkillResponse updateSkill(Long id, SkillRequest request) {
        log.info("Updating skill ID: {}", id);

        SkillEntity entity = skillRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));

        // 更新字段
        if (request.getSkillName() != null) {
            entity.setSkillName(request.getSkillName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getMcpMetadata() != null) {
            entity.setMcpMetadata(request.getMcpMetadata());
        }
        if (request.getApiEndpoint() != null) {
            entity.setApiEndpoint(request.getApiEndpoint());
        }
        if (request.getApiMethod() != null) {
            entity.setApiMethod(request.getApiMethod());
        }
        if (request.getApiHeaders() != null) {
            entity.setApiHeaders(request.getApiHeaders());
        }
        if (request.getInputSchema() != null) {
            entity.setInputSchema(request.getInputSchema());
        }
        if (request.getOutputSchema() != null) {
            entity.setOutputSchema(request.getOutputSchema());
        }

        // 重新生成 SKILL.md
        entity.setSkillMdContent(generateSkillMdContent(entity));

        SkillEntity updated = skillRepository.save(entity);
        log.info("Skill updated successfully: {}", id);

        return SkillResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteSkill(Long id) {
        log.info("Deleting skill ID: {}", id);

        if (!skillRepository.existsById(id)) {
            throw new IllegalArgumentException("Skill not found: " + id);
        }

        skillRepository.deleteById(id);
        log.info("Skill deleted successfully: {}", id);
    }

    @Override
    public SkillResponse getSkillById(Long id) {
        SkillEntity entity = skillRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
        return SkillResponse.fromEntity(entity);
    }

    @Override
    public List<SkillResponse> getAllSkills() {
        return skillRepository.findAll().stream()
                .map(SkillResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillResponse> getSkillsByType(SkillEntity.SkillType skillType) {
        return skillRepository.findBySkillType(skillType).stream()
                .map(SkillResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillResponse> getSkillsByStatus(SkillEntity.SkillStatus status) {
        return skillRepository.findByStatus(status).stream()
                .map(SkillResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public String generateSkillMd(Long id) {
        SkillEntity entity = skillRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));
        return generateSkillMdContent(entity);
    }

    @Override
    @Transactional
    public SkillResponse importExternalSkill(String platform, String reference, Map<String, Object> config) {
        log.info("Importing skill from platform: {}, reference: {}", platform, reference);

        // 创建技能请求
        SkillRequest request = SkillRequest.builder()
                .skillName((String) config.getOrDefault("name", "Imported_" + platform + "_" + reference))
                .skillType(SkillEntity.SkillType.COMPOSITE)
                .description((String) config.getOrDefault("description", "Imported from " + platform))
                .externalPlatform(platform)
                .externalReference(reference)
                .build();

        return createSkill(request);
    }

    @Override
    public Map<String, Object> executeSkill(Long id, Map<String, Object> inputs) {
        log.info("Executing skill ID: {} with inputs: {}", id, inputs);

        SkillEntity skill = skillRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + id));

        Map<String, Object> result = new HashMap<>();

        try {
            switch (skill.getSkillType()) {
                case API:
                    result = executeApiSkill(skill, inputs);
                    break;
                case KNOWLEDGE:
                    result = executeKnowledgeSkill(skill, inputs);
                    break;
                case COMPOSITE:
                    result = executeCompositeSkill(skill, inputs);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported skill type: " + skill.getSkillType());
            }
            result.put("success", true);
        } catch (Exception e) {
            log.error("Error executing skill: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Override
    public boolean validateSkillConfig(SkillRequest request) {
        if (request.getSkillName() == null || request.getSkillName().trim().isEmpty()) {
            return false;
        }

        if (request.getSkillType() == null) {
            return false;
        }

        // 根据技能类型验证必需字段
        switch (request.getSkillType()) {
            case API:
                return request.getApiEndpoint() != null && request.getApiMethod() != null;
            case KNOWLEDGE:
                return request.getKnowledgeConfigId() != null;
            case COMPOSITE:
                return request.getWorkflowId() != null || request.getExternalReference() != null;
            default:
                return false;
        }
    }

    /**
     * 生成 SKILL.md 内容
     */
    private String generateSkillMdContent(SkillEntity skill) {
        StringBuilder md = new StringBuilder();
        md.append("---\n");
        md.append("name: ").append(skill.getSkillName()).append("\n");
        md.append("description: ").append(skill.getDescription() != null ? skill.getDescription() : "").append("\n");
        md.append("type: ").append(skill.getSkillType()).append("\n");
        md.append("version: ").append(skill.getVersion()).append("\n");
        md.append("---\n\n");

        md.append("# ").append(skill.getSkillName()).append("\n\n");
        md.append("## Description\n\n");
        md.append(skill.getDescription() != null ? skill.getDescription() : "No description provided").append("\n\n");

        md.append("## Type\n\n");
        md.append(skill.getSkillType()).append("\n\n");

        if (skill.getSkillType() == SkillEntity.SkillType.API) {
            md.append("## API Configuration\n\n");
            md.append("- **Endpoint**: ").append(skill.getApiEndpoint()).append("\n");
            md.append("- **Method**: ").append(skill.getApiMethod()).append("\n\n");
        }

        if (skill.getInputSchema() != null) {
            md.append("## Input Schema\n\n");
            md.append("```json\n");
            md.append(skill.getInputSchema().toString()).append("\n");
            md.append("```\n\n");
        }

        if (skill.getOutputSchema() != null) {
            md.append("## Output Schema\n\n");
            md.append("```json\n");
            md.append(skill.getOutputSchema().toString()).append("\n");
            md.append("```\n\n");
        }

        md.append("## Usage\n\n");
        md.append("This skill can be invoked through the Skill-Hub API or integrated into workflows.\n");

        return md.toString();
    }

    /**
     * 执行 API 类型技能
     */
    private Map<String, Object> executeApiSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing API skill: {}", skill.getSkillName());

        // TODO: 实现 API 调用逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("message", "API skill execution not yet implemented");
        result.put("endpoint", skill.getApiEndpoint());

        return result;
    }

    /**
     * 执行知识库类型技能
     */
    private Map<String, Object> executeKnowledgeSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing knowledge skill: {}", skill.getSkillName());

        // TODO: 实现知识库检索逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Knowledge skill execution not yet implemented");

        return result;
    }

    /**
     * 执行复合类型技能
     */
    private Map<String, Object> executeCompositeSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing composite skill: {}", skill.getSkillName());

        // TODO: 实现工作流调用逻辑
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Composite skill execution not yet implemented");

        return result;
    }
}
