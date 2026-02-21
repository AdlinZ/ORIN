package com.adlin.orin.modules.agent.controller;

import com.adlin.orin.common.dto.Result;
import com.adlin.orin.modules.agent.repository.AgentMetadataRepository;
import com.adlin.orin.modules.monitor.repository.AgentHealthStatusRepository;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent管理控制器 - 用于管理员操作
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/agents")
@Tag(name = "Agent Admin", description = "Agent管理接口")
public class AgentAdminController {

    @Autowired
    private AgentMetadataRepository metadataRepository;

    @Autowired
    private AgentHealthStatusRepository healthStatusRepository;

    @Autowired
    private ModelMetadataRepository modelMetadataRepository;

    @PostMapping("/sync-viewtype")
    @Operation(summary = "同步所有智能体的viewType")
    public Result<Map<String, Object>> syncAllAgentViewTypes() {
        log.info("Starting to sync viewType for all agents");

        int updatedMetadata = 0;
        int updatedHealth = 0;

        // 获取所有智能体元数据
        var allAgents = metadataRepository.findAll();

        for (var agent : allAgents) {
            String modelName = agent.getModelName();
            if (modelName == null || modelName.isEmpty()) {
                continue;
            }

            // 查找模型类型
            var modelOpt = modelMetadataRepository.findByModelId(modelName);
            if (modelOpt.isPresent()) {
                String modelType = modelOpt.get().getType();
                if (modelType != null && !modelType.isEmpty()) {
                    // 更新 AgentMetadata
                    if (!modelType.equals(agent.getViewType())) {
                        agent.setViewType(modelType);
                        metadataRepository.save(agent);
                        updatedMetadata++;
                        log.info("Updated viewType for agent {} to {}", agent.getAgentId(), modelType);
                    }

                    // 更新 AgentHealthStatus
                    var healthOpt = healthStatusRepository.findById(agent.getAgentId());
                    if (healthOpt.isPresent()) {
                        var health = healthOpt.get();
                        if (!modelType.equals(health.getViewType())) {
                            health.setViewType(modelType);
                            healthStatusRepository.save(health);
                            updatedHealth++;
                        }
                    }
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("updatedMetadata", updatedMetadata);
        data.put("updatedHealth", updatedHealth);

        return Result.success(data, String.format("Successfully synced viewType for %d agents", updatedMetadata));
    }
}
