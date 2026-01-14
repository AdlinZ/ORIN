package com.adlin.orin.modules.model.service;

import com.adlin.orin.modules.model.entity.ModelConfig;

public interface ModelConfigService {
    ModelConfig getConfig();

    ModelConfig updateConfig(ModelConfig config);

    boolean testDifyConnection(String endpoint, String apiKey);

    boolean testSiliconFlowConnection(String endpoint, String apiKey, String model);
}