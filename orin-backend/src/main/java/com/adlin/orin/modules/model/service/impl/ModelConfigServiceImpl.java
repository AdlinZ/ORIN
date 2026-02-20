package com.adlin.orin.modules.model.service.impl;

import com.adlin.orin.modules.agent.service.DifyIntegrationService;
import com.adlin.orin.modules.model.service.SiliconFlowIntegrationService;
import com.adlin.orin.modules.model.service.ZhipuIntegrationService;
import com.adlin.orin.modules.model.service.DeepSeekIntegrationService;
import com.adlin.orin.modules.model.service.MinimaxIntegrationService;
import com.adlin.orin.modules.model.entity.ModelConfig;
import com.adlin.orin.modules.model.repository.ModelConfigRepository;
import com.adlin.orin.modules.model.service.ModelConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigRepository modelConfigRepository;
    private final DifyIntegrationService difyIntegrationService;
    private final SiliconFlowIntegrationService siliconFlowIntegrationService;
    private final ZhipuIntegrationService zhipuIntegrationService;
    private final DeepSeekIntegrationService deepSeekIntegrationService;
    private final MinimaxIntegrationService minimaxIntegrationService;

    @Autowired
    public ModelConfigServiceImpl(ModelConfigRepository modelConfigRepository,
            DifyIntegrationService difyIntegrationService,
            SiliconFlowIntegrationService siliconFlowIntegrationService,
            ZhipuIntegrationService zhipuIntegrationService,
            DeepSeekIntegrationService deepSeekIntegrationService,
            MinimaxIntegrationService minimaxIntegrationService) {
        this.modelConfigRepository = modelConfigRepository;
        this.difyIntegrationService = difyIntegrationService;
        this.siliconFlowIntegrationService = siliconFlowIntegrationService;
        this.zhipuIntegrationService = zhipuIntegrationService;
        this.deepSeekIntegrationService = deepSeekIntegrationService;
        this.minimaxIntegrationService = minimaxIntegrationService;
    }

    @Override
    public ModelConfig getConfig() {
        Optional<ModelConfig> existingConfig = modelConfigRepository.findFirstByOrderByIdDesc();
        if (existingConfig.isPresent()) {
            ModelConfig config = existingConfig.get();
            // Ensure defaults for new fields if they are null
            if (config.getVlmModel() == null)
                config.setVlmModel("Qwen/Qwen2-VL-72B-Instruct");
            if (config.getEmbeddingModel() == null)
                config.setEmbeddingModel("BAAI/bge-m3");
            if (config.getAutoAnalysisEnabled() == null)
                config.setAutoAnalysisEnabled(true);
            return config;
        }

        // Create default configuration if none exists
        ModelConfig defaultConfig = new ModelConfig();
        defaultConfig.setBaseUrl("http://localhost:3000");
        defaultConfig.setUsername("admin");
        defaultConfig.setApiPath("/api/v1");
        defaultConfig.setTimeout(30000);
        defaultConfig.setLlamaFactoryPath("/usr/local/bin/llama-factory");
        defaultConfig.setLlamaFactoryWebUI("http://localhost:7860");
        defaultConfig.setModelSavePath("/app/models/checkpoints");
        defaultConfig.setRemark("核心大模型调度中枢配置");
        defaultConfig.setDifyEndpoint("http://localhost:3000/v1");
        defaultConfig.setDifyApiKey("");
        defaultConfig.setSiliconFlowEndpoint("https://api.siliconflow.cn/v1");
        defaultConfig.setSiliconFlowApiKey("");
        defaultConfig.setSiliconFlowModel("Qwen/Qwen2-7B-Instruct");

        // Multimodal defaults
        defaultConfig.setVlmModel("Qwen/Qwen2-VL-72B-Instruct");
        defaultConfig.setEmbeddingModel("BAAI/bge-m3");
        defaultConfig.setAutoAnalysisEnabled(true);

        return modelConfigRepository.save(defaultConfig);
    }

    @Override
    public ModelConfig updateConfig(ModelConfig config) {
        Optional<ModelConfig> existingConfig = modelConfigRepository.findFirstByOrderByIdDesc();

        if (existingConfig.isPresent()) {
            ModelConfig existing = existingConfig.get();
            // Update existing config with new values
            existing.setBaseUrl(config.getBaseUrl());
            existing.setUsername(config.getUsername());
            // Only update password if provided (not empty)
            if (config.getPassword() != null && !config.getPassword().isEmpty()) {
                existing.setPassword(config.getPassword());
            }
            existing.setApiPath(config.getApiPath());
            existing.setTimeout(config.getTimeout());
            existing.setLlamaFactoryPath(config.getLlamaFactoryPath());
            existing.setLlamaFactoryWebUI(config.getLlamaFactoryWebUI());
            existing.setModelSavePath(config.getModelSavePath());
            existing.setRemark(config.getRemark());
            existing.setDifyEndpoint(config.getDifyEndpoint());
            // Only update Dify API key if provided
            if (config.getDifyApiKey() != null) {
                existing.setDifyApiKey(config.getDifyApiKey());
            }

            // Update SiliconFlow configuration
            existing.setSiliconFlowEndpoint(config.getSiliconFlowEndpoint());
            if (config.getSiliconFlowApiKey() != null) {
                existing.setSiliconFlowApiKey(config.getSiliconFlowApiKey());
            }
            existing.setSiliconFlowModel(config.getSiliconFlowModel());

            // Update Multimodal configuration
            existing.setVlmModel(config.getVlmModel());
            existing.setEmbeddingModel(config.getEmbeddingModel());
            existing.setAutoAnalysisEnabled(config.getAutoAnalysisEnabled());

            return modelConfigRepository.save(existing);
        } else {
            // If no config exists, save the new one
            return modelConfigRepository.save(config);
        }
    }

    @Override
    public boolean testDifyConnection(String endpoint, String apiKey) {
        try {
            return difyIntegrationService.testConnection(endpoint, apiKey);
        } catch (Exception e) {
            log.error("Error testing Dify connection: ", e);
            return false;
        }
    }

    @Override
    public boolean testSiliconFlowConnection(String endpoint, String apiKey, String model) {
        try {
            return testSiliconFlowConnectionInternal(endpoint, apiKey, model);
        } catch (Exception e) {
            log.error("Error testing SiliconFlow connection: ", e);
            return false;
        }
    }

    @Override
    public boolean testZhipuConnection(String endpoint, String apiKey, String model) {
        try {
            return zhipuIntegrationService.testConnection(endpoint, apiKey, model);
        } catch (Exception e) {
            log.error("Error testing Zhipu AI connection: ", e);
            return false;
        }
    }

    @Override
    public boolean testDeepSeekConnection(String endpoint, String apiKey, String model) {
        try {
            return deepSeekIntegrationService.testConnection(endpoint, apiKey, model);
        } catch (Exception e) {
            log.error("Error testing DeepSeek connection: ", e);
            return false;
        }
    }

    @Override
    public boolean testMinimaxConnection(String endpoint, String apiKey, String model) {
        try {
            return minimaxIntegrationService.testConnection(endpoint, apiKey, model);
        } catch (Exception e) {
            log.error("Error testing MiniMax connection: ", e);
            return false;
        }
    }

    private boolean testSiliconFlowConnectionInternal(String endpoint, String apiKey, String model) {
        try {
            siliconFlowIntegrationService.testConnection(endpoint, apiKey);
            return true;
        } catch (Exception e) {
            log.error("SiliconFlow connection test internal failed: ", e);
            return false;
        }
    }
}