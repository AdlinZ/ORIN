package com.adlin.orin.modules.skill.controller;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import com.adlin.orin.modules.skill.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 技能管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Tag(name = "Skill Management", description = "技能管理 API")
public class SkillController {

    private final SkillService skillService;

    @PostMapping
    @Operation(summary = "创建技能")
    public ResponseEntity<SkillResponse> createSkill(@RequestBody SkillRequest request) {
        log.info("REST request to create skill: {}", request.getSkillName());
        SkillResponse response = skillService.createSkill(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新技能")
    public ResponseEntity<SkillResponse> updateSkill(
            @PathVariable Long id,
            @RequestBody SkillRequest request) {
        log.info("REST request to update skill: {}", id);
        SkillResponse response = skillService.updateSkill(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除技能")
    public ResponseEntity<Void> deleteSkill(@PathVariable Long id) {
        log.info("REST request to delete skill: {}", id);
        skillService.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取技能详情")
    public ResponseEntity<SkillResponse> getSkill(@PathVariable Long id) {
        log.info("REST request to get skill: {}", id);
        SkillResponse response = skillService.getSkillById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "获取所有技能")
    public ResponseEntity<List<SkillResponse>> getAllSkills(
            @RequestParam(required = false) SkillEntity.SkillType type,
            @RequestParam(required = false) SkillEntity.SkillStatus status) {
        log.info("REST request to get all skills, type: {}, status: {}", type, status);

        List<SkillResponse> skills;
        if (type != null) {
            skills = skillService.getSkillsByType(type);
        } else if (status != null) {
            skills = skillService.getSkillsByStatus(status);
        } else {
            skills = skillService.getAllSkills();
        }

        return ResponseEntity.ok(skills);
    }

    @GetMapping("/{id}/skill-md")
    @Operation(summary = "获取技能的 SKILL.md 内容")
    public ResponseEntity<Map<String, String>> getSkillMd(@PathVariable Long id) {
        log.info("REST request to get SKILL.md for skill: {}", id);
        String skillMd = skillService.generateSkillMd(id);
        return ResponseEntity.ok(Map.of("content", skillMd));
    }

    @PostMapping("/import")
    @Operation(summary = "从外部平台导入技能")
    public ResponseEntity<SkillResponse> importSkill(
            @RequestParam String platform,
            @RequestParam String reference,
            @RequestBody Map<String, Object> config) {
        log.info("REST request to import skill from platform: {}, reference: {}", platform, reference);
        SkillResponse response = skillService.importExternalSkill(platform, reference, config);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "执行技能")
    public ResponseEntity<Map<String, Object>> executeSkill(
            @PathVariable Long id,
            @RequestBody Map<String, Object> inputs) {
        log.info("REST request to execute skill: {}", id);
        Map<String, Object> result = skillService.executeSkill(id, inputs);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/validate")
    @Operation(summary = "验证技能配置")
    public ResponseEntity<Map<String, Boolean>> validateSkill(@RequestBody SkillRequest request) {
        log.info("REST request to validate skill configuration");
        boolean isValid = skillService.validateSkillConfig(request);
        return ResponseEntity.ok(Map.of("valid", isValid));
    }
}
