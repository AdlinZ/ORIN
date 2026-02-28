package com.adlin.orin.modules.knowledge.controller;

import com.adlin.orin.modules.knowledge.entity.KnowledgeSkill;
import com.adlin.orin.modules.knowledge.entity.meta.PromptTemplate;
import com.adlin.orin.modules.knowledge.service.ProceduralService;
import com.adlin.orin.modules.knowledge.service.meta.MetaKnowledgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/intelligence")
@RequiredArgsConstructor
@Tag(name = "Phase 5: Intelligence Center", description = "智力资产中心 (Memory, Skills, Prompts)")
public class IntelligenceCenterController {

    private final MetaKnowledgeService metaKnowledgeService;
    private final ProceduralService proceduralService;

    // --- Long-Term Memory ---

    @Operation(summary = "获取智能体的长期记忆")
    @GetMapping("/memories")
    public List<Map<String, Object>> getMemories(@RequestParam String agentId) {
        return metaKnowledgeService.getAgentMemory(agentId);
    }

    @Operation(summary = "新增或更新记忆")
    @PostMapping("/memories")
    public void saveMemory(@RequestParam String agentId, @RequestBody Map<String, String> payload) {
        metaKnowledgeService.saveMemory(agentId, payload.get("key"), payload.get("value"));
    }

    @Operation(summary = "彻底遣忘(删除)记忆")
    @DeleteMapping("/memories/{id}")
    public void deleteMemory(@PathVariable String id) {
        metaKnowledgeService.deleteMemoryEntry(id);
    }

    // --- Skills & Tools ---

    @Operation(summary = "获取智能体的技能列表")
    @GetMapping("/skills")
    public List<KnowledgeSkill> getSkills(@RequestParam String agentId) {
        return proceduralService.getAgentSkills(agentId);
    }

    @Operation(summary = "保存/更新技能")
    @PostMapping("/skills")
    public void saveSkill(@RequestBody KnowledgeSkill skill) {
        if (skill.getId() != null) {
            proceduralService.updateSkill(skill);
        } else {
            proceduralService.registerSkill(skill.getAgentId(), skill.getDefinition(), skill.getTriggerName());
        }
    }

    @Operation(summary = "删除技能")
    @DeleteMapping("/skills/{id}")
    public void deleteSkill(@PathVariable String id) {
        proceduralService.deleteSkill(id);
    }

    // --- Prompt Management ---

    @Operation(summary = "获取 Prompt 模板")
    @GetMapping("/prompts")
    public List<Map<String, Object>> getPrompts(
            @RequestParam String agentId,
            @RequestParam(required = false) String userId) {
        return metaKnowledgeService.getPromptTemplatesByUser(agentId, userId);
    }

    @Operation(summary = "保存 Prompt 模板")
    @PostMapping("/prompts")
    public PromptTemplate savePrompt(@RequestBody PromptTemplate template) {
        return metaKnowledgeService.savePromptTemplate(template);
    }

    @Operation(summary = "删除 Prompt 模板")
    @DeleteMapping("/prompts/{id}")
    public void deletePrompt(@PathVariable String id) {
        metaKnowledgeService.deletePromptTemplate(id);
    }
}
