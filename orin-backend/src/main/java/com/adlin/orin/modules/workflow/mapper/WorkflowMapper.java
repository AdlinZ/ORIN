package com.adlin.orin.modules.workflow.mapper;

import com.adlin.orin.modules.workflow.dto.WorkflowRequest;
import com.adlin.orin.modules.workflow.dto.WorkflowResponse;
import com.adlin.orin.modules.workflow.dto.WorkflowStepRequest;
import com.adlin.orin.modules.workflow.entity.WorkflowEntity;
import com.adlin.orin.modules.workflow.entity.WorkflowStepEntity;
import org.mapstruct.*;

import java.util.List;

/**
 * 工作流实体与DTO转换Mapper
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE, builder = @Builder(disableBuilder = true))
public interface WorkflowMapper {

    /**
     * Entity转Response DTO
     */
    WorkflowResponse toResponse(WorkflowEntity entity);

    /**
     * Entity列表转Response列表
     */
    List<WorkflowResponse> toResponseList(List<WorkflowEntity> entities);

    /**
     * Request转Entity（创建时使用）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    WorkflowEntity toEntity(WorkflowRequest request);

    /**
     * Request更新Entity（更新时使用）
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    void updateEntityFromRequest(WorkflowRequest request, @MappingTarget WorkflowEntity entity);

    /**
     * WorkflowStepRequest转Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workflowId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    WorkflowStepEntity stepRequestToEntity(WorkflowStepRequest request);

    /**
     * 批量转换WorkflowStep
     */
    List<WorkflowStepEntity> stepRequestsToEntities(List<WorkflowStepRequest> requests);
}
