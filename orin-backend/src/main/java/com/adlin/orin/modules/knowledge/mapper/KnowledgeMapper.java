package com.adlin.orin.modules.knowledge.mapper;

import com.adlin.orin.modules.knowledge.dto.KnowledgeBaseCreateRequest;
import com.adlin.orin.modules.knowledge.dto.KnowledgeBaseResponse;
import com.adlin.orin.modules.knowledge.entity.KnowledgeBase;
import org.mapstruct.*;

import java.util.List;

/**
 * 知识库实体与DTO转换Mapper
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface KnowledgeMapper {

    /**
     * Entity转Response DTO
     */
    @Mapping(target = "kbId", source = "id")
    @Mapping(target = "agentId", source = "sourceAgentId")
    @Mapping(target = "documentCount", source = "docCount")
    @Mapping(target = "enabled", expression = "java(\"ENABLED\".equalsIgnoreCase(knowledgeBase.getStatus()))")
    @Mapping(target = "updatedAt", ignore = true) // Entity中没有updatedAt
    KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase);

    /**
     * Entity列表转Response列表
     */
    List<KnowledgeBaseResponse> toResponseList(List<KnowledgeBase> knowledgeBases);

    /**
     * CreateRequest转Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sourceAgentId", source = "agentId")
    @Mapping(target = "status", expression = "java(request.getEnabled() != null && request.getEnabled() ? \"ENABLED\" : \"DISABLED\")")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "docCount", constant = "0")
    @Mapping(target = "totalSizeMb", constant = "0.0")
    @Mapping(target = "syncTime", ignore = true)
    @Mapping(target = "configuration", ignore = true)
    KnowledgeBase toEntity(KnowledgeBaseCreateRequest request);
}
