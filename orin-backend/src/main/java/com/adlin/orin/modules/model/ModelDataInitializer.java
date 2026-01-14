package com.adlin.orin.modules.model;

import com.adlin.orin.modules.model.entity.ModelMetadata;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ModelDataInitializer implements CommandLineRunner {

    private final ModelMetadataRepository modelRepository;

    public ModelDataInitializer(ModelMetadataRepository modelRepository) {
        this.modelRepository = modelRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        if (modelRepository.count() == 0) {
            modelRepository.save(createModel("GPT-4o", "gpt-4o", "OpenAI", "LLM", "最强大的多模态大模型"));
            modelRepository
                    .save(createModel("Claude 3.5 Sonnet", "claude-3-5-sonnet", "Anthropic", "LLM", "平衡性能与速度的智能模型"));
            modelRepository.save(createModel("Llama 3 70B", "llama3-70b", "Ollama", "LLM", "本地部署的最佳开源模型"));
            modelRepository.save(createModel("Text-Embedding-3-Small", "text-embedding-3-small", "OpenAI", "EMBEDDING",
                    "高效的向量提取模型"));
            System.out.println("Default model metadata initialized.");
        }
    }

    private ModelMetadata createModel(String name, String modelId, String provider, String type, String desc) {
        ModelMetadata model = new ModelMetadata();
        model.setName(name);
        model.setModelId(modelId);
        model.setProvider(provider);
        model.setType(type);
        model.setDescription(desc);
        model.setStatus("ENABLED");
        return model;
    }
}
