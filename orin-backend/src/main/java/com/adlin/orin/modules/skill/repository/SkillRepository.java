package com.adlin.orin.modules.skill.repository;

import com.adlin.orin.modules.skill.entity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 技能仓储接口
 */
@Repository
public interface SkillRepository extends JpaRepository<SkillEntity, Long> {

    /**
     * 根据技能名称查找
     */
    Optional<SkillEntity> findBySkillName(String skillName);

    /**
     * 根据技能类型查找
     */
    List<SkillEntity> findBySkillType(SkillEntity.SkillType skillType);

    /**
     * 根据状态查找
     */
    List<SkillEntity> findByStatus(SkillEntity.SkillStatus status);

    /**
     * 根据外部平台查找
     */
    List<SkillEntity> findByExternalPlatform(String externalPlatform);

    /**
     * 根据技能类型和状态查找
     */
    List<SkillEntity> findBySkillTypeAndStatus(
            SkillEntity.SkillType skillType,
            SkillEntity.SkillStatus status);

    /**
     * 检查技能名称是否存在
     */
    boolean existsBySkillName(String skillName);
}
