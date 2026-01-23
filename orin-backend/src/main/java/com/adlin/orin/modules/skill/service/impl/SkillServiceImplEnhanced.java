package com.adlin.orin.modules.skill.service.impl;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import com.adlin.orin.modules.skill.service.SkillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import com.adlin.orin.modules.knowledge.service.MilvusVectorService;
import org.springframework.beans.factory.annotation.Value;

/**
 * 技能服务实现类 - 增强版
 * 包含完整的 API 调用和知识库检索逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImplEnhanced implements SkillService {

    private final SkillRepository skillRepository;
    private final RestTemplate restTemplate;
    private final MilvusVectorService milvusVectorService;

    @Value("${milvus.host:localhost}")
    private String milvusHost;

    @Value("${milvus.port:19530}")
    private int milvusPort;

    @Value("${milvus.token:}")
    private String milvusToken;

    @Override
    @Transactional
    public SkillResponse createSkill(SkillRequest request) {
        log.info("Creating skill: {}", request.getSkillName());

        if (skillRepository.existsBySkillName(request.getSkillName())) {
            throw new IllegalArgumentException("Skill name already exists: " + request.getSkillName());
        }

        if (!validateSkillConfig(request)) {
            throw new IllegalArgumentException("Invalid skill configuration");
        }

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

        entity.setSkillMdContent(generateSkillMdContent(entity));
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

        if (request.getSkillName() != null)
            entity.setSkillName(request.getSkillName());
        if (request.getDescription() != null)
            entity.setDescription(request.getDescription());
        if (request.getMcpMetadata() != null)
            entity.setMcpMetadata(request.getMcpMetadata());
        if (request.getApiEndpoint() != null)
            entity.setApiEndpoint(request.getApiEndpoint());
        if (request.getApiMethod() != null)
            entity.setApiMethod(request.getApiMethod());
        if (request.getApiHeaders() != null)
            entity.setApiHeaders(request.getApiHeaders());
        if (request.getInputSchema() != null)
            entity.setInputSchema(request.getInputSchema());
        if (request.getOutputSchema() != null)
            entity.setOutputSchema(request.getOutputSchema());

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
     * 执行 API 类型技能 - 完整实现
     */
    private Map<String, Object> executeApiSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing API skill: {}", skill.getSkillName());

        try {
            String url = skill.getApiEndpoint();
            String method = skill.getApiMethod();

            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (skill.getApiHeaders() != null) {
                skill.getApiHeaders().forEach(headers::set);
            }

            // 构建请求体
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(inputs, headers);

            // 根据 HTTP 方法执行请求
            ResponseEntity<Map> response;
            switch (method.toUpperCase()) {
                case "GET":
                    String queryParams = buildQueryParams(inputs);
                    String getUrl = url + (queryParams.isEmpty() ? "" : "?" + queryParams);
                    response = restTemplate.exchange(getUrl, HttpMethod.GET,
                            new HttpEntity<>(headers), Map.class);
                    break;

                case "POST":
                    response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
                    break;

                case "PUT":
                    response = restTemplate.exchange(url, HttpMethod.PUT, entity, Map.class);
                    break;

                case "DELETE":
                    response = restTemplate.exchange(url, HttpMethod.DELETE, entity, Map.class);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported HTTP method: " + method);
            }

            // 处理响应
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("statusCode", response.getStatusCode().value());
            result.put("data", response.getBody());

            return result;

        } catch (Exception e) {
            log.error("API skill execution failed: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            errorResult.put("errorType", e.getClass().getSimpleName());
            return errorResult;
        }
    }

    /**
     * 构建查询参数字符串
     */
    private String buildQueryParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        return params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    /**
     * 执行知识库类型技能 - 完整实现
     */
    private Map<String, Object> executeKnowledgeSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing knowledge skill: {}", skill.getSkillName());

        try {
            Long knowledgeConfigId = skill.getKnowledgeConfigId();
            if (knowledgeConfigId == null) {
                throw new IllegalStateException("Knowledge config ID is not set for skill: " + skill.getSkillName());
            }

            // 获取查询文本
            String query = (String) inputs.getOrDefault("query", inputs.getOrDefault("question", ""));
            if (query.isEmpty()) {
                throw new IllegalArgumentException("Query text is required for knowledge skill");
            }

            // 获取检索参数
            int topK = (int) inputs.getOrDefault("topK", 5);
            double threshold = (double) inputs.getOrDefault("threshold", 0.7);

            // 1. Convert text to vector
            String embeddingModel = (String) inputs.getOrDefault("embeddingModel", "text-embedding-ada-002");
            List<Float> queryVector = milvusVectorService.textToVector(query, embeddingModel);

            // 2. Search in Milvus
            String collectionName = "kb_" + knowledgeConfigId;
            List<Map<String, Object>> searchResults = milvusVectorService.search(
                    milvusHost, milvusPort, milvusToken,
                    collectionName,
                    queryVector,
                    topK,
                    threshold);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("query", query);
            result.put("knowledgeConfigId", knowledgeConfigId);
            result.put("topK", topK);
            result.put("threshold", threshold);
            result.put("documents", searchResults);

            if (searchResults.isEmpty()) {
                result.put("message", "No relevant documents found.");
            } else {
                result.put("message", "Found " + searchResults.size() + " relevant documents.");
            }

            return result;

        } catch (Exception e) {
            log.error("Knowledge skill execution failed: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * 执行复合类型技能
     */
    private Map<String, Object> executeCompositeSkill(SkillEntity skill, Map<String, Object> inputs) {
        log.info("Executing composite skill: {}", skill.getSkillName());

        // TODO: [Plan] Integrate Workflow Engine (e.g. LiteFlow/Flowable) to support
        // composite skills
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Composite skill execution is not supported yet (Workflow Engine missing).");
        result.put("workflowId", skill.getWorkflowId());

        return result;
    }
}
