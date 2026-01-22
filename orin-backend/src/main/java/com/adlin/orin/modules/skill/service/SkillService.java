package com.adlin.orin.modules.skill.service;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;

import java.util.List;
import java.util.Map;

/**
 * 技能服务接口
 */
public interface SkillService {

    /**
     * 创建技能
     */
    SkillResponse createSkill(SkillRequest request);

    /**
     * 更新技能
     */
    SkillResponse updateSkill(Long id, SkillRequest request);

    /**
     * 删除技能
     */
    void deleteSkill(Long id);

    /**
     * 根据 ID 获取技能
     */
    SkillResponse getSkillById(Long id);

    /**
     * 获取所有技能
     */
    List<SkillResponse> getAllSkills();

    /**
     * 根据类型获取技能
     */
    List<SkillResponse> getSkillsByType(SkillEntity.SkillType skillType);

    /**
     * 根据状态获取技能
     */
    List<SkillResponse> getSkillsByStatus(SkillEntity.SkillStatus status);

    /**
     * 生成符合 MCP 标准的 SKILL.md
     */
    String generateSkillMd(Long id);

    /**
     * 从外部平台导入技能
     */
    SkillResponse importExternalSkill(String platform, String reference, Map<String, Object> config);

    /**
     * 执行技能
     */
    Map<String, Object> executeSkill(Long id, Map<String, Object> inputs);

    /**
     * 验证技能配置
     */
    boolean validateSkillConfig(SkillRequest request);
}
