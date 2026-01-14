package com.adlin.orin.modules.model.service;

import com.adlin.orin.modules.model.entity.ModelMetadata;
import com.adlin.orin.modules.model.repository.ModelMetadataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelManageService {

    private final ModelMetadataRepository modelRepository;

    public List<ModelMetadata> getAllModels() {
        return modelRepository.findAll();
    }

    public ModelMetadata saveModel(ModelMetadata model) {
        return modelRepository.save(model);
    }

    public void deleteModel(Long id) {
        modelRepository.deleteById(id);
    }

    public ModelMetadata toggleStatus(Long id) {
        ModelMetadata model = modelRepository.findById(id).orElseThrow();
        model.setStatus("ENABLED".equals(model.getStatus()) ? "DISABLED" : "ENABLED");
        return modelRepository.save(model);
    }
}
