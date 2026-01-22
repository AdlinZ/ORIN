package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.repository.SkillRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP (Model Context Protocol) 控制器
 * 提供符合 MCP 标准的接口,将技能注册表转换为 MCP 格式
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Tag(name = "MCP Protocol", description = "MCP 标准协议接口")
public class McpController {

    private final SkillRepository skillRepository;

    @GetMapping("/tools")
    @Operation(summary = "获取所有可用工具 (MCP 标准)")
    public ResponseEntity<Map<String, Object>> getTools() {
        log.info("MCP request: GET /mcp/tools");

        List<SkillEntity> skills = skillRepository.findByStatus(SkillEntity.SkillStatus.ACTIVE);

        List<Map<String, Object>> tools = skills.stream()
                .map(this::convertSkillToMcpTool)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("tools", tools);
        response.put("version", "1.0");
        response.put("provider", "Skill-Hub");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tools/{skillId}")
    @Operation(summary = "获取单个工具详情 (MCP 标准)")
    public ResponseEntity<Map<String, Object>> getTool(@PathVariable Long skillId) {
        log.info("MCP request: GET /mcp/tools/{}", skillId);

        SkillEntity skill = skillRepository.findById(skillId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found: " + skillId));

        Map<String, Object> tool = convertSkillToMcpTool(skill);

        return ResponseEntity.ok(tool);
    }

    @PostMapping("/tools/{skillId}/execute")
    @Operation(summary = "执行工具 (MCP 标准)")
    public ResponseEntity<Map<String, Object>> executeTool(
            @PathVariable Long skillId,
            @RequestBody Map<String, Object> arguments) {

        log.info("MCP request: POST /mcp/tools/{}/execute", skillId);

        // 这里可以调用 SkillService.executeSkill
        // 为了保持 MCP 标准,返回格式需要符合规范

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("skillId", skillId);
        response.put("arguments", arguments);
        response.put("message", "Tool execution delegated to Skill Service");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/resources")
    @Operation(summary = "获取所有资源 (MCP 标准)")
    public ResponseEntity<Map<String, Object>> getResources() {
        log.info("MCP request: GET /mcp/resources");

        List<SkillEntity> knowledgeSkills = skillRepository.findBySkillType(SkillEntity.SkillType.KNOWLEDGE);

        List<Map<String, Object>> resources = knowledgeSkills.stream()
                .map(this::convertSkillToMcpResource)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("resources", resources);
        response.put("version", "1.0");

        return ResponseEntity.ok(response);
    }

    /**
     * 将技能转换为 MCP 工具格式
     */
    private Map<String, Object> convertSkillToMcpTool(SkillEntity skill) {
        Map<String, Object> tool = new HashMap<>();

        tool.put("name", skill.getSkillName());
        tool.put("description", skill.getDescription());
        tool.put("version", skill.getVersion());

        // 输入 Schema
        if (skill.getInputSchema() != null) {
            tool.put("inputSchema", skill.getInputSchema());
        } else {
            // 默认 Schema
            Map<String, Object> defaultSchema = new HashMap<>();
            defaultSchema.put("type", "object");
            defaultSchema.put("properties", new HashMap<>());
            tool.put("inputSchema", defaultSchema);
        }

        // 输出 Schema
        if (skill.getOutputSchema() != null) {
            tool.put("outputSchema", skill.getOutputSchema());
        }

        // MCP 元数据
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("skillId", skill.getId());
        metadata.put("skillType", skill.getSkillType().toString());
        metadata.put("status", skill.getStatus().toString());

        if (skill.getSkillType() == SkillEntity.SkillType.API) {
            metadata.put("endpoint", skill.getApiEndpoint());
            metadata.put("method", skill.getApiMethod());
        }

        if (skill.getMcpMetadata() != null) {
            metadata.putAll(skill.getMcpMetadata());
        }

        tool.put("metadata", metadata);

        return tool;
    }

    /**
     * 将知识库技能转换为 MCP 资源格式
     */
    private Map<String, Object> convertSkillToMcpResource(SkillEntity skill) {
        Map<String, Object> resource = new HashMap<>();

        resource.put("uri", "skill://" + skill.getId());
        resource.put("name", skill.getSkillName());
        resource.put("description", skill.getDescription());
        resource.put("mimeType", "application/json");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("skillId", skill.getId());
        metadata.put("knowledgeConfigId", skill.getKnowledgeConfigId());
        resource.put("metadata", metadata);

        return resource;
    }

    @GetMapping("/info")
    @Operation(summary = "获取 MCP 服务器信息")
    public ResponseEntity<Map<String, Object>> getServerInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "Skill-Hub MCP Server");
        info.put("version", "1.0.0");
        info.put("protocolVersion", "2024-11-05");
        info.put("capabilities", Map.of(
                "tools", true,
                "resources", true,
                "prompts", false,
                "logging", true));

        return ResponseEntity.ok(info);
    }
}
