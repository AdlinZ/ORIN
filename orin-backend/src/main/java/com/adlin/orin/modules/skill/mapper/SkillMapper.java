package com.adlin.orin.modules.skill.mapper;

import com.adlin.orin.modules.skill.dto.SkillRequest;
import com.adlin.orin.modules.skill.dto.SkillResponse;
import com.adlin.orin.modules.skill.entity.SkillEntity;
import org.mapstruct.*;

import java.util.List;

/**
 * 技能实体与DTO转换Mapper
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true))
public interface SkillMapper {

    /**
     * Entity转Response DTO
     */
    SkillResponse toResponse(SkillEntity entity);

    /**
     * Entity列表转Response列表
     */
    List<SkillResponse> toResponseList(List<SkillEntity> entities);

    /**
     * Request转Entity（创建时使用）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "skillMdContent", ignore = true)
    SkillEntity toEntity(SkillRequest request);

    /**
     * Request更新Entity（更新时使用）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "skillMdContent", ignore = true)
    void updateEntityFromRequest(SkillRequest request, @MappingTarget SkillEntity entity);
}
